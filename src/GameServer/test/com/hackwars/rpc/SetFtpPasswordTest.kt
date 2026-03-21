package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class SetFtpPasswordTest {
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
        val params = arrayOf<Any?>("value", null)
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.SETFTPPASSWORD, params)

        val call = SetFtpPassword.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.password)
        val serialized = call.toRfc()

        assertEquals(com.hackwars.rpc.GameCommandWires.SETFTPPASSWORD, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
