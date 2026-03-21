package com.hackwars.game.functions

import game.ApplicationData
import game.Watch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.*

class ChangeWatchPortTest {
    @Test
    fun execute_updatesWatchPort_andDispatchesFetchWatches() {
        val computer = FunctionTestSupport.baseComputer("1.2.3.4")
        val watchHandler = computer.watchHandler
        val networkSwitch = computer.computerHandler
        val watch = mock<Watch>()
        whenever(watchHandler.watches).thenReturn(arrayListOf(Any()))
        whenever(watchHandler.getWatch(0)).thenReturn(watch)

        ChangeWatchPort(computer).execute(FunctionTestSupport.changeWatchPort(0, 443))

        verify(watch).port = 443
        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch).addData(appCaptor.capture(), eq("1.2.3.4"))
        assertEquals(com.hackwars.rpc.GameCommandWires.FETCHWATCHES, appCaptor.firstValue.command.wireName())
    }
}
