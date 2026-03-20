package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.verify

class AddShowChoicesTest {
    @Test
    fun execute_sendsPackets_andAppendsParameters() {
        val computer = FunctionTestSupport.baseComputer()
        val data = FunctionTestSupport.addShowChoices("a", 1)

        AddShowChoices(computer).execute(data)

        verify(computer).sendPacket()
        verify(computer).sendDamagePacket()
        assertEquals(1, computer.showChoicesArray.size)
        assertArrayEquals(arrayOf<Any>("a", 1), computer.showChoicesArray[0] as Array<*>)
    }
}
