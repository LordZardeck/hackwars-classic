package com.hackwars.game.functions

import game.ApplicationData
import game.Watch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.hackwars.rpc.FetchWatches

class ChangeWatchPortTest {
    @Test
    fun execute_updatesWatchPort_andDispatchesFetchWatches() {
        val computer = FunctionTestSupport.baseComputer("1.2.3.4")
        val watchHandler = computer.watchHandler
        val networkSwitch = computer.computerHandler
        val watch = mock<Watch>()
        whenever(watchHandler.watches).thenReturn(arrayListOf<Any>(Any()))
        whenever(watchHandler.getWatch(0)).thenReturn(watch)

        ChangeWatchPort(computer).execute(
            ApplicationData("changewatchport", arrayOf<Any>(0, 443), 0, "source")
        )

        verify(watch).setPort(443)
        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch).addData(appCaptor.capture(), eq("1.2.3.4"))
        assertEquals(FetchWatches.FUNCTION, appCaptor.firstValue.function)
    }
}
