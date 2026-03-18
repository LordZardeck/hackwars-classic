package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class RequestPurchaseTest {
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
        val params = arrayOf<Any?>("value", "value", null, null)
        val rpc = RemoteFunctionCall(1, RequestPurchase.FUNCTION, params)

        val call = RequestPurchase.fromRpc(rpc)
        assertEquals(params[0], call.targetIp)
        assertEquals(params[1], call.sourceIp)
        assertEquals(params[2], call.fileName)
        assertEquals(params[3], call.quantity)
        val serialized = call.toRfc()

        assertEquals(RequestPurchase.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
