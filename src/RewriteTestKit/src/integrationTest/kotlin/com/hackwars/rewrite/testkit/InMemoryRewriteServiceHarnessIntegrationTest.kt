package com.hackwars.rewrite.testkit

import com.hackwars.rewrite.protocol.ConnectionLifecycleState
import com.hackwars.rewrite.protocol.DisconnectReason
import com.hackwars.rewrite.protocol.FrameCodec
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.protocol.payloadType
import hackwars.rewrite.v1.CommandResponseStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryRewriteServiceHarnessIntegrationTest {
    @Test
    fun validAuthProducesAcceptedFrameAndBootstrapSnapshot() = runTest {
        val harness = createGameHarness()
        val connection = harness.connect()

        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-it",
                playFabIdHint = "PF-LOCALUSER",
                requestedIp = "192.0.2.10",
            ),
        )

        val authResponse = connection.awaitFrame()
        val snapshot = connection.awaitFrame()

        assertNotNull(authResponse.auth_response?.accepted)
        assertEquals("PF-LOCALUSER", authResponse.auth_response?.accepted?.playfab_id)
        assertNotNull(snapshot.snapshot)
        assertEquals(ConnectionLifecycleState.ACTIVE, connection.state())
    }

    @Test
    fun invalidSessionTicketIsRejectedAndLogged() = runTest {
        val harness = createGameHarness()
        val connection = harness.connect()

        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = "SESSION-UNKNOWN",
                clientBuild = "rewrite-it",
            ),
        )

        val authResponse = connection.awaitFrame()

        assertEquals("INVALID_SESSION", authResponse.auth_response?.rejected?.reason_code)
        assertEquals(ConnectionLifecycleState.CLOSED, connection.state())
        assertEquals(DisconnectReason.INVALID_AUTH, connection.disconnectReason())
        assertTrue(harness.log().snapshot().any { it.reason == DisconnectReason.INVALID_AUTH })
    }

    @Test
    fun missingAuthClosesConnectionBeforeServiceTraffic() = runTest {
        val harness = createGameHarness()
        val connection = harness.connect()

        connection.send(
            RewriteFrames.command(
                commandId = "cmd-1",
                commandName = "requestscan",
                targetGameStateIds = listOf("192.0.2.10"),
                expectsResponse = true,
            ),
        )

        assertEquals(ConnectionLifecycleState.CLOSED, connection.state())
        assertEquals(DisconnectReason.MISSING_AUTH, connection.disconnectReason())
        assertTrue(connection.drainFrames().isEmpty())
    }

    @Test
    fun malformedAndOversizedFramesAreRejected() = runTest {
        val harness = createGameHarness()
        val malformedConnection = harness.connect("malformed")
        malformedConnection.sendEncoded(byteArrayOf(0, 0, 0, 9, 1, 2, 3))

        assertEquals(DisconnectReason.MALFORMED_FRAME, malformedConnection.disconnectReason())

        val oversizedConnection = harness.connect("oversized")
        val oversized = ByteArray(Int.SIZE_BYTES).also {
            java.nio.ByteBuffer.wrap(it).putInt(512 * 1024)
        }
        oversizedConnection.sendEncoded(oversized)

        assertEquals(DisconnectReason.OVERSIZED_FRAME, oversizedConnection.disconnectReason())
    }

    @Test
    fun oversizedAuthPayloadIsRejectedBeforeVerification() = runTest {
        val harness = createGameHarness(maxAuthPayloadBytes = 8)
        val connection = harness.connect()

        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-it",
            ),
        )

        assertEquals(ConnectionLifecycleState.CLOSED, connection.state())
        assertEquals(DisconnectReason.OVERSIZED_AUTH_PAYLOAD, connection.disconnectReason())
    }

    @Test
    fun pingSuccessEchoesConnectionAndAckTime() = runTest {
        val harness = createGameHarness()
        val connection = authenticatedConnection(harness)

        val pingFrame = RewriteFrames.ping(
            connectionId = "ignored-by-server",
            sentAtEpochMillis = 1_234L,
            acknowledgedAtEpochMillis = 0L,
        )
        connection.send(pingFrame)

        val pingResponse = connection.awaitFrame()

        assertNotNull(pingResponse.ping)
        assertEquals(connection.connectionId, pingResponse.ping?.connection_id)
        assertTrue((pingResponse.ping?.acknowledged_at_epoch_millis ?: 0L) >= 0L)
    }

    @Test
    fun authAndIdleTimeoutsCloseAndLogConnections() = runTest {
        val authTimeoutHarness = createGameHarness(authTimeout = 5.seconds, idleTimeout = 20.seconds)
        val authTimeoutConnection = authTimeoutHarness.connect()
        testScheduler.advanceTimeBy(5_000)
        authTimeoutHarness.sweepTimeouts()

        assertEquals(DisconnectReason.AUTH_TIMEOUT, authTimeoutConnection.disconnectReason())

        val idleTimeoutHarness = createGameHarness(authTimeout = 5.seconds, idleTimeout = 10.seconds)
        val idleTimeoutConnection = authenticatedConnection(idleTimeoutHarness)
        testScheduler.advanceTimeBy(10_000)
        idleTimeoutHarness.sweepTimeouts()

        assertEquals(DisconnectReason.IDLE_TIMEOUT, idleTimeoutConnection.disconnectReason())
        assertTrue(idleTimeoutHarness.log().snapshot().any { it.reason == DisconnectReason.IDLE_TIMEOUT })
    }

    @Test
    fun commandResponsesKeepCorrelationAndAllowTargetedPushUpdates() = runTest {
        val harness = createGameHarness()
        val connection = authenticatedConnection(harness)

        connection.send(
            RewriteFrames.command(
                commandId = "scan-1",
                commandName = "requestscan",
                targetGameStateIds = listOf("192.0.2.10"),
                expectsResponse = true,
            ),
        )

        val response = connection.awaitFrame()
        assertEquals("scan-1", response.command_response?.command_id)
        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, response.command_response?.status)
        assertNull(connection.drainFrames().firstOrNull { it.delta != null })

        connection.send(
            RewriteFrames.command(
                commandId = "mutate-1",
                commandName = "mutate-state",
                targetGameStateIds = listOf("192.0.2.10"),
                expectsResponse = true,
            ),
        )

        val mutateResponse = connection.awaitFrame()
        val delta = connection.awaitFrame()

        assertEquals("mutate-1", mutateResponse.command_response?.command_id)
        assertEquals("192.0.2.10", delta.delta?.game_state_id)
        assertEquals(listOf("computer.cash"), delta.delta?.changed_paths)
        assertEquals(listOf("petty_cash"), delta.delta?.delta_keys)
    }

    @Test
    fun gameAndChatCanAuthenticateIndependentlyWithTheSameSessionTicket() = runTest {
        val gameHarness = createGameHarness()
        val chatHarness = InMemoryRewriteServiceHarness(
            adapter = StubRewriteAdapters.chat(),
            verifier = FakeSessionTicketVerifier(clock = { Instant.ofEpochMilli(testScheduler.currentTime) }),
            scope = backgroundScope,
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )

        val gameConnection = gameHarness.connect("game")
        val chatConnection = chatHarness.connect("chat")

        gameConnection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-it",
                requestedIp = "192.0.2.10",
            ),
        )
        chatConnection.send(
            RewriteFrames.authRequest(
                service = RewriteService.CHAT,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-it",
            ),
        )

        val gameAuth = gameConnection.awaitFrame()
        val gameSnapshot = gameConnection.awaitFrame()
        val chatAuth = chatConnection.awaitFrame()

        assertNotNull(gameAuth.auth_response?.accepted)
        assertNotNull(gameSnapshot.snapshot)
        assertNotNull(chatAuth.auth_response?.accepted)
        assertFalse(chatConnection.drainFrames().any { it.payloadType().name.contains("SNAPSHOT") })
    }

    private suspend fun TestScope.authenticatedConnection(harness: InMemoryRewriteServiceHarness): InMemoryClientConnection {
        val connection = harness.connect()
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-it",
                requestedIp = "192.0.2.10",
            ),
        )
        connection.awaitFrame()
        connection.awaitFrame()
        return connection
    }

    private fun TestScope.createGameHarness(
        authTimeout: kotlin.time.Duration = 5.seconds,
        idleTimeout: kotlin.time.Duration = 45.seconds,
        maxAuthPayloadBytes: Int = 2_048,
    ): InMemoryRewriteServiceHarness {
        return InMemoryRewriteServiceHarness(
            adapter = StubRewriteAdapters.game(),
            verifier = FakeSessionTicketVerifier(
                clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
            ),
            scope = backgroundScope,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = authTimeout,
                idleTimeout = idleTimeout,
                maxAuthPayloadBytes = maxAuthPayloadBytes,
            ),
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )
    }
}
