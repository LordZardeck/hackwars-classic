package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.verify

class RedirectXPTest {
    @Test
    fun execute_integerCommodityInput_usesCommodityTable_andSendsDamagePacket() {
        val computer = FunctionTestSupport.baseComputer()
        computer.stats["Redirecting"] = 5f

        RedirectXP(computer).execute(ApplicationData("redirectxp", 0, 0, "source"))

        assertEquals(5f + Computer.commodityXP[0], computer.stats["Redirecting"])
        verify(computer).sendDamagePacket()
    }
}
