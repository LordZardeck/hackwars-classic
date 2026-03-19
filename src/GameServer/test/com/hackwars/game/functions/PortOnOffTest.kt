package com.hackwars.game.functions

import game.ApplicationData
import game.MessageHandler
import game.Port
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PortOnOffTest {
    @Test
    fun execute_whenTurningOffUnderAttack_addsFailureMessage_andFetchesPorts() {
        val computer = FunctionTestSupport.baseComputer("3.3.3.3")
        val networkSwitch = computer.computerHandler
        val port = mock<Port>()
        whenever(port.number).thenReturn(8080)
        whenever(port.on).thenReturn(true)
        whenever(port.accessing).thenReturn("attacker")
        whenever(computer.ports).thenReturn(hashMapOf(8080 to port))

        PortOnOff(computer).execute(ApplicationData("portonoff", false, 8080, "source"))

        verify(computer).addMessage(MessageHandler.PORT_OFF_FAIL_UNDER_ATTACK)
        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch).addData(appCaptor.capture(), eq("3.3.3.3"))
        assertEquals("fetchports", appCaptor.firstValue.function)
    }
}
