package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class SetWatchOnOffTest {
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
        val rpc = RemoteFunctionCall(1, SetWatchOnOff.FUNCTION, params)

        val call = SetWatchOnOff.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.watchID)
        assertEquals(params[2], call.state)
        val serialized = call.toRfc()

        assertEquals(SetWatchOnOff.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
