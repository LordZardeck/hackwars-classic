package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class ChangeWatchPortTest {
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
        val params = arrayOf<Any?>("value", null, null)
        val rpc = RemoteFunctionCall(1, ChangeWatchPort.FUNCTION, params)

        val call = ChangeWatchPort.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.watchId)
        assertEquals(params[2], call.portId)
        val serialized = call.toRfc()

        assertEquals(ChangeWatchPort.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
