package com.hackwars.rpc

import assignments.RemoteFunctionCall
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import util.Encryption

class RemoteFunctionCallParsingTest {
    private data class TestRequiredParameters(val a: String, val b: Int, val c: HashMap<*, *>) :
        RemoteFunctionCallImpl() {
        companion object {
            const val FUNCTION = "test_required_parameters"
            fun fromRpc(rfc: RemoteFunctionCall): TestRequiredParameters {
                return TestRequiredParameters(
                    getPositionalParameter<String>(rfc, 0),
                    getPositionalParameter<Int>(rfc, 1),
                    getPositionalParameter<HashMap<*, *>>(rfc, 2)
                )
            }
        }

        override val function = FUNCTION
        override fun toRfc() = RemoteFunctionCall(0, function, arrayOf<Any?>(a, b, c))
    }

    private data class TestOptionalParameters(val a: String?, val b: Int?, val c: HashMap<*, *>?) :
        RemoteFunctionCallImpl() {
        companion object {
            const val FUNCTION = "test_optional_parameters"
            fun fromRpc(rfc: RemoteFunctionCall): TestOptionalParameters {
                return TestOptionalParameters(
                    getPositionalParameter<String?>(rfc, 0),
                    getPositionalParameter<Int?>(rfc, 1),
                    getPositionalParameter<HashMap<*, *>?>(rfc, 2)
                )
            }
        }

        override val function = FUNCTION
        override fun toRfc() = RemoteFunctionCall(0, function, arrayOf<Any?>(a, b, c))
    }

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
    fun fromRpc_readsRequiredAndOptionalParameters() {
        val rpc = RemoteFunctionCall(1, TestOptionalParameters.FUNCTION, arrayOf<Any?>("encrypted-ip", 44, null))
        val call = TestOptionalParameters.fromRpc(rpc)

        assertEquals("encrypted-ip", call.a)
        assertEquals(44, call.b)
        assertEquals(null, call.c)
    }

    @Test
    fun fromRpc_nullWhenOptionalPositionIsMissing() {
        val rpc = RemoteFunctionCall(1, TestOptionalParameters.FUNCTION, arrayOf<Any?>("encrypted-ip", 44))
        val call = TestOptionalParameters.fromRpc(rpc)

        assertEquals("encrypted-ip", call.a)
        assertEquals(44, call.b)
        assertEquals(null, call.c)
    }

    @Test
    fun toRfc_serializesNullableParameter() {
        val call = TestOptionalParameters("encrypted-ip", 44, null)
        val rpc = call.toRfc()

        assertEquals(TestOptionalParameters.FUNCTION, rpc.function)
        assertArrayEquals(arrayOf("encrypted-ip", 44, null), rpc.parameters as Array<*>)
    }

    @Test
    fun fromRpc_readsRequiredStringAndInt() {
        val rpc = RemoteFunctionCall(
            1,
            TestRequiredParameters.FUNCTION,
            arrayOf<Any?>("encrypted-ip", 21, HashMap<Any, Any>())
        )
        val call = TestRequiredParameters.fromRpc(rpc)

        assertEquals("encrypted-ip", call.a)
        assertEquals(21, call.b)
    }

    @Test
    fun fromRpc_throwsWhenRequiredMissing() {
        val rpc = RemoteFunctionCall(
            1,
            TestRequiredParameters.FUNCTION,
            arrayOf<Any?>("encrypted-ip")
        )
        val ex = assertThrows(IllegalParameterException::class.java) {
            TestRequiredParameters.fromRpc(rpc)
        }

        assertEquals(TestRequiredParameters.FUNCTION, ex.function)
        assertEquals(1, ex.position)
        assertEquals(Int::class.javaObjectType.name, ex.expectedType)
    }

    @Test
    fun fromRpc_readsMapParameterWhenProvided() {
        val rpc = RemoteFunctionCall(
            1,
            TestRequiredParameters.FUNCTION,
            arrayOf("target", 21, hashMapOf<Any?, Any?>("q" to "search"))
        )
        val call = TestRequiredParameters.fromRpc(rpc)

        assertEquals("search", call.c["q"])
    }

    @Test
    fun toRfc_serializesAllPositionalParameters() {
        val map = HashMap<Any?, Any?>()
        map["packetid"] = 123

        val call = TestRequiredParameters("target", 21, map)
        val rpc = call.toRfc()

        assertEquals(TestRequiredParameters.FUNCTION, rpc.function)
        assertArrayEquals(arrayOf("target", 21, map), rpc.parameters as Array<*>)
    }
}
