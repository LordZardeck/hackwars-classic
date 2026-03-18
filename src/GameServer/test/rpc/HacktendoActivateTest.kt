package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class HacktendoActivateTest {
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
        val params = arrayOf<Any?>(42, 42, null)
        val rpc = RemoteFunctionCall(1, HacktendoActivate.FUNCTION, params)

        val call = HacktendoActivate.fromRpc(rpc)
        assertEquals(params[0], call.activateID)
        assertEquals(params[1], call.activateType)
        assertEquals(params[2], call.ip)
        val serialized = call.toRfc()

        assertEquals(HacktendoActivate.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
