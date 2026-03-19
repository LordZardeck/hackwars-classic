package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HttpXPTest {
    @Test
    fun execute_votePath_updatesVoteCountAndWebdesignStat() {
        val computer = FunctionTestSupport.baseComputer()
        whenever(computer.checkHTTP()).thenReturn(true)
        whenever(computer.voteCount).thenReturn(3)
        computer.stats["Webdesign"] = 10f

        HttpXP(computer).execute(ApplicationData("httpxp", 500.7337f, 0, "source"))

        verify(computer).setVoteCount(4)
        verify(computer).sendDamagePacket()
        assertEquals(510f, computer.stats["Webdesign"])
    }
}
