package com.hackwars.game.functions

import game.ApplicationData
import game.Port
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SetDummyPortTest {
    @Test
    fun execute_setsDummyFlag_whenPortIsEligible() {
        val computer = FunctionTestSupport.baseComputer("8.8.8.8")
        val networkSwitch = computer.computerHandler
        val port = mock<Port>()
        whenever(port.attacking).thenReturn(false)
        whenever(port.accessing).thenReturn("")
        whenever(port.overHeated).thenReturn(false)
        whenever(computer.ports).thenReturn(hashMapOf(31 to port))

        SetDummyPort(computer).execute(FunctionTestSupport.booleanCommand("setdummyport", true, 31))

        verify(port).setDummy(true)
        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch).addData(appCaptor.capture(), eq("8.8.8.8"))
        assertEquals("fetchports", appCaptor.firstValue.command.wireName())
    }
}
