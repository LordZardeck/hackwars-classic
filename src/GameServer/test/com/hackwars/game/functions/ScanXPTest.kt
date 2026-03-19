package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.verify

class ScanXPTest {
    @Test
    fun execute_updatesScanningStat_andSendsDamagePacket() {
        val computer = FunctionTestSupport.baseComputer()
        computer.stats["Scanning"] = 100f

        ScanXP(computer).execute(ApplicationData("scanxp", 4f, 0, "source"))

        verify(computer).sendDamagePacket()
        assertEquals(104f, computer.stats["Scanning"])
    }
}
