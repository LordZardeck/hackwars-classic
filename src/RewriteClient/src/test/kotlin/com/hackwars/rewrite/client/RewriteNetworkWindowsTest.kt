package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.network.buildNetworkDirectoryView
import com.hackwars.rewrite.client.network.buildNetworkMapNodes
import com.hackwars.rewrite.client.network.buildPortScanRows
import com.hackwars.rewrite.protocol.ClientChangeNetworkPayload
import com.hackwars.rewrite.protocol.ClientDefaultPortVisibility
import com.hackwars.rewrite.protocol.ClientFirewallKind
import com.hackwars.rewrite.protocol.ClientFirewallView
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientNetworkState
import com.hackwars.rewrite.protocol.ClientNetworkSwitchResponse
import com.hackwars.rewrite.protocol.ClientNpcCategory
import com.hackwars.rewrite.protocol.ClientNpcDirectoryEntry
import com.hackwars.rewrite.protocol.ClientRequestScanPayload
import com.hackwars.rewrite.protocol.ClientScanResponse
import com.hackwars.rewrite.protocol.ClientScannedPortView
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteNetworkWindowsTest {
    @Test
    fun networkViewsStayBehaviorFreeUntilControllersBindActions() {
        val networkView = com.hackwars.rewrite.client.network.RewriteNetworkWindowView()
        val mapButtons = networkView.renderMapNodes(state = null, requestInFlight = false)
        val portScanView = com.hackwars.rewrite.client.network.RewritePortScanWindowView()

        assertEquals("Waiting for network data...", networkView.currentStatusText())
        assertTrue(mapButtons.values.all { it.actionListeners.isEmpty() })
        assertTrue(portScanView.scanButton.actionListeners.isEmpty())
    }

    @Test
    fun buildNetworkDirectoryViewAndMapNodesReflectDecodedState() {
        val state = ClientNetworkState(
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
        )

        val view = buildNetworkDirectoryView(state)
        val nodeNames = buildNetworkMapNodes(state).map { it.networkName }.toSet()

        assertEquals("UGOPNet", view.currentNetworkName)
        assertEquals(listOf("ProgNet"), view.allowedNetworks)
        assertEquals("Root Attacker - Attack NPC", view.regularNpcs.single())
        assertEquals("Quest Guide - Quest NPC", view.questNpcs.single())
        assertEquals("Miner One - Mining NPC [Silicon]", view.miningNpcs.single())
        assertEquals("Shard Store - Store NPC", view.storeNpcs.single())
        assertTrue(nodeNames.containsAll(listOf("UGOPNet", "ProgNet", "JuniperPenetentiary")))
    }

    @Test
    fun buildPortScanRowsReflectsCorrelatedScanResponse() {
        val rows = buildPortScanRows(
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
                requesterVersion = 5,
            ),
        )

        assertEquals(1, rows.size)
        assertEquals(6, rows.single().number)
        assertEquals("Guard", rows.single().firewallLabel)
        assertEquals("Yes", rows.single().defaultVisibility)
        assertEquals("3/10", rows.single().cpuDisplay)
        assertEquals("88", rows.single().healthDisplay)
    }

    @Test
    fun controllerHelpersSendExpectedNetworkPayloadsAndSelectorTracksDecodedState() = runTest {
        val sessionGateway = FakeNetworkSessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        acceptGameAuth(controller)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.snapshot(
                gameStateId = "192.0.2.10",
                sequence = 1,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(
                        id = "192.0.2.10",
                        network = ClientNetworkState(
                            currentNetworkName = "UGOPNet",
                            allowedNetworks = setOf("ProgNet"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("UGOPNet", controller.gameNetworkState()?.currentNetworkName)

        val changeNetworkPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestChangeNetwork("ProgNet")
        }
        runCurrent()
        val session = sessionGateway.requireLatestGameSession()
        val changeNetworkCommand = session.sentFrames.last().command!!
        val changeNetworkPayload = RewriteClientJson.decode(
            ClientChangeNetworkPayload.serializer(),
            changeNetworkCommand.payload.toByteArray(),
        )
        assertEquals("changenetwork", changeNetworkCommand.command_name)
        assertEquals("192.0.2.10", changeNetworkPayload.ip)
        assertEquals("ProgNet", changeNetworkPayload.network)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = changeNetworkCommand.command_id,
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
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientNetworkSwitchResponse>>(changeNetworkPending.await())

        val scanPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestScan("10.0.0.8")
        }
        runCurrent()
        val scanCommand = session.sentFrames.last().command!!
        val scanPayload = RewriteClientJson.decode(
            ClientRequestScanPayload.serializer(),
            scanCommand.payload.toByteArray(),
        )
        assertEquals("requestscan", scanCommand.command_name)
        assertEquals("192.0.2.10", scanPayload.ip)
        assertEquals("10.0.0.8", scanPayload.targetIp)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = scanCommand.command_id,
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
                            ),
                        ),
                        requesterVersion = 3,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientScanResponse>>(scanPending.await())
    }

    private fun testController(
        sessionGateway: RewriteServiceSessionGateway,
        scheduler: TestCoroutineScheduler,
    ): RewriteRootController {
        return RewriteRootController(
            authGateway = DeterministicRewriteLoginAuthGateway(),
            sessionGateway = sessionGateway,
            workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler)),
        )
    }

    private fun acceptGameAuth(controller: RewriteRootController) {
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
    }

    private class FakeNetworkSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeNetworkSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeNetworkSession(service, onInboundFrame).also(sessions::add)
        }

        fun requireLatestGameSession(): FakeNetworkSession {
            return sessions.last { it.service == RewriteService.GAME }
        }
    }

    private class FakeNetworkSession(
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
