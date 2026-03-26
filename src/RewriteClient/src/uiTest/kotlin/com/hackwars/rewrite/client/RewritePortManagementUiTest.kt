package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientHealPortPayload
import com.hackwars.rewrite.protocol.ClientHealPortOutcome
import com.hackwars.rewrite.protocol.ClientHealPortResponse
import com.hackwars.rewrite.protocol.ClientInstallApplicationPayload
import com.hackwars.rewrite.protocol.ClientInstallApplicationResponse
import com.hackwars.rewrite.protocol.ClientInstallFirewallPayload
import com.hackwars.rewrite.protocol.ClientInstallFirewallResponse
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientInstalledFirewall
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.time.Instant
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JInternalFrame
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewritePortManagementUiTest {
    @Test
    fun portManagementLaunchesAsRealWindowRendersRowsAndReusesSingleInstance() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakePortManagementUiSessionGateway()
        val frame = portManagementReadyFrame(
            sessionGateway = sessionGateway,
            snapshot = portManagementSnapshot(),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PORT_MANAGEMENT)
                frame.controller.launchShellCommand(RewriteShellCommand.PORT_MANAGEMENT)
            }

            val window = waitForWindow(frame, "rewrite-port-management-window")
            waitUntil {
                frame.desktopPane.allFrames.count { it.name == "rewrite-port-management-window" } == 1 &&
                    table(window, "rewrite-port-management-table").rowCount == 2
            }

            val portTable = table(window, "rewrite-port-management-table")
            assertEquals(6, portTable.getValueAt(0, 0))
            assertEquals("bank.bin", portTable.getValueAt(0, 1))
            assertEquals("guard.fw", portTable.getValueAt(0, 2))
            assertEquals("4/10", portTable.getValueAt(0, 3))
            assertEquals("Main bank", portTable.getValueAt(0, 9))

            val enabledToggle = checkBox(window, "rewrite-port-management-enabled-toggle")
            val defaultToggle = checkBox(window, "rewrite-port-management-default-toggle")
            val dummyToggle = checkBox(window, "rewrite-port-management-dummy-toggle")
            val noteField = textField(window, "rewrite-port-management-note-field")
            assertFalse(enabledToggle.isEnabled)
            assertFalse(defaultToggle.isEnabled)
            assertFalse(dummyToggle.isEnabled)
            assertFalse(noteField.isEnabled)
            assertFalse(noteField.isEditable)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun healActionSendsHealPortAndKeepsWindowOpen() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakePortManagementUiSessionGateway()
        val frame = portManagementReadyFrame(
            sessionGateway = sessionGateway,
            snapshot = portManagementSnapshot(),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PORT_MANAGEMENT)
            }

            val window = waitForWindow(frame, "rewrite-port-management-window")
            SwingUtilities.invokeAndWait {
                table(window, "rewrite-port-management-table").selectionModel.setSelectionInterval(0, 0)
                button(window, "rewrite-port-management-heal-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.isNotEmpty() }
            val command = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val payload = RewriteClientJson.decode(
                ClientHealPortPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals("healport", command.command_name)
            assertEquals("LOCAL-IP", payload.ip)
            assertEquals(6, payload.port)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    payload = RewriteClientJson.encode(
                        ClientHealPortResponse.serializer(),
                        ClientHealPortResponse(
                            stateId = "LOCAL-IP",
                            portNumber = 6,
                            accepted = true,
                            outcome = ClientHealPortOutcome.SUCCESS,
                            message = "healport-succeeded",
                            chargedAmount = 5.0,
                            pettyCashAfter = 20.0,
                            healthAfter = 100.0,
                            healCountAfter = 1,
                            version = 2,
                        ),
                    ),
                ),
            )

            waitUntil { text(window, "rewrite-port-management-status") == "Healed port 6." }
            assertTrue(window.isDisplayable)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun installProgramAndFirewallFlowsUseChooserAndKeepWindowOpen() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakePortManagementUiSessionGateway()
        val frame = portManagementReadyFrame(
            sessionGateway = sessionGateway,
            snapshot = portManagementSnapshot(
                ports = listOf(
                    ClientPortState(
                        number = 6,
                        enabled = true,
                        health = 100.0,
                        maxCpuCost = 10.0,
                        note = "Empty",
                    ),
                ),
            ),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PORT_MANAGEMENT)
            }

            val window = waitForWindow(frame, "rewrite-port-management-window")
            SwingUtilities.invokeAndWait {
                table(window, "rewrite-port-management-table").selectionModel.setSelectionInterval(0, 0)
                button(window, "rewrite-port-management-install-program-button").doClick()
            }

            val programChooser = waitForWindow(frame, "rewrite-port-management-program-chooser-window")
            respondToLatestDirectoryCommand(
                frame = frame,
                sessionGateway = sessionGateway,
                response = directoryListing(
                    path = "/Programs",
                    files = listOf(
                        ClientStoredFile(
                            path = "/Programs/http.bin",
                            name = "http.bin",
                            kind = ClientStoredFileKind.APPLICATION_BINARY,
                            compiledBinary = ClientCompiledBinaryMetadata(
                                applicationKind = ClientApplicationKind.HTTP,
                            ),
                        ),
                        ClientStoredFile(
                            path = "/Programs/readme.txt",
                            name = "readme.txt",
                            kind = ClientStoredFileKind.TEXT,
                        ),
                    ),
                ),
            )
            waitUntil { list(programChooser, "rewrite-files-entry-list").model.size == 1 }
            SwingUtilities.invokeAndWait {
                list(programChooser, "rewrite-files-entry-list").selectedIndex = 0
                button(programChooser, "rewrite-files-choose-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "installapplication" }
            val installProgramCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val installProgramPayload = RewriteClientJson.decode(
                ClientInstallApplicationPayload.serializer(),
                installProgramCommand.payload.toByteArray(),
            )
            assertEquals("/Programs", installProgramPayload.path)
            assertEquals("http.bin", installProgramPayload.name)
            assertEquals(6, installProgramPayload.portNumber)
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = installProgramCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientInstallApplicationResponse.serializer(),
                        ClientInstallApplicationResponse(
                            stateId = "LOCAL-IP",
                            portNumber = 6,
                            installedApplication = ClientInstalledApplication(
                                name = "http.bin",
                                kind = "HTTP",
                                cpuCost = 2.0,
                            ),
                            version = 3,
                        ),
                    ),
                ),
            )

            waitUntil { text(window, "rewrite-port-management-status") == "Installed http.bin on port 6." }
            assertTrue(window.isDisplayable)

            SwingUtilities.invokeAndWait {
                button(window, "rewrite-port-management-install-firewall-button").doClick()
            }

            val firewallChooser = waitForWindow(frame, "rewrite-port-management-firewall-chooser-window")
            respondToLatestDirectoryCommand(
                frame = frame,
                sessionGateway = sessionGateway,
                response = directoryListing(
                    path = "/Programs",
                    files = listOf(
                        ClientStoredFile(
                            path = "/Programs/basic.fw",
                            name = "basic.fw",
                            kind = ClientStoredFileKind.FIREWALL_BINARY,
                            compiledBinary = ClientCompiledBinaryMetadata(),
                        ),
                        ClientStoredFile(
                            path = "/Programs/http.bin",
                            name = "http.bin",
                            kind = ClientStoredFileKind.APPLICATION_BINARY,
                            compiledBinary = ClientCompiledBinaryMetadata(
                                applicationKind = ClientApplicationKind.HTTP,
                            ),
                        ),
                    ),
                ),
            )
            waitUntil { list(firewallChooser, "rewrite-files-entry-list").model.size == 1 }
            SwingUtilities.invokeAndWait {
                list(firewallChooser, "rewrite-files-entry-list").selectedIndex = 0
                button(firewallChooser, "rewrite-files-choose-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "installfirewall" }
            val installFirewallCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val installFirewallPayload = RewriteClientJson.decode(
                ClientInstallFirewallPayload.serializer(),
                installFirewallCommand.payload.toByteArray(),
            )
            assertEquals("/Programs", installFirewallPayload.path)
            assertEquals("basic.fw", installFirewallPayload.name)
            assertEquals(6, installFirewallPayload.portNumber)
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = installFirewallCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientInstallFirewallResponse.serializer(),
                        ClientInstallFirewallResponse(
                            stateId = "LOCAL-IP",
                            portNumber = 6,
                            installedFirewall = ClientInstalledFirewall(
                                name = "basic.fw",
                                kind = "BASIC",
                                cpuCost = 1.5,
                            ),
                            version = 4,
                        ),
                    ),
                ),
            )

            waitUntil { text(window, "rewrite-port-management-status") == "Installed basic.fw on port 6." }
            assertTrue(window.isDisplayable)
        } finally {
            disposeFrame(frame)
        }
    }

    private fun portManagementReadyFrame(
        sessionGateway: FakePortManagementUiSessionGateway,
        snapshot: ClientGameSnapshot,
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
        frame.controller.accept(RewriteService.GAME, snapshotFrame(snapshot))
        waitUntil { frame.desktopPane.isShowing }
        return frame
    }

    private fun portManagementSnapshot(
        ports: List<ClientPortState> = listOf(
            ClientPortState(
                number = 6,
                enabled = true,
                defaultPort = true,
                health = 92.0,
                healCount = 3,
                note = "Main bank",
                maxCpuCost = 10.0,
                installedApplication = ClientInstalledApplication(
                    name = "bank.bin",
                    kind = "BANKING",
                    cpuCost = 2.5,
                ),
                installedFirewall = ClientInstalledFirewall(
                    name = "guard.fw",
                    kind = "BASIC",
                    cpuCost = 1.5,
                ),
            ),
            ClientPortState(
                number = 9,
                enabled = false,
                dummy = true,
                health = 100.0,
                healCount = 0,
                note = "Decoy",
                maxCpuCost = 8.0,
            ),
        ),
    ): ClientGameSnapshot {
        return ClientGameSnapshot(
            id = "LOCAL-IP",
            identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
            ports = ports,
        )
    }

    private fun directoryListing(
        path: String,
        files: List<ClientStoredFile>,
    ): ClientDirectoryListingResponse {
        return ClientDirectoryListingResponse(
            stateId = "LOCAL-IP",
            path = path,
            files = files,
            version = 5,
        )
    }

    private fun respondToLatestDirectoryCommand(
        frame: RewriteRootFrame,
        sessionGateway: FakePortManagementUiSessionGateway,
        response: ClientDirectoryListingResponse,
    ) {
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
    ): JInternalFrame {
        waitUntil { frame.desktopPane.allFrames.any { it.name == windowName } }
        return frame.desktopPane.allFrames.first { it.name == windowName }
    }

    private fun snapshotFrame(snapshot: ClientGameSnapshot): FrameEnvelope {
        return RewriteFrames.snapshot(
            gameStateId = snapshot.id,
            sequence = snapshot.version,
            payload = RewriteClientJson.encode(ClientGameSnapshot.serializer(), snapshot),
        )
    }

    private fun waitUntil(timeoutMillis: Long = 3_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            flushEdt()
            if (predicate()) {
                return
            }
            Thread.sleep(25)
        }
        flushEdt()
        if (!predicate()) {
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

    private fun table(root: Component, name: String): JTable {
        return findComponent(root, name) as? JTable
            ?: error("Unable to find table named $name")
    }

    private fun button(root: Component, name: String): JButton {
        return findComponent(root, name) as? JButton
            ?: error("Unable to find button named $name")
    }

    private fun checkBox(root: Component, name: String): JCheckBox {
        return findComponent(root, name) as? JCheckBox
            ?: error("Unable to find checkbox named $name")
    }

    private fun textField(root: Component, name: String): JTextField {
        return findComponent(root, name) as? JTextField
            ?: error("Unable to find text field named $name")
    }

    private fun list(root: Component, name: String): JList<*> {
        return findComponent(root, name) as? JList<*>
            ?: error("Unable to find list named $name")
    }

    private fun text(root: Component, name: String): String {
        val component = findComponent(root, name) ?: error("Unable to find component named $name")
        return when (component) {
            is JLabel -> component.text
            is JTextField -> component.text
            else -> error("Unsupported text component ${component::class.java.name}")
        }
    }

    private class FakePortManagementUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakePortManagementUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakePortManagementUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakePortManagementUiSession? {
            return sessions.lastOrNull { it.service == RewriteService.GAME }
        }
    }

    private class FakePortManagementUiSession(
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
