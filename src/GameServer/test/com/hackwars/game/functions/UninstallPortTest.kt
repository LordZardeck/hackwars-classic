package com.hackwars.game.functions

import game.ApplicationData
import game.Port
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UninstallPortTest {
    @Test
    fun execute_removesEligiblePort_andRequestsPortRefresh() {
        val computer = FunctionTestSupport.baseComputer("9.9.9.9")
        val networkSwitch = computer.computerHandler
        whenever(computer.cpuLoad).thenReturn(0f)
        val port = mock<Port>()
        whenever(port.number).thenReturn(12)
        whenever(port.accessing).thenReturn("")
        whenever(port.attacking).thenReturn(false)
        whenever(port.overHeated).thenReturn(false)
        val ports = hashMapOf<Any?, Any?>(12 to port)
        whenever(computer.ports).thenReturn(ports)

        UninstallPort(computer).execute(FunctionTestSupport.intCommand("uninstallport", 12))

        assertFalse(ports.containsKey(12))
        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch).addData(appCaptor.capture(), eq("9.9.9.9"))
        assertEquals("fetchports", appCaptor.firstValue.command.wireName())
    }
}
