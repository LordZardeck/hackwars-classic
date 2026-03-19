package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Test
import org.mockito.kotlin.verify

class DeleteLogsTest {
    @Test
    fun execute_resetsLogs_andSendsPacket() {
        val computer = FunctionTestSupport.baseComputer()

        DeleteLogs(computer).execute(ApplicationData("deletelogs", null, 0, "source"))

        verify(computer).resetLogs()
        verify(computer).sendPacket()
    }
}
