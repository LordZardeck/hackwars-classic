package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class SubmitTest {
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
    fun fromRpc_readsNullableTargetAndParameterMap() {
        val parameters = hashMapOf<Any?, Any?>("field" to "value")
        val rpc =
            RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.SUBMIT, arrayOf<Any?>(null, "source", parameters))

        val call = Submit.fromRpc(rpc)

        assertNull(call.targetIp)
        assertEquals("source", call.sourceIp)
        assertEquals("value", call.parameters["field"])
    }

    @Test
    fun fromRpc_defaultsToEmptyMapWhenMapMissing() {
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.SUBMIT, arrayOf<Any?>(null, "source"))

        val call = Submit.fromRpc(rpc)

        assertNull(call.targetIp)
        assertEquals("source", call.sourceIp)
        assertTrue(call.parameters.isEmpty())
    }

    @Test
    fun toRfc_serializesFunctionAndParameters() {
        val parameters = hashMapOf<Any?, Any?>("packetid" to 123)
        val call = Submit("target", "source", parameters)

        val rpc = call.toRfc()

        assertEquals(com.hackwars.rpc.GameCommandWires.SUBMIT, rpc.function)
        assertArrayEquals(arrayOf("target", "source", parameters), rpc.parameters as Array<*>)
    }

    @Test
    fun fromRpc_throwsWhenRequiredSourceIpMissing() {
        val rpc = RemoteFunctionCall(1, com.hackwars.rpc.GameCommandWires.SUBMIT, arrayOf<Any?>(null))

        val ex = assertThrows(IllegalParameterException::class.java) {
            Submit.fromRpc(rpc)
        }

        assertEquals(com.hackwars.rpc.GameCommandWires.SUBMIT, ex.function)
        assertEquals(1, ex.position)
        assertEquals(String::class.java.name, ex.expectedType)
        assertNull(ex.actualType)
    }
}
