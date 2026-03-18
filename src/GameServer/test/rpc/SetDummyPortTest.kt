package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class SetDummyPortTest {
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
        val params = arrayOf<Any?>("value", 42, null)
        val rpc = RemoteFunctionCall(1, SetDummyPort.FUNCTION, params)

        val call = SetDummyPort.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.port)
        assertEquals(params[2], call.dummy)
        val serialized = call.toRfc()

        assertEquals(SetDummyPort.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
