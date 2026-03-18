package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        val rpc = RemoteFunctionCall(1, HealPort.FUNCTION, arrayOf<Any?>("encrypted-ip", 21))

        val call = HealPort.fromRpc(rpc)

        assertEquals("encrypted-ip", call.encryptedIp)
        assertEquals(21, call.port)
    }

    @Test
    fun toRfc_serializesFunctionAndParameters() {
        val call = HealPort("encrypted-ip", 21)

        val rpc = call.toRfc()

        assertEquals(HealPort.FUNCTION, rpc.function)
        assertArrayEquals(arrayOf("encrypted-ip", 21), rpc.parameters as Array<*>)
    }

    @Test
    fun fromRpc_throwsWhenPortHasWrongType() {
        val rpc = RemoteFunctionCall(1, HealPort.FUNCTION, arrayOf<Any?>("encrypted-ip", "21"))

        val ex = assertThrows(IllegalParameterException::class.java) {
            HealPort.fromRpc(rpc)
        }

        assertEquals(HealPort.FUNCTION, ex.function)
        assertEquals(1, ex.position)
        assertEquals(Int::class.javaObjectType.name, ex.expectedType)
        assertEquals(String::class.java.name, ex.actualType)
    }
}
