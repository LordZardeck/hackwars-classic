package com.hackwars.rewrite.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class FrameCodecTest {
    @Test
    fun roundTripsEncodedFrames() {
        val frame = RewriteFrames.authRequest(
            service = RewriteService.GAME,
            sessionTicket = "SESSION-LOCALUSER",
            clientBuild = "rewrite-test",
            playFabIdHint = "PF-LOCALUSER",
            requestedIp = "10.0.0.5",
        )

        val decoded = FrameCodec.decode(FrameCodec.encode(frame))

        assertEquals(frame, decoded)
        assertEquals(FramePayloadType.AUTH_REQUEST, decoded.payloadType())
    }

    @Test
    fun rejectsMalformedFramesWhenHeaderLengthDoesNotMatchPayload() {
        val malformed = byteArrayOf(0, 0, 0, 9, 1, 2, 3)

        assertFailsWith<MalformedFrameException> {
            FrameCodec.decode(malformed)
        }
    }

    @Test
    fun rejectsOversizedFramesBeforeDecode() {
        val oversized = ByteArray(Int.SIZE_BYTES).also {
            java.nio.ByteBuffer.wrap(it).putInt(4_096)
        }

        val exception = assertFailsWith<OversizedFrameException> {
            FrameCodec.decode(oversized, maxFrameBytes = 32)
        }

        assertEquals(4_096, exception.declaredLength)
        assertEquals(32, exception.maxFrameBytes)
    }

    @Test
    fun stateMachineRequiresAuthBeforeOtherTraffic() {
        val stateMachine = ConnectionStateMachine(java.time.Instant.EPOCH)

        val reason = stateMachine.requireAuth(FramePayloadType.COMMAND)

        assertEquals(DisconnectReason.MISSING_AUTH, reason)
        assertEquals(ConnectionLifecycleState.CLOSED, stateMachine.state)
    }

    @Test
    fun stateMachineTracksAuthAndIdleTimeouts() {
        val stateMachine = ConnectionStateMachine(
            connectedAt = java.time.Instant.EPOCH,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = 5.seconds,
                idleTimeout = 10.seconds,
            ),
        )

        assertNull(stateMachine.timeoutReason(java.time.Instant.ofEpochMilli(4_000)))
        assertEquals(DisconnectReason.AUTH_TIMEOUT, stateMachine.timeoutReason(java.time.Instant.ofEpochMilli(5_000)))
    }
}
