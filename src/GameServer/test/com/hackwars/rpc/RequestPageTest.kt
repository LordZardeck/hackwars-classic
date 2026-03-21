package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class RequestPageTest {
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
        val params = arrayOf<Any?>("value")
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.REQUESTPAGE, params)

        val call = RequestPage.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        val serialized = call.toRfc()

        assertEquals(com.hackwars.rpc.GameCommandWires.REQUESTPAGE, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
