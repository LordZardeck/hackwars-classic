package com.hackwars.rewrite.client.utilities

import com.hackwars.rewrite.client.RewriteGameConnectionConfig
import com.hackwars.rewrite.client.RewriteLoginAuthGateway
import com.hackwars.rewrite.client.RewriteLoginAuthResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.RewriteServiceSession
import com.hackwars.rewrite.client.RewriteServiceSessionGateway
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.shell.RewriteShellWindowHost
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientComputerLogEntry
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientLogState
import com.hackwars.rewrite.protocol.ClientPersonalSettingsResponse
import com.hackwars.rewrite.protocol.ClientPreferenceState
import com.hackwars.rewrite.protocol.ClientPlayerProfileView
import com.hackwars.rewrite.protocol.ClientRequestPersonalSettingsPayload
import com.hackwars.rewrite.protocol.ClientSavePersonalSettingsPayload
import com.hackwars.rewrite.protocol.ClientSetPreferencePayload
import com.hackwars.rewrite.protocol.ClientSetPreferenceResponse
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.time.Instant
import javax.swing.JInternalFrame
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JTextArea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteUtilitiesTest {
    @Test
    fun requestSetPreferenceHelperSendsExpectedPayload() = runTest {
        val sessionGateway = FakeUtilitySessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )

        val deferred = backgroundScope.async {
            controller.requestSetPreference("network", "false")
        }
        runCurrent()

        val command = sessionGateway.requireLatestSession(RewriteService.GAME).sentFrames.single().command!!
        val payload = RewriteClientJson.decode(
            ClientSetPreferencePayload.serializer(),
            command.payload.toByteArray(),
        )
        assertEquals("setpreferences", command.command_name)
        assertEquals("network", payload.key)
        assertEquals("false", payload.value)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientSetPreferenceResponse.serializer(),
                    ClientSetPreferenceResponse(
                        key = "network",
                        value = "false",
                        version = 2,
                    ),
                ),
            ),
        )
        runCurrent()

        val result = deferred.await()
        assertTrue(result is com.hackwars.rewrite.client.RewriteGameCommandResult.Success)
    }

    @Test
    fun legacyCheckboxAndTriStateDefaultsMatchLockedBehavior() {
        assertTrue(legacyCheckboxPreferenceSelected(null))
        assertTrue(legacyCheckboxPreferenceSelected(""))
        assertTrue(legacyCheckboxPreferenceSelected("true"))
        assertFalse(legacyCheckboxPreferenceSelected("false"))
        assertEquals("true", normalizeLegacyCheckboxPreference(null))
        assertEquals("false", normalizeLegacyCheckboxPreference("false"))
        assertEquals(RewritePreferenceTriStateValue.ASK, RewritePreferenceTriStateValue.fromPersisted(null))
        assertEquals(RewritePreferenceTriStateValue.ASK, RewritePreferenceTriStateValue.fromPersisted(""))
        assertEquals(RewritePreferenceTriStateValue.ALWAYS, RewritePreferenceTriStateValue.fromPersisted("always"))
    }

    @Test
    fun startupCoordinatorLaunchesNetworkAndLogWindowOncePerSessionAndIgnoresTutorialPreference() {
        val launchedCommands = mutableListOf<com.hackwars.rewrite.client.shell.RewriteShellCommand>()
        val coordinator = RewriteStartupUtilityCoordinator(launchedCommands::add)
        val shellState = ClientGameSnapshot(
            id = "192.0.2.10",
            identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
            preferences = ClientPreferenceState(
                values = mapOf(
                    "attacktutorial" to "false",
                ),
            ),
            logs = ClientLogState(
                entries = listOf(
                    ClientComputerLogEntry(
                        createdAtEpochMillis = 1L,
                        renderedLine = "Boot complete",
                        sourceIp = "192.0.2.10",
                    ),
                ),
            ),
        )

        coordinator.noteAuthenticatedSessionReady("192.0.2.10")
        coordinator.maybeLaunch(
            route = com.hackwars.rewrite.clientmodel.RewriteClientRoute.DESKTOP,
            playerIp = "192.0.2.10",
            shellState = shellState,
        )
        coordinator.maybeLaunch(
            route = com.hackwars.rewrite.clientmodel.RewriteClientRoute.DESKTOP,
            playerIp = "192.0.2.10",
            shellState = shellState,
        )

        assertEquals(
            listOf(
                com.hackwars.rewrite.client.shell.RewriteShellCommand.NETWORK,
                com.hackwars.rewrite.client.shell.RewriteShellCommand.LOG_WINDOW,
            ),
            launchedCommands,
        )
    }

    @Test
    fun preferencesApplyOnlyDispatchesChangedKeys() = runTest {
        val sessionGateway = FakeUtilitySessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.snapshot(
                gameStateId = "192.0.2.10",
                sequence = 1,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(
                        id = "192.0.2.10",
                        identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                        preferences = ClientPreferenceState(
                            values = mapOf(
                                "network" to "true",
                                "logwindow" to "false",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val window = RewritePreferencesWindow(controller)
        val logWindowCheckBox = findComponent(window, "rewrite-preferences-option-logwindow") as JCheckBox
        logWindowCheckBox.doClick()
        val applyButton = findComponent(window, "rewrite-preferences-apply-button") as JButton
        applyButton.doClick()

        waitUntil { sessionGateway.latestSession(RewriteService.GAME)?.sentFrames?.isNotEmpty() == true }
        val command = sessionGateway.latestSession(RewriteService.GAME)!!.sentFrames.single().command!!
        val payload = RewriteClientJson.decode(
            ClientSetPreferencePayload.serializer(),
            command.payload.toByteArray(),
        )
        assertEquals("logwindow", payload.key)
        assertEquals("true", payload.value)

        window.dispose()
        controller.shutdown()
    }

    @Test
    fun requestPersonalSettingsHelperSendsExpectedPayload() = runTest {
        val sessionGateway = FakeUtilitySessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )

        val deferred = backgroundScope.async {
            controller.requestPersonalSettings()
        }
        runCurrent()

        val command = sessionGateway.requireLatestSession(RewriteService.GAME).sentFrames.single().command!!
        val payload = RewriteClientJson.decode(
            ClientRequestPersonalSettingsPayload.serializer(),
            command.payload.toByteArray(),
        )
        assertEquals("requestpersonalsettings", command.command_name)
        assertEquals("192.0.2.10", payload.ip)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientPersonalSettingsResponse.serializer(),
                    ClientPersonalSettingsResponse(
                        stateId = "192.0.2.10",
                        profile = ClientPlayerProfileView(
                            displayName = "LOCALUSER",
                            imagePath = "images/nopic.png",
                            description = "Profile description",
                            location = "UGOPNet",
                        ),
                    ),
                ),
            ),
        )
        runCurrent()

        val result = deferred.await()
        assertTrue(result is com.hackwars.rewrite.client.RewriteGameCommandResult.Success)
        assertEquals("LOCALUSER", (result as com.hackwars.rewrite.client.RewriteGameCommandResult.Success).value.profile.displayName)
    }

    @Test
    fun savePersonalSettingsHelperSendsExpectedPayload() = runTest {
        val sessionGateway = FakeUtilitySessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )

        val deferred = backgroundScope.async {
            controller.savePersonalSettings(
                imagePath = "images/Bill_001.png",
                description = "Pilot profile",
                location = "UGOPNet",
            )
        }
        runCurrent()

        val command = sessionGateway.requireLatestSession(RewriteService.GAME).sentFrames.single().command!!
        val payload = RewriteClientJson.decode(
            ClientSavePersonalSettingsPayload.serializer(),
            command.payload.toByteArray(),
        )
        assertEquals("setpersonalsettings", command.command_name)
        assertEquals("192.0.2.10", payload.ip)
        assertEquals("images/Bill_001.png", payload.imagePath)
        assertEquals("Pilot profile", payload.description)
        assertEquals("UGOPNet", payload.location)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientPersonalSettingsResponse.serializer(),
                    ClientPersonalSettingsResponse(
                        stateId = "192.0.2.10",
                        profile = ClientPlayerProfileView(
                            displayName = "LOCALUSER",
                            imagePath = "images/Bill_001.png",
                            description = "Pilot profile",
                            location = "UGOPNet",
                        ),
                    ),
                ),
            ),
        )
        runCurrent()

        val result = deferred.await()
        assertTrue(result is com.hackwars.rewrite.client.RewriteGameCommandResult.Success)
        assertEquals("Pilot profile", (result as com.hackwars.rewrite.client.RewriteGameCommandResult.Success).value.profile.description)
    }

    @Test
    fun personalSettingsLaunchesFreshWindowsAndPreservesDuplicateBehavior() {
        val controller = RewriteRootController(
            gameConnectionConfig = RewriteGameConnectionConfig(),
            authGateway = FixedUtilityAuthGateway(),
            sessionGateway = FakeUtilitySessionGateway(),
        )
        val host = FakeWindowHost()
        controller.attachShellHost(host)

        controller.launchShellCommand(RewriteShellCommand.PERSONAL_SETTINGS)
        controller.launchShellCommand(RewriteShellCommand.PERSONAL_SETTINGS)

        assertEquals(2, host.showWindowCalls.size)
        assertTrue(host.showWindowCalls.all { it.name == "rewrite-personal-settings-window" })
        controller.shutdown()
    }

    @Test
    fun logWindowRendersDecodedEntriesAndStaysReadOnly() {
        val controller = RewriteRootController(
            authGateway = FixedUtilityAuthGateway(),
            sessionGateway = FakeUtilitySessionGateway(),
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.snapshot(
                gameStateId = "192.0.2.10",
                sequence = 1,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(
                        id = "192.0.2.10",
                        identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                        logs = ClientLogState(
                            entries = listOf(
                                ClientComputerLogEntry(
                                    createdAtEpochMillis = 1L,
                                    renderedLine = "First line",
                                    sourceIp = "198.51.100.71",
                                ),
                                ClientComputerLogEntry(
                                    createdAtEpochMillis = 2L,
                                    renderedLine = "Second line",
                                    sourceIp = "198.51.100.72",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val window = RewriteLogWindow(controller)
        val textArea = findComponent(window, "rewrite-log-window-text") as JTextArea
        assertFalse(textArea.isEditable)
        assertEquals("First line\nSecond line", textArea.text)

        window.dispose()
        controller.shutdown()
    }

    private fun testController(
        sessionGateway: RewriteServiceSessionGateway,
        scheduler: TestCoroutineScheduler,
    ): RewriteRootController {
        return RewriteRootController(
            gameConnectionConfig = RewriteGameConnectionConfig(),
            authGateway = FixedUtilityAuthGateway(),
            sessionGateway = sessionGateway,
            workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler)),
        )
    }

    private fun waitUntil(timeoutMillis: Long = 3_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) {
                return
            }
            Thread.sleep(20)
        }
        assertTrue(predicate())
    }

    private fun findComponent(root: Component, name: String): Component {
        if (root.name == name) {
            return root
        }
        return (root as? java.awt.Container)
            ?.components
            ?.asSequence()
            ?.mapNotNull { child ->
                runCatching { findComponent(child, name) }.getOrNull()
            }
            ?.firstOrNull()
            ?: error("Unable to find component named $name")
    }

    private class FixedUtilityAuthGateway : RewriteLoginAuthGateway {
        override suspend fun authenticate(email: String, password: CharArray): RewriteLoginAuthResult {
            return RewriteLoginAuthResult.success("PF-LOCAL", "SESSION-LOCAL")
        }
    }

    private class FakeWindowHost : RewriteShellWindowHost {
        val showWindowCalls = mutableListOf<JInternalFrame>()

        override fun showWindow(frame: JInternalFrame) {
            showWindowCalls += frame
        }

        override fun focusWindow(frame: JInternalFrame) = Unit

        override fun disposeAllWindows() = Unit

        override fun renderTaskBar(state: com.hackwars.rewrite.client.shell.RewriteShellTaskBarState) = Unit
    }

    private class FakeUtilitySessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeUtilitySession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeUtilitySession(service, onInboundFrame).also { sessions += it }
        }

        fun requireLatestSession(service: RewriteService): FakeUtilitySession {
            return sessions.last { it.service == service }
        }

        fun latestSession(service: RewriteService): FakeUtilitySession? {
            return sessions.lastOrNull { it.service == service }
        }
    }

    private class FakeUtilitySession(
        override val service: RewriteService,
        private val onInboundFrame: (FrameEnvelope) -> Unit,
    ) : RewriteServiceSession {
        val sentFrames = mutableListOf<FrameEnvelope>()

        override suspend fun send(frame: FrameEnvelope) {
            sentFrames += frame
        }

        override fun receive(frame: FrameEnvelope) {
            onInboundFrame(frame)
        }

        override fun close() = Unit
    }
}
