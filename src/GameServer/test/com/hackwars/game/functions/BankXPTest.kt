package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.verify

class BankXPTest {
    @Test
    fun execute_addsToBankStat_andSendsDamagePacket() {
        val computer = FunctionTestSupport.baseComputer()
        computer.stats["Bank"] = 20f

        BankXP(computer).execute(FunctionTestSupport.floatCommand("bankxp", 5f))

        assertEquals(25f, computer.stats["Bank"])
        verify(computer).sendDamagePacket()
    }
}
