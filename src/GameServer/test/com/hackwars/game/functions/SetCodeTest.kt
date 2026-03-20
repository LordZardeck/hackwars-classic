package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Test
import org.mockito.kotlin.verify

class SetCodeTest {
    @Test
    fun execute_setsPeakCode_andSendsPacket() {
        val computer = FunctionTestSupport.baseComputer()
        val packetAssignment = computer.packetAssignment
        val peakCode = hashMapOf<Any, Any>("line" to "print(\"hi\")")

        SetCode(computer).execute(FunctionTestSupport.mapCommand("code", peakCode))

        verify(computer).sendPacket()
        verify(packetAssignment).setPeakCode(peakCode)
    }
}
