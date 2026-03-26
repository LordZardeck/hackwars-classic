package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.testsupport.rewriteUiAuthenticatedDesktopFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiDisposeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiFindNamedComponent
import com.hackwars.rewrite.client.testsupport.rewriteUiInvokeAndWaitResult
import com.hackwars.rewrite.client.testsupport.rewriteUiSetSegmentedIp
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForDialog
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForWindow
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitUntil
import com.hackwars.rewrite.protocol.ClientAttackMode
import com.hackwars.rewrite.protocol.ClientAttackSessionKind
import com.hackwars.rewrite.protocol.ClientAttackSessionState
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientGameUiEvent
import com.hackwars.rewrite.protocol.ClientProgramLifecycleStatus
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.ClientRequestZombieAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestZombieCancelAttackPayload
import com.hackwars.rewrite.protocol.ClientZombieAttackCancelResponse
import com.hackwars.rewrite.protocol.ClientZombieAttackStartResponse
import com.hackwars.rewrite.protocol.ClientZombieAttackUiEvent
import com.hackwars.rewrite.protocol.ClientShowChoicesType
import com.hackwars.rewrite.protocol.ClientShowChoicesUiEvent
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.GraphicsEnvironment
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteZombieAttackWindowsUiTest {
    @Test
    fun zombieLauncherOpensKeyedPanesAndReusesSameZombieSource() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeZombieAttackUiSessionGateway()
        val frame = zombieReadyFrame(sessionGateway)
        try {
            openZombieLauncher(frame)
            val firstDialog = waitForDialog("rewrite-zombie-launch-dialog")
            SwingUtilities.invokeAndWait {
                setSegmentedIp(firstDialog, "10.0.0.5")
                spinner(firstDialog, "rewrite-zombie-launch-port-spinner").value = 12
                button(firstDialog, "rewrite-zombie-launch-continue").doClick()
            }

            val firstWindow = waitForWindow(frame, zombieWindowName("10.0.0.5", 12))
            assertEquals("Zombie Attack -- 10.0.0.5:12", firstWindow.title)

            openZombieLauncher(frame)
            val secondDialog = waitForDialog("rewrite-zombie-launch-dialog")
            SwingUtilities.invokeAndWait {
                setSegmentedIp(secondDialog, "10.0.0.5")
                spinner(secondDialog, "rewrite-zombie-launch-port-spinner").value = 12
                button(secondDialog, "rewrite-zombie-launch-continue").doClick()
            }

            waitUntil {
                frame.desktopPane.allFrames.count { it.name == zombieWindowName("10.0.0.5", 12) } == 1
            }

            openZombieLauncher(frame)
            val thirdDialog = waitForDialog("rewrite-zombie-launch-dialog")
            SwingUtilities.invokeAndWait {
                setSegmentedIp(thirdDialog, "10.0.0.6")
                spinner(thirdDialog, "rewrite-zombie-launch-port-spinner").value = 13
                button(thirdDialog, "rewrite-zombie-launch-continue").doClick()
            }

            waitUntil {
                frame.desktopPane.allFrames.count { it.name?.startsWith("rewrite-zombie-window-") == true } == 2
            }
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun acceptedZombieAttackCorrelatesUiEventsAndIgnoresShowChoices() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeZombieAttackUiSessionGateway()
        val frame = zombieReadyFrame(sessionGateway)
        try {
            val zombieWindow = openZombieWindow(frame, "10.0.0.5", 12)

            SwingUtilities.invokeAndWait {
                setSegmentedIp(zombieWindow, "10.0.0.8")
                spinner(zombieWindow, "rewrite-zombie-target-port-spinner").value = 4
                button(zombieWindow, "rewrite-zombie-primary-button").doClick()
            }

            waitUntil {
                sessionGateway.latestGameSession()
                    ?.sentFrames
                    ?.lastOrNull()
                    ?.command
                    ?.command_name == "requestzombieattack"
            }
            val attackCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val attackPayload = RewriteClientJson.decode(
                ClientRequestZombieAttackPayload.serializer(),
                attackCommand.payload.toByteArray(),
            )
            assertEquals("10.0.0.5", attackPayload.sourceIp)
            assertEquals(12, attackPayload.sourcePort)
            assertEquals("10.0.0.8", attackPayload.targetIp)
            assertEquals("192.0.2.10", attackPayload.parentIp)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = attackCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientZombieAttackStartResponse.serializer(),
                        ClientZombieAttackStartResponse(
                            controllerStateId = "192.0.2.10",
                            zombieStateId = "10.0.0.5",
                            sourcePort = 12,
                            targetStateId = "10.0.0.8",
                            targetPort = 4,
                            accepted = true,
                            message = "zombie-attack-started",
                            chargedAmount = 20.0,
                            controllerPettyCashAfter = 80.0,
                            zombieCpuLoadAfter = 6.0,
                            session = ClientAttackSessionState(
                                programId = "zombie-program-1",
                                sourcePort = 12,
                                targetStateId = "10.0.0.8",
                                targetPort = 4,
                                sessionKind = ClientAttackSessionKind.ATTACK,
                                attackMode = ClientAttackMode.ZOMBIE,
                            ),
                            controllerVersion = 7,
                            zombieVersion = 3,
                        ),
                    ),
                ),
            )

            waitUntil {
                button(zombieWindow, "rewrite-zombie-primary-button").text == "Cancel" &&
                    !zombieWindow.isClosable
            }

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.gameUiEvent(
                    eventId = "zombie-ui-1",
                    eventType = "zombie_attack",
                    payload = RewriteClientJson.encode(
                        ClientGameUiEvent.serializer(),
                        ClientZombieAttackUiEvent(
                            message = "Zombie attack landed.",
                            zombieIp = "10.0.0.5",
                            sourcePort = 12,
                        ),
                    ),
                ),
            )
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.gameUiEvent(
                    eventId = "zombie-show-choices-ignored",
                    eventType = "show_choices",
                    payload = RewriteClientJson.encode(
                        ClientGameUiEvent.serializer(),
                        ClientShowChoicesUiEvent(
                            targetIp = "10.0.0.8",
                            targetPort = 4,
                            choiceType = ClientShowChoicesType.HTTP,
                            windowHandle = 0,
                        ),
                    ),
                ),
            )
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.programUpdate(
                    programId = "zombie-program-1",
                    programType = "zombie-attack",
                    status = hackwars.rewrite.v1.ProgramStatus.PROGRAM_STATUS_COMPLETED,
                    payload = RewriteClientJson.encode(
                        ClientProgramUpdate.serializer(),
                        ClientProgramUpdate(
                            programId = "zombie-program-1",
                            programType = "zombie-attack",
                            status = ClientProgramLifecycleStatus.COMPLETED,
                        ),
                    ),
                ),
            )

            waitUntil {
                textArea(zombieWindow, "rewrite-zombie-transcript").text.contains("Zombie attack landed.") &&
                    button(zombieWindow, "rewrite-zombie-primary-button").text == "Attack" &&
                    zombieWindow.isClosable &&
                    frame.desktopPane.allFrames.none { it.title.startsWith("Choices - ") }
            }
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun failedZombieCancelKeepsPaneOpenAndShowsInlineError() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeZombieAttackUiSessionGateway()
        val frame = zombieReadyFrame(sessionGateway)
        try {
            val zombieWindow = openZombieWindow(frame, "10.0.0.5", 12)

            SwingUtilities.invokeAndWait {
                setSegmentedIp(zombieWindow, "10.0.0.8")
                spinner(zombieWindow, "rewrite-zombie-target-port-spinner").value = 4
                button(zombieWindow, "rewrite-zombie-primary-button").doClick()
            }

            waitUntil {
                sessionGateway.latestGameSession()
                    ?.sentFrames
                    ?.lastOrNull()
                    ?.command
                    ?.command_name == "requestzombieattack"
            }
            val startCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = startCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientZombieAttackStartResponse.serializer(),
                        ClientZombieAttackStartResponse(
                            controllerStateId = "192.0.2.10",
                            zombieStateId = "10.0.0.5",
                            sourcePort = 12,
                            targetStateId = "10.0.0.8",
                            targetPort = 4,
                            accepted = true,
                            message = "zombie-attack-started",
                            chargedAmount = 20.0,
                            controllerPettyCashAfter = 80.0,
                            zombieCpuLoadAfter = 6.0,
                            session = ClientAttackSessionState(
                                programId = "zombie-program-2",
                                sourcePort = 12,
                                targetStateId = "10.0.0.8",
                                targetPort = 4,
                                sessionKind = ClientAttackSessionKind.ATTACK,
                                attackMode = ClientAttackMode.ZOMBIE,
                            ),
                            controllerVersion = 7,
                            zombieVersion = 3,
                        ),
                    ),
                ),
            )

            waitUntil { button(zombieWindow, "rewrite-zombie-primary-button").text == "Cancel" }
            SwingUtilities.invokeAndWait {
                button(zombieWindow, "rewrite-zombie-primary-button").doClick()
            }

            waitUntil {
                sessionGateway.latestGameSession()
                    ?.sentFrames
                    ?.lastOrNull()
                    ?.command
                    ?.command_name == "requestzombiecancelattack"
            }
            val cancelCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val cancelPayload = RewriteClientJson.decode(
                ClientRequestZombieCancelAttackPayload.serializer(),
                cancelCommand.payload.toByteArray(),
            )
            assertEquals("192.0.2.10", cancelPayload.ip)
            assertEquals(12, cancelPayload.port)
            assertEquals("10.0.0.5", cancelPayload.targetIp)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = cancelCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientZombieAttackCancelResponse.serializer(),
                        ClientZombieAttackCancelResponse(
                            controllerStateId = "192.0.2.10",
                            zombieStateId = "10.0.0.5",
                            sourcePort = 12,
                            accepted = false,
                            hadActiveSession = true,
                            message = "Unable to cancel zombie attack.",
                            controllerVersion = 8,
                            zombieVersion = 4,
                        ),
                    ),
                ),
            )

            waitUntil {
                text(zombieWindow, "rewrite-zombie-error") == "Unable to cancel zombie attack." &&
                    button(zombieWindow, "rewrite-zombie-primary-button").text == "Cancel" &&
                    zombieWindow.isDisplayable
            }
        } finally {
            disposeFrame(frame)
        }
    }

    private fun zombieReadyFrame(
        sessionGateway: FakeZombieAttackUiSessionGateway,
    ): RewriteRootFrame {
        return rewriteUiAuthenticatedDesktopFrame(
            sessionGateway = sessionGateway,
            snapshot = ClientGameSnapshot(id = "192.0.2.10"),
        )
    }

    private fun openZombieWindow(
        frame: RewriteRootFrame,
        zombieIp: String,
        zombiePort: Int,
    ): JInternalFrame {
        openZombieLauncher(frame)
        val dialog = waitForDialog("rewrite-zombie-launch-dialog")
        SwingUtilities.invokeAndWait {
            setSegmentedIp(dialog, zombieIp)
            spinner(dialog, "rewrite-zombie-launch-port-spinner").value = zombiePort
            button(dialog, "rewrite-zombie-launch-continue").doClick()
        }
        return waitForWindow(frame, zombieWindowName(zombieIp, zombiePort))
    }

    private fun openZombieLauncher(frame: RewriteRootFrame) {
        SwingUtilities.invokeLater {
            frame.controller.launchShellCommand(RewriteShellCommand.ZOMBIE_ATTACK)
        }
    }

    private fun zombieWindowName(
        zombieIp: String,
        zombiePort: Int,
    ): String = "rewrite-zombie-window-${zombieIp.replace('.', '_')}-$zombiePort"

    private fun waitForWindow(frame: RewriteRootFrame, name: String): JInternalFrame {
        return rewriteUiWaitForWindow(frame, name)
    }

    private fun waitForDialog(name: String): JDialog {
        return rewriteUiWaitForDialog(name)
    }

    private fun waitUntil(timeoutMillis: Long = 3_000, condition: () -> Boolean) {
        rewriteUiWaitUntil(timeoutMillis, condition)
    }

    private fun disposeFrame(frame: RewriteRootFrame) {
        rewriteUiDisposeFrame(frame)
    }

    private fun <T> invokeAndWaitResult(block: () -> T): T {
        return rewriteUiInvokeAndWaitResult(block)
    }

    private fun findComponent(root: Component, name: String): Component? {
        return rewriteUiFindNamedComponent(root, name)
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

    private fun textArea(root: Component, name: String): JTextArea {
        return findComponent(root, name) as? JTextArea ?: error("Unable to find text area named $name")
    }

    private fun spinner(root: Component, name: String): JSpinner {
        return findComponent(root, name) as? JSpinner ?: error("Unable to find spinner named $name")
    }

    private fun setSegmentedIp(root: Component, ip: String) {
        rewriteUiSetSegmentedIp(root, ip)
    }

    private fun textField(root: Component, name: String): JTextField {
        return findComponent(root, name) as? JTextField ?: error("Unable to find text field named $name")
    }

    private class FakeZombieAttackUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeZombieAttackUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeZombieAttackUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakeZombieAttackUiSession? {
            return sessions.lastOrNull { it.service == RewriteService.GAME }
        }
    }

    private class FakeZombieAttackUiSession(
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
