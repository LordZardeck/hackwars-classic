package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class ChangeNetworkTest {
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
    fun fromRpc_readsEncryptedIpAndNullableNetwork() {
        val rpc = RemoteFunctionCall(1, ChangeNetwork.FUNCTION, arrayOf<Any?>("encrypted-ip", "network-a"))

        val call = ChangeNetwork.fromRpc(rpc)

        assertEquals("encrypted-ip", call.encryptedIp)
        assertEquals("network-a", call.network)
    }

    @Test
    fun fromRpc_returnsNullNetworkWhenMissing() {
        val rpc = RemoteFunctionCall(1, ChangeNetwork.FUNCTION, arrayOf<Any?>("encrypted-ip"))

        val call = ChangeNetwork.fromRpc(rpc)

        assertEquals("encrypted-ip", call.encryptedIp)
        assertNull(call.network)
    }

    @Test
    fun toRfc_serializesFunctionAndParameters() {
        val call = ChangeNetwork("encrypted-ip", null)

        val rpc = call.toRfc()

        assertEquals(ChangeNetwork.FUNCTION, rpc.function)
        assertArrayEquals(arrayOf("encrypted-ip", null), rpc.parameters as Array<*>)
    }

    @Test
    fun fromRpc_throwsWhenEncryptedIpMissing() {
        val rpc = RemoteFunctionCall(1, ChangeNetwork.FUNCTION, emptyArray<Any>())

        val ex = assertThrows(IllegalParameterException::class.java) {
            ChangeNetwork.fromRpc(rpc)
        }

        assertEquals(ChangeNetwork.FUNCTION, ex.function)
        assertEquals(0, ex.position)
        assertEquals(String::class.java.name, ex.expectedType)
        assertNull(ex.actualType)
    }
}
