package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class RequestCancelAttackTest {
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
        val params = arrayOf<Any?>("value", 42)
        val rpc = RemoteFunctionCall(1, RequestCancelAttack.FUNCTION, params)

        val call = RequestCancelAttack.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.port)
        val serialized = call.toRfc()

        assertEquals(RequestCancelAttack.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
