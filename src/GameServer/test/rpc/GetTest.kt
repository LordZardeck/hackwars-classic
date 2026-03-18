package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class GetTest {
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
        val params = arrayOf<Any?>(null, 42, null, null, null, "value", null, null)
        val rpc = RemoteFunctionCall(1, Get.FUNCTION, params)

        val call = Get.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.port)
        assertEquals(params[2], call.name)
        assertEquals(params[3], call.fetchPath)
        assertEquals(params[4], call.putPath)
        assertEquals(params[5], call.targetIP)
        assertEquals(params[6], call.password)
        assertEquals(params[7], call.quantity)
        val serialized = call.toRfc()

        assertEquals(Get.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
