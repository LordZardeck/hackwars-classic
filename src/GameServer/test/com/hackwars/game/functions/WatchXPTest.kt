package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.verify

class WatchXPTest {
    @Test
    fun execute_updatesWatchStat_andSendsDamagePacket() {
        val computer = FunctionTestSupport.baseComputer()
        computer.stats["Watch"] = 25f

        WatchXP(computer).execute(ApplicationData("watchxp", 10f, 0, "source"))

        assertEquals(35f, computer.stats["Watch"])
        verify(computer).sendDamagePacket()
    }
}
