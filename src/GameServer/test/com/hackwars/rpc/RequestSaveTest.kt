package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class RequestSaveTest {
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
        val params = arrayOf<Any?>(null, hashMapOf<Any?, Any?>("k" to "v"), null)
        val rpc = RemoteFunctionCall(1, RequestSave.FUNCTION, params)

        val call = RequestSave.fromRpc(rpc)
        assertEquals(params[0], call.fileName)
        assertEquals(params[1], call.triggerParam)
        assertEquals(params[2], call.targetIP)
        val serialized = call.toRfc()

        assertEquals(RequestSave.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
