package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class PeekCodeTest {
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
        val params = arrayOf<Any?>("value", null, 42)
        val rpc = RemoteFunctionCall(1, PeekCode.FUNCTION, params)

        val call = PeekCode.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.targetIP)
        assertEquals(params[2], call.port)
        val serialized = call.toRfc()

        assertEquals(PeekCode.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
