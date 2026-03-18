package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class ExitTest {
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
        val params = arrayOf<Any?>(null, "value")
        val rpc = RemoteFunctionCall(1, Exit.FUNCTION, params)

        val call = Exit.fromRpc(rpc)
        assertEquals(params[0], call.targetIp)
        assertEquals(params[1], call.sourceIp)
        val serialized = call.toRfc()

        assertEquals(Exit.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
