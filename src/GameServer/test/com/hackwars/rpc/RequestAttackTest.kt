package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class RequestAttackTest {
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
        val params = arrayOf<Any?>("value", 42, "value", 42, arrayOf<Int?>(1, null), arrayOf<Array<String?>?>(arrayOf<String?>("a", null)), arrayOf<Any?>("x", 1), null)
        val rpc = RemoteFunctionCall(1, RequestAttack.FUNCTION, params)

        val call = RequestAttack.fromRpc(rpc)
        assertEquals(params[0], call.targetIP)
        assertEquals(params[1], call.targetPort)
        assertEquals(params[2], call.sourceIP)
        assertEquals(params[3], call.sourcePort)
        assertEquals(params[4], call.secondaryPorts)
        assertEquals(params[5], call.scripts)
        assertEquals(params[6], call.extraInfo)
        assertEquals(params[7], call.windowHandle)
        val serialized = call.toRfc()

        assertEquals(RequestAttack.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
