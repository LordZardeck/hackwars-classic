package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class SetDefaultPortTest {
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
    fun fromRpc_readsEncryptedIpPortAndOptionalType() {
        val rpc = RemoteFunctionCall(1, SetDefaultPort.FUNCTION, arrayOf<Any?>("encrypted-ip", 44, 7))

        val call = SetDefaultPort.fromRpc(rpc)

        assertEquals("encrypted-ip", call.encryptedIp)
        assertEquals(44, call.port)
        assertEquals(7, call.type)
    }

    @Test
    fun fromRpc_returnsNullTypeWhenTypeParameterMissing() {
        val rpc = RemoteFunctionCall(1, SetDefaultPort.FUNCTION, arrayOf<Any?>("encrypted-ip", 44))

        val call = SetDefaultPort.fromRpc(rpc)

        assertEquals("encrypted-ip", call.encryptedIp)
        assertEquals(44, call.port)
        assertNull(call.type)
    }

    @Test
    fun toRfc_serializesFunctionAndParameters() {
        val call = SetDefaultPort("encrypted-ip", 44, null)

        val rpc = call.toRfc()

        assertEquals(SetDefaultPort.FUNCTION, rpc.function)
        assertArrayEquals(arrayOf("encrypted-ip", 44, null), rpc.parameters as Array<*>)
    }

    @Test
    fun fromRpc_throwsWhenRequiredPortMissing() {
        val rpc = RemoteFunctionCall(1, SetDefaultPort.FUNCTION, arrayOf<Any?>("encrypted-ip"))

        val ex = assertThrows(IllegalParameterException::class.java) {
            SetDefaultPort.fromRpc(rpc)
        }

        assertEquals(SetDefaultPort.FUNCTION, ex.function)
        assertEquals(1, ex.position)
        assertEquals(Int::class.javaObjectType.name, ex.expectedType)
        assertNull(ex.actualType)
    }
}
