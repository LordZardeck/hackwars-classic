package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class WithdrawTest {
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
        val params = arrayOf<Any?>(1.5f, "value", 42)
        val rpc = RemoteFunctionCall(1, Withdraw.FUNCTION, params)

        val call = Withdraw.fromRpc(rpc)
        assertEquals(params[0], call.amount)
        assertEquals(params[1], call.ip)
        assertEquals(params[2], call.port)
        val serialized = call.toRfc()

        assertEquals(Withdraw.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
