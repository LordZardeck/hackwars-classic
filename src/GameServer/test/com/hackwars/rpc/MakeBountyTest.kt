package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class MakeBountyTest {
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
        val params = arrayOf<Any?>("value", null, null, null, null, null, null, null)
        val rpc = RemoteFunctionCall(1, MakeBounty.FUNCTION, params)

        val call = MakeBounty.fromRpc(rpc)
        assertEquals(params[0], call.sourceIp)
        assertEquals(params[1], call.anonymous)
        assertEquals(params[2], call.target)
        assertEquals(params[3], call.type)
        assertEquals(params[4], call.fname)
        assertEquals(params[5], call.folder)
        assertEquals(params[6], call.iterations)
        assertEquals(params[7], call.reward)
        val serialized = call.toRfc()

        assertEquals(MakeBounty.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
