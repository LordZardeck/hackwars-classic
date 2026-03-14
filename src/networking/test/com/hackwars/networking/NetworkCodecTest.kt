package com.hackwars.networking

import com.google.protobuf.ByteString
import com.google.protobuf.BytesValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class NetworkCodecTest {

    @Test
    fun `encodeFrame writes type length and payload`() {
        val message = BytesValue.newBuilder().setValue(ByteString.copyFromUtf8("abc")).build()

        val encoded = encodeFrame(FrameMessageType.AUTH, message)
        val buffer = ByteBuffer.wrap(encoded)

        assertEquals(FrameMessageType.AUTH.wireValue, buffer.get())
        assertEquals(message.toByteArray().size, buffer.int)

        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)
        assertArrayEquals(message.toByteArray(), payload)
    }

    @Test
    fun `encodeFrame throws when payload exceeds configured max size`() {
        val oversized = BytesValue.newBuilder().setValue(ByteString.copyFrom(ByteArray(65))).build()

        assertThrows(IllegalArgumentException::class.java) {
            encodeFrame(FrameMessageType.SERVICE, oversized, maxServicePayloadSize = 64)
        }
    }

    @Test
    fun `decodeFrame decodes a valid auth frame`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val frame = frameBytes(FrameMessageType.AUTH.wireValue, payload.size, payload)

        val decoded = decodeFrame(ByteArrayInputStream(frame))

        assertEquals(FrameMessageType.AUTH, decoded.messageType)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun `decodeFrame decodes service frame with custom max payload size`() {
        val payload = ByteArray(16) { it.toByte() }
        val frame = frameBytes(FrameMessageType.SERVICE.wireValue, payload.size, payload)

        val decoded = decodeFrame(ByteArrayInputStream(frame), maxServicePayloadSize = 32)

        assertEquals(FrameMessageType.SERVICE, decoded.messageType)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun `decodeFrame rejects unsupported frame type`() {
        val frame = frameBytes(99.toByte(), 0, byteArrayOf())

        val ex = assertThrows(FrameDecodingException.InvalidFrame::class.java) {
            decodeFrame(ByteArrayInputStream(frame))
        }
        assertEquals("Unsupported frame type: 99. Connection must be closed.", ex.message)
    }

    @Test
    fun `decodeFrame rejects missing type or length header`() {
        val ex = assertThrows(FrameDecodingException.InvalidFrame::class.java) {
            decodeFrame(ByteArrayInputStream(byteArrayOf(FrameMessageType.AUTH.wireValue)))
        }
        assertEquals("Frame is missing the type/length header.", ex.message)
    }

    @Test
    fun `decodeFrame rejects payload larger than configured limit`() {
        val payload = ByteArray(8) { 7 }
        val frame = frameBytes(FrameMessageType.SERVICE.wireValue, payload.size, payload)

        val ex = assertThrows(FrameDecodingException.IllegalPayloadSize::class.java) {
            decodeFrame(ByteArrayInputStream(frame), maxServicePayloadSize = 4)
        }
        assertEquals(FrameMessageType.SERVICE, ex.messageType)
        assertEquals(payload.size, ex.payloadSize)
    }

    @Test
    fun `decodeFrame rejects truncated payload`() {
        val declaredLength = 5
        val actualPayload = byteArrayOf(9, 8)
        val frame = frameBytes(FrameMessageType.AUTH.wireValue, declaredLength, actualPayload)

        val ex = assertThrows(FrameDecodingException.InvalidFrame::class.java) {
            decodeFrame(ByteArrayInputStream(frame))
        }
        assertEquals("Frame payload ended before the declared length was satisfied.", ex.message)
    }

    private fun frameBytes(typeWire: Byte, declaredLength: Int, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(Byte.SIZE_BYTES + Int.SIZE_BYTES / Byte.SIZE_BYTES + payload.size)
        buffer.put(typeWire)
        buffer.putInt(declaredLength)
        buffer.put(payload)
        return buffer.array()
    }
}
