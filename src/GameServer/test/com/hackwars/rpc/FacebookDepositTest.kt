package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class FacebookDepositTest {
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
        val params = arrayOf<Any?>(null, null, 42)
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.FACEBOOKDEPOSIT, params)

        val call = FacebookDeposit.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.amount)
        assertEquals(params[2], call.defaultPort)
        val serialized = call.toRfc()

        assertEquals(com.hackwars.rpc.GameCommandWires.FACEBOOKDEPOSIT, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
