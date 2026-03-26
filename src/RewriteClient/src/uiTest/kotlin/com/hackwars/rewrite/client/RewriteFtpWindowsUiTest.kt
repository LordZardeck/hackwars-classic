package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.testsupport.rewriteUiAuthenticatedDesktopFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiDisposeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiFindNamedComponent
import com.hackwars.rewrite.client.testsupport.rewriteUiInvokeAndWaitResult
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForDialog
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForWindow
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitUntil
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientRequestDirectoryPayload
import com.hackwars.rewrite.protocol.ClientRequestSecondaryDirectoryPayload
import com.hackwars.rewrite.protocol.ClientSecondaryDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientSellFilePayload
import com.hackwars.rewrite.protocol.ClientSellFileResponse
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.ErrorEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.time.Instant
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JInternalFrame
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.JSpinner
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteFtpWindowsUiTest {
    @Test
    fun shopAndPublicFtpLaunchAsRealWindowsAndReuseSingleInstance() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeFtpUiSessionGateway()
        val frame = ftpReadyFrame(sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.SHOP_FTP)
                frame.controller.launchShellCommand(RewriteShellCommand.SHOP_FTP)
                frame.controller.launchShellCommand(RewriteShellCommand.PUBLIC_FTP)
                frame.controller.launchShellCommand(RewriteShellCommand.PUBLIC_FTP)
            }

            waitForWindow(frame, "rewrite-shell-window-shop_ftp")
            waitForWindow(frame, "rewrite-shell-window-public_ftp")

            waitUntil {
                frame.desktopPane.allFrames.count { it.name == "rewrite-shell-window-shop_ftp" } == 1 &&
                    frame.desktopPane.allFrames.count { it.name == "rewrite-shell-window-public_ftp" } == 1
            }
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun shopFtpRendersLocalSourceAndStoreListingAndSuccessfulSellRefreshesStore() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeFtpUiSessionGateway()
        val frame = ftpReadyFrame(sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.SHOP_FTP)
            }

            val shopWindow = waitForWindow(frame, "rewrite-shell-window-shop_ftp")
            waitUntil {
                latestGameCommand(sessionGateway, "requestdirectory") != null &&
                    latestGameCommand(sessionGateway, "requestsecondarydirectory") != null
            }

            val localCommand = latestGameCommand(sessionGateway, "requestdirectory")!!
            val localPayload = RewriteClientJson.decode(
                ClientRequestDirectoryPayload.serializer(),
                localCommand.payload.toByteArray(),
            )
            assertEquals(null, localPayload.path)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = localCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientDirectoryListingResponse.serializer(),
                        ClientDirectoryListingResponse(
                            stateId = "192.0.2.10",
                            path = "/",
                            directories = listOf(
                                ClientDirectoryEntry(path = "/Store", name = "Store"),
                                ClientDirectoryEntry(path = "/Public", name = "Public"),
                                ClientDirectoryEntry(path = "/Scripts", name = "Scripts"),
                            ),
                            files = listOf(
                                ClientStoredFile(
                                    path = "/merchant.bin",
                                    name = "merchant.bin",
                                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                                    quantity = 3,
                                ),
                            ),
                            version = 2,
                        ),
                    ),
                ),
            )

            val storeCommand = latestGameCommand(sessionGateway, "requestsecondarydirectory")!!
            val storePayload = RewriteClientJson.decode(
                ClientRequestSecondaryDirectoryPayload.serializer(),
                storeCommand.payload.toByteArray(),
            )
            assertEquals("/Store", storePayload.path)
            assertEquals("192.0.2.10", storePayload.targetIp)
            assertEquals(9, storePayload.port)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = storeCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientSecondaryDirectoryListingResponse.serializer(),
                        ClientSecondaryDirectoryListingResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "192.0.2.10",
                            portNumber = 9,
                            path = "/Store",
                            files = listOf(
                                ClientStoredFile(
                                    path = "/Store/existing.bin",
                                    name = "existing.bin",
                                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                                    quantity = 1,
                                ),
                            ),
                            version = 3,
                        ),
                    ),
                ),
            )

            waitUntil {
                list(shopWindow, "rewrite-files-entry-list").model.size >= 2 &&
                    text(shopWindow, "rewrite-remote-files-path-label") == "/Store" &&
                    listContents(shopWindow, "rewrite-remote-files-entry-list").contains("existing.bin")
            }
            val localEntries = listContents(shopWindow, "rewrite-files-entry-list")
            assertEquals(listOf("Scripts", "merchant.bin"), localEntries)

            SwingUtilities.invokeAndWait {
                list(shopWindow, "rewrite-files-entry-list").selectedIndex = 1
            }
            waitUntil { button(shopWindow, "rewrite-shop-ftp-sell-button").isEnabled }

            SwingUtilities.invokeAndWait {
                button(shopWindow, "rewrite-shop-ftp-sell-button").doClick()
            }

            val sellDialog = waitForDialog("rewrite-shop-ftp-sell-dialog")
            waitUntil { text(sellDialog, "rewrite-shop-ftp-sell-file-field") == "merchant.bin" }
            assertEquals(1, spinner(sellDialog, "rewrite-shop-ftp-sell-quantity-spinner").value)

            SwingUtilities.invokeAndWait {
                button(sellDialog, "rewrite-shop-ftp-sell-confirm-button").doClick()
            }

            waitUntil { latestGameCommand(sessionGateway, "sellfile") != null }
            val sellCommand = latestGameCommand(sessionGateway, "sellfile")!!
            val sellPayload = RewriteClientJson.decode(
                ClientSellFilePayload.serializer(),
                sellCommand.payload.toByteArray(),
            )
            assertEquals("192.0.2.10", sellPayload.ip)
            assertEquals("/", sellPayload.location)
            assertEquals("merchant.bin", sellPayload.fileName)
            assertEquals(1, sellPayload.quantity)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = sellCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientSellFileResponse.serializer(),
                        ClientSellFileResponse(
                            stateId = "192.0.2.10",
                            file = ClientStoredFile(
                                path = "/Store/merchant.bin",
                                name = "merchant.bin",
                                kind = ClientStoredFileKind.APPLICATION_BINARY,
                                quantity = 1,
                            ),
                            version = 4,
                        ),
                    ),
                ),
            )
            waitUntil {
                gameCommands(sessionGateway, "requestsecondarydirectory").size >= 2
            }
            val refreshedStoreCommand = gameCommands(sessionGateway, "requestsecondarydirectory").last()
            assertTrue(refreshedStoreCommand.command_id != storeCommand.command_id)
            val refreshedPayload = RewriteClientJson.decode(
                ClientRequestSecondaryDirectoryPayload.serializer(),
                refreshedStoreCommand.payload.toByteArray(),
            )
            assertEquals("/Store", refreshedPayload.path)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = refreshedStoreCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientSecondaryDirectoryListingResponse.serializer(),
                        ClientSecondaryDirectoryListingResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "192.0.2.10",
                            portNumber = 9,
                            path = "/Store",
                            files = listOf(
                                ClientStoredFile(
                                    path = "/Store/existing.bin",
                                    name = "existing.bin",
                                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                                    quantity = 1,
                                ),
                                ClientStoredFile(
                                    path = "/Store/merchant.bin",
                                    name = "merchant.bin",
                                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                                    quantity = 1,
                                ),
                            ),
                            version = 5,
                        ),
                    ),
                ),
            )

            waitUntil {
                shopWindow.isDisplayable
            }
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun publicFtpConnectPopulatesReadOnlyRemoteListing() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeFtpUiSessionGateway()
        val frame = ftpReadyFrame(sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PUBLIC_FTP)
            }

            val publicWindow = waitForWindow(frame, "rewrite-shell-window-public_ftp")
            SwingUtilities.invokeAndWait {
                textField(publicWindow, "rewrite-public-ftp-target-ip-field").text = "198.51.100.20"
                spinner(publicWindow, "rewrite-public-ftp-target-port-spinner").value = 25
                button(publicWindow, "rewrite-public-ftp-connect-button").doClick()
            }

            waitUntil { latestGameCommand(sessionGateway, "requestsecondarydirectory") != null }
            val connectCommand = latestGameCommand(sessionGateway, "requestsecondarydirectory")!!
            val connectPayload = RewriteClientJson.decode(
                ClientRequestSecondaryDirectoryPayload.serializer(),
                connectCommand.payload.toByteArray(),
            )
            assertEquals("/Public", connectPayload.path)
            assertEquals("198.51.100.20", connectPayload.targetIp)
            assertEquals(25, connectPayload.port)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = connectCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientSecondaryDirectoryListingResponse.serializer(),
                        ClientSecondaryDirectoryListingResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "198.51.100.20",
                            portNumber = 25,
                            path = "/Public",
                            directories = listOf(
                                ClientDirectoryEntry(path = "/Public/docs", name = "docs"),
                            ),
                            files = listOf(
                                ClientStoredFile(
                                    path = "/Public/readme.txt",
                                    name = "readme.txt",
                                    kind = ClientStoredFileKind.TEXT,
                                ),
                            ),
                            version = 2,
                        ),
                    ),
                ),
            )

            waitUntil {
                list(publicWindow, "rewrite-remote-files-entry-list").model.size == 2 &&
                    text(publicWindow, "rewrite-remote-files-path-label") == "/Public" &&
                    !button(publicWindow, "rewrite-remote-files-up-button").isEnabled
            }

        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun publicFtpFailedConnectKeepsWindowOpenAndShowsInlineError() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeFtpUiSessionGateway()
        val frame = ftpReadyFrame(sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PUBLIC_FTP)
            }

            val publicWindow = waitForWindow(frame, "rewrite-shell-window-public_ftp")
            SwingUtilities.invokeAndWait {
                textField(publicWindow, "rewrite-public-ftp-target-ip-field").text = "FAIL-IP"
                spinner(publicWindow, "rewrite-public-ftp-target-port-spinner").value = 26
                button(publicWindow, "rewrite-public-ftp-connect-button").doClick()
            }

            waitUntil { latestGameCommand(sessionGateway, "requestsecondarydirectory") != null }
            val failedCommand = latestGameCommand(sessionGateway, "requestsecondarydirectory")!!
            val failedPayload = RewriteClientJson.decode(
                ClientRequestSecondaryDirectoryPayload.serializer(),
                failedCommand.payload.toByteArray(),
            )
            assertEquals("/Public", failedPayload.path)
            assertEquals("FAIL-IP", failedPayload.targetIp)
            assertEquals(26, failedPayload.port)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = failedCommand.command_id,
                    status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                    error = ErrorEnvelope(
                        code = "REMOTE_CONNECT_FAILED",
                        message = "Remote FTP unavailable.",
                        retryable = false,
                    ),
                ),
            )

            waitUntil {
                text(publicWindow, "rewrite-public-ftp-error") == "Remote FTP unavailable." &&
                    publicWindow.isDisplayable
            }
        } finally {
            disposeFrame(frame)
        }
    }

    private fun ftpReadyFrame(
        sessionGateway: FakeFtpUiSessionGateway,
    ): RewriteRootFrame {
        return rewriteUiAuthenticatedDesktopFrame(
            sessionGateway = sessionGateway,
            snapshot = ftpSnapshot(),
        )
    }

    private fun ftpSnapshot(): ClientGameSnapshot {
        return ClientGameSnapshot(
            id = "192.0.2.10",
            ports = listOf(
                ClientPortState(
                    number = 9,
                    enabled = true,
                    dummy = false,
                    defaultPort = true,
                    note = "shop-default",
                    installedApplication = ClientInstalledApplication(name = "ftp", kind = "FTP"),
                ),
                ClientPortState(
                    number = 4,
                    enabled = true,
                    dummy = false,
                    note = "ftp",
                    installedApplication = ClientInstalledApplication(name = "ftp", kind = "FTP"),
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
        sessionGateway: FakeFtpUiSessionGateway,
        commandName: String,
    ): List<hackwars.rewrite.v1.CommandEnvelope> {
        return sessionGateway.latestGameSession()
            ?.sentFrames
            .orEmpty()
            .mapNotNull(FrameEnvelope::command)
            .filter { it.command_name == commandName }
    }

    private fun latestGameCommand(
        sessionGateway: FakeFtpUiSessionGateway,
        commandName: String,
    ): hackwars.rewrite.v1.CommandEnvelope? {
        return gameCommands(sessionGateway, commandName).lastOrNull()
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

    private fun spinner(root: Component, name: String): JSpinner {
        return findComponent(root, name) as? JSpinner ?: error("Unable to find spinner named $name")
    }

    private fun list(root: Component, name: String): JList<*> {
        return findComponent(root, name) as? JList<*> ?: error("Unable to find list named $name")
    }

    private fun listContents(root: Component, name: String): List<String> {
        val model = list(root, name).model
        return (0 until model.size).map { index -> model.getElementAt(index).toString() }
    }

    private class FakeFtpUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeFtpUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeFtpUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakeFtpUiSession? {
            return sessions.lastOrNull { it.service == RewriteService.GAME }
        }
    }

    private class FakeFtpUiSession(
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
