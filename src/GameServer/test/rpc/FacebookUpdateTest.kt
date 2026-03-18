package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class FacebookUpdateTest {
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
        val params = arrayOf<Any?>(null)
        val rpc = RemoteFunctionCall(1, FacebookUpdate.FUNCTION, params)

        val call = FacebookUpdate.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        val serialized = call.toRfc()

        assertEquals(FacebookUpdate.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
