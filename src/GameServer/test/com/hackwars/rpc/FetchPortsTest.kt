package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.BeforeClass
import org.junit.Assert.*
import org.junit.Test
import util.Encryption

class FetchPortsTest {
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
    fun fromRpc_readsEncryptedIpFromFirstPositionalParameter() {
        val rpc = RemoteFunctionCall(42, FetchPorts.FUNCTION, arrayOf("encrypted-ip", "unused"))

        val fetchPorts = FetchPorts.fromRpc(rpc)

        assertEquals("encrypted-ip", fetchPorts.encryptedIp)
    }

    @Test
    fun toRfc_serializesFunctionAndEncryptedIpParameter() {
        val fetchPorts = FetchPorts("encrypted-ip")

        val rfc = fetchPorts.toRfc()

        assertEquals(FetchPorts.FUNCTION, rfc.function)
        assertArrayEquals(arrayOf("encrypted-ip"), rfc.parameters as Array<*>)
    }

    @Test
    fun fromRpc_throwsWhenFirstParameterIsMissing() {
        val rpc = RemoteFunctionCall(42, FetchPorts.FUNCTION, emptyArray<Any>())

        val ex = assertThrows(IllegalParameterException::class.java) {
            FetchPorts.fromRpc(rpc)
        }

        assertEquals(FetchPorts.FUNCTION, ex.function)
        assertEquals(0, ex.position)
        assertEquals(String::class.java.name, ex.expectedType)
    }
}
