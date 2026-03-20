package com.hackwars.game.functions

import org.junit.Test
import org.mockito.kotlin.verify

class DeleteLogsTest {
    @Test
    fun execute_resetsLogs_andSendsPacket() {
        val computer = FunctionTestSupport.baseComputer()

        DeleteLogs(computer).execute(FunctionTestSupport.noArgsCommand("deletelogs"))

        verify(computer).resetLogs()
        verify(computer).sendPacket()
    }
}
