package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class DeleteMultiTest {
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
        val params = arrayOf<Any?>("value", arrayOf<Any?>("x", 1))
        val rpc = RemoteFunctionCall(1, DeleteMulti.FUNCTION, params)

        val call = DeleteMulti.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.allFiles)
        val serialized = call.toRfc()

        assertEquals(DeleteMulti.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
