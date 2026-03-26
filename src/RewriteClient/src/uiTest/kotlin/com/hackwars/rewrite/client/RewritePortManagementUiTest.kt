package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.testsupport.rewriteUiAuthenticatedDesktopFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiDisposeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiFindNamedComponent
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForWindow
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitUntil
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
import java.awt.GraphicsEnvironment
import javax.swing.JButton
import javax.swing.JCheckBox
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

            val window = rewriteUiWaitForWindow(frame, "rewrite-port-management-window")
            rewriteUiWaitUntil {
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
            rewriteUiDisposeFrame(frame)
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

            val window = rewriteUiWaitForWindow(frame, "rewrite-port-management-window")
            SwingUtilities.invokeAndWait {
                table(window, "rewrite-port-management-table").selectionModel.setSelectionInterval(0, 0)
                button(window, "rewrite-port-management-heal-button").doClick()
            }

            rewriteUiWaitUntil { sessionGateway.latestGameSession()!!.sentFrames.isNotEmpty() }
            val command = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val payload = RewriteClientJson.decode(
                ClientHealPortPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals("healport", command.command_name)
            assertEquals("192.0.2.10", payload.ip)
            assertEquals(6, payload.port)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    payload = RewriteClientJson.encode(
                        ClientHealPortResponse.serializer(),
                        ClientHealPortResponse(
                            stateId = "192.0.2.10",
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
            rewriteUiDisposeFrame(frame)
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

            val window = rewriteUiWaitForWindow(frame, "rewrite-port-management-window")
            SwingUtilities.invokeAndWait {
                table(window, "rewrite-port-management-table").selectionModel.setSelectionInterval(0, 0)
                button(window, "rewrite-port-management-install-program-button").doClick()
            }

            val programChooser = rewriteUiWaitForWindow(frame, "rewrite-port-management-program-chooser-window")
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

            rewriteUiWaitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "installapplication" }
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
                            stateId = "192.0.2.10",
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

            val firewallChooser = rewriteUiWaitForWindow(frame, "rewrite-port-management-firewall-chooser-window")
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

            rewriteUiWaitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "installfirewall" }
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
                            stateId = "192.0.2.10",
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
            rewriteUiDisposeFrame(frame)
        }
    }

    private fun portManagementReadyFrame(
        sessionGateway: FakePortManagementUiSessionGateway,
        snapshot: ClientGameSnapshot,
    ): RewriteRootFrame {
        return rewriteUiAuthenticatedDesktopFrame(
            sessionGateway = sessionGateway,
            snapshot = snapshot,
        )
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
            id = "192.0.2.10",
            identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
            ports = ports,
        )
    }

    private fun directoryListing(
        path: String,
        files: List<ClientStoredFile>,
    ): ClientDirectoryListingResponse {
        return ClientDirectoryListingResponse(
            stateId = "192.0.2.10",
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

    private fun findComponent(root: Component, name: String): Component? {
        return rewriteUiFindNamedComponent(root, name)
    }

    private fun waitUntil(timeoutMillis: Long = 3_000, predicate: () -> Boolean) {
        rewriteUiWaitUntil(timeoutMillis, predicate)
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
