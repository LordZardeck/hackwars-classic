package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.testsupport.rewriteUiAuthenticatedDesktopFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiDisposeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiFindNamedComponent
import com.hackwars.rewrite.client.testsupport.rewriteUiInvokeAndWaitResult
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForDialog
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForWindow
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitUntil
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledWatch
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientScriptFamily
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.ClientWatchKind
import com.hackwars.rewrite.protocol.ClientWatchListResponse
import com.hackwars.rewrite.protocol.ClientWatchManagerState
import com.hackwars.rewrite.protocol.ClientWatchMutationResponse
import com.hackwars.rewrite.protocol.ClientInstallWatchPayload
import com.hackwars.rewrite.protocol.ClientSetWatchObservedPortsPayload
import com.hackwars.rewrite.protocol.ClientSetWatchOnOffPayload
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.GraphicsEnvironment
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteWatchManagerUiTest {
    @Test
    fun watchManagerLaunchesAsRealWindowAndReusesSingleInstance() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeWatchUiSessionGateway()
        val frame = watchReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.WATCH_MANAGER)
                frame.controller.launchShellCommand(RewriteShellCommand.WATCH_MANAGER)
            }

            val window = waitForWindow(frame, "rewrite-watch-manager-window")
            respondToLatestWatchFetch(frame, sessionGateway)
            waitUntil {
                frame.desktopPane.allFrames.count { it.name == "rewrite-watch-manager-window" } == 1 &&
                    text(window, "rewrite-watch-manager-status") == "Loaded 2 watches."
            }

            assertEquals("Guard", textField(window, "rewrite-watch-row-note-0").text)
            assertTrue(comboBox(window, "rewrite-watch-row-port-1").isEnabled.not())
            assertTrue(comboBox(window, "rewrite-watch-row-observed-1").isEnabled.not())
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun installNewWatchOpensChooserAndSendsInstallwatch() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeWatchUiSessionGateway()
        val frame = watchReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.WATCH_MANAGER)
            }

            val window = waitForWindow(frame, "rewrite-watch-manager-window")
            respondToLatestWatchFetch(frame, sessionGateway)
            waitUntil { menuItem(window.jMenuBar, "rewrite-watch-manager-install-menu-item").isEnabled }

            SwingUtilities.invokeAndWait {
                menuItem(window.jMenuBar, "rewrite-watch-manager-install-menu-item").doClick()
            }

            val chooser = waitForWindow(frame, "rewrite-watch-install-chooser-window")
            respondToLatestDirectoryCommand(
                frame = frame,
                sessionGateway = sessionGateway,
                response = ClientDirectoryListingResponse(
                    stateId = "192.0.2.10",
                    path = "/Programs",
                    files = listOf(
                        ClientStoredFile(
                            path = "/Programs/watch.bin",
                            name = "watch.bin",
                            kind = ClientStoredFileKind.APPLICATION_BINARY,
                            compiledBinary = ClientCompiledBinaryMetadata(
                                scriptFamily = ClientScriptFamily.WATCH,
                            ),
                        ),
                        ClientStoredFile(
                            path = "/Programs/attack.bin",
                            name = "attack.bin",
                            kind = ClientStoredFileKind.APPLICATION_BINARY,
                            compiledBinary = ClientCompiledBinaryMetadata(
                                scriptFamily = ClientScriptFamily.ATTACK,
                            ),
                        ),
                    ),
                    version = 4,
                ),
            )

            waitUntil { list(chooser, "rewrite-files-entry-list").model.size == 1 }
            SwingUtilities.invokeAndWait {
                list(chooser, "rewrite-files-entry-list").selectedIndex = 0
                comboBox(chooser, "rewrite-watch-install-type-combo").selectedIndex = 1
                comboBox(chooser, "rewrite-watch-install-port-combo").selectedItem = "8"
                button(chooser, "rewrite-watch-install-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "installwatch" }
            val command = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val payload = RewriteClientJson.decode(
                ClientInstallWatchPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals("/Programs", payload.path)
            assertEquals("watch.bin", payload.name)
            assertEquals(1, payload.type)
            assertEquals(8, payload.port)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    payload = RewriteClientJson.encode(
                        ClientWatchMutationResponse.serializer(),
                        ClientWatchMutationResponse(
                            stateId = "192.0.2.10",
                            operation = "installwatch",
                            accepted = true,
                            message = "installwatch-succeeded",
                            snapshot = watchListResponse(),
                        ),
                    ),
                ),
            )

            waitUntil { text(window, "rewrite-watch-manager-status") == "installwatch-succeeded" }
            assertTrue(window.isDisplayable)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun observedPortsDialogSubmitsExpectedPayload() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeWatchUiSessionGateway()
        val frame = watchReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.WATCH_MANAGER)
            }

            val window = waitForWindow(frame, "rewrite-watch-manager-window")
            respondToLatestWatchFetch(frame, sessionGateway)
            waitUntil { button(window, "rewrite-watch-row-edit-observed-0").isEnabled }

            SwingUtilities.invokeLater {
                button(window, "rewrite-watch-row-edit-observed-0").doClick()
            }

            val dialog = waitForDialog("rewrite-watch-observed-ports-dialog")
            SwingUtilities.invokeAndWait {
                checkBox(dialog, "rewrite-watch-observed-port-8").isSelected = true
                button(dialog, "rewrite-watch-observed-ports-ok").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "setwatchobservedports" }
            val command = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val payload = RewriteClientJson.decode(
                ClientSetWatchObservedPortsPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals(0, payload.watchId)
            assertEquals(listOf(6, 8), payload.observedPorts)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun failedWatchMutationShowsInlineErrorAndKeepsWindowOpen() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeWatchUiSessionGateway()
        val frame = watchReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.WATCH_MANAGER)
            }

            val window = waitForWindow(frame, "rewrite-watch-manager-window")
            respondToLatestWatchFetch(frame, sessionGateway)
            waitUntil { button(window, "rewrite-watch-row-onoff-0").isEnabled }

            SwingUtilities.invokeAndWait {
                button(window, "rewrite-watch-row-onoff-0").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "setwatchonoff" }
            val command = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val payload = RewriteClientJson.decode(
                ClientSetWatchOnOffPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals(0, payload.watchId)
            assertEquals(false, payload.state)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    payload = RewriteClientJson.encode(
                        ClientWatchMutationResponse.serializer(),
                        ClientWatchMutationResponse(
                            stateId = "192.0.2.10",
                            operation = "setwatchonoff",
                            accepted = false,
                            message = "You cannot disable watches while overheated.",
                            snapshot = watchListResponse(),
                        ),
                    ),
                ),
            )

            waitUntil { text(window, "rewrite-watch-manager-error") == "You cannot disable watches while overheated." }
            assertTrue(window.isDisplayable)
        } finally {
            disposeFrame(frame)
        }
    }

    private fun watchReadyFrame(
        sessionGateway: FakeWatchUiSessionGateway,
        snapshot: ClientGameSnapshot = watchSnapshot(),
    ): RewriteRootFrame {
        return rewriteUiAuthenticatedDesktopFrame(
            sessionGateway = sessionGateway,
            snapshot = snapshot,
        )
    }

    private fun watchSnapshot(): ClientGameSnapshot {
        return ClientGameSnapshot(
            id = "192.0.2.10",
            version = 1,
            identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
            ports = listOf(
                ClientPortState(number = 4),
                ClientPortState(number = 6),
                ClientPortState(number = 8),
            ),
            watches = ClientWatchManagerState(
                watches = listOf(
                    ClientInstalledWatch(
                        kind = ClientWatchKind.HEALTH,
                        enabled = true,
                        note = "Guard",
                        cpuCost = 2.5,
                        quantityThreshold = 75.0,
                        installPort = 6,
                        observedPorts = listOf(6),
                        searchFirewallType = 2,
                    ),
                    ClientInstalledWatch(
                        kind = ClientWatchKind.SCAN,
                        enabled = false,
                        note = "Scanner",
                        cpuCost = 1.0,
                        quantityThreshold = 0.0,
                        installPort = 4,
                    ),
                ),
            ),
        )
    }

    private fun watchListResponse(): ClientWatchListResponse {
        return ClientWatchListResponse(
            stateId = "192.0.2.10",
            watches = watchSnapshot().watches.watches,
            installedCount = 2,
            maximumInstalledCount = 21,
            activeCount = 1,
            maximumActiveCount = 6,
            currentCpuLoad = 3.5,
            maximumCpuLoad = 25.0,
        )
    }

    private fun respondToLatestWatchFetch(
        frame: RewriteRootFrame,
        sessionGateway: FakeWatchUiSessionGateway,
    ) {
        rewriteUiWaitUntil { sessionGateway.latestGameSession()?.sentFrames?.lastOrNull()?.command?.command_name == "fetchwatches" }
        val command = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
        frame.controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientWatchListResponse.serializer(),
                    watchListResponse(),
                ),
            ),
        )
    }

    private fun respondToLatestDirectoryCommand(
        frame: RewriteRootFrame,
        sessionGateway: FakeWatchUiSessionGateway,
        response: ClientDirectoryListingResponse,
    ) {
        rewriteUiWaitUntil { sessionGateway.latestGameSession()?.sentFrames?.lastOrNull()?.command?.command_name == "requestdirectory" }
        val command = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
        frame.controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientDirectoryListingResponse.serializer(),
                    response,
                ),
            ),
        )
    }

    private fun waitForWindow(
        frame: RewriteRootFrame,
        windowName: String,
    ) = rewriteUiWaitForWindow(frame, windowName)

    private fun waitForDialog(name: String) = rewriteUiWaitForDialog(name)

    private fun waitUntil(timeoutMillis: Long = 3_000, predicate: () -> Boolean) {
        rewriteUiWaitUntil(timeoutMillis, predicate)
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

    private fun menuItem(menuBar: JMenuBar, name: String): JMenuItem {
        for (menuIndex in 0 until menuBar.menuCount) {
            val menu = menuBar.getMenu(menuIndex) ?: continue
            for (itemIndex in 0 until menu.itemCount) {
                val item = menu.getItem(itemIndex) ?: continue
                if (item.name == name) {
                    return item
                }
            }
        }
        error("Unable to find menu item named $name")
    }

    private fun button(root: Component, name: String): JButton {
        return findComponent(root, name) as? JButton
            ?: error("Unable to find button named $name")
    }

    private fun comboBox(root: Component, name: String): JComboBox<*> {
        return findComponent(root, name) as? JComboBox<*>
            ?: error("Unable to find combo box named $name")
    }

    private fun list(root: Component, name: String): JList<*> {
        return findComponent(root, name) as? JList<*>
            ?: error("Unable to find list named $name")
    }

    private fun checkBox(root: Component, name: String): JCheckBox {
        return findComponent(root, name) as? JCheckBox
            ?: error("Unable to find check box named $name")
    }

    private fun textField(root: Component, name: String): JTextField {
        return findComponent(root, name) as? JTextField
            ?: error("Unable to find text field named $name")
    }

    private fun text(root: Component, name: String): String {
        val component = findComponent(root, name) ?: error("Unable to find component named $name")
        return when (component) {
            is JLabel -> component.text
            else -> error("Unsupported text component ${component::class.java.name}")
        }
    }

    private class FakeWatchUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeWatchUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeWatchUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakeWatchUiSession? {
            return sessions.lastOrNull { it.service == RewriteService.GAME }
        }
    }

    private class FakeWatchUiSession(
        override val service: RewriteService,
        private val onInboundFrame: (FrameEnvelope) -> Unit,
    ) : RewriteServiceSession {
        val sentFrames = mutableListOf<FrameEnvelope>()
        private var closed = false

        override suspend fun send(frame: FrameEnvelope) {
            check(!closed)
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
