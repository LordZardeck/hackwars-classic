package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class ChangeWatchTypeTest {
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
        val params = arrayOf<Any?>("value", null, null)
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.CHANGEWATCHTYPE, params)

        val call = ChangeWatchType.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.watchID)
        assertEquals(params[2], call.portID)
        val serialized = call.toRfc()

        assertEquals(com.hackwars.rpc.GameCommandWires.CHANGEWATCHTYPE, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
