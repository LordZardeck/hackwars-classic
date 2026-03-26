package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientComputerLogEntry
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientLogState
import com.hackwars.rewrite.protocol.ClientNetworkState
import com.hackwars.rewrite.protocol.ClientPreferenceState
import com.hackwars.rewrite.protocol.ClientSetPreferencePayload
import com.hackwars.rewrite.protocol.ClientSetPreferenceResponse
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.ErrorEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.time.Instant
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteUtilitiesUiTest {
    @Test
    fun preferencesLaunchesRealWindowReusesInstanceAndRendersLegacySections() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = utilitiesReadyFrame()
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PREFERENCES)
                frame.controller.launchShellCommand(RewriteShellCommand.PREFERENCES)
            }

            val preferencesWindow = waitForWindow(frame, "rewrite-preferences-window")
            waitUntil {
                invokeAndWaitResult {
                    frame.desktopPane.allFrames.count { it.name == "rewrite-preferences-window" } == 1
                }
            }

            assertEquals("Preferences", preferencesWindow.title)
            val sectionTexts = findComponents(preferencesWindow)
                .filterIsInstance<JLabel>()
                .filter { it.name?.startsWith("rewrite-preferences-section-") == true }
                .map { it.text }
            assertEquals(
                listOf(
                    " Startup Options",
                    " Port Management Options",
                    " Equipment Manager Options",
                    " Scan/Attack/Redirect Options",
                    " Commodity Converstion Options",
                    " Banking Options",
                    " Web Browser",
                ),
                sectionTexts,
            )
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun preferencesSuccessfulApplyUpdatesStatusAndClearsDirtyState() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUtilityUiSessionGateway()
        val frame = utilitiesReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PREFERENCES)
            }

            val preferencesWindow = waitForWindow(frame, "rewrite-preferences-window")
            SwingUtilities.invokeAndWait {
                checkBox(preferencesWindow, "rewrite-preferences-option-logwindow").doClick()
                button(preferencesWindow, "rewrite-preferences-apply-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true }
            val command = sessionGateway.latestGameSession()!!.sentFrames.single().command!!
            val payload = RewriteClientJson.decode(
                ClientSetPreferencePayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals("logwindow", payload.key)
            assertEquals("true", payload.value)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    payload = RewriteClientJson.encode(
                        ClientSetPreferenceResponse.serializer(),
                        ClientSetPreferenceResponse(
                            key = "logwindow",
                            value = "true",
                            version = 3,
                        ),
                    ),
                ),
            )

            waitUntil {
                label(preferencesWindow, "rewrite-preferences-status").text == "Preferences saved." &&
                    !button(preferencesWindow, "rewrite-preferences-apply-button").isEnabled
            }
            assertEquals(" ", label(preferencesWindow, "rewrite-preferences-error").text)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun preferencesFailedApplyKeepsWindowOpenAndShowsInlineError() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUtilityUiSessionGateway()
        val frame = utilitiesReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PREFERENCES)
            }

            val preferencesWindow = waitForWindow(frame, "rewrite-preferences-window")
            SwingUtilities.invokeAndWait {
                @Suppress("UNCHECKED_CAST")
                (comboBox(preferencesWindow, "rewrite-preferences-option-appnote") as JComboBox<String>).selectedItem = "Never"
                button(preferencesWindow, "rewrite-preferences-apply-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true }
            val command = sessionGateway.latestGameSession()!!.sentFrames.single().command!!

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                    error = ErrorEnvelope(
                        code = "PREFERENCE_REJECTED",
                        message = "Preference save failed.",
                        retryable = false,
                    ),
                ),
            )

            waitUntil {
                label(preferencesWindow, "rewrite-preferences-error").text == "Preference save failed."
            }
            assertTrue(preferencesWindow.isDisplayable)
            assertTrue(button(preferencesWindow, "rewrite-preferences-apply-button").isEnabled)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun logWindowLaunchesRealWindowReusesInstanceAndShowsDecodedLogs() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = utilitiesReadyFrame(
            shellState = utilityShellState(
                preferences = mapOf("network" to "false", "logwindow" to "false"),
                logs = listOf(
                    ClientComputerLogEntry(
                        createdAtEpochMillis = 1L,
                        renderedLine = "Decoded log line",
                        sourceIp = "LOCAL-IP",
                    ),
                ),
            ),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.LOG_WINDOW)
                frame.controller.launchShellCommand(RewriteShellCommand.LOG_WINDOW)
            }

            val logWindow = waitForWindow(frame, "rewrite-log-window")
            waitUntil {
                invokeAndWaitResult {
                    frame.desktopPane.allFrames.count { it.name == "rewrite-log-window" } == 1
                }
            }

            assertEquals("Log Window", logWindow.title)
            assertEquals("Decoded log line", (findComponent(logWindow, "rewrite-log-window-text") as JTextArea).text)
            assertFalse((findComponent(logWindow, "rewrite-log-window-text") as JTextArea).isEditable)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun startupPreferencesAutoOpenNetworkAndLogWindowAfterSuccessfulBootstrap() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUtilityUiSessionGateway()
        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = sessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            frame.controller.submitLogin("localuser", "password1234".toCharArray())
            waitUntil { sessionGateway.latestGameSession() != null }

            val session = sessionGateway.latestGameSession()!!
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.authAccepted(
                    connectionId = "conn-1",
                    playFabId = "PF-LOCALUSER",
                    playerIp = "LOCAL-IP",
                    heartbeatInterval = kotlin.time.Duration.parse("15s"),
                    sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
                ),
            )
            frame.controller.accept(
                RewriteService.GAME,
                snapshotFrame(
                    utilityShellState(
                        preferences = emptyMap(),
                        logs = listOf(
                            ClientComputerLogEntry(
                                createdAtEpochMillis = 1L,
                                renderedLine = "Startup log line",
                                sourceIp = "LOCAL-IP",
                            ),
                        ),
                    ),
                ),
            )

            waitUntil {
                invokeAndWaitResult {
                    frame.controller.route() == com.hackwars.rewrite.clientmodel.RewriteClientRoute.DESKTOP &&
                        frame.desktopPane.allFrames.map { it.name }.toSet().containsAll(
                            setOf(
                                "rewrite-shell-window-network",
                                "rewrite-log-window",
                            ),
                        )
                }
            }
            assertEquals(1, session.sentFrames.count { it.auth_request != null })
        } finally {
            disposeFrame(frame)
        }
    }

    private fun utilitiesReadyFrame(
        sessionGateway: RewriteServiceSessionGateway = NoOpRewriteServiceSessionGateway,
        shellState: ClientGameSnapshot = utilityShellState(),
    ): RewriteRootFrame {
        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = sessionGateway,
                ),
            ).apply { isVisible = true }
        }
        frame.controller.store.showDesktop()
        frame.controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "LOCAL-IP",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        frame.controller.accept(RewriteService.GAME, snapshotFrame(shellState))
        waitUntil { frame.desktopPane.isShowing && frame.jMenuBar != null }
        return frame
    }

    private fun utilityShellState(
        preferences: Map<String, String> = mapOf(
            "network" to "false",
            "logwindow" to "false",
        ),
        logs: List<ClientComputerLogEntry> = emptyList(),
    ): ClientGameSnapshot {
        return ClientGameSnapshot(
            id = "LOCAL-IP",
            identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
            preferences = ClientPreferenceState(values = preferences),
            logs = ClientLogState(entries = logs),
            network = ClientNetworkState(
                currentNetworkName = "UGOPNet",
                allowedNetworks = setOf("ProgNet"),
            ),
        )
    }

    private fun snapshotFrame(snapshot: ClientGameSnapshot): FrameEnvelope {
        return RewriteFrames.snapshot(
            gameStateId = snapshot.id,
            sequence = snapshot.version,
            payload = RewriteClientJson.encode(ClientGameSnapshot.serializer(), snapshot),
        )
    }

    private fun waitForWindow(frame: RewriteRootFrame, name: String): JInternalFrame {
        waitUntil {
            invokeAndWaitResult {
                frame.desktopPane.allFrames.any { it.name == name }
            }
        }
        return invokeAndWaitResult {
            frame.desktopPane.allFrames.first { it.name == name }
        }
    }

    private fun disposeFrame(frame: RewriteRootFrame) {
        SwingUtilities.invokeAndWait {
            frame.dispose()
        }
    }

    private inline fun <T> invokeAndWaitResult(crossinline block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait {
            result = runCatching { block() }
        }
        return result!!.getOrThrow()
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

    private fun label(root: Component, name: String): JLabel {
        return findComponent(root, name) as? JLabel
            ?: error("Unable to find JLabel named $name")
    }

    private fun checkBox(root: Component, name: String): JCheckBox {
        return findComponent(root, name) as? JCheckBox
            ?: error("Unable to find JCheckBox named $name")
    }

    private fun comboBox(root: Component, name: String): JComboBox<*> {
        return findComponent(root, name) as? JComboBox<*>
            ?: error("Unable to find JComboBox named $name")
    }

    private fun button(root: Component, name: String): JButton {
        return findComponent(root, name) as? JButton
            ?: error("Unable to find JButton named $name")
    }

    private fun findComponent(root: Component, name: String): Component? {
        if (root.name == name) {
            return root
        }
        val container = root as? Container ?: return null
        container.components.forEach { child ->
            val match = findComponent(child, name)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun findComponents(root: Component): List<Component> {
        val children = (root as? Container)?.components.orEmpty().flatMap(::findComponents)
        return listOf(root) + children
    }

    private class FakeUtilityUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeUtilityUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeUtilityUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakeUtilityUiSession? = sessions.lastOrNull { it.service == RewriteService.GAME }
    }

    private class FakeUtilityUiSession(
        override val service: RewriteService,
        private val onInboundFrame: (FrameEnvelope) -> Unit,
    ) : RewriteServiceSession {
        val sentFrames = mutableListOf<FrameEnvelope>()
        var closed: Boolean = false

        override suspend fun send(frame: FrameEnvelope) {
            sentFrames += frame
        }

        override fun receive(frame: FrameEnvelope) {
            onInboundFrame(frame)
        }

        override fun close() {
            closed = true
        }
    }
}
