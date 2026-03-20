package com.hackwars.game.functions

import game.ApplicationData
import game.payload.CombatScanXpPayload
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.verify

class ScanXPTest {
    @Test
    fun execute_updatesScanningStat_andSendsDamagePacket() {
        val computer = FunctionTestSupport.baseComputer()
        computer.stats["Scanning"] = 100f

        ScanXP(computer).execute(FunctionTestSupport.floatCommand("scanxp", 4f))

        verify(computer).sendDamagePacket()
        assertEquals(104f, computer.stats["Scanning"])
    }

    @Test
    fun execute_acceptsCombatScanXpPayload() {
        val computer = FunctionTestSupport.baseComputer()
        computer.stats["Scanning"] = 100f

        ScanXP(computer).execute(ApplicationData(CombatScanXpPayload(4f), 0, "source"))

        verify(computer).sendDamagePacket()
        assertEquals(104f, computer.stats["Scanning"])
    }
}
