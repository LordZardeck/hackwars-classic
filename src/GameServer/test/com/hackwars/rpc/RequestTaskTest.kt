package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class RequestTaskTest {
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
        val params = arrayOf<Any?>(null, null, null, null)
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.REQUESTTASK, params)

        val call = RequestTask.fromRpc(rpc)
        assertEquals(params[0], call.fileName)
        assertEquals(params[1], call.questID)
        assertEquals(params[2], call.taskName)
        assertEquals(params[3], call.targetIP)
        val serialized = call.toRfc()

        assertEquals(com.hackwars.rpc.GameCommandWires.REQUESTTASK, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
