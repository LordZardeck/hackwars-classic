package com.hackwars.game.functions

import com.hackwars.rpc.RequestAttack
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
        whenever(computer.getDefaultBank()).thenReturn(33)

        RequestAttackDefault(computer).execute(FunctionTestSupport.requestAttackDefault("Bank"))

        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch).addData(appCaptor.capture(), eq("7.7.7.7"))
        val dispatched = appCaptor.firstValue
        assertEquals("requestattack", dispatched.command.wireName())
        assertEquals(12, dispatched.port)
        val payload = dispatched.payload as RequestAttack
        assertEquals("4.4.4.4", payload.sourceIP)
        assertEquals(33, payload.sourcePort)
    }
}
