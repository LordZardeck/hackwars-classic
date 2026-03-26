package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.protocol.ClientChangeNetworkPayload
import com.hackwars.rewrite.protocol.ClientDefaultPortVisibility
import com.hackwars.rewrite.protocol.ClientFirewallKind
import com.hackwars.rewrite.protocol.ClientFirewallView
import com.hackwars.rewrite.protocol.ClientGameDeltaProjection
import com.hackwars.rewrite.protocol.ClientGameSectionsProjection
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientNetworkState
import com.hackwars.rewrite.protocol.ClientNetworkSwitchResponse
import com.hackwars.rewrite.protocol.ClientNpcCategory
import com.hackwars.rewrite.protocol.ClientNpcDirectoryEntry
import com.hackwars.rewrite.protocol.ClientRequestScanPayload
import com.hackwars.rewrite.protocol.ClientScanFailureCode
import com.hackwars.rewrite.protocol.ClientScanResponse
import com.hackwars.rewrite.protocol.ClientScannedPortView
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.time.Instant
import javax.swing.JButton
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteNetworkWindowsUiTest {
    @Test
    fun networkLaunchesAsRealWindowRendersTabsNpcDataAndReusesSingleInstance() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeNetworkUiSessionGateway()
        val frame = networkReadyFrame(
            sessionGateway = sessionGateway,
            snapshot = networkSnapshot(),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.NETWORK)
                frame.controller.launchShellCommand(RewriteShellCommand.NETWORK)
            }

            val window = waitForWindow(frame, "rewrite-shell-window-network")
            waitUntil {
                frame.desktopPane.allFrames.count { it.name == "rewrite-shell-window-network" } == 1 &&
                    tabbedPane(window, "rewrite-network-tabs").tabCount == 2
            }

            val tabs = tabbedPane(window, "rewrite-network-tabs")
            assertEquals("Network", tabs.getTitleAt(0))
            assertEquals("Map", tabs.getTitleAt(1))
            assertEquals("UGOPNet", text(window, "rewrite-network-current-name"))
            assertEquals("ProgNet", text(window, "rewrite-network-allowed-networks"))
            assertEquals("Root Attacker - Attack NPC", list(window, "rewrite-network-regular-list").model.getElementAt(0))
            assertEquals("Quest Guide - Quest NPC", list(window, "rewrite-network-quest-list").model.getElementAt(0))
            assertEquals("Miner One - Mining NPC [Silicon]", list(window, "rewrite-network-mining-list").model.getElementAt(0))
            assertEquals("Shard Store - Store NPC", list(window, "rewrite-network-store-list").model.getElementAt(0))
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun networkMapActionSendsChangeNetworkAndRefreshesOnDecodedDelta() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeNetworkUiSessionGateway()
        val frame = networkReadyFrame(
            sessionGateway = sessionGateway,
            snapshot = networkSnapshot(),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.NETWORK)
            }

            val window = waitForWindow(frame, "rewrite-shell-window-network")
            SwingUtilities.invokeAndWait {
                button(window, "rewrite-network-map-node-prognet").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true }
            val command = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val payload = RewriteClientJson.decode(
                ClientChangeNetworkPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals("changenetwork", command.command_name)
            assertEquals("192.0.2.10", payload.ip)
            assertEquals("ProgNet", payload.network)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.delta(
                    gameStateId = "192.0.2.10",
                    sequence = 2,
                    changedPaths = listOf("network.currentNetworkName"),
                    deltaKeys = listOf("network"),
                    payload = RewriteClientJson.encode(
                        ClientGameDeltaProjection.serializer(),
                        ClientGameSectionsProjection(
                            network = ClientNetworkState(
                                currentNetworkName = "ProgNet",
                                allowedNetworks = setOf("UGOPNet"),
                                regularNpcs = listOf(
                                    ClientNpcDirectoryEntry(
                                        stateId = "203.0.113.101",
                                        displayName = "Prog Runner",
                                        title = "Attack NPC",
                                        category = ClientNpcCategory.REGULAR,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    payload = RewriteClientJson.encode(
                        ClientNetworkSwitchResponse.serializer(),
                        ClientNetworkSwitchResponse(
                            stateId = "192.0.2.10",
                            requestedNetworkName = "ProgNet",
                            currentNetworkName = "ProgNet",
                            storeStateId = "198.51.100.40",
                            accepted = true,
                            message = "Changed network to ProgNet.",
                            version = 2,
                        ),
                    ),
                ),
            )

            waitUntil {
                text(window, "rewrite-network-current-name") == "ProgNet" &&
                    text(window, "rewrite-network-status") == "Changed network to ProgNet."
            }
            assertTrue(window.isDisplayable)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun portScanLaunchesAsRealWindowPopulatesResultsAndShowsFailuresInline() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeNetworkUiSessionGateway()
        val frame = networkReadyFrame(
            sessionGateway = sessionGateway,
            snapshot = networkSnapshot(),
        )
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.PORT_SCAN)
            }

            val window = waitForWindow(frame, "rewrite-shell-window-port_scan")
            SwingUtilities.invokeAndWait {
                setSegmentedIp(window, "10.0.0.8")
                button(window, "rewrite-port-scan-scan-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true }
            val successCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val successPayload = RewriteClientJson.decode(
                ClientRequestScanPayload.serializer(),
                successCommand.payload.toByteArray(),
            )
            assertEquals("requestscan", successCommand.command_name)
            assertEquals("192.0.2.10", successPayload.ip)
            assertEquals("10.0.0.8", successPayload.targetIp)
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = successCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientScanResponse.serializer(),
                        ClientScanResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "10.0.0.8",
                            accepted = true,
                            chargedAmount = 10.0,
                            experienceAwarded = 60.0,
                            ports = listOf(
                                ClientScannedPortView(
                                    number = 6,
                                    type = "Bank",
                                    enabled = true,
                                    dummy = false,
                                    attacking = false,
                                    cpuCost = 3.0,
                                    maxCpuCost = 10.0,
                                    health = 88.0,
                                    note = "192.0.2.10",
                                    defaultVisibility = ClientDefaultPortVisibility.YES,
                                    firewall = ClientFirewallView(
                                        name = "Guard",
                                        kind = ClientFirewallKind.BASIC,
                                        maker = "192.0.2.10",
                                        strength = 25,
                                        cpuCost = 1.5,
                                    ),
                                ),
                            ),
                            requesterVersion = 3,
                        ),
                    ),
                ),
            )

            waitUntil {
                table(window, "rewrite-port-scan-table").rowCount == 1 &&
                    text(window, "rewrite-port-scan-status") == "Scanned 10.0.0.8."
            }
            assertEquals(6, table(window, "rewrite-port-scan-table").getValueAt(0, 0))
            assertEquals("Guard", table(window, "rewrite-port-scan-table").getValueAt(0, 2))
            assertEquals("Yes", table(window, "rewrite-port-scan-table").getValueAt(0, 3))

            SwingUtilities.invokeAndWait {
                setSegmentedIp(window, "10.0.0.9")
                button(window, "rewrite-port-scan-scan-button").doClick()
            }
            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.size >= 2 }
            val failureCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = failureCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientScanResponse.serializer(),
                        ClientScanResponse(
                            requesterStateId = "192.0.2.10",
                            targetStateId = "10.0.0.9",
                            accepted = false,
                            failureCode = ClientScanFailureCode.INSUFFICIENT_PETTY_CASH,
                            failureMessage = "Scanning requires at least \$10 petty cash.",
                            requesterVersion = 4,
                        ),
                    ),
                ),
            )

            waitUntil { text(window, "rewrite-port-scan-error") == "Scanning requires at least \$10 petty cash." }
            assertTrue(window.isDisplayable)
        } finally {
            disposeFrame(frame)
        }
    }

    private fun networkReadyFrame(
        sessionGateway: FakeNetworkUiSessionGateway,
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
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        frame.controller.accept(RewriteService.GAME, snapshotFrame(snapshot))
        waitUntil { frame.desktopPane.isShowing }
        return frame
    }

    private fun networkSnapshot(): ClientGameSnapshot {
        return ClientGameSnapshot(
            id = "192.0.2.10",
            network = ClientNetworkState(
                currentNetworkName = "UGOPNet",
                allowedNetworks = setOf("ProgNet"),
                regularNpcs = listOf(
                    ClientNpcDirectoryEntry(
                        stateId = "198.51.100.101",
                        displayName = "Root Attacker",
                        title = "Attack NPC",
                        category = ClientNpcCategory.REGULAR,
                    ),
                ),
                questNpcs = listOf(
                    ClientNpcDirectoryEntry(
                        stateId = "198.51.100.102",
                        displayName = "Quest Guide",
                        title = "Quest NPC",
                        category = ClientNpcCategory.QUEST,
                    ),
                ),
                miningNpcs = listOf(
                    ClientNpcDirectoryEntry(
                        stateId = "198.51.100.103",
                        displayName = "Miner One",
                        title = "Mining NPC",
                        category = ClientNpcCategory.MINING,
                        commodity = "Silicon",
                    ),
                ),
                storeNpcs = listOf(
                    ClientNpcDirectoryEntry(
                        stateId = "198.51.100.40",
                        displayName = "Shard Store",
                        title = "Store NPC",
                        category = ClientNpcCategory.STORE,
                    ),
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

    private fun setSegmentedIp(
        root: Component,
        ip: String,
    ) {
        val segments = ip.split('.')
        require(segments.size == 4)
        segments.forEachIndexed { index, value ->
            textField(root, "rewrite-economy-ip-segment-$index").text = value
        }
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

    private fun tabbedPane(root: Component, name: String): JTabbedPane {
        return findComponent(root, name) as? JTabbedPane
            ?: error("Unable to find tabbed pane named $name")
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

    private fun textField(root: Component, name: String): JTextField {
        return findComponent(root, name) as? JTextField
            ?: error("Unable to find text field named $name")
    }

    private fun text(root: Component, name: String): String {
        return (findComponent(root, name) as? JLabel)?.text
            ?: error("Unable to find label named $name")
    }

    private class FakeNetworkUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeNetworkUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeNetworkUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakeNetworkUiSession? {
            return sessions.lastOrNull { it.service == RewriteService.GAME }
        }
    }

    private class FakeNetworkUiSession(
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
