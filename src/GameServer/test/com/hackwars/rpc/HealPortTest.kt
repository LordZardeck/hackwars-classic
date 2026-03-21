package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class HealPortTest {
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
    fun fromRpc_readsEncryptedIpAndPort() {
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.HEALPORT, arrayOf<Any?>("encrypted-ip", 21))

        val call = HealPort.fromRpc(rpc)

        assertEquals("encrypted-ip", call.encryptedIp)
        assertEquals(21, call.port)
    }

    @Test
    fun toRfc_serializesFunctionAndParameters() {
        val call = HealPort("encrypted-ip", 21)

        val rpc = call.toRfc()

        assertEquals(com.hackwars.rpc.GameCommandWires.HEALPORT, rpc.function)
        assertArrayEquals(arrayOf("encrypted-ip", 21), rpc.parameters as Array<*>)
    }

    @Test
    fun fromRpc_throwsWhenPortHasWrongType() {
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.HEALPORT, arrayOf<Any?>("encrypted-ip", "21"))

        val ex = assertThrows(IllegalParameterException::class.java) {
            HealPort.fromRpc(rpc)
        }

        assertEquals(com.hackwars.rpc.GameCommandWires.HEALPORT, ex.function)
        assertEquals(1, ex.position)
        assertEquals(Int::class.javaObjectType.name, ex.expectedType)
        assertEquals(String::class.java.name, ex.actualType)
    }
}
