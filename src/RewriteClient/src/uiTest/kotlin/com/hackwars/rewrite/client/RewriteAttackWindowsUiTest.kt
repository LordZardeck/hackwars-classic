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
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientAttackCancelResponse
import com.hackwars.rewrite.protocol.ClientAttackMessageUiEvent
import com.hackwars.rewrite.protocol.ClientAttackPaneType
import com.hackwars.rewrite.protocol.ClientAttackSessionKind
import com.hackwars.rewrite.protocol.ClientAttackStartResponse
import com.hackwars.rewrite.protocol.ClientAttackSessionState
import com.hackwars.rewrite.protocol.ClientChangeDailyPayOutcome
import com.hackwars.rewrite.protocol.ClientChangeDailyPayPayload
import com.hackwars.rewrite.protocol.ClientChangeDailyPayResponse
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientFtpTransferResponse
import com.hackwars.rewrite.protocol.ClientFinalizeCancelledOutcome
import com.hackwars.rewrite.protocol.ClientFinalizeCancelledPayload
import com.hackwars.rewrite.protocol.ClientFinalizeCancelledResponse
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientMalGetPayload
import com.hackwars.rewrite.protocol.ClientProgramLifecycleStatus
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.ClientRequestAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestCancelAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestDirectoryPayload
import com.hackwars.rewrite.protocol.ClientRequestSecondaryDirectoryPayload
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientSecondaryDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientShowChoicesType
import com.hackwars.rewrite.protocol.ClientShowChoicesUiEvent
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
                            stateId = "192.0.2.10",
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
            assertEquals("192.0.2.10", attackPayload.sourceIp)
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
                            attackerStateId = "192.0.2.10",
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
                            attackerStateId = "192.0.2.10",
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
            assertEquals("192.0.2.10", cancelPayload.ip)
            assertEquals(7, cancelPayload.port)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = cancelCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientAttackCancelResponse.serializer(),
                        ClientAttackCancelResponse(
                            stateId = "192.0.2.10",
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

    @Test
    fun showChoicesOpensRemoteBrowserAndReusesSingleChoicesWindow() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeAttackUiSessionGateway()
        val frame = attackReadyFrame(sessionGateway = sessionGateway)
        try {
            val attackSession = startAcceptedAttackSession(
                frame = frame,
                sessionGateway = sessionGateway,
                command = RewriteShellCommand.ATTACK_PORT,
                targetIp = "10.0.0.8",
                targetPort = 4,
                sourcePort = 6,
                programId = "attack-program-followup-1",
            )

            sendShowChoicesEvent(
                frame = frame,
                targetIp = "10.0.0.8",
                targetPort = 4,
                choiceType = ClientShowChoicesType.FTP,
                windowHandle = attackSession.windowHandle,
            )
            val choicesWindow = waitForWindow(frame, "rewrite-show-choices-window")
            waitUntil {
                comboBox(choicesWindow, "rewrite-show-choices-action-combo").itemCount == 1 &&
                    comboBox(choicesWindow, "rewrite-show-choices-action-combo").selectedItem.toString() == "Open Public FTP"
            }

            sendShowChoicesEvent(
                frame = frame,
                targetIp = "10.0.0.8",
                targetPort = 4,
                choiceType = ClientShowChoicesType.FTP,
                windowHandle = attackSession.windowHandle,
            )
            waitUntil {
                frame.desktopPane.allFrames.count { it.name == "rewrite-show-choices-window" } == 1
            }

            SwingUtilities.invokeAndWait {
                button(choicesWindow, "rewrite-show-choices-go-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "requestsecondarydirectory" }
            val initialDirectoryCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val initialDirectoryPayload = RewriteClientJson.decode(
                ClientRequestSecondaryDirectoryPayload.serializer(),
                initialDirectoryCommand.payload.toByteArray(),
            )

            assertEquals("/Public", initialDirectoryPayload.path)
            assertEquals("10.0.0.8", initialDirectoryPayload.targetIp)
            assertEquals(4, initialDirectoryPayload.port)
            assertFalse(
                sessionGateway.latestGameSession()!!.sentFrames
                    .mapNotNull { it.command?.command_name }
                    .contains("finalizecancelled"),
            )

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = initialDirectoryCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientSecondaryDirectoryListingResponse.serializer(),
                        ClientSecondaryDirectoryListingResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "10.0.0.8",
                            portNumber = 4,
                            path = "/Public",
                            directories = listOf(
                                ClientDirectoryEntry(path = "/Public/docs", name = "docs"),
                            ),
                            files = listOf(
                                ClientStoredFile(path = "/Public/readme.txt", name = "readme.txt", kind = ClientStoredFileKind.TEXT),
                            ),
                            version = 5,
                        ),
                    ),
                ),
            )

            val remoteBrowser = waitForWindow(frame, "rewrite-remote-directory-browser-window-public_ftp")
            waitUntil {
                text(remoteBrowser, "rewrite-remote-files-path-label") == "/Public" &&
                    list(remoteBrowser, "rewrite-remote-files-entry-list").model.size == 2 &&
                    !button(remoteBrowser, "rewrite-remote-files-up-button").isEnabled
            }

            SwingUtilities.invokeAndWait {
                list(remoteBrowser, "rewrite-remote-files-entry-list").selectedIndex = 0
                button(remoteBrowser, "rewrite-remote-files-open-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "requestsecondarydirectory" && sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_id != initialDirectoryCommand.command_id }
            val nestedDirectoryCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val nestedDirectoryPayload = RewriteClientJson.decode(
                ClientRequestSecondaryDirectoryPayload.serializer(),
                nestedDirectoryCommand.payload.toByteArray(),
            )
            assertEquals("/Public/docs", nestedDirectoryPayload.path)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = nestedDirectoryCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientSecondaryDirectoryListingResponse.serializer(),
                        ClientSecondaryDirectoryListingResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "10.0.0.8",
                            portNumber = 4,
                            path = "/Public/docs",
                            version = 6,
                        ),
                    ),
                ),
            )

            waitUntil {
                text(remoteBrowser, "rewrite-remote-files-path-label") == "/Public/docs" &&
                    button(remoteBrowser, "rewrite-remote-files-up-button").isEnabled
            }

            SwingUtilities.invokeAndWait {
                button(remoteBrowser, "rewrite-remote-files-up-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "requestsecondarydirectory" && sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_id != nestedDirectoryCommand.command_id }
            val backUpCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val backUpPayload = RewriteClientJson.decode(
                ClientRequestSecondaryDirectoryPayload.serializer(),
                backUpCommand.payload.toByteArray(),
            )
            assertEquals("/Public", backUpPayload.path)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun showChoicesPublicFtpTakeSendsMalGet() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeAttackUiSessionGateway()
        val frame = attackReadyFrame(sessionGateway = sessionGateway)
        try {
            val attackSession = startAcceptedAttackSession(
                frame = frame,
                sessionGateway = sessionGateway,
                command = RewriteShellCommand.ATTACK_PORT,
                targetIp = "10.0.0.8",
                targetPort = 4,
                sourcePort = 6,
                programId = "attack-program-followup-take-1",
            )

            sendShowChoicesEvent(
                frame = frame,
                targetIp = "10.0.0.8",
                targetPort = 4,
                choiceType = ClientShowChoicesType.FTP,
                windowHandle = attackSession.windowHandle,
            )
            val choicesWindow = waitForWindow(frame, "rewrite-show-choices-window")
            SwingUtilities.invokeAndWait {
                button(choicesWindow, "rewrite-show-choices-go-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "requestsecondarydirectory" }
            val initialDirectoryCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = initialDirectoryCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientSecondaryDirectoryListingResponse.serializer(),
                        ClientSecondaryDirectoryListingResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "10.0.0.8",
                            portNumber = 4,
                            path = "/Public",
                            files = listOf(
                                ClientStoredFile(
                                    path = "/Public/loot.bin",
                                    name = "loot.bin",
                                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                                    quantity = 1,
                                ),
                            ),
                            version = 5,
                        ),
                    ),
                ),
            )

            val remoteBrowser = waitForWindow(frame, "rewrite-remote-directory-browser-window-public_ftp")
            waitUntil {
                list(remoteBrowser, "rewrite-remote-files-entry-list").model.size == 1
            }
            SwingUtilities.invokeAndWait {
                list(remoteBrowser, "rewrite-remote-files-entry-list").selectedIndex = 0
                button(remoteBrowser, "rewrite-remote-files-take-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "malget" }
            val malGetCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val malGetPayload = RewriteClientJson.decode(
                ClientMalGetPayload.serializer(),
                malGetCommand.payload.toByteArray(),
            )
            assertEquals("10.0.0.8", malGetPayload.ip)
            assertEquals("192.0.2.10", malGetPayload.targetIp)
            assertEquals("/Public", malGetPayload.fetchPath)
            assertEquals(attackSession.windowHandle, malGetPayload.attackPort)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = malGetCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientFtpTransferResponse.serializer(),
                        ClientFtpTransferResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "10.0.0.8",
                            targetPort = 4,
                            operation = "malget",
                            file = ClientStoredFile(
                                path = "/loot.bin",
                                name = "loot.bin",
                                kind = ClientStoredFileKind.APPLICATION_BINARY,
                                quantity = 1,
                            ),
                            fulfilledQuantity = 1,
                            message = "ftp-malget-complete",
                            requesterVersion = 6,
                            targetVersion = 7,
                        ),
                    ),
                ),
            )

            waitUntil {
                textArea(attackSession.window, "rewrite-attack-transcript").text.contains("ftp-malget-complete")
            }
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun publicFtpFollowupTakeSendsMalGetAndRefreshesBrowser() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeAttackUiSessionGateway()
        val frame = attackReadyFrame(sessionGateway = sessionGateway)
        try {
            val attackSession = startAcceptedAttackSession(
                frame = frame,
                sessionGateway = sessionGateway,
                command = RewriteShellCommand.ATTACK_PORT,
                targetIp = "10.0.0.8",
                targetPort = 4,
                sourcePort = 6,
                programId = "attack-program-malget-1",
            )
            val attackWindow = waitForWindow(frame, "rewrite-shell-window-attack_port")

            sendShowChoicesEvent(
                frame = frame,
                targetIp = "10.0.0.8",
                targetPort = 4,
                choiceType = ClientShowChoicesType.FTP,
                windowHandle = attackSession.windowHandle,
            )
            val choicesWindow = waitForWindow(frame, "rewrite-show-choices-window")
            SwingUtilities.invokeAndWait {
                button(choicesWindow, "rewrite-show-choices-go-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "requestsecondarydirectory" }
            val initialDirectoryCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = initialDirectoryCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientSecondaryDirectoryListingResponse.serializer(),
                        ClientSecondaryDirectoryListingResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "10.0.0.8",
                            portNumber = 4,
                            path = "/Public",
                            files = listOf(
                                ClientStoredFile(
                                    path = "/Public/loot.bin",
                                    name = "loot.bin",
                                    kind = ClientStoredFileKind.TEXT,
                                    quantity = 1,
                                ),
                            ),
                            version = 5,
                        ),
                    ),
                ),
            )

            val remoteBrowser = waitForWindow(frame, "rewrite-remote-directory-browser-window-public_ftp")
            waitUntil { list(remoteBrowser, "rewrite-remote-files-entry-list").model.size == 1 }

            SwingUtilities.invokeAndWait {
                list(remoteBrowser, "rewrite-remote-files-entry-list").selectedIndex = 0
            }
            waitUntil { button(remoteBrowser, "rewrite-remote-files-take-button").isEnabled }
            SwingUtilities.invokeAndWait {
                button(remoteBrowser, "rewrite-remote-files-take-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "malget" }
            val malGetCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val malGetPayload = RewriteClientJson.decode(
                ClientMalGetPayload.serializer(),
                malGetCommand.payload.toByteArray(),
            )
            assertEquals("10.0.0.8", malGetPayload.ip)
            assertEquals("192.0.2.10", malGetPayload.targetIp)
            assertEquals("/Public", malGetPayload.fetchPath)
            assertEquals(attackSession.windowHandle, malGetPayload.attackPort)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = malGetCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientFtpTransferResponse.serializer(),
                        ClientFtpTransferResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "10.0.0.8",
                            targetPort = 4,
                            operation = "malget",
                            file = ClientStoredFile(
                                path = "/loot.bin",
                                name = "loot.bin",
                                kind = ClientStoredFileKind.TEXT,
                                quantity = 1,
                            ),
                            fulfilledQuantity = 1,
                            message = "ftp-malget-complete",
                            requesterVersion = 6,
                            targetVersion = 7,
                        ),
                    ),
                ),
            )

            waitUntil {
                textArea(attackWindow, "rewrite-attack-transcript").text.contains("ftp-malget-complete") &&
                    gameCommands(sessionGateway, "requestsecondarydirectory").size >= 2
            }
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun showChoicesHttpDialogAndExplicitCancelUseRewriteFollowupCommands() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeAttackUiSessionGateway()
        val frame = attackReadyFrame(sessionGateway = sessionGateway)
        try {
            val attackSession = startAcceptedAttackSession(
                frame = frame,
                sessionGateway = sessionGateway,
                command = RewriteShellCommand.ATTACK_PORT,
                targetIp = "10.0.0.8",
                targetPort = 4,
                sourcePort = 6,
                programId = "attack-program-followup-2",
            )

            sendShowChoicesEvent(
                frame = frame,
                targetIp = "10.0.0.8",
                targetPort = 4,
                choiceType = ClientShowChoicesType.HTTP,
                windowHandle = attackSession.windowHandle,
            )
            val httpChoicesWindow = waitForWindow(frame, "rewrite-show-choices-window")
            waitUntil {
                comboBox(httpChoicesWindow, "rewrite-show-choices-action-combo").selectedItem.toString() == "Change Daily Pay Target"
            }
            SwingUtilities.invokeAndWait {
                button(httpChoicesWindow, "rewrite-show-choices-go-button").doClick()
            }

            val dialog = waitForDialog("rewrite-change-daily-pay-dialog")
            waitUntil { text(dialog, "rewrite-change-daily-pay-ip-field") == "192.0.2.10" }
            SwingUtilities.invokeAndWait {
                textField(dialog, "rewrite-change-daily-pay-ip-field").text = "198.51.100.60"
                button(dialog, "rewrite-change-daily-pay-submit-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "changedailypay" }
            val changeDailyPayCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val changeDailyPayPayload = RewriteClientJson.decode(
                ClientChangeDailyPayPayload.serializer(),
                changeDailyPayCommand.payload.toByteArray(),
            )

            assertEquals("10.0.0.8", changeDailyPayPayload.ip)
            assertEquals(4, changeDailyPayPayload.port)
            assertEquals("198.51.100.60", changeDailyPayPayload.change)
            assertEquals("192.0.2.10", changeDailyPayPayload.finalizeIp)
            assertEquals(attackSession.windowHandle, changeDailyPayPayload.attackPort)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = changeDailyPayCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientChangeDailyPayResponse.serializer(),
                        ClientChangeDailyPayResponse(
                            actorStateId = "192.0.2.10",
                            targetStateId = "10.0.0.8",
                            targetPort = 4,
                            requestedRevenueTargetStateId = "198.51.100.60",
                            accepted = true,
                            outcome = ClientChangeDailyPayOutcome.SUCCESS,
                            message = "Daily pay successfully changed.",
                            reductionMultiplierAfter = 1.0,
                            revenueTargetStateIdAfter = "198.51.100.60",
                            requesterHttpExperienceAfter = 20.0,
                            actorVersion = 7,
                            targetVersion = 9,
                        ),
                    ),
                ),
            )

            waitUntil {
                Window.getWindows().filterIsInstance<JDialog>().none { it.name == "rewrite-change-daily-pay-dialog" && it.isShowing } &&
                    text(attackSession.window, "rewrite-attack-status") == "Daily pay successfully changed."
            }

            sendShowChoicesEvent(
                frame = frame,
                targetIp = "10.0.0.8",
                targetPort = 4,
                choiceType = ClientShowChoicesType.BANK,
                windowHandle = attackSession.windowHandle,
            )
            val bankChoicesWindow = waitForWindow(frame, "rewrite-show-choices-window")
            waitUntil {
                !button(bankChoicesWindow, "rewrite-show-choices-go-button").isEnabled &&
                    text(bankChoicesWindow, "rewrite-show-choices-info").contains("No rewrite follow-up actions")
            }

            SwingUtilities.invokeAndWait {
                button(bankChoicesWindow, "rewrite-show-choices-cancel-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "finalizecancelled" }
            val finalizeCancelledCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val finalizeCancelledPayload = RewriteClientJson.decode(
                ClientFinalizeCancelledPayload.serializer(),
                finalizeCancelledCommand.payload.toByteArray(),
            )

            assertEquals("192.0.2.10", finalizeCancelledPayload.ip)
            assertEquals("10.0.0.8", finalizeCancelledPayload.targetIp)
            assertEquals(4, finalizeCancelledPayload.targetPort)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = finalizeCancelledCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientFinalizeCancelledResponse.serializer(),
                        ClientFinalizeCancelledResponse(
                            actorStateId = "192.0.2.10",
                            targetStateId = "10.0.0.8",
                            targetPort = 4,
                            accepted = true,
                            outcome = ClientFinalizeCancelledOutcome.SUCCESS,
                            message = "finalizecancelled-succeeded",
                            targetVersion = 10,
                        ),
                    ),
                ),
            )

            waitUntil {
                frame.desktopPane.allFrames.none { it.name == "rewrite-show-choices-window" }
            }
        } finally {
            disposeFrame(frame)
        }
    }

    private fun attackReadyFrame(
        sessionGateway: FakeAttackUiSessionGateway,
    ): RewriteRootFrame {
        return rewriteUiAuthenticatedDesktopFrame(
            sessionGateway = sessionGateway,
            snapshot = attackSnapshot(),
        )
    }

    private fun attackSnapshot(): ClientGameSnapshot {
        return ClientGameSnapshot(
            id = "192.0.2.10",
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

    private fun gameCommands(
        sessionGateway: FakeAttackUiSessionGateway,
        commandName: String,
    ): List<hackwars.rewrite.v1.CommandEnvelope> {
        return sessionGateway.latestGameSession()
            ?.sentFrames
            .orEmpty()
            .mapNotNull(FrameEnvelope::command)
            .filter { it.command_name == commandName }
    }

    private fun startAcceptedAttackSession(
        frame: RewriteRootFrame,
        sessionGateway: FakeAttackUiSessionGateway,
        command: RewriteShellCommand,
        targetIp: String,
        targetPort: Int,
        sourcePort: Int,
        programId: String,
    ): StartedAttackSession {
        SwingUtilities.invokeAndWait {
            frame.controller.launchShellCommand(command)
        }
        val windowName = when (command) {
            RewriteShellCommand.ATTACK_PORT -> "rewrite-shell-window-attack_port"
            RewriteShellCommand.REDIRECT_PORT -> "rewrite-shell-window-redirect_port"
            else -> error("Unsupported attack command ${command.name}")
        }
        val window = waitForWindow(frame, windowName)
        SwingUtilities.invokeAndWait {
            setSegmentedIp(window, targetIp)
            spinner(window, "rewrite-attack-target-port-spinner").value = targetPort
            button(window, "rewrite-attack-primary-button").doClick()
        }

        waitUntil { gameCommands(sessionGateway, "requestattack").isNotEmpty() }
        val attackCommand = gameCommands(sessionGateway, "requestattack").last()
        val attackPayload = RewriteClientJson.decode(
            ClientRequestAttackPayload.serializer(),
            attackCommand.payload.toByteArray(),
        )

        frame.controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = attackCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientAttackStartResponse.serializer(),
                    ClientAttackStartResponse(
                        attackerStateId = "192.0.2.10",
                        sourcePort = sourcePort,
                        targetStateId = targetIp,
                        targetPort = targetPort,
                        accepted = true,
                        message = if (command == RewriteShellCommand.ATTACK_PORT) "Attack accepted." else "Redirect accepted.",
                        session = ClientAttackSessionState(
                            programId = programId,
                            sourcePort = sourcePort,
                            targetStateId = targetIp,
                            targetPort = targetPort,
                            sessionKind = if (command == RewriteShellCommand.ATTACK_PORT) ClientAttackSessionKind.ATTACK else ClientAttackSessionKind.REDIRECT,
                            windowHandle = attackPayload.windowHandle ?: 0,
                        ),
                        version = 3,
                    ),
                ),
            ),
        )

        waitUntil { button(window, "rewrite-attack-primary-button").text == "Cancel" }
        return StartedAttackSession(
            window = window,
            windowHandle = attackPayload.windowHandle ?: 0,
        )
    }

    private fun sendShowChoicesEvent(
        frame: RewriteRootFrame,
        targetIp: String,
        targetPort: Int,
        choiceType: ClientShowChoicesType,
        windowHandle: Int,
    ) {
        frame.controller.accept(
            RewriteService.GAME,
            RewriteFrames.gameUiEvent(
                eventId = "show-choices-$choiceType-$windowHandle",
                eventType = "show_choices",
                payload = RewriteClientJson.encode(
                    com.hackwars.rewrite.protocol.ClientGameUiEvent.serializer(),
                    ClientShowChoicesUiEvent(
                        targetIp = targetIp,
                        targetPort = targetPort,
                        choiceType = choiceType,
                        windowHandle = windowHandle,
                    ),
                ),
            ),
        )
    }

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
        rewriteUiSetSegmentedIp(root, ip)
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

    private data class StartedAttackSession(
        val window: JInternalFrame,
        val windowHandle: Int,
    )
}
