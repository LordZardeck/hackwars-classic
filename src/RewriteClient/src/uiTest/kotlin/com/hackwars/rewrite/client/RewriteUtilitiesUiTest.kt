package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.testsupport.rewriteUiAuthenticatedDesktopFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiDisposeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiFindComponents
import com.hackwars.rewrite.client.testsupport.rewriteUiFindNamedComponent
import com.hackwars.rewrite.client.testsupport.rewriteUiInvokeAndWaitResult
import com.hackwars.rewrite.client.testsupport.rewriteUiSnapshotFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForWindow
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitUntil
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientComputerLogEntry
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientLogState
import com.hackwars.rewrite.protocol.ClientPersonalSettingsResponse
import com.hackwars.rewrite.protocol.ClientPlayerProfileView
import com.hackwars.rewrite.protocol.ClientNetworkState
import com.hackwars.rewrite.protocol.ClientPreferenceState
import com.hackwars.rewrite.protocol.ClientRequestPersonalSettingsPayload
import com.hackwars.rewrite.protocol.ClientSavePersonalSettingsPayload
import com.hackwars.rewrite.protocol.ClientSetPreferencePayload
import com.hackwars.rewrite.protocol.ClientSetPreferenceResponse
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.ErrorEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.time.Instant
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JTextField
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

            val preferencesWindow = rewriteUiWaitForWindow(frame, "rewrite-preferences-window")
            rewriteUiWaitUntil {
                rewriteUiInvokeAndWaitResult {
                    frame.desktopPane.allFrames.count { it.name == "rewrite-preferences-window" } == 1
                }
            }

            assertEquals("Preferences", preferencesWindow.title)
            val sectionTexts = rewriteUiFindComponents(preferencesWindow)
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
            rewriteUiDisposeFrame(frame)
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

            val preferencesWindow = rewriteUiWaitForWindow(frame, "rewrite-preferences-window")
            SwingUtilities.invokeAndWait {
                checkBox(preferencesWindow, "rewrite-preferences-option-logwindow").doClick()
                button(preferencesWindow, "rewrite-preferences-apply-button").doClick()
            }

            rewriteUiWaitUntil { sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true }
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

            rewriteUiWaitUntil {
                label(preferencesWindow, "rewrite-preferences-status").text == "Preferences saved." &&
                    !button(preferencesWindow, "rewrite-preferences-apply-button").isEnabled
            }
            assertEquals(" ", label(preferencesWindow, "rewrite-preferences-error").text)
        } finally {
            rewriteUiDisposeFrame(frame)
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

            val preferencesWindow = rewriteUiWaitForWindow(frame, "rewrite-preferences-window")
            SwingUtilities.invokeAndWait {
                @Suppress("UNCHECKED_CAST")
                (comboBox(preferencesWindow, "rewrite-preferences-option-appnote") as JComboBox<String>).selectedItem = "Never"
                button(preferencesWindow, "rewrite-preferences-apply-button").doClick()
            }

            rewriteUiWaitUntil { sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true }
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

            rewriteUiWaitUntil {
                label(preferencesWindow, "rewrite-preferences-error").text == "Preference save failed."
            }
            assertTrue(preferencesWindow.isDisplayable)
            assertTrue(button(preferencesWindow, "rewrite-preferences-apply-button").isEnabled)
        } finally {
            rewriteUiDisposeFrame(frame)
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
                        sourceIp = "192.0.2.10",
                    ),
                ),
            ),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.LOG_WINDOW)
                frame.controller.launchShellCommand(RewriteShellCommand.LOG_WINDOW)
            }

            val logWindow = rewriteUiWaitForWindow(frame, "rewrite-log-window")
            rewriteUiWaitUntil {
                rewriteUiInvokeAndWaitResult {
                    frame.desktopPane.allFrames.count { it.name == "rewrite-log-window" } == 1
                }
            }

            assertEquals("Log Window", logWindow.title)
            assertEquals("Decoded log line", (findComponent(logWindow, "rewrite-log-window-text") as JTextArea).text)
            assertFalse((findComponent(logWindow, "rewrite-log-window-text") as JTextArea).isEditable)
        } finally {
            rewriteUiDisposeFrame(frame)
        }
    }

    @Test
    fun startupPreferencesAutoOpenNetworkAndLogWindowAfterSuccessfulBootstrap() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUtilityUiSessionGateway()
        val frame = rewriteUiInvokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = sessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            frame.controller.submitLogin("localuser", "password1234".toCharArray())
            rewriteUiWaitUntil { sessionGateway.latestGameSession() != null }

            val session = sessionGateway.latestGameSession()!!
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.authAccepted(
                    connectionId = "conn-1",
                    playFabId = "PF-LOCALUSER",
                    playerIp = "192.0.2.10",
                    heartbeatInterval = kotlin.time.Duration.parse("15s"),
                    sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
                ),
            )
            frame.controller.accept(
                RewriteService.GAME,
                rewriteUiSnapshotFrame(
                    utilityShellState(
                        preferences = emptyMap(),
                        logs = listOf(
                            ClientComputerLogEntry(
                                createdAtEpochMillis = 1L,
                                renderedLine = "Startup log line",
                                sourceIp = "192.0.2.10",
                            ),
                        ),
                    ),
                ),
            )

            rewriteUiWaitUntil {
                rewriteUiInvokeAndWaitResult {
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
            rewriteUiDisposeFrame(frame)
        }
    }

    @Test
    fun personalSettingsLaunchesFreshWindowsAndRendersLoadedProfile() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUtilityUiSessionGateway()
        val frame = utilitiesReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PERSONAL_SETTINGS)
                frame.controller.launchShellCommand(RewriteShellCommand.PERSONAL_SETTINGS)
            }

            rewriteUiWaitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.count { it.command?.command_name == "requestpersonalsettings" } == 2
            }

            val commandPayloads = sessionGateway.latestGameSession()!!.sentFrames
                .filter { it.command?.command_name == "requestpersonalsettings" }
            commandPayloads.forEach { frameEnvelope ->
                val command = frameEnvelope.command!!
                frame.controller.accept(
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
                                    description = "Pilot profile",
                                    location = "UGOPNet",
                                ),
                            ),
                        ),
                    ),
                )
            }

            rewriteUiWaitUntil {
                rewriteUiInvokeAndWaitResult {
                    frame.desktopPane.allFrames
                        .filter { it.name == "rewrite-personal-settings-window" }
                        .all { window ->
                            window.title == "Personal Settings - LOCALUSER" &&
                                (rewriteUiFindNamedComponent(window, "rewrite-personal-settings-description") as JTextArea).text == "Pilot profile" &&
                                (rewriteUiFindNamedComponent(window, "rewrite-personal-settings-location") as JTextField).text == "UGOPNet"
                        }
                }
            }

            val windows = rewriteUiInvokeAndWaitResult {
                frame.desktopPane.allFrames.filter { it.name == "rewrite-personal-settings-window" }
            }
            assertEquals(2, windows.size)
            val firstWindow = windows.first()
            assertEquals("Personal Settings - LOCALUSER", firstWindow.title)
            assertEquals("Pilot profile", (rewriteUiFindNamedComponent(firstWindow, "rewrite-personal-settings-description") as JTextArea).text)
            assertEquals("UGOPNet", (rewriteUiFindNamedComponent(firstWindow, "rewrite-personal-settings-location") as JTextField).text)
            assertTrue(rewriteUiFindNamedComponent(firstWindow, "rewrite-personal-settings-prev-button") is JButton)
            assertTrue(rewriteUiFindNamedComponent(firstWindow, "rewrite-personal-settings-save-image-button") is JButton)
            assertTrue(rewriteUiFindNamedComponent(firstWindow, "rewrite-personal-settings-save-description-button") is JButton)
            assertTrue(rewriteUiFindNamedComponent(firstWindow, "rewrite-personal-settings-save-location-button") is JButton)
        } finally {
            rewriteUiDisposeFrame(frame)
        }
    }

    @Test
    fun personalSettingsSaveButtonsSendRetainedPayloads() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUtilityUiSessionGateway()
        val frame = utilitiesReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PERSONAL_SETTINGS)
            }

            val personalSettingsWindow = rewriteUiWaitForWindow(frame, "rewrite-personal-settings-window")
            rewriteUiWaitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.any { it.command?.command_name == "requestpersonalsettings" } == true
            }
            val initialRequest = sessionGateway.latestGameSession()!!.sentFrames
                .first { it.command?.command_name == "requestpersonalsettings" }
                .command!!
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = initialRequest.command_id,
                    payload = RewriteClientJson.encode(
                        ClientPersonalSettingsResponse.serializer(),
                        ClientPersonalSettingsResponse(
                            stateId = "192.0.2.10",
                            profile = ClientPlayerProfileView(
                                displayName = "LOCALUSER",
                                imagePath = "images/nopic.png",
                                description = "Original description",
                                location = "Original location",
                            ),
                        ),
                    ),
                ),
            )

            rewriteUiWaitUntil {
                (rewriteUiFindNamedComponent(personalSettingsWindow, "rewrite-personal-settings-save-image-button") as JButton).isEnabled
            }

            SwingUtilities.invokeAndWait {
                (rewriteUiFindNamedComponent(personalSettingsWindow, "rewrite-personal-settings-next-button") as JButton).doClick()
                (rewriteUiFindNamedComponent(personalSettingsWindow, "rewrite-personal-settings-save-image-button") as JButton).doClick()
            }

            rewriteUiWaitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.count { it.command?.command_name == "setpersonalsettings" } == 1
            }
            val imageSaveCommand = sessionGateway.latestGameSession()!!.sentFrames
                .first { it.command?.command_name == "setpersonalsettings" }
                .command!!
            val imageSavePayload = RewriteClientJson.decode(
                ClientSavePersonalSettingsPayload.serializer(),
                imageSaveCommand.payload.toByteArray(),
            )
            assertEquals("images/Snow_001.png", imageSavePayload.imagePath)
            assertEquals("Original description", imageSavePayload.description)
            assertEquals("Original location", imageSavePayload.location)
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = imageSaveCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientPersonalSettingsResponse.serializer(),
                        ClientPersonalSettingsResponse(
                            stateId = "192.0.2.10",
                            profile = ClientPlayerProfileView(
                                displayName = "LOCALUSER",
                                imagePath = "images/Snow_001.png",
                                description = "Original description",
                                location = "Original location",
                            ),
                        ),
                    ),
                ),
            )
            rewriteUiWaitUntil {
                (rewriteUiFindNamedComponent(personalSettingsWindow, "rewrite-personal-settings-save-description-button") as JButton).isEnabled
            }

            SwingUtilities.invokeAndWait {
                (rewriteUiFindNamedComponent(personalSettingsWindow, "rewrite-personal-settings-description") as JTextArea).text = "Updated description"
                (rewriteUiFindNamedComponent(personalSettingsWindow, "rewrite-personal-settings-save-description-button") as JButton).doClick()
            }
            rewriteUiWaitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.count { it.command?.command_name == "setpersonalsettings" } == 2
            }
            val descriptionSaveCommand = sessionGateway.latestGameSession()!!.sentFrames
                .filter { it.command?.command_name == "setpersonalsettings" }[1]
                .command!!
            val descriptionSavePayload = RewriteClientJson.decode(
                ClientSavePersonalSettingsPayload.serializer(),
                descriptionSaveCommand.payload.toByteArray(),
            )
            assertEquals("images/Snow_001.png", descriptionSavePayload.imagePath)
            assertEquals("Updated description", descriptionSavePayload.description)
            assertEquals("Original location", descriptionSavePayload.location)
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = descriptionSaveCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientPersonalSettingsResponse.serializer(),
                        ClientPersonalSettingsResponse(
                            stateId = "192.0.2.10",
                            profile = ClientPlayerProfileView(
                                displayName = "LOCALUSER",
                                imagePath = "images/Snow_001.png",
                                description = "Updated description",
                                location = "Original location",
                            ),
                        ),
                    ),
                ),
            )
            rewriteUiWaitUntil {
                (rewriteUiFindNamedComponent(personalSettingsWindow, "rewrite-personal-settings-save-location-button") as JButton).isEnabled
            }

            SwingUtilities.invokeAndWait {
                (rewriteUiFindNamedComponent(personalSettingsWindow, "rewrite-personal-settings-location") as JTextField).text = "Updated location"
                (rewriteUiFindNamedComponent(personalSettingsWindow, "rewrite-personal-settings-save-location-button") as JButton).doClick()
            }
            rewriteUiWaitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.count { it.command?.command_name == "setpersonalsettings" } == 3
            }
            val locationSaveCommand = sessionGateway.latestGameSession()!!.sentFrames
                .filter { it.command?.command_name == "setpersonalsettings" }[2]
                .command!!
            val locationSavePayload = RewriteClientJson.decode(
                ClientSavePersonalSettingsPayload.serializer(),
                locationSaveCommand.payload.toByteArray(),
            )
            assertEquals("images/Snow_001.png", locationSavePayload.imagePath)
            assertEquals("Updated description", locationSavePayload.description)
            assertEquals("Updated location", locationSavePayload.location)

            assertTrue(personalSettingsWindow.isDisplayable)
            assertEquals("Personal Settings - LOCALUSER", personalSettingsWindow.title)
        } finally {
            rewriteUiDisposeFrame(frame)
        }
    }

    private fun utilitiesReadyFrame(
        sessionGateway: RewriteServiceSessionGateway = NoOpRewriteServiceSessionGateway,
        shellState: ClientGameSnapshot = utilityShellState(),
    ): RewriteRootFrame {
        return rewriteUiAuthenticatedDesktopFrame(
            sessionGateway = sessionGateway,
            snapshot = shellState,
        )
    }

    private fun utilityShellState(
        preferences: Map<String, String> = mapOf(
            "network" to "false",
            "logwindow" to "false",
        ),
        logs: List<ClientComputerLogEntry> = emptyList(),
    ): ClientGameSnapshot {
        return ClientGameSnapshot(
            id = "192.0.2.10",
            identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
            preferences = ClientPreferenceState(values = preferences),
            logs = ClientLogState(entries = logs),
            network = ClientNetworkState(
                currentNetworkName = "UGOPNet",
                allowedNetworks = setOf("ProgNet"),
            ),
        )
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
        return rewriteUiFindNamedComponent(root, name)
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
