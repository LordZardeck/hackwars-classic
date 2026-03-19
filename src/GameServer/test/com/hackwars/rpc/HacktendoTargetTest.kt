package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class HacktendoTargetTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setupEncryption() {
            Encryption.breakSingleton()
            val encryption = Encryption.getInstance()
            encryption.init()
            encryption.finalize(encryption.encodedKey)
        }
    }

    @Test
    fun fromRpc_and_toRfc_roundtrip() {
        val params = arrayOf<Any?>(42, 42, null, 42, 42)
        val rpc = RemoteFunctionCall(1, HacktendoTarget.FUNCTION, params)

        val call = HacktendoTarget.fromRpc(rpc)
        assertEquals(params[0], call.targetX)
        assertEquals(params[1], call.targetY)
        assertEquals(params[2], call.ip)
        assertEquals(params[3], call.currentX)
        assertEquals(params[4], call.currentY)
        val serialized = call.toRfc()

        assertEquals(HacktendoTarget.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
