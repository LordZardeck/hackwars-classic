package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RequestZombieAttackTest {
    @Test
    fun execute_whenAffordable_dispatchesPettycashAndZombieattack() {
        val computer = FunctionTestSupport.baseComputer("6.6.6.6")
        val networkSwitch = computer.computerHandler
        whenever(computer.checkBank()).thenReturn(true)
        whenever(computer.getPettyCash()).thenReturn(100f)
        val parameters = arrayOf<Any?>(null, null, null, null, null, "8.8.8.8")

        RequestZombieAttack(computer).execute(
            ApplicationData("requestzombieattack", parameters, 22, "source")
        )

        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch, times(2)).addData(appCaptor.capture(), any())
        val first = appCaptor.allValues[0]
        val second = appCaptor.allValues[1]
        assertEquals("pettycash", first.function)
        assertEquals(-20.0f, first.parameters)
        assertEquals("zombieattack", second.function)
        assertArrayEquals(parameters, second.parameters as Array<*>)
    }
}
