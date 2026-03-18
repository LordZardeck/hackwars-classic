package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
        val rpc = RemoteFunctionCall(1, Submit.FUNCTION, arrayOf<Any?>(null, "source", parameters))

        val call = Submit.fromRpc(rpc)

        assertNull(call.targetIp)
        assertEquals("source", call.sourceIp)
        assertEquals("value", call.parameters["field"])
    }

    @Test
    fun fromRpc_defaultsToEmptyMapWhenMapMissing() {
        val rpc = RemoteFunctionCall(1, Submit.FUNCTION, arrayOf<Any?>(null, "source"))

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

        assertEquals(Submit.FUNCTION, rpc.function)
        assertArrayEquals(arrayOf("target", "source", parameters), rpc.parameters as Array<*>)
    }

    @Test
    fun fromRpc_throwsWhenRequiredSourceIpMissing() {
        val rpc = RemoteFunctionCall(1, Submit.FUNCTION, arrayOf<Any?>(null))

        val ex = assertThrows(IllegalParameterException::class.java) {
            Submit.fromRpc(rpc)
        }

        assertEquals(Submit.FUNCTION, ex.function)
        assertEquals(1, ex.position)
        assertEquals(String::class.java.name, ex.expectedType)
        assertNull(ex.actualType)
    }
}
