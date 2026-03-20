package com.hackwars.game.functions

import org.junit.Test
import org.mockito.kotlin.verify

class RequestFTPUpdateTest {
    @Test
    fun execute_setsPacketAssignmentFlags_andSendsPacket() {
        val computer = FunctionTestSupport.baseComputer()
        val packetAssignment = computer.packetAssignment

        RequestFTPUpdate(computer).execute(FunctionTestSupport.noArgsCommand("requestftpupdate"))

        verify(packetAssignment).setRequestPrimary(true, 8)
        verify(packetAssignment).setRequestSecondary(true, 8)
        verify(computer).sendPacket()
    }
}
