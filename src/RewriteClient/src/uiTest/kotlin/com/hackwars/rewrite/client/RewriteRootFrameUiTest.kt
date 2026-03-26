package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.protocol.ClientBountyCreatedResponse
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.protocol.ClientBankTransactionResponse
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientEconomyState
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientMakeBountyPayload
import com.hackwars.rewrite.protocol.ClientRequestDirectoryPayload
import com.hackwars.rewrite.protocol.ClientRuntimeState
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientTransferPayload
import com.hackwars.rewrite.protocol.ClientWithdrawPayload
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
import javax.swing.JDialog
import javax.swing.JFormattedTextField
import javax.swing.JInternalFrame
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JSpinner
import javax.swing.SwingUtilities
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteRootFrameUiTest {
    @Test
    fun rootFrameStartsOnLoginSurface() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            assertEquals(RewriteClientRoute.LOGIN, frame.controller.route())
            assertNotNull(frame.loginScene)
            assertNotNull(frame.desktopPane)
            assertNotNull(frame.shellHost)
            assertNull(frame.jMenuBar)
            assertEquals("rewrite-root-login", frame.loginScene.name)
            assertTrue(frame.loginScene.isShowing)
            assertFalse(frame.shellHost.statsRail.isVisible)
            assertFalse(frame.shellHost.countdownLabel.isVisible)
            assertEquals("Hack Wars", frame.title)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun frameSwitchesToDesktopWhenControllerShowsDesktop() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            frame.controller.store.showDesktop()
            waitUntil { frame.desktopPane.isShowing && frame.jMenuBar != null }
            assertTrue(frame.desktopPane.isShowing)
            assertTrue(frame.shellHost.isShowing)
            assertEquals("rewrite-shell-desktop", frame.desktopPane.name)
            assertEquals("rewrite-shell-menu-bar", frame.jMenuBar?.name)
            assertTrue(frame.shellHost.statsRail.isVisible)
            assertFalse(frame.shellHost.countdownLabel.isVisible)
            assertEquals("Hack Wars", frame.title)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun frameKeepsMenuBarHiddenWhileBootstrapping() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            frame.controller.store.beginGameBootstrap()
            waitUntil { frame.loginScene.isShowing }
            assertTrue(frame.loginScene.isShowing)
            assertNull(frame.jMenuBar)
            assertFalse(frame.shellHost.statsRail.isVisible)
            assertFalse(frame.shellHost.countdownLabel.isVisible)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun desktopChromeRendersBoundStatsValuesAndCountdownTitle() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
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
            frame.controller.accept(
                RewriteService.GAME,
                snapshotFrame(
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        identity = ClientComputerIdentity(playerIp = ""),
                        economy = ClientEconomyState(
                            pettyCash = 125.5,
                            bankMoney = 88.25,
                            commodities = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
                        ),
                        runtime = ClientRuntimeState(countdownSeconds = 90),
                    ),
                ),
            )

            waitUntil {
                frame.shellHost.statsRail.isVisible &&
                    frame.shellHost.countdownLabel.isVisible &&
                    frame.title == "Server Shutdown in 1:30"
            }

            assertEquals("LOCAL-IP", label(frame.shellHost.statsRail, "rewrite-shell-stats-ip").text)
            assertEquals("$125.50", label(frame.shellHost.statsRail, "rewrite-shell-stats-petty-cash").text)
            assertEquals("$88.25", label(frame.shellHost.statsRail, "rewrite-shell-stats-bank").text)
            assertEquals("1", label(frame.shellHost.statsRail, "rewrite-shell-stats-commodity-duct-tape").text)
            assertEquals("5", label(frame.shellHost.statsRail, "rewrite-shell-stats-commodity-plutonium").text)
            assertEquals("Server Shutdown in 1:30", frame.shellHost.countdownLabel.text)
            assertEquals("Server Shutdown in 1:30", frame.title)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun clearedCountdownResetsDesktopChromeBackToPlayerTitle() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
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
            frame.controller.accept(
                RewriteService.GAME,
                snapshotFrame(
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
                        runtime = ClientRuntimeState(countdownSeconds = 5),
                    ),
                ),
            )
            waitUntil { frame.shellHost.countdownLabel.isVisible }

            frame.controller.accept(
                RewriteService.GAME,
                snapshotFrame(
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
                        runtime = ClientRuntimeState(countdownSeconds = 0),
                    ),
                ),
            )

            waitUntil {
                !frame.shellHost.countdownLabel.isVisible &&
                    frame.title == "Hack Wars - LOCAL-IP"
            }
            assertEquals("Hack Wars - LOCAL-IP", frame.title)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun shellCanOpenAndMinimizePlaceholderWindow() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            frame.controller.store.showDesktop()
            waitUntil { frame.desktopPane.isShowing && frame.jMenuBar != null }

            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.DEPOSIT)
            }
            waitUntil { frame.desktopPane.allFrames.size == 1 }
            val bankingFrame = frame.desktopPane.allFrames.single()
            assertEquals("rewrite-economy-window-deposit", bankingFrame.name)
            SwingUtilities.invokeAndWait {
                bankingFrame.isIcon = true
            }

            waitUntil { frame.shellHost.menuBar.taskBar.minimizedApplicationCount() == 1 }
            assertEquals(1, frame.shellHost.menuBar.taskBar.minimizedApplicationCount())

            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.DEPOSIT)
            }
            waitUntil {
                frame.shellHost.menuBar.taskBar.minimizedApplicationCount() == 0 &&
                    !bankingFrame.isIcon
            }
            assertEquals(0, frame.shellHost.menuBar.taskBar.minimizedApplicationCount())
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun shellTaskBarScrollButtonsEnableWhenManyFramesAreMinimized() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply {
                setSize(900, 700)
                isVisible = true
            }
        }
        try {
            frame.controller.store.showDesktop()
            waitUntil { frame.desktopPane.isShowing && frame.jMenuBar != null }

            SwingUtilities.invokeAndWait {
                RewriteShellCommand.entries.take(10).forEach { command ->
                    frame.controller.launchShellCommand(command)
                    frame.desktopPane.allFrames
                        .first { it.name == expectedWindowName(command) }
                        .isIcon = true
                }
            }

            waitUntil {
                frame.shellHost.menuBar.taskBar.minimizedApplicationCount() == 10 &&
                    frame.shellHost.menuBar.taskBar.rightScrollButton.isEnabled
            }
            assertTrue(frame.shellHost.menuBar.taskBar.rightScrollButton.isEnabled)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun bankingMenuLaunchesRealWindowsAndDepositAllClosesOnSuccess() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUiSessionGateway()
        val frame = bankingReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.DEPOSIT)
                frame.controller.launchShellCommand(RewriteShellCommand.WITHDRAW)
                frame.controller.launchShellCommand(RewriteShellCommand.TRANSFER)
            }
            waitUntil {
                frame.desktopPane.allFrames.map { it.name }.toSet().containsAll(
                    setOf(
                        "rewrite-economy-window-deposit",
                        "rewrite-economy-window-withdraw",
                        "rewrite-economy-window-transfer",
                    ),
                )
            }

            val depositFrame = frame.desktopPane.allFrames.first { it.name == "rewrite-economy-window-deposit" }
            SwingUtilities.invokeAndWait {
                button(depositFrame, "rewrite-economy-submit-all").doClick()
            }

            waitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true
            }
            val command = sessionGateway.latestGameSession()!!.sentFrames.single().command!!
            val payload = RewriteClientJson.decode(
                com.hackwars.rewrite.protocol.ClientDepositPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals(125.5, payload.amount)
            assertEquals(4, payload.port)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    payload = RewriteClientJson.encode(
                        ClientBankTransactionResponse.serializer(),
                        ClientBankTransactionResponse(
                            stateId = "LOCAL-IP",
                            operation = "deposit",
                            portNumber = 4,
                            requestedAmount = 125.5,
                            appliedAmount = 125.5,
                            pettyCashAfter = 0.0,
                            bankMoneyAfter = 213.75,
                            version = 2,
                        ),
                    ),
                ),
            )

            waitUntil {
                frame.desktopPane.allFrames.none { it.name == "rewrite-economy-window-deposit" }
            }
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun createBountyMenuLaunchesRealDialogAndReusesSingleInstance() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = bankingReadyFrame()
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.CREATE_BOUNTY)
                frame.controller.launchShellCommand(RewriteShellCommand.CREATE_BOUNTY)
            }

            val dialog = waitForDialog("Create Bounty")
            waitUntil {
                java.awt.Window.getWindows()
                    .filterIsInstance<JDialog>()
                    .count { it.isDisplayable && it.title == "Create Bounty" } == 1
            }
            assertEquals("rewrite-bounty-dialog", dialog.name)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun createBountyBrowseFlowPopulatesSelectionAndClosesOnSuccess() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUiSessionGateway()
        val frame = bankingReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.CREATE_BOUNTY)
            }
            val dialog = waitForDialog("Create Bounty")

            SwingUtilities.invokeAndWait {
                @Suppress("UNCHECKED_CAST")
                (comboBox(dialog, "rewrite-bounty-type") as JComboBox<Any>).selectedIndex = 2
            }
            waitUntil { button(dialog, "rewrite-bounty-browse").isEnabled }

            SwingUtilities.invokeAndWait {
                checkBox(dialog, "rewrite-bounty-any-player").doClick()
                button(dialog, "rewrite-bounty-browse").doClick()
            }

            val chooser = waitForWindow(frame, "rewrite-bounty-file-chooser-window")
            waitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true
            }
            val directoryCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = directoryCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientDirectoryListingResponse.serializer(),
                        ClientDirectoryListingResponse(
                            stateId = "LOCAL-IP",
                            path = "/Scripts",
                            directories = listOf(
                                ClientDirectoryEntry(path = "/Scripts/Public", name = "Public"),
                            ),
                            files = listOf(
                                ClientStoredFile(
                                    path = "/Scripts/installer.bin",
                                    name = "installer.bin",
                                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                                    compiledBinary = ClientCompiledBinaryMetadata(
                                        applicationKind = ClientApplicationKind.ATTACK,
                                    ),
                                ),
                                ClientStoredFile(
                                    path = "/Scripts/watch.bin",
                                    name = "watch.bin",
                                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                                    compiledBinary = ClientCompiledBinaryMetadata(
                                        applicationKind = ClientApplicationKind.WATCH,
                                    ),
                                ),
                            ),
                            version = 2,
                        ),
                    ),
                ),
            )

            waitUntil { entryList(chooser).model.size == 1 }
            SwingUtilities.invokeAndWait {
                entryList(chooser).selectedIndex = 0
                button(chooser, "rewrite-files-choose-button").doClick()
            }

            waitUntil { findComponent(dialog, "rewrite-bounty-file-field") != null }
            assertEquals("installer.bin", (findComponent(dialog, "rewrite-bounty-file-field") as JTextField).text)

            SwingUtilities.invokeAndWait {
                spinner(dialog, "rewrite-bounty-reward").value = 125.0
                button(dialog, "rewrite-bounty-create").doClick()
            }

            waitUntil {
                sessionGateway.latestGameSession()!!.sentFrames.size >= 2
            }
            val bountyCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val payload = RewriteClientJson.decode(
                ClientMakeBountyPayload.serializer(),
                bountyCommand.payload.toByteArray(),
            )
            assertEquals("*", payload.target)
            assertEquals(2, payload.type)
            assertEquals("installer.bin", payload.fname)
            assertEquals("/Scripts", payload.folder)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = bountyCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientBountyCreatedResponse.serializer(),
                        ClientBountyCreatedResponse(
                            creatorStateId = "LOCAL-IP",
                            storeStateId = "store1",
                            bountyFile = ClientStoredFile(
                                path = "/Store/install-1.bnty",
                                name = "install-1.bnty",
                            ),
                            reward = 125.0,
                            creatorVersion = 3,
                            storeVersion = 4,
                        ),
                    ),
                ),
            )

            waitUntil { !dialog.isDisplayable }
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun failedCreateBountyResponseKeepsDialogOpenAndShowsInlineError() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUiSessionGateway()
        val frame = bankingReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.CREATE_BOUNTY)
            }
            val dialog = waitForDialog("Create Bounty")

            SwingUtilities.invokeAndWait {
                checkBox(dialog, "rewrite-bounty-any-player").doClick()
                spinner(dialog, "rewrite-bounty-reward").value = 25.0
                button(dialog, "rewrite-bounty-create").doClick()
            }

            waitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true
            }
            val bountyCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = bountyCommand.command_id,
                    status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                    error = ErrorEnvelope(
                        code = "INSUFFICIENT_PETTY_CASH",
                        message = "Not enough petty cash to create this bounty.",
                        retryable = false,
                    ),
                ),
            )

            waitUntil {
                label(dialog, "rewrite-bounty-error").text == "Not enough petty cash to create this bounty."
            }
            assertTrue(dialog.isDisplayable)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun homeLaunchesAsRealWindowAndRendersDirectoryListingContents() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUiSessionGateway()
        val frame = bankingReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.HOME)
            }
            val homeFrame = waitForWindow(frame, "rewrite-home-window")

            waitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true
            }
            val requestCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val requestPayload = RewriteClientJson.decode(
                ClientRequestDirectoryPayload.serializer(),
                requestCommand.payload.toByteArray(),
            )
            assertNull(requestPayload.path)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = requestCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientDirectoryListingResponse.serializer(),
                        ClientDirectoryListingResponse(
                            stateId = "LOCAL-IP",
                            path = "/Public",
                            directories = listOf(
                                ClientDirectoryEntry(
                                    path = "/Public/Archive",
                                    name = "Archive",
                                ),
                            ),
                            files = listOf(
                                ClientStoredFile(
                                    path = "/Public/readme.txt",
                                    name = "readme.txt",
                                    contents = "hello",
                                ),
                            ),
                            version = 2,
                        ),
                    ),
                ),
            )

            waitUntil {
                label(homeFrame, "rewrite-files-path-label").text == "/Public" &&
                    entryList(homeFrame).model.size == 2
            }
            assertEquals("/Public", label(homeFrame, "rewrite-files-path-label").text)
            val rows = (0 until entryList(homeFrame).model.size)
                .map { index -> entryList(homeFrame).model.getElementAt(index).toString() }
            assertEquals(listOf("Archive", "readme.txt"), rows)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun homeErrorStateRendersInlineAndKeepsWindowOpen() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUiSessionGateway()
        val frame = bankingReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.HOME)
            }
            val homeFrame = waitForWindow(frame, "rewrite-home-window")
            waitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true
            }
            val requestCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = requestCommand.command_id,
                    status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                    error = ErrorEnvelope(
                        code = "DIRECTORY_NOT_FOUND",
                        message = "Directory does not exist.",
                        retryable = false,
                    ),
                ),
            )

            waitUntil {
                label(homeFrame, "rewrite-files-error").text == "Directory does not exist."
            }
            assertTrue(homeFrame.isDisplayable)
            assertEquals("Directory does not exist.", label(homeFrame, "rewrite-files-error").text)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun chooserFoundationCanRenderListingAndEmitSelectionCallback() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUiSessionGateway()
        val frame = bankingReadyFrame(sessionGateway = sessionGateway)
        var selectedFilePath: String? = null
        var selectedDisplayedPath: String? = null

        try {
            val chooser = invokeAndWaitResult {
                instantiateLocalFileChooser(
                    controller = frame.controller,
                    title = "Choose File",
                    onFileSelected = { filePath, displayedPath ->
                        selectedFilePath = filePath
                        selectedDisplayedPath = displayedPath
                    },
                ).also { frame.shellHost.showWindow(it) }
            }

            waitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true
            }
            val requestCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = requestCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientDirectoryListingResponse.serializer(),
                        ClientDirectoryListingResponse(
                            stateId = "LOCAL-IP",
                            path = "/Public",
                            files = listOf(
                                ClientStoredFile(
                                    path = "/Public/readme.txt",
                                    name = "readme.txt",
                                    contents = "hello",
                                ),
                            ),
                            version = 2,
                        ),
                    ),
                ),
            )

            waitUntil { entryList(chooser).model.size == 1 }
            SwingUtilities.invokeAndWait {
                entryList(chooser).selectedIndex = 0
                button(chooser, "rewrite-files-choose-button").doClick()
            }

            waitUntil {
                selectedFilePath == "/Public/readme.txt" && selectedDisplayedPath == "/Public"
            }
            assertEquals("/Public/readme.txt", selectedFilePath)
            assertEquals("/Public", selectedDisplayedPath)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun bankingWindowsShowBoundBalancesAndRefreshWhenShellStateChanges() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = bankingReadyFrame()
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.DEPOSIT)
            }
            val depositFrame = waitForWindow(frame, "rewrite-economy-window-deposit")
            assertEquals("$125.50", label(depositFrame, "rewrite-economy-balance-value").text)
            assertEquals(
                "4: Integra...",
                comboBox(depositFrame, "rewrite-economy-port-combo").getItemAt(0).toString(),
            )

            frame.controller.accept(
                RewriteService.GAME,
                snapshotFrame(
                    bankingShellState(
                        pettyCash = 200.0,
                        bankMoney = 88.25,
                        ports = listOf(
                            bankingPort(4, "Primary"),
                            bankingPort(7, "Second Bank"),
                        ),
                    ),
                ),
            )

            waitUntil {
                label(depositFrame, "rewrite-economy-balance-value").text == "$200.00" &&
                    comboBox(depositFrame, "rewrite-economy-port-combo").itemCount == 2
            }
            assertEquals("4: Primary", comboBox(depositFrame, "rewrite-economy-port-combo").getItemAt(0).toString())
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun noBankPortStateDisablesSubmitControlsAndFailedRequestShowsInlineError() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeUiSessionGateway()
        val frame = bankingReadyFrame(
            sessionGateway = sessionGateway,
            shellState = bankingShellState(ports = emptyList()),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.WITHDRAW)
            }
            val withdrawFrame = waitForWindow(frame, "rewrite-economy-window-withdraw")
            assertFalse(button(withdrawFrame, "rewrite-economy-submit").isEnabled)

            frame.controller.accept(
                RewriteService.GAME,
                snapshotFrame(),
            )
            waitUntil {
                button(withdrawFrame, "rewrite-economy-submit").isEnabled
            }

            SwingUtilities.invokeAndWait {
                amountField(withdrawFrame).value = 10.0
                button(withdrawFrame, "rewrite-economy-submit").doClick()
            }
            waitUntil {
                sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true
            }

            val command = sessionGateway.latestGameSession()!!.sentFrames.single().command!!
            val payload = RewriteClientJson.decode(
                ClientWithdrawPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals(10.0, payload.amount)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                    error = ErrorEnvelope(
                        code = "NO_BANK_MONEY",
                        message = "Not enough bank money to withdraw.",
                        retryable = false,
                    ),
                ),
            )

            waitUntil {
                label(withdrawFrame, "rewrite-economy-error").text == "Not enough bank money to withdraw."
            }
            assertTrue(withdrawFrame.isDisplayable)
            assertEquals("Not enough bank money to withdraw.", label(withdrawFrame, "rewrite-economy-error").text)
        } finally {
            disposeFrame(frame)
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

    private fun snapshotFrame(snapshot: ClientGameSnapshot): FrameEnvelope {
        return RewriteFrames.snapshot(
            gameStateId = snapshot.id,
            sequence = snapshot.version,
            payload = RewriteClientJson.encode(ClientGameSnapshot.serializer(), snapshot),
        )
    }

    private fun snapshotFrame(): FrameEnvelope = snapshotFrame(bankingShellState())

    private fun bankingShellState(
        pettyCash: Double = 125.5,
        bankMoney: Double = 88.25,
        ports: List<ClientPortState> = listOf(
            bankingPort(4, "Integration Bank", defaultPort = true),
        ),
    ): ClientGameSnapshot {
        return ClientGameSnapshot(
            id = "LOCAL-IP",
            identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
            economy = ClientEconomyState(
                pettyCash = pettyCash,
                bankMoney = bankMoney,
                defaultBankPort = ports.firstOrNull { it.defaultPort }?.number,
            ),
            ports = ports,
            runtime = ClientRuntimeState(),
        )
    }

    private fun bankingPort(
        portNumber: Int,
        note: String,
        defaultPort: Boolean = false,
    ): ClientPortState {
        return ClientPortState(
            number = portNumber,
            defaultPort = defaultPort,
            note = note,
            installedApplication = ClientInstalledApplication(kind = "BANKING"),
        )
    }

    private fun bankingReadyFrame(
        sessionGateway: RewriteServiceSessionGateway = NoOpRewriteServiceSessionGateway,
        shellState: ClientGameSnapshot = bankingShellState(),
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

    private fun label(root: Component, name: String): JLabel {
        return findComponent(root, name) as? JLabel
            ?: error("Unable to find JLabel named $name")
    }

    private fun comboBox(root: Component, name: String): JComboBox<*> {
        return findComponent(root, name) as? JComboBox<*>
            ?: error("Unable to find JComboBox named $name")
    }

    private fun checkBox(root: Component, name: String): JCheckBox {
        return findComponent(root, name) as? JCheckBox
            ?: error("Unable to find JCheckBox named $name")
    }

    private fun button(root: Component, name: String): JButton {
        return findComponent(root, name) as? JButton
            ?: error("Unable to find JButton named $name")
    }

    private fun entryList(root: Component): JList<*> {
        return findComponent(root, "rewrite-files-entry-list") as? JList<*>
            ?: error("Unable to find entry list")
    }

    private fun amountField(root: Component): JFormattedTextField {
        return findComponent(root, "rewrite-economy-amount-field") as? JFormattedTextField
            ?: error("Unable to find amount field")
    }

    private fun spinner(root: Component, name: String): JSpinner {
        return findComponent(root, name) as? JSpinner
            ?: error("Unable to find spinner named $name")
    }

    private fun waitForWindow(
        frame: RewriteRootFrame,
        windowName: String,
    ): JInternalFrame {
        waitUntil { frame.desktopPane.allFrames.any { it.name == windowName } }
        return frame.desktopPane.allFrames.first { it.name == windowName }
    }

    private fun waitForDialog(
        title: String,
    ): JDialog {
        waitUntil {
            java.awt.Window.getWindows().any { window ->
                window is JDialog && window.isDisplayable && window.title == title
            }
        }
        return java.awt.Window.getWindows()
            .filterIsInstance<JDialog>()
            .first { it.isDisplayable && it.title == title }
    }

    private fun expectedWindowName(command: RewriteShellCommand): String = when (command) {
        RewriteShellCommand.DEPOSIT,
        RewriteShellCommand.WITHDRAW,
        RewriteShellCommand.TRANSFER -> "rewrite-economy-window-${command.stableId}"
        RewriteShellCommand.HOME -> "rewrite-home-window"
        else -> "rewrite-shell-window-${command.stableId}"
    }

    private fun instantiateLocalFileChooser(
        controller: RewriteRootController,
        title: String,
        onFileSelected: (filePath: String, displayedPath: String) -> Unit,
    ): JInternalFrame {
        val chooserClass = Class.forName("com.hackwars.rewrite.client.files.RewriteLocalFileChooserWindow")
        val constructor = chooserClass.declaredConstructors.first { it.parameterCount == 5 }
        constructor.isAccessible = true
        val chooser = constructor.newInstance(
            controller,
            title,
            { selection: Any? ->
                if (selection != null) {
                    val displayedPath = selection.javaClass.getMethod("getDisplayedPath").invoke(selection) as String
                    val file = selection.javaClass.getMethod("getFile").invoke(selection)
                    val filePath = file.javaClass.getMethod("getPath").invoke(file) as String
                    onFileSelected(filePath, displayedPath)
                }
            },
            { _: Any? -> true },
            { _: Any? -> true },
        )
        return chooser as JInternalFrame
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

    private class FakeUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakeUiSession? = sessions.lastOrNull { it.service == RewriteService.GAME }
    }

    private class FakeUiSession(
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
