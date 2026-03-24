package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.gamecore.DefaultPortVisibility
import com.hackwars.rewrite.gamecore.DeltaProjection
import com.hackwars.rewrite.gamecore.EconomyState
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.HardwareState
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.InstalledFirewall
import com.hackwars.rewrite.gamecore.NetworkState
import com.hackwars.rewrite.gamecore.NetworkSwitchResponse
import com.hackwars.rewrite.gamecore.NpcCategory
import com.hackwars.rewrite.gamecore.NpcDirectoryEntry
import com.hackwars.rewrite.gamecore.PlayerStatsState
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.RequestScanPayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.ROOT_NETWORK_NAME
import com.hackwars.rewrite.gamecore.ScanResponse
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.StateSectionsDeltaProjection
import com.hackwars.rewrite.gamecore.FirewallKind
import com.hackwars.rewrite.gamecore.ChangeNetworkPayload
import com.hackwars.rewrite.gamecore.NetworkDirectoryDefinition
import com.hackwars.rewrite.gamecore.RuntimeState
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.testkit.FakePlayerAccount
import com.hackwars.rewrite.testkit.FakeSessionCatalog
import com.hackwars.rewrite.testkit.FakeSessionTicketVerifier
import com.hackwars.rewrite.testkit.InMemoryAuthenticatedSession
import com.hackwars.rewrite.testkit.InMemoryClientConnection
import com.hackwars.rewrite.testkit.InMemoryRewriteServiceHarness
import com.hackwars.rewrite.testkit.RewriteServiceAdapter
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteGameNetworkProtocolAdapterTest {
    @Test
    fun authBootstrapReturnsSingleRefreshedSnapshotWithoutPreBootstrapDelta() = runTest {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                GameStateId("LOCAL-IP") to localState().copy(
                    network = localState().network.copy(
                        currentNetworkName = "GhostNet",
                        storeStateId = null,
                        regularNpcs = emptyList(),
                        questNpcs = emptyList(),
                        miningNpcs = emptyList(),
                        storeNpcs = emptyList(),
                    ),
                ),
                GameStateId("store1") to ComputerState.empty(GameStateId("store1"), playerIp = "store1"),
            ),
        )
        val interests = InMemoryInterestRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
            ),
            interestRegistry = interests,
            networkDirectoryRepository = testNetworkRepository(),
        )
        val harnessAdapter = HarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(FakePlayerAccount("PF-LOCALUSER", "LOCAL-IP", "SESSION-LOCALUSER")),
                ),
                clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
            ),
            scope = backgroundScope,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = 5.seconds,
                idleTimeout = 45.seconds,
            ),
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )
        harnessAdapter.attachHarness(harness)

        val connection = harness.connect()
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-it",
                playFabIdHint = "PF-LOCALUSER",
                requestedIp = "LOCAL-IP",
            ),
        )

        val authAccepted = connection.awaitFrame()
        val snapshot = connection.awaitFrame()
        val snapshotState = RewriteGameJson.decode(
            serializer = ComputerState.serializer(),
            payload = snapshot.snapshot!!.payload.toByteArray(),
        )

        assertTrue(authAccepted.auth_response?.accepted != null)
        assertTrue(snapshot.snapshot != null)
        assertEquals(ROOT_NETWORK_NAME, snapshotState.network.currentNetworkName)
        assertEquals("Root Hunter", snapshotState.network.regularNpcs.single().displayName)
        assertNull(connection.drainFrames().firstOrNull())
        assertEquals(ROOT_NETWORK_NAME, repository.load(GameStateId("LOCAL-IP"))?.network?.currentNetworkName)
    }

    @Test
    fun changenetworkPublishesNetworkDeltaBeforeCorrelatedResponse() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.send(
            RewriteFrames.command(
                commandId = "network-1",
                commandName = "changenetwork",
                payload = RewriteGameJson.encode(
                    serializer = ChangeNetworkPayload.serializer(),
                    value = ChangeNetworkPayload(
                        ip = "LOCAL-IP",
                        network = "ProgNet",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = local.awaitFrame()
        val responseFrame = local.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = NetworkSwitchResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )
        val projection = RewriteGameJson.decode(
            serializer = DeltaProjection.serializer(),
            payload = delta.delta!!.payload.toByteArray(),
        )

        assertEquals(listOf("network"), delta.delta?.delta_keys)
        assertTrue(response.accepted)
        assertEquals("ProgNet", response.currentNetworkName)
        assertIs<StateSectionsDeltaProjection>(projection)
        assertEquals("ProgNet", projection.network?.currentNetworkName)
        assertEquals("Prog Attack", projection.network?.regularNpcs?.single()?.displayName)
    }

    @Test
    fun requestscanPublishesEconomyAndStatsDeltaBeforeResponseAndNoTargetFrames() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        local.send(
            RewriteFrames.command(
                commandId = "scan-1",
                commandName = "requestscan",
                payload = RewriteGameJson.encode(
                    serializer = RequestScanPayload.serializer(),
                    value = RequestScanPayload(
                        ip = "LOCAL-IP",
                        targetIp = "TARGET-IP",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = local.awaitFrame()
        val responseFrame = local.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = ScanResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )
        val projection = RewriteGameJson.decode(
            serializer = DeltaProjection.serializer(),
            payload = delta.delta!!.payload.toByteArray(),
        )

        assertEquals(listOf("economy", "stats"), delta.delta?.delta_keys)
        assertTrue(response.accepted)
        assertEquals(10.0, response.chargedAmount)
        assertEquals(60, response.experienceAwarded)
        assertEquals(DefaultPortVisibility.YES, response.ports.first().defaultVisibility)
        assertEquals("LOCAL-IP", response.ports.first().note)
        assertIs<StateSectionsDeltaProjection>(projection)
        assertEquals(90.0, projection.economy?.pettyCash)
        assertTrue((projection.stats?.experienceByFamily?.get(ScriptFamily.SCANNING) ?: 0) >= 60)
        assertNull(target.drainFrames().firstOrNull())
    }

    @Test
    fun requestscanFailureReturnsOnlyCorrelatedResponse() = runTest {
        val fixture = createFixture(
            localState = localState().copy(
                economy = EconomyState(pettyCash = 5.0, defaultBankPort = 6),
            ),
        )
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.send(
            RewriteFrames.command(
                commandId = "scan-2",
                commandName = "requestscan",
                payload = RewriteGameJson.encode(
                    serializer = RequestScanPayload.serializer(),
                    value = RequestScanPayload(
                        ip = "LOCAL-IP",
                        targetIp = "TARGET-IP",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val responseFrame = local.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = ScanResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertFalse(response.accepted)
        assertEquals("scan-2", responseFrame.command_response?.command_id)
        assertTrue(local.drainFrames().none { it.delta != null })
    }

    private fun TestScope.createFixture(
        localState: ComputerState = localState(),
        targetState: ComputerState = targetState(),
    ): Fixture {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                GameStateId("LOCAL-IP") to localState,
                GameStateId("TARGET-IP") to targetState,
                GameStateId("store1") to ComputerState.empty(GameStateId("store1"), playerIp = "store1"),
            ),
        )
        val interests = InMemoryInterestRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
            ),
            interestRegistry = interests,
            networkDirectoryRepository = testNetworkRepository(),
        )
        val harnessAdapter = HarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(
                        FakePlayerAccount("PF-LOCALUSER", "LOCAL-IP", "SESSION-LOCALUSER"),
                        FakePlayerAccount("PF-TARGET", "TARGET-IP", "SESSION-TARGET"),
                        FakePlayerAccount("PF-STORE", "store1", "SESSION-STORE"),
                    ),
                ),
                clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
            ),
            scope = backgroundScope,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = 5.seconds,
                idleTimeout = 45.seconds,
            ),
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )
        harnessAdapter.attachHarness(harness)
        return Fixture(harness)
    }

    private suspend fun Fixture.authenticatedConnection(requestedIp: String): InMemoryClientConnection {
        val connection = harness.connect()
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = sessionTicketFor(requestedIp),
                clientBuild = "rewrite-it",
                playFabIdHint = playFabIdFor(requestedIp),
                requestedIp = requestedIp,
            ),
        )
        connection.awaitFrame()
        connection.awaitFrame()
        return connection
    }

    private fun localState(): ComputerState {
        return ComputerState.empty(
            id = GameStateId("LOCAL-IP"),
            playFabId = "PF-LOCALUSER",
            playerIp = "LOCAL-IP",
        ).copy(
            hardware = HardwareState(cpuMax = 50.0),
            economy = EconomyState(
                pettyCash = 100.0,
                bankMoney = 25.0,
                defaultBankPort = 6,
            ),
            ports = listOf(
                PortState(
                    number = 6,
                    type = "banking",
                    enabled = true,
                    defaultPort = true,
                    installedApplication = InstalledApplication(
                        name = "bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                    ),
                ),
            ),
            network = NetworkState(
                currentNetworkName = ROOT_NETWORK_NAME,
                storeStateId = GameStateId("store1"),
                allowedNetworks = setOf("ProgNet"),
            ),
            stats = PlayerStatsState(
                experienceByFamily = mapOf(ScriptFamily.SCANNING to 10_000_000),
            ),
            runtime = RuntimeState(currentCpuLoad = 5.0),
        )
    }

    private fun targetState(): ComputerState {
        return ComputerState.empty(
            id = GameStateId("TARGET-IP"),
            playFabId = "PF-TARGET",
            playerIp = "TARGET-IP",
        ).copy(
            ports = listOf(
                PortState(
                    number = 22,
                    type = "banking",
                    enabled = true,
                    defaultPort = true,
                    maxCpuCost = 8.0,
                    installedApplication = InstalledApplication(
                        name = "bank.bin",
                        kind = ApplicationKind.BANKING,
                        cpuCost = 5.0,
                        banking = true,
                    ),
                    installedFirewall = InstalledFirewall(
                        name = "Shield",
                        kind = FirewallKind.BASIC,
                        maker = "Rewrite",
                        strength = 12,
                        cpuCost = 2.0,
                    ),
                ),
                PortState(
                    number = 80,
                    type = "http",
                    enabled = true,
                    defaultPort = false,
                    installedApplication = InstalledApplication(
                        name = "http.bin",
                        kind = ApplicationKind.HTTP,
                        cpuCost = 3.0,
                    ),
                ),
            ),
        )
    }

    private fun testNetworkRepository(): InMemoryNetworkDirectoryRepository {
        return InMemoryNetworkDirectoryRepository(
            definitions = mapOf(
                ROOT_NETWORK_NAME to NetworkDirectoryDefinition(
                    name = ROOT_NETWORK_NAME,
                    storeStateId = GameStateId("store1"),
                    regularNpcs = listOf(
                        NpcDirectoryEntry(
                            stateId = GameStateId("UGOP-ATTACK-1"),
                            displayName = "Root Hunter",
                            title = "Attack NPC",
                            category = NpcCategory.REGULAR,
                        ),
                    ),
                    questNpcs = listOf(
                        NpcDirectoryEntry(
                            stateId = GameStateId("UGOP-QUEST-1"),
                            displayName = "Quest Guide",
                            title = "Quest NPC",
                            category = NpcCategory.QUEST,
                        ),
                    ),
                    miningNpcs = listOf(
                        NpcDirectoryEntry(
                            stateId = GameStateId("UGOP-MINE-1"),
                            displayName = "Root Miner",
                            title = "Mining NPC",
                            category = NpcCategory.MINING,
                            commodity = "Silicon",
                        ),
                    ),
                    storeNpcs = listOf(
                        NpcDirectoryEntry(
                            stateId = GameStateId("store1"),
                            displayName = "Shard Store",
                            title = "Store NPC",
                            category = NpcCategory.STORE,
                        ),
                    ),
                ),
                "ProgNet" to NetworkDirectoryDefinition(
                    name = "ProgNet",
                    storeStateId = GameStateId("store1"),
                    regularNpcs = listOf(
                        NpcDirectoryEntry(
                            stateId = GameStateId("PROG-ATTACK-1"),
                            displayName = "Prog Attack",
                            title = "Attack NPC",
                            category = NpcCategory.REGULAR,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun sessionTicketFor(requestedIp: String): String = when (requestedIp) {
        "LOCAL-IP" -> "SESSION-LOCALUSER"
        "TARGET-IP" -> "SESSION-TARGET"
        "store1" -> "SESSION-STORE"
        else -> error("No session ticket for $requestedIp")
    }

    private fun playFabIdFor(requestedIp: String): String = when (requestedIp) {
        "LOCAL-IP" -> "PF-LOCALUSER"
        "TARGET-IP" -> "PF-TARGET"
        "store1" -> "PF-STORE"
        else -> error("No PlayFab id for $requestedIp")
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
    )

    private class HarnessBackedGameAdapter(
        private val adapter: RewriteGameProtocolAdapter,
    ) : RewriteServiceAdapter {
        override val service = RewriteService.GAME

        private lateinit var harness: InMemoryRewriteServiceHarness

        fun attachHarness(harness: InMemoryRewriteServiceHarness) {
            this.harness = harness
        }

        override suspend fun onSessionStarted(session: InMemoryAuthenticatedSession): List<FrameEnvelope> {
            return adapter.onSessionStarted(
                session = session.toGameSession(),
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }

        override suspend fun onCommand(
            session: InMemoryAuthenticatedSession,
            command: hackwars.rewrite.v1.CommandEnvelope,
        ): List<FrameEnvelope> {
            return adapter.onCommand(
                session = session.toGameSession(),
                command = command,
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }
    }
}

private fun InMemoryAuthenticatedSession.toGameSession(): AuthenticatedGameSession {
    return AuthenticatedGameSession(
        connectionId = connectionId,
        playFabId = verifiedSession.playFabId,
        playerIp = verifiedSession.playerIp,
    )
}
