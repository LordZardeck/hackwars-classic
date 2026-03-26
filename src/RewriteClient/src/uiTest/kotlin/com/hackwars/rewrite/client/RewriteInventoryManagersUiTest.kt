package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientEquipmentSlot
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientHardwareState
import com.hackwars.rewrite.protocol.ClientInstallEquipmentPayload
import com.hackwars.rewrite.protocol.ClientInstallEquipmentResponse
import com.hackwars.rewrite.protocol.ClientInstallFirewallPayload
import com.hackwars.rewrite.protocol.ClientInstallFirewallResponse
import com.hackwars.rewrite.protocol.ClientInstalledEquipment
import com.hackwars.rewrite.protocol.ClientInstalledFirewall
import com.hackwars.rewrite.protocol.ClientPortState
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
import java.time.Instant
import javax.swing.JButton
import javax.swing.JInternalFrame
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteInventoryManagersUiTest {
    @Test
    fun equipmentAndFirewallManagersLaunchAsRealWindowsAndReuseSingleInstance() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeInventoryUiSessionGateway()
        val frame = inventoryReadyFrame(sessionGateway = sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.EQUIPMENT_MANAGER)
                frame.controller.launchShellCommand(RewriteShellCommand.EQUIPMENT_MANAGER)
                frame.controller.launchShellCommand(RewriteShellCommand.FIREWALL_MANAGER)
                frame.controller.launchShellCommand(RewriteShellCommand.FIREWALL_MANAGER)
            }

            val equipmentWindow = waitForWindow(frame, "rewrite-equipment-manager-window")
            val firewallWindow = waitForWindow(frame, "rewrite-firewall-manager-window")
            waitUntil {
                frame.desktopPane.allFrames.count { it.name == "rewrite-equipment-manager-window" } == 1 &&
                    frame.desktopPane.allFrames.count { it.name == "rewrite-firewall-manager-window" } == 1 &&
                    table(equipmentWindow, "rewrite-equipment-manager-table").rowCount == 5 &&
                    table(firewallWindow, "rewrite-firewall-manager-table").rowCount == 2
            }

            val equipmentTable = table(equipmentWindow, "rewrite-equipment-manager-table")
            val firewallTable = table(firewallWindow, "rewrite-firewall-manager-table")
            assertEquals("CPU", equipmentTable.getValueAt(0, 0))
            assertEquals("turbo-cpu.bin", equipmentTable.getValueAt(0, 1))
            assertEquals("12", equipmentTable.getValueAt(0, 4))
            assertEquals(6, firewallTable.getValueAt(0, 0))
            assertEquals("guard.fw", firewallTable.getValueAt(0, 1))
            assertEquals("Main bank", firewallTable.getValueAt(0, 9))
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun equipmentInstallOpensChooserSendsInstallEquipmentAndKeepsWindowOpen() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeInventoryUiSessionGateway()
        val frame = inventoryReadyFrame(
            sessionGateway = sessionGateway,
            snapshot = inventorySnapshot(hardware = ClientHardwareState()),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.EQUIPMENT_MANAGER)
            }

            val window = waitForWindow(frame, "rewrite-equipment-manager-window")
            SwingUtilities.invokeAndWait {
                table(window, "rewrite-equipment-manager-table").selectionModel.setSelectionInterval(0, 0)
                button(window, "rewrite-equipment-manager-install-button").doClick()
            }

            val chooser = waitForWindow(frame, "rewrite-equipment-manager-chooser-window")
            respondToLatestDirectoryCommand(
                frame = frame,
                sessionGateway = sessionGateway,
                response = directoryListing(
                    path = "/Programs",
                    files = listOf(
                        ClientStoredFile(
                            path = "/Programs/cpu-card.bin",
                            name = "cpu-card.bin",
                            kind = ClientStoredFileKind.EQUIPMENT_BINARY,
                            compiledBinary = ClientCompiledBinaryMetadata(
                                equipmentSlot = ClientEquipmentSlot.CPU,
                            ),
                        ),
                        ClientStoredFile(
                            path = "/Programs/memory-card.bin",
                            name = "memory-card.bin",
                            kind = ClientStoredFileKind.EQUIPMENT_BINARY,
                            compiledBinary = ClientCompiledBinaryMetadata(
                                equipmentSlot = ClientEquipmentSlot.MEMORY,
                            ),
                        ),
                    ),
                ),
            )
            waitUntil { list(chooser, "rewrite-files-entry-list").model.size == 1 }
            SwingUtilities.invokeAndWait {
                list(chooser, "rewrite-files-entry-list").selectedIndex = 0
                button(chooser, "rewrite-files-choose-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "installequipment" }
            val command = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val payload = RewriteClientJson.decode(
                ClientInstallEquipmentPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals("/Programs", payload.path)
            assertEquals("cpu-card.bin", payload.name)
            assertEquals(ClientEquipmentSlot.CPU, payload.slot)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    payload = RewriteClientJson.encode(
                        ClientInstallEquipmentResponse.serializer(),
                        ClientInstallEquipmentResponse(
                            stateId = "192.0.2.10",
                            slot = ClientEquipmentSlot.CPU,
                            equipment = ClientInstalledEquipment(
                                slot = "CPU",
                                name = "cpu-card.bin",
                                maker = "Maker A",
                                durability = 100,
                                cpuBoost = 8.0,
                            ),
                            version = 3,
                        ),
                    ),
                ),
            )

            waitUntil { text(window, "rewrite-equipment-manager-status") == "Installed cpu-card.bin in CPU." }
            assertTrue(window.isDisplayable)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun firewallInstallShowsInlineErrorsAndKeepsWindowOpen() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeInventoryUiSessionGateway()
        val frame = inventoryReadyFrame(
            sessionGateway = sessionGateway,
            snapshot = inventorySnapshot(
                hardware = ClientHardwareState(),
                ports = listOf(
                    ClientPortState(
                        number = 6,
                        enabled = true,
                        defaultPort = true,
                        note = "Main bank",
                    ),
                ),
            ),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.FIREWALL_MANAGER)
            }

            val window = waitForWindow(frame, "rewrite-firewall-manager-window")
            SwingUtilities.invokeAndWait {
                table(window, "rewrite-firewall-manager-table").selectionModel.setSelectionInterval(0, 0)
                button(window, "rewrite-firewall-manager-install-button").doClick()
            }

            val chooser = waitForWindow(frame, "rewrite-firewall-manager-chooser-window")
            respondToLatestDirectoryCommand(
                frame = frame,
                sessionGateway = sessionGateway,
                response = directoryListing(
                    path = "/Programs",
                    files = listOf(
                        ClientStoredFile(
                            path = "/Programs/guard.fw",
                            name = "guard.fw",
                            kind = ClientStoredFileKind.FIREWALL_BINARY,
                            compiledBinary = ClientCompiledBinaryMetadata(),
                        ),
                        ClientStoredFile(
                            path = "/Programs/readme.txt",
                            name = "readme.txt",
                            kind = ClientStoredFileKind.TEXT,
                        ),
                    ),
                ),
            )
            waitUntil { list(chooser, "rewrite-files-entry-list").model.size == 1 }
            SwingUtilities.invokeAndWait {
                list(chooser, "rewrite-files-entry-list").selectedIndex = 0
                button(chooser, "rewrite-files-choose-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.last().command!!.command_name == "installfirewall" }
            val command = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val payload = RewriteClientJson.decode(
                ClientInstallFirewallPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals("/Programs", payload.path)
            assertEquals("guard.fw", payload.name)
            assertEquals(6, payload.portNumber)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                    error = ErrorEnvelope(
                        code = "CANNOT_INSTALL_FIREWALL",
                        message = "Cannot install firewall.",
                        retryable = false,
                    ),
                ),
            )

            waitUntil { text(window, "rewrite-firewall-manager-error") == "Cannot install firewall." }
            assertTrue(window.isDisplayable)
        } finally {
            disposeFrame(frame)
        }
    }

    private fun inventoryReadyFrame(
        sessionGateway: FakeInventoryUiSessionGateway,
        snapshot: ClientGameSnapshot = inventorySnapshot(),
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
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        frame.controller.accept(RewriteService.GAME, snapshotFrame(snapshot))
        waitUntil { frame.desktopPane.isShowing }
        return frame
    }

    private fun inventorySnapshot(
        hardware: ClientHardwareState = ClientHardwareState(
            equipmentSlots = mapOf(
                "CPU" to ClientInstalledEquipment(
                    slot = "CPU",
                    name = "turbo-cpu.bin",
                    maker = "Maker A",
                    durability = 87,
                    cpuBoost = 12.0,
                    watchCapacityBoost = 2,
                    healCostMultiplier = 0.5,
                    healModifierDelta = -2,
                    freezeImmune = true,
                ),
            ),
        ),
        ports: List<ClientPortState> = listOf(
            ClientPortState(
                number = 6,
                enabled = true,
                defaultPort = true,
                note = "Main bank",
                installedFirewall = ClientInstalledFirewall(
                    name = "guard.fw",
                    kind = "BASIC",
                    maker = "Maker B",
                    strength = 6,
                    cpuCost = 1.5,
                ),
            ),
            ClientPortState(
                number = 9,
                enabled = false,
                dummy = true,
                note = "Decoy",
            ),
        ),
    ): ClientGameSnapshot {
        return ClientGameSnapshot(
            id = "192.0.2.10",
            version = 1,
            identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
            hardware = hardware,
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
        sessionGateway: FakeInventoryUiSessionGateway,
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

    private fun snapshotFrame(snapshot: ClientGameSnapshot): FrameEnvelope {
        return RewriteFrames.snapshot(
            gameStateId = snapshot.id,
            sequence = snapshot.version,
            payload = RewriteClientJson.encode(ClientGameSnapshot.serializer(), snapshot),
        )
    }

    private fun waitForWindow(
        frame: RewriteRootFrame,
        windowName: String,
    ): JInternalFrame {
        waitUntil { frame.desktopPane.allFrames.any { it.name == windowName } }
        return frame.desktopPane.allFrames.first { it.name == windowName }
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

    private fun list(root: Component, name: String): JList<*> {
        return findComponent(root, name) as? JList<*>
            ?: error("Unable to find list named $name")
    }

    private fun text(root: Component, name: String): String {
        val component = findComponent(root, name) ?: error("Unable to find component named $name")
        return when (component) {
            is JLabel -> component.text
            else -> error("Unsupported text component ${component::class.java.name}")
        }
    }

    private class FakeInventoryUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeInventoryUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeInventoryUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakeInventoryUiSession? {
            return sessions.lastOrNull { it.service == RewriteService.GAME }
        }
    }

    private class FakeInventoryUiSession(
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
