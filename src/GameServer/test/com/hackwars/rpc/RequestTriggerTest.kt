package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class RequestTriggerTest {
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
        val params = arrayOf<Any?>(null, hashMapOf<Any?, Any?>("k" to "v"), null, null)
        val rpc = RemoteFunctionCall(1, RequestTrigger.FUNCTION, params)

        val call = RequestTrigger.fromRpc(rpc)
        assertEquals(params[0], call.watchNote)
        assertEquals(params[1], call.triggerParam)
        assertEquals(params[2], call.sourceIP)
        assertEquals(params[3], call.targetIP)
        val serialized = call.toRfc()

        assertEquals(RequestTrigger.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
