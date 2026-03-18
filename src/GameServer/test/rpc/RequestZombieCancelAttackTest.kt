package rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class RequestZombieCancelAttackTest {
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
        val params = arrayOf<Any?>(null, 42, "value")
        val rpc = RemoteFunctionCall(1, RequestZombieCancelAttack.FUNCTION, params)

        val call = RequestZombieCancelAttack.fromRpc(rpc)
        assertEquals(params[0], call.ip)
        assertEquals(params[1], call.port)
        assertEquals(params[2], call.targetIP)
        val serialized = call.toRfc()

        assertEquals(RequestZombieCancelAttack.FUNCTION, serialized.function)
        assertArrayEquals(params, serialized.parameters as Array<*>)
    }
}
