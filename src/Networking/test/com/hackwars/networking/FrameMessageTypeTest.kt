package com.hackwars.networking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameMessageTypeTest {

    @Test
    fun `wire values remain stable`() {
        assertEquals(1.toByte(), FrameMessageType.AUTH.wireValue)
        assertEquals(2.toByte(), FrameMessageType.SERVICE.wireValue)
    }

    @Test
    fun `fromWireValue returns matching enum for known values`() {
        assertEquals(FrameMessageType.AUTH, FrameMessageType.fromWireValue(1))
        assertEquals(FrameMessageType.SERVICE, FrameMessageType.fromWireValue(2))
    }

    @Test
    fun `fromWireValue returns null for unknown values`() {
        assertNull(FrameMessageType.fromWireValue(0))
        assertNull(FrameMessageType.fromWireValue(-1))
        assertNull(FrameMessageType.fromWireValue(3))
        assertNull(FrameMessageType.fromWireValue(255))
    }

    @Test
    fun `auth maxPayloadSize is fixed and ignores override`() {
        assertEquals(2048, FrameMessageType.AUTH.maxPayloadSize())
        assertEquals(2048, FrameMessageType.AUTH.maxPayloadSize(10 * 1024 * 1024))
    }

    @Test
    fun `service maxPayloadSize uses default when no override provided`() {
        assertEquals(8 * 1024 * 1024, FrameMessageType.SERVICE.maxPayloadSize())
        assertEquals(8 * 1024 * 1024, FrameMessageType.SERVICE.maxPayloadSize(null))
    }

    @Test
    fun `service maxPayloadSize uses override when provided`() {
        assertEquals(16 * 1024 * 1024, FrameMessageType.SERVICE.maxPayloadSize(16 * 1024 * 1024))
    }
}
