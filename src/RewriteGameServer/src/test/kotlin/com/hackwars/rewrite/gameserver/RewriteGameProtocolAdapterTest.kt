package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.DeltaProjection
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.PreferenceDeltaProjection
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.ScanResponse
import com.hackwars.rewrite.gamecore.SetPreferencePayload
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.testkit.FakeSessionTicketVerifier
import com.hackwars.rewrite.testkit.InMemoryAuthenticatedSession
import com.hackwars.rewrite.testkit.InMemoryClientConnection
import com.hackwars.rewrite.testkit.InMemoryRewriteServiceHarness
import com.hackwars.rewrite.testkit.RewriteServiceAdapter
import hackwars.rewrite.v1.CommandEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteGameProtocolAdapterTest {
    @Test
    fun authSuccessProducesExactlyOneBootstrapSnapshot() = runTest {
        val fixture = createFixture()
        val connection = fixture.harness.connect()

        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-it",
                playFabIdHint = "PF-LOCALUSER",
                requestedIp = "LOCAL-IP",
            ),
        )

        val auth = connection.awaitFrame()
        val snapshot = connection.awaitFrame()

        assertNotNull(auth.auth_response?.accepted)
        assertEquals("LOCAL-IP", snapshot.snapshot?.game_state_id)
        val state = RewriteGameJson.decode(
            serializer = ComputerState.serializer(),
            payload = snapshot.snapshot!!.payload.toByteArray(),
        )
        assertEquals(GameStateId("LOCAL-IP"), state.id)
        assertFalse(connection.drainFrames().any { it.snapshot != null })
    }

    @Test
    fun scanRequestReturnsExactlyOneCorrelatedResponseWithoutDeltaStream() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()

        connection.send(
            RewriteFrames.command(
                commandId = "scan-1",
                commandName = "requestscan",
                targetGameStateIds = listOf("TARGET-IP"),
                expectsResponse = true,
            ),
        )

        val response = connection.awaitFrame()

        assertEquals("scan-1", response.command_response?.command_id)
        val scan = RewriteGameJson.decode(
            serializer = ScanResponse.serializer(),
            payload = response.command_response!!.payload.toByteArray(),
        )
        assertEquals("TARGET-IP", scan.targetIp)
        assertEquals(listOf(22, 80, 443), scan.openPorts)
        assertFalse(connection.drainFrames().any { it.delta != null })
    }

    @Test
    fun setPreferencesPersistsEventAndFansOutTargetedDeltaToAllRegisteredListeners() = runTest {
        val fixture = createFixture()
        val primary = fixture.authenticatedConnection()
        val secondary = fixture.authenticatedConnection()

        primary.send(
            RewriteFrames.command(
                commandId = "pref-1",
                commandName = "setpreferences",
                targetGameStateIds = listOf("LOCAL-IP"),
                payload = RewriteGameJson.encode(
                    serializer = SetPreferencePayload.serializer(),
                    value = SetPreferencePayload(
                        key = "show_clock",
                        value = "true",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val primaryDelta = primary.awaitFrame()
        val secondaryDelta = secondary.awaitFrame()
        val response = primary.awaitFrame()

        assertEquals("LOCAL-IP", primaryDelta.delta?.game_state_id)
        assertEquals(listOf("preferences.values.show_clock"), primaryDelta.delta?.changed_paths)
        assertEquals(listOf("preferences"), primaryDelta.delta?.delta_keys)
        val projection = RewriteGameJson.decode(
            serializer = DeltaProjection.serializer(),
            payload = primaryDelta.delta!!.payload.toByteArray(),
        )
        assertIs<PreferenceDeltaProjection>(projection)
        assertEquals("true", projection.values["show_clock"])

        assertEquals(primaryDelta.delta, secondaryDelta.delta)
        assertEquals("pref-1", response.command_response?.command_id)
        assertEquals(1, fixture.repository.eventsFor(GameStateId("LOCAL-IP")).size)
        assertFalse(primary.drainFrames().any { it.snapshot != null })
    }

    private fun TestScope.createFixture(): Fixture {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                GameStateId("LOCAL-IP") to ComputerState.empty(
                    id = GameStateId("LOCAL-IP"),
                    playFabId = "PF-LOCALUSER",
                ),
                GameStateId("TARGET-IP") to ComputerState.empty(
                    id = GameStateId("TARGET-IP"),
                    playerIp = "TARGET-IP",
                ).copy(
                    ports = listOf(
                        PortState(number = 22, type = "ssh"),
                        PortState(number = 80, type = "http"),
                        PortState(number = 443, type = "https"),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
            ),
            interestRegistry = interests,
        )
        val harnessAdapter = HarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
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
        return Fixture(
            harness = harness,
            repository = repository,
        )
    }

    private suspend fun Fixture.authenticatedConnection(): InMemoryClientConnection {
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
        connection.awaitFrame()
        connection.awaitFrame()
        return connection
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
        val repository: InMemoryComputerStateRepository,
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
            command: CommandEnvelope,
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
