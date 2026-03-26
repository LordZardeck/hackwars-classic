package com.hackwars.rewrite.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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

    @Test
    fun roundTripsChatEventFramesAndClassifiesPayloadType() {
        val frame = RewriteFrames.chatEvent(
            eventId = "event-1",
            eventType = "channel_text",
            channelName = "global",
            payload = """{"message":"hello"}""".encodeToByteArray(),
        )

        val decoded = FrameCodec.decode(FrameCodec.encode(frame))

        assertEquals(FramePayloadType.CHAT_EVENT, decoded.payloadType())
        assertNotNull(decoded.chat_event)
        assertEquals("event-1", decoded.chat_event?.event_id)
        assertEquals("channel_text", decoded.chat_event?.event_type)
        assertEquals("global", decoded.chat_event?.channel_name)
    }

    @Test
    fun rewriteServiceMapsChatProtoKind() {
        assertEquals(RewriteService.CHAT, RewriteService.fromProto(hackwars.rewrite.v1.ServiceKind.SERVICE_KIND_CHAT))
        assertEquals(hackwars.rewrite.v1.ServiceKind.SERVICE_KIND_CHAT, RewriteService.CHAT.toProto())
    }

    @Test
    fun stateMachineTracksIdleTimeoutAndInboundActivityAfterAuth() {
        val stateMachine = ConnectionStateMachine(
            connectedAt = java.time.Instant.EPOCH,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = 5.seconds,
                idleTimeout = 10.seconds,
            ),
        )

        stateMachine.authenticate(java.time.Instant.ofEpochMilli(1_000))
        stateMachine.activate(java.time.Instant.ofEpochMilli(1_000))

        assertNull(stateMachine.timeoutReason(java.time.Instant.ofEpochMilli(10_999)))
        assertEquals(DisconnectReason.IDLE_TIMEOUT, stateMachine.timeoutReason(java.time.Instant.ofEpochMilli(11_000)))

        stateMachine.noteInbound(java.time.Instant.ofEpochMilli(12_000))

        assertNull(stateMachine.timeoutReason(java.time.Instant.ofEpochMilli(21_999)))
        assertEquals(DisconnectReason.IDLE_TIMEOUT, stateMachine.timeoutReason(java.time.Instant.ofEpochMilli(22_000)))
    }
}
