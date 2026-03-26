package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryFtpPasswordRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.RequestWebpagePayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.WebsiteRenderResponse
import com.hackwars.rewrite.protocol.ClientHelpTopicListResponse
import com.hackwars.rewrite.protocol.ClientRequestHelpTopicListPayload
import com.hackwars.rewrite.protocol.ClientRequestTutorialPayload
import com.hackwars.rewrite.protocol.ClientTutorialResponse
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
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteGameHelpTutorialProtocolAdapterTest {
    @Test
    fun requestHelpTopicListReturnsRequestedRetainedGroup() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("192.0.2.10")

        local.send(
            RewriteFrames.command(
                commandId = "help-1",
                commandName = "requesthelptopiclist",
                payload = RewriteGameJson.encode(
                    serializer = ClientRequestHelpTopicListPayload.serializer(),
                    value = ClientRequestHelpTopicListPayload(topicGroup = "Banking"),
                ),
                expectsResponse = true,
            ),
        )

        val response = RewriteGameJson.decode(
            serializer = ClientHelpTopicListResponse.serializer(),
            payload = local.awaitCommandResponse().payload.toByteArray(),
        )

        assertEquals("Banking", response.topicGroup)
        assertEquals(
            listOf("Deposit Money", "Withdraw Money", "Transfer Money"),
            response.topics.map { it.name },
        )
        assertEquals("http://203.0.113.213/", response.topics.last().targetUrl)
    }

    @Test
    fun requestTutorialReturnsRetainedOpeningHtml() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("192.0.2.10")

        local.send(
            RewriteFrames.command(
                commandId = "tutorial-1",
                commandName = "requesttutorial",
                payload = RewriteGameJson.encode(
                    serializer = ClientRequestTutorialPayload.serializer(),
                    value = ClientRequestTutorialPayload(tutorialId = "first-attack"),
                ),
                expectsResponse = true,
            ),
        )

        val response = RewriteGameJson.decode(
            serializer = ClientTutorialResponse.serializer(),
            payload = local.awaitCommandResponse().payload.toByteArray(),
        )

        assertEquals("first-attack", response.tutorialId)
        assertEquals("First Attack", response.title)
        assertTrue(response.body.contains("Applications&gt;Internet&gt;Store"))
    }

    @Test
    fun requestWebpageRendersRetainedHelpPagesWithoutFallback() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("192.0.2.10")

        local.send(
            RewriteFrames.command(
                commandId = "web-1",
                commandName = "requestwebpage",
                payload = RewriteGameJson.encode(
                    serializer = RequestWebpagePayload.serializer(),
                    value = RequestWebpagePayload(
                        targetIp = "203.0.113.210",
                        sourceIp = "192.0.2.10",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val response = RewriteGameJson.decode(
            serializer = WebsiteRenderResponse.serializer(),
            payload = local.awaitCommandResponse().payload.toByteArray(),
        )

        assertEquals("203.0.113.210", response.resolvedTargetStateId.value)
        assertEquals("First Attack", response.title)
        assertTrue(response.body.contains("Port Management"))
        assertFalse(response.fallback)
    }

    private fun TestScope.createFixture(): Fixture {
        val localStateId = GameStateId("192.0.2.10")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                localStateId to ComputerState.empty(
                    id = localStateId,
                    playFabId = "PF-LOCALUSER",
                    playerIp = localStateId.value,
                ),
            ),
        )
        val interests = InMemoryInterestRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
            ),
            ftpPasswordRepository = InMemoryFtpPasswordRepository(),
            interestRegistry = interests,
            networkDirectoryRepository = InMemoryNetworkDirectoryRepository.defaultWorld(),
        )
        val harnessAdapter = HelpTutorialHarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(
                        FakePlayerAccount("PF-LOCALUSER", "192.0.2.10", "SESSION-LOCALUSER"),
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
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-it",
                playFabIdHint = "PF-LOCALUSER",
                requestedIp = requestedIp,
            ),
        )
        connection.awaitFrame()
        connection.awaitFrame()
        yield()
        connection.drainFrames()
        return connection
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
    )

    private class HelpTutorialHarnessBackedGameAdapter(
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

private suspend fun InMemoryClientConnection.awaitCommandResponse(): hackwars.rewrite.v1.CommandResponseEnvelope {
    while (true) {
        val frame = awaitFrame()
        val response = frame.command_response
        if (response != null) {
            assertEquals(
                CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK,
                response.status,
                response.error?.message ?: "Expected an OK command response.",
            )
            assertNotNull(response.payload, "Expected a typed command payload for ${response.command_id}.")
            return response
        }
    }
}
