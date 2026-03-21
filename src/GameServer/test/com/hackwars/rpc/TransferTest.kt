package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class TransferTest {
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
        val params = arrayOf<Any?>(1.5f, "value", null, 42)
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.TRANSFER, params)

        val call = Transfer.fromRpc(rpc)
        assertEquals(params[0], call.amount)
        assertEquals(params[1], call.ip)
        assertEquals(params[2], call.targetIp)
        assertEquals(params[3], call.port)
        val serialized = call.toRfc()

        assertEquals(com.hackwars.rpc.GameCommandWires.TRANSFER, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
