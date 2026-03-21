package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.*
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
        val rpc = RemoteFunctionCall(
            1,
            com.hackwars.rpc.GameCommandWires.CHANGENETWORK,
            arrayOf<Any?>("encrypted-ip", "network-a")
        )

        val call = ChangeNetwork.fromRpc(rpc)

        assertEquals("encrypted-ip", call.encryptedIp)
        assertEquals("network-a", call.network)
    }

    @Test
    fun fromRpc_returnsNullNetworkWhenMissing() {
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.CHANGENETWORK, arrayOf<Any?>("encrypted-ip"))

        val call = ChangeNetwork.fromRpc(rpc)

        assertEquals("encrypted-ip", call.encryptedIp)
        assertNull(call.network)
    }

    @Test
    fun toRfc_serializesFunctionAndParameters() {
        val call = ChangeNetwork("encrypted-ip", null)

        val rpc = call.toRfc()

        assertEquals(com.hackwars.rpc.GameCommandWires.CHANGENETWORK, rpc.function)
        assertArrayEquals(arrayOf("encrypted-ip", null), rpc.parameters as Array<*>)
    }

    @Test
    fun fromRpc_throwsWhenEncryptedIpMissing() {
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.CHANGENETWORK, emptyArray<Any>())

        val ex = assertThrows(IllegalParameterException::class.java) {
            ChangeNetwork.fromRpc(rpc)
        }

        assertEquals(com.hackwars.rpc.GameCommandWires.CHANGENETWORK, ex.function)
        assertEquals(0, ex.position)
        assertEquals(String::class.java.name, ex.expectedType)
        assertNull(ex.actualType)
    }
}
