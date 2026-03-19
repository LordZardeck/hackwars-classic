package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionTest {
    @Test
    fun getPositionalParameter_readsTypedValues_andThrowsForBadType() {
        val data = ApplicationData("fn", arrayOf<Any>("ok", 7), 0, "source")

        assertEquals("ok", Function.getPositionalParameter<String>(data, 0))
        assertEquals(7, Function.getPositionalParameter<Int>(data, 1))

        val ex = runCatching { Function.getPositionalParameter<Int>(data, 0) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is IllegalParameterException)
        assertEquals(0, (ex as IllegalParameterException).position)
        assertEquals(Int::class.javaObjectType.name, ex.expectedType)
    }

    @Test
    fun getPositionalParameter_nullableType_returnsNullWhenMissing() {
        val data = ApplicationData("fn", arrayOf<Any>("only"), 0, "source")

        val value: String? = Function.getPositionalParameter(data, 2)

        assertNull(value)
    }
}
