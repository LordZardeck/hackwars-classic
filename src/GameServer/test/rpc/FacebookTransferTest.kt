package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class FacebookTransferTest {
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
        val params = arrayOf<Any?>(null, null, 1.5f, 42)
        val rpc = RemoteFunctionCall(1, FacebookTransfer.FUNCTION, params)

        val call = FacebookTransfer.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.ip2)
        assertEquals(params[2], call.amount)
        assertEquals(params[3], call.defaultPort)
        val serialized = call.toRfc()

        assertEquals(FacebookTransfer.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
