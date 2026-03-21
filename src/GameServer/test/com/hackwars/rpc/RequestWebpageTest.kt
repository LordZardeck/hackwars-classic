package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class RequestWebpageTest {
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
    fun fromRpc_readsIpsAndParameterMap() {
        val parameters = hashMapOf<Any?, Any?>("q" to "search")
        val rpc = RemoteFunctionCall(
            1,
            com.hackwars.rpc.GameCommandWires.REQUESTWEBPAGE,
            arrayOf<Any?>("target", "source", parameters)
        )

        val call = RequestWebpage.fromRpc(rpc)

        assertEquals("target", call.targetIp)
        assertEquals("source", call.sourceIp)
        assertEquals("search", call.parameters["q"])
    }

    @Test
    fun fromRpc_defaultsToEmptyMapWhenMapMissing() {
        val rpc =
            RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.REQUESTWEBPAGE, arrayOf<Any?>("target", "source"))

        val call = RequestWebpage.fromRpc(rpc)

        assertEquals("target", call.targetIp)
        assertEquals("source", call.sourceIp)
        assertTrue(call.parameters.isEmpty())
    }

    @Test
    fun toRfc_serializesFunctionAndParameters() {
        val parameters = hashMapOf<Any?, Any?>("packetid" to 123)
        val call = RequestWebpage("target", "source", parameters)

        val rpc = call.toRfc()

        assertEquals(com.hackwars.rpc.GameCommandWires.REQUESTWEBPAGE, rpc.function)
        assertArrayEquals(arrayOf("target", "source", parameters), rpc.parameters as Array<*>)
    }

    @Test
    fun fromRpc_throwsWhenSourceIpMissing() {
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.REQUESTWEBPAGE, arrayOf<Any?>("target"))

        val ex = assertThrows(IllegalParameterException::class.java) {
            RequestWebpage.fromRpc(rpc)
        }

        assertEquals(com.hackwars.rpc.GameCommandWires.REQUESTWEBPAGE, ex.function)
        assertEquals(1, ex.position)
        assertEquals(String::class.java.name, ex.expectedType)
    }
}
