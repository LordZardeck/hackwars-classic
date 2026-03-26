package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryFtpPasswordRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.NetworkState
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.RequestSearchPayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.ROOT_NETWORK_NAME
import com.hackwars.rewrite.gamecore.SearchCatalogRepository
import com.hackwars.rewrite.gamecore.SearchResultsResponse
import com.hackwars.rewrite.gamecore.SearchableWebsiteDocument
import com.hackwars.rewrite.gamecore.WebsiteState
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
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteGameSearchProtocolAdapterTest {
    @Test
    fun requestsearchReturnsExactlyOneCorrelatedResponseWithoutDeltas() = runTest {
        val fixture = createFixture()
        val searcher = fixture.authenticatedConnection("SEARCHER-IP")

        searcher.send(
            RewriteFrames.command(
                commandId = "search-1",
                commandName = "requestsearch",
                payload = RewriteGameJson.encode(
                    serializer = RequestSearchPayload.serializer(),
                    value = RequestSearchPayload(
                        query = "alpha beta",
                        startIndex = 0,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val responseFrame = searcher.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = SearchResultsResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals("search-1", responseFrame.command_response?.command_id)
        assertEquals(listOf("alpha", "beta"), response.queryTerms)
        assertEquals(listOf("inactive-ip", "npc-ip"), response.results.map { it.address })
        assertTrue(searcher.drainFrames().none { it.delta != null })
    }

    @Test
    fun playerLoginHidesThatSiteFromSubsequentSearchResults() = runTest {
        val fixture = createFixture()
        val searcher = fixture.authenticatedConnection("SEARCHER-IP")

        searcher.send(
            RewriteFrames.command(
                commandId = "search-before",
                commandName = "requestsearch",
                payload = RewriteGameJson.encode(
                    serializer = RequestSearchPayload.serializer(),
                    value = RequestSearchPayload(query = "beta", startIndex = 0),
                ),
                expectsResponse = true,
            ),
        )
        val before = RewriteGameJson.decode(
            serializer = SearchResultsResponse.serializer(),
            payload = searcher.awaitFrame().command_response!!.payload.toByteArray(),
        )
        assertEquals(listOf("inactive-ip"), before.results.map { it.address })

        fixture.authenticatedConnection("INACTIVE-IP")

        searcher.send(
            RewriteFrames.command(
                commandId = "search-after",
                commandName = "requestsearch",
                payload = RewriteGameJson.encode(
                    serializer = RequestSearchPayload.serializer(),
                    value = RequestSearchPayload(query = "beta", startIndex = 0),
                ),
                expectsResponse = true,
            ),
        )
        val after = RewriteGameJson.decode(
            serializer = SearchResultsResponse.serializer(),
            payload = searcher.awaitFrame().command_response!!.payload.toByteArray(),
        )

        assertTrue(after.results.none { it.address == "inactive-ip" })
    }

    private fun TestScope.createFixture(): Fixture {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                GameStateId("SEARCHER-IP") to searcherState(),
                GameStateId("INACTIVE-IP") to websiteState(
                    ip = "INACTIVE-IP",
                    title = "Inactive Title",
                    body = "alpha beta gamma",
                    lastLoginAtEpochMillis = 0L,
                    isNpc = false,
                    enableHttp = true,
                ),
                GameStateId("NPC-IP") to websiteState(
                    ip = "NPC-IP",
                    title = "Npc Title",
                    body = "alpha",
                    lastLoginAtEpochMillis = null,
                    isNpc = true,
                    enableHttp = true,
                ),
                GameStateId("ACTIVE-IP") to websiteState(
                    ip = "ACTIVE-IP",
                    title = "Active Title",
                    body = "alpha beta alpha beta",
                    lastLoginAtEpochMillis = 19.days.inWholeMilliseconds,
                    isNpc = false,
                    enableHttp = true,
                ),
                GameStateId("NO-HTTP-IP") to websiteState(
                    ip = "NO-HTTP-IP",
                    title = "No Http Title",
                    body = "alpha beta alpha beta alpha",
                    lastLoginAtEpochMillis = 0L,
                    isNpc = false,
                    enableHttp = false,
                ),
            ),
        )
        val interests = InMemoryInterestRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
            ),
            combatMaintenanceProgramRegistry = DisabledCombatMaintenanceProgramRegistry,
            ftpPasswordRepository = InMemoryFtpPasswordRepository(),
            interestRegistry = interests,
            clock = { 20.days.inWholeMilliseconds + testScheduler.currentTime },
            networkDirectoryRepository = InMemoryNetworkDirectoryRepository.defaultWorld(),
            searchCatalogRepository = RepositoryBackedSearchCatalogRepository(
                repository = repository,
                stateIds = listOf(
                    GameStateId("INACTIVE-IP"),
                    GameStateId("NPC-IP"),
                    GameStateId("ACTIVE-IP"),
                    GameStateId("NO-HTTP-IP"),
                ),
            ),
        )
        val harnessAdapter = SearchHarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(
                        FakePlayerAccount("PF-SEARCHER", "SEARCHER-IP", "SESSION-SEARCHER"),
                        FakePlayerAccount("PF-INACTIVE", "INACTIVE-IP", "SESSION-INACTIVE"),
                        FakePlayerAccount("PF-NPC", "NPC-IP", "SESSION-NPC"),
                    ),
                ),
                clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
            ),
            scope = backgroundScope,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = 5.seconds,
                idleTimeout = 45.seconds,
            ),
            clock = { Instant.ofEpochMilli(20.days.inWholeMilliseconds + testScheduler.currentTime) },
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
        yield()
        connection.drainFrames()
        return connection
    }

    private fun searcherState(): ComputerState {
        return ComputerState.empty(
            id = GameStateId("SEARCHER-IP"),
            playFabId = "PF-SEARCHER",
            playerIp = "SEARCHER-IP",
        ).copy(
            network = NetworkState(currentNetworkName = ROOT_NETWORK_NAME),
        )
    }

    private fun websiteState(
        ip: String,
        title: String,
        body: String,
        lastLoginAtEpochMillis: Long?,
        isNpc: Boolean,
        enableHttp: Boolean,
    ): ComputerState {
        return ComputerState.empty(
            id = GameStateId(ip),
            playFabId = "PF-$ip",
            playerIp = ip,
            isNpc = isNpc,
        ).copy(
            identity = ComputerState.empty(
                id = GameStateId(ip),
                playFabId = "PF-$ip",
                playerIp = ip,
                isNpc = isNpc,
            ).identity.copy(lastLoginAtEpochMillis = lastLoginAtEpochMillis),
            website = WebsiteState(title = title, body = body),
            ports = if (enableHttp) {
                listOf(
                    PortState(
                        number = 80,
                        type = "http",
                        enabled = true,
                        defaultPort = true,
                        installedApplication = InstalledApplication(
                            name = "http.bin",
                            kind = ApplicationKind.HTTP,
                        ),
                    ),
                )
            } else {
                emptyList()
            },
            network = NetworkState(currentNetworkName = ROOT_NETWORK_NAME),
        )
    }

    private fun sessionTicketFor(requestedIp: String): String = when (requestedIp) {
        "SEARCHER-IP" -> "SESSION-SEARCHER"
        "INACTIVE-IP" -> "SESSION-INACTIVE"
        "NPC-IP" -> "SESSION-NPC"
        else -> error("No session ticket for $requestedIp")
    }

    private fun playFabIdFor(requestedIp: String): String = when (requestedIp) {
        "SEARCHER-IP" -> "PF-SEARCHER"
        "INACTIVE-IP" -> "PF-INACTIVE"
        "NPC-IP" -> "PF-NPC"
        else -> error("No PlayFab id for $requestedIp")
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
    )

    private class SearchHarnessBackedGameAdapter(
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

    private class RepositoryBackedSearchCatalogRepository(
        private val repository: InMemoryComputerStateRepository,
        private val stateIds: List<GameStateId>,
    ) : SearchCatalogRepository {
        override suspend fun loadDocuments(): List<SearchableWebsiteDocument> {
            return stateIds.mapNotNull { stateId ->
                repository.load(stateId)?.let { state ->
                    SearchableWebsiteDocument(
                        address = state.identity.playerIp.trim().lowercase(),
                        title = state.website.title,
                        body = state.website.body,
                        searchable = state.ports.any { port ->
                            port.defaultPort &&
                                port.enabled &&
                                port.installedApplication?.kind == ApplicationKind.HTTP
                        },
                        lastLoginAtEpochMillis = state.identity.lastLoginAtEpochMillis,
                        isNpc = state.identity.isNpc,
                    )
                }
            }
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
