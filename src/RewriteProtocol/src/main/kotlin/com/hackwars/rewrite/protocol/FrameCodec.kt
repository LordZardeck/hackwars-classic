package com.hackwars.rewrite.protocol

import hackwars.rewrite.v1.FrameEnvelope
import java.nio.ByteBuffer

open class ProtocolFrameException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class MalformedFrameException(
    message: String,
    cause: Throwable? = null,
) : ProtocolFrameException(message, cause)

class OversizedFrameException(
    val declaredLength: Int,
    val maxFrameBytes: Int,
) : ProtocolFrameException("Frame length $declaredLength exceeds max allowed size $maxFrameBytes")

object FrameCodec {
    private const val HEADER_BYTES = Int.SIZE_BYTES

    fun encode(frame: FrameEnvelope): ByteArray {
        val payload = FrameEnvelope.ADAPTER.encode(frame)
        return ByteBuffer.allocate(HEADER_BYTES + payload.size)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    fun decode(
        encodedFrame: ByteArray,
        maxFrameBytes: Int = ProtocolTimeoutPolicy().maxFrameBytes,
    ): FrameEnvelope {
        if (encodedFrame.size < HEADER_BYTES) {
            throw MalformedFrameException("Encoded frame is shorter than the 4-byte length header.")
        }

        val payloadLength = ByteBuffer.wrap(encodedFrame, 0, HEADER_BYTES).int
        if (payloadLength < 0) {
            throw MalformedFrameException("Encoded frame declared a negative payload length: $payloadLength")
        }
        if (payloadLength > maxFrameBytes) {
            throw OversizedFrameException(payloadLength, maxFrameBytes)
        }

        val actualPayloadLength = encodedFrame.size - HEADER_BYTES
        if (actualPayloadLength != payloadLength) {
            throw MalformedFrameException(
                "Encoded frame payload length mismatch: declared=$payloadLength actual=$actualPayloadLength",
            )
        }

        return decodePayload(encodedFrame.copyOfRange(HEADER_BYTES, encodedFrame.size))
    }

    fun decodePayload(payload: ByteArray): FrameEnvelope {
        return try {
            FrameEnvelope.ADAPTER.decode(payload)
        } catch (exception: Exception) {
            throw MalformedFrameException("Failed to decode FrameEnvelope payload.", exception)
        }
    }
}
