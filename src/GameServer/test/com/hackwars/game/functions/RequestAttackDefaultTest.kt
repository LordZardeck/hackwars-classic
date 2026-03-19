package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RequestAttackDefaultTest {
    @Test
    fun execute_dispatchesRequestattackWithDefaultBankPort() {
        val computer = FunctionTestSupport.baseComputer("4.4.4.4")
        val networkSwitch = computer.computerHandler
        whenever(computer.defaultBank).thenReturn(33)

        RequestAttackDefault(computer).execute(
            ApplicationData("requestattackdefault", arrayOf<Any>("ignored", "Bank"), 12, "7.7.7.7")
        )

        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch).addData(appCaptor.capture(), eq("7.7.7.7"))
        val dispatched = appCaptor.firstValue
        assertEquals("requestattack", dispatched.function)
        assertEquals(12, dispatched.port)
        val payload = dispatched.parameters as Array<*>
        assertEquals("4.4.4.4", payload[0])
        assertEquals(33, payload[1])
    }
}
