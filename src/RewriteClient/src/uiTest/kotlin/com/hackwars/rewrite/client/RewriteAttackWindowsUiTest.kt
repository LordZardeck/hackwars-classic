package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientAttackCancelResponse
import com.hackwars.rewrite.protocol.ClientAttackMessageUiEvent
import com.hackwars.rewrite.protocol.ClientAttackPaneType
import com.hackwars.rewrite.protocol.ClientAttackSessionKind
import com.hackwars.rewrite.protocol.ClientAttackStartResponse
import com.hackwars.rewrite.protocol.ClientAttackSessionState
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientProgramLifecycleStatus
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.ClientRequestAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestCancelAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestDirectoryPayload
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.time.Instant
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JSpinner
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteAttackWindowsUiTest {
    @Test
    fun attackAndRedirectLaunchAsRealWindowsAndReuseSingleInstance() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeAttackUiSessionGateway()
        val frame = attackReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.ATTACK_PORT)
                frame.controller.launchShellCommand(RewriteShellCommand.ATTACK_PORT)
                frame.controller.launchShellCommand(RewriteShellCommand.REDIRECT_PORT)
                frame.controller.launchShellCommand(RewriteShellCommand.REDIRECT_PORT)
            }

            val attackWindow = waitForWindow(frame, "rewrite-shell-window-attack_port")
            val redirectWindow = waitForWindow(frame, "rewrite-shell-window-redirect_port")

            waitUntil {
                frame.desktopPane.allFrames.count { it.name == "rewrite-shell-window-attack_port" } == 1 &&
                    frame.desktopPane.allFrames.count { it.name == "rewrite-shell-window-redirect_port" } == 1
            }

            assertTrue(findComponent(attackWindow, "rewrite-attack-advanced-panel") != null)
            assertNull(findComponent(redirectWindow, "rewrite-attack-advanced-panel"))
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun attackChooserAndAcceptedAttackCorrelateMessagesAndProgramUpdates() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeAttackUiSessionGateway()
        val frame = attackReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.ATTACK_PORT)
            }

            val attackWindow = waitForWindow(frame, "rewrite-shell-window-attack_port")
            SwingUtilities.invokeAndWait {
                button(attackWindow, "rewrite-attack-browse-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "requestdirectory" }
            val directoryCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val directoryPayload = RewriteClientJson.decode(
                ClientRequestDirectoryPayload.serializer(),
                directoryCommand.payload.toByteArray(),
            )
            assertEquals("/", directoryPayload.path)
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = directoryCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientDirectoryListingResponse.serializer(),
                        ClientDirectoryListingResponse(
                            stateId = "LOCAL-IP",
                            path = "/",
                            directories = listOf(
                                com.hackwars.rewrite.protocol.ClientDirectoryEntry(path = "/Store", name = "Store"),
                                com.hackwars.rewrite.protocol.ClientDirectoryEntry(path = "/Public", name = "Public"),
                            ),
                            files = listOf(
                                ClientStoredFile(
                                    path = "/bank.bin",
                                    name = "bank.bin",
                                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                                    compiledBinary = ClientCompiledBinaryMetadata(applicationKind = ClientApplicationKind.BANKING),
                                ),
                                ClientStoredFile(
                                    path = "/attack.bin",
                                    name = "attack.bin",
                                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                                    compiledBinary = ClientCompiledBinaryMetadata(applicationKind = ClientApplicationKind.ATTACK),
                                ),
                            ),
                            version = 2,
                        ),
                    ),
                ),
            )

            val chooser = waitForWindow(frame, "rewrite-attack-file-chooser-window")
            waitUntil { list(chooser, "rewrite-files-entry-list").model.size == 1 }
            assertEquals("bank.bin", list(chooser, "rewrite-files-entry-list").model.getElementAt(0).toString())

            SwingUtilities.invokeAndWait {
                list(chooser, "rewrite-files-entry-list").selectedIndex = 0
                setSegmentedIp(chooser, "10.0.0.9")
                button(chooser, "rewrite-attack-chooser-choose-button").doClick()
            }

            waitUntil { textField(attackWindow, "rewrite-attack-script-field").text == "bank.bin" }
            SwingUtilities.invokeAndWait {
                setSegmentedIp(attackWindow, "10.0.0.8")
                spinner(attackWindow, "rewrite-attack-target-port-spinner").value = 4
                button(attackWindow, "rewrite-attack-primary-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "requestattack" }
            val attackCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val attackPayload = RewriteClientJson.decode(
                ClientRequestAttackPayload.serializer(),
                attackCommand.payload.toByteArray(),
            )
            assertEquals("LOCAL-IP", attackPayload.sourceIp)
            assertEquals("10.0.0.8", attackPayload.targetIp)
            assertEquals(4, attackPayload.targetPort)
            assertEquals(listOf("/", "bank.bin"), attackPayload.scripts.first())
            assertEquals("10.0.0.9", (attackPayload.extraInfo[0] as com.hackwars.rewrite.protocol.ClientStringHookValue).value)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = attackCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientAttackStartResponse.serializer(),
                        ClientAttackStartResponse(
                            attackerStateId = "LOCAL-IP",
                            sourcePort = 6,
                            targetStateId = "10.0.0.8",
                            targetPort = 4,
                            accepted = true,
                            message = "Attack accepted.",
                            session = ClientAttackSessionState(
                                programId = "attack-program-1",
                                sourcePort = 6,
                                targetStateId = "10.0.0.8",
                                targetPort = 4,
                                sessionKind = ClientAttackSessionKind.ATTACK,
                                windowHandle = attackPayload.windowHandle ?: 0,
                            ),
                            version = 3,
                        ),
                    ),
                ),
            )

            waitUntil {
                button(attackWindow, "rewrite-attack-primary-button").text == "Cancel" &&
                    !attackWindow.isClosable
            }

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.gameUiEvent(
                    eventId = "attack-ui-1",
                    eventType = "attack_message",
                    payload = RewriteClientJson.encode(
                        com.hackwars.rewrite.protocol.ClientGameUiEvent.serializer(),
                        ClientAttackMessageUiEvent(
                            message = "Attack landed.",
                            port = 6,
                            ip = "10.0.0.8",
                            windowHandle = attackPayload.windowHandle,
                            paneType = ClientAttackPaneType.ATTACK,
                        ),
                    ),
                ),
            )
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.programUpdate(
                    programId = "attack-program-1",
                    programType = "attack",
                    status = hackwars.rewrite.v1.ProgramStatus.PROGRAM_STATUS_COMPLETED,
                    payload = RewriteClientJson.encode(
                        ClientProgramUpdate.serializer(),
                        ClientProgramUpdate(
                            programId = "attack-program-1",
                            programType = "attack",
                            status = ClientProgramLifecycleStatus.COMPLETED,
                        ),
                    ),
                ),
            )

            waitUntil {
                textArea(attackWindow, "rewrite-attack-transcript").text.contains("Attack landed.") &&
                    button(attackWindow, "rewrite-attack-primary-button").text == "Attack" &&
                    attackWindow.isClosable
            }
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun failedCancelKeepsRedirectWindowOpenAndShowsInlineError() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeAttackUiSessionGateway()
        val frame = attackReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.REDIRECT_PORT)
            }

            val redirectWindow = waitForWindow(frame, "rewrite-shell-window-redirect_port")
            SwingUtilities.invokeAndWait {
                setSegmentedIp(redirectWindow, "10.0.0.8")
                spinner(redirectWindow, "rewrite-attack-target-port-spinner").value = 4
                button(redirectWindow, "rewrite-attack-primary-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "requestattack" }
            val startCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val startPayload = RewriteClientJson.decode(
                ClientRequestAttackPayload.serializer(),
                startCommand.payload.toByteArray(),
            )
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = startCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientAttackStartResponse.serializer(),
                        ClientAttackStartResponse(
                            attackerStateId = "LOCAL-IP",
                            sourcePort = 7,
                            targetStateId = "10.0.0.8",
                            targetPort = 4,
                            accepted = true,
                            message = "Redirect accepted.",
                            session = ClientAttackSessionState(
                                programId = "redirect-program-1",
                                sourcePort = 7,
                                targetStateId = "10.0.0.8",
                                targetPort = 4,
                                sessionKind = ClientAttackSessionKind.REDIRECT,
                                windowHandle = startPayload.windowHandle ?: 0,
                            ),
                            version = 3,
                        ),
                    ),
                ),
            )

            waitUntil { button(redirectWindow, "rewrite-attack-primary-button").text == "Cancel" }
            SwingUtilities.invokeAndWait {
                button(redirectWindow, "rewrite-attack-primary-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "requestcancelattack" }
            val cancelCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val cancelPayload = RewriteClientJson.decode(
                ClientRequestCancelAttackPayload.serializer(),
                cancelCommand.payload.toByteArray(),
            )
            assertEquals("LOCAL-IP", cancelPayload.ip)
            assertEquals(7, cancelPayload.port)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = cancelCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientAttackCancelResponse.serializer(),
                        ClientAttackCancelResponse(
                            stateId = "LOCAL-IP",
                            sourcePort = 7,
                            accepted = false,
                            hadActiveSession = true,
                            message = "Unable to cancel redirect.",
                            version = 4,
                        ),
                    ),
                ),
            )

            waitUntil {
                text(redirectWindow, "rewrite-attack-error") == "Unable to cancel redirect." &&
                    button(redirectWindow, "rewrite-attack-primary-button").text == "Cancel" &&
                    redirectWindow.isDisplayable
            }
        } finally {
            disposeFrame(frame)
        }
    }

    private fun attackReadyFrame(
        sessionGateway: FakeAttackUiSessionGateway,
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
        frame.controller.accept(RewriteService.GAME, snapshotFrame(attackSnapshot()))
        waitUntil { frame.desktopPane.isShowing }
        return frame
    }

    private fun attackSnapshot(): ClientGameSnapshot {
        return ClientGameSnapshot(
            id = "LOCAL-IP",
            ports = listOf(
                ClientPortState(
                    number = 6,
                    enabled = true,
                    dummy = false,
                    note = "attack",
                    installedApplication = ClientInstalledApplication(name = "attack", kind = "ATTACK"),
                ),
                ClientPortState(
                    number = 7,
                    enabled = true,
                    dummy = false,
                    note = "redirect",
                    defaultPort = true,
                    installedApplication = ClientInstalledApplication(name = "redirect", kind = "REDIRECT"),
                ),
            ),
        )
    }

    private fun snapshotFrame(snapshot: ClientGameSnapshot): FrameEnvelope {
        return RewriteFrames.snapshot(
            gameStateId = snapshot.id,
            sequence = 1,
            payload = RewriteClientJson.encode(ClientGameSnapshot.serializer(), snapshot),
        )
    }

    private fun waitForWindow(frame: RewriteRootFrame, name: String): JInternalFrame {
        waitUntil {
            SwingUtilities.isEventDispatchThread().not() &&
                invokeAndWaitResult { frame.desktopPane.allFrames.firstOrNull { it.name == name } != null }
        }
        return invokeAndWaitResult {
            frame.desktopPane.allFrames.first { it.name == name }
        }
    }

    private fun waitForDialog(name: String): JDialog {
        waitUntil {
            invokeAndWaitResult {
                Window.getWindows()
                    .filterIsInstance<JDialog>()
                    .any { it.name == name && it.isShowing }
            }
        }
        return invokeAndWaitResult {
            Window.getWindows()
                .filterIsInstance<JDialog>()
                .first { it.name == name && it.isShowing }
        }
    }

    private fun waitUntil(timeoutMillis: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            flushEdt()
            if (condition()) {
                return
            }
            Thread.sleep(20)
        }
        flushEdt()
        if (!condition()) {
            error("Condition was not met within ${timeoutMillis}ms")
        }
    }

    private fun flushEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            return
        }
        SwingUtilities.invokeAndWait {}
    }

    private fun disposeFrame(frame: RewriteRootFrame) {
        SwingUtilities.invokeAndWait {
            frame.dispose()
        }
    }

    private fun <T> invokeAndWaitResult(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait {
            result = runCatching(block)
        }
        return result!!.getOrThrow()
    }

    private fun findComponent(root: Component, name: String): Component? {
        if (root.name == name) {
            return root
        }
        if (root is Container) {
            root.components.forEach { child ->
                findComponent(child, name)?.let { return it }
            }
        }
        return null
    }

    private fun button(root: Component, name: String): JButton {
        return findComponent(root, name) as? JButton ?: error("Unable to find button named $name")
    }

    private fun text(root: Component, name: String): String {
        val component = findComponent(root, name) ?: error("Unable to find component named $name")
        return when (component) {
            is JLabel -> component.text
            is JTextField -> component.text
            else -> error("Unsupported text component ${component::class.java.name}")
        }
    }

    private fun textField(root: Component, name: String): JTextField {
        return findComponent(root, name) as? JTextField ?: error("Unable to find text field named $name")
    }

    private fun textArea(root: Component, name: String): JTextArea {
        return findComponent(root, name) as? JTextArea ?: error("Unable to find text area named $name")
    }

    private fun spinner(root: Component, name: String): JSpinner {
        return findComponent(root, name) as? JSpinner ?: error("Unable to find spinner named $name")
    }

    private fun list(root: Component, name: String): JList<*> {
        return findComponent(root, name) as? JList<*> ?: error("Unable to find list named $name")
    }

    private fun comboBox(root: Component, name: String): JComboBox<*> {
        return findComponent(root, name) as? JComboBox<*> ?: error("Unable to find combo box named $name")
    }

    private fun setSegmentedIp(root: Component, ip: String) {
        val parts = ip.split('.')
        repeat(4) { index ->
            textField(root, "rewrite-economy-ip-segment-$index").text = parts[index]
        }
    }

    private class FakeAttackUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeAttackUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeAttackUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakeAttackUiSession? {
            return sessions.lastOrNull { it.service == RewriteService.GAME }
        }
    }

    private class FakeAttackUiSession(
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

        override fun close() {
            Unit
        }
    }
}
