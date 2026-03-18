package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class InstallWatchTest {
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
        val params = arrayOf<Any?>("value", null, null, 42, 42)
        val rpc = RemoteFunctionCall(1, InstallWatch.FUNCTION, params)

        val call = InstallWatch.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.path)
        assertEquals(params[2], call.name)
        assertEquals(params[3], call.type)
        assertEquals(params[4], call.port)
        val serialized = call.toRfc()

        assertEquals(InstallWatch.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
