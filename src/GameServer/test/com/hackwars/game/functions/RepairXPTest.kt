package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.verify

class RepairXPTest {
    @Test
    fun execute_updatesRepairStat_andSendsDamagePacket() {
        val computer = FunctionTestSupport.baseComputer()
        computer.stats["Repair"] = 8f

        RepairXP(computer).execute(ApplicationData("repairxp", 7f, 0, "source"))

        assertEquals(15f, computer.stats["Repair"])
        verify(computer).sendDamagePacket()
    }
}
