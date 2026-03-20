package com.hackwars.game.functions

import game.ApplicationData
import game.MessageHandler
import org.junit.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CreateFolderTest {
    @Test
    fun execute_whenAddFails_addsHdFullMessage_andRequestsPrimaryDirectory() {
        val computer = FunctionTestSupport.baseComputer()
        val fileSystem = computer.fileSystem
        val packetAssignment = computer.packetAssignment
        whenever(fileSystem.addDirectory("/tmp/new")).thenReturn(false)

        CreateFolder(computer).execute(FunctionTestSupport.stringCommand("createfolder", "/tmp/new"))

        verify(computer).sendPacket()
        verify(computer).addMessage(MessageHandler.SAVE_FAIL_HD_FULL)
        verify(packetAssignment).setRequestPrimary(true, 1)
    }
}
