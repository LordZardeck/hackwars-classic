package com.hackwars.game.functions

import assignments.PacketPort
import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

class SetDefaultPortTest {
    @Test
    fun execute_setsBankDefault_andRequestsPortRefresh() {
        val computer = FunctionTestSupport.baseComputer("7.7.7.7")
        val networkSwitch = computer.computerHandler

        SetDefaultPort(computer).execute(
            ApplicationData("setdefaultport", PacketPort.BANKING, 45, "source")
        )

        verify(computer).setDefaultBank(45)
        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch).addData(appCaptor.capture(), eq("7.7.7.7"))
        assertEquals("fetchports", appCaptor.firstValue.function)
    }
}
