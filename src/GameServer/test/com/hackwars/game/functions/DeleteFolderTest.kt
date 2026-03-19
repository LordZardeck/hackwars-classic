package com.hackwars.game.functions

import game.ApplicationData
import game.MessageHandler
import org.junit.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DeleteFolderTest {
    @Test
    fun execute_whenDeleteFails_addsFolderNotEmptyMessage_andRequestsPrimaryDirectory() {
        val computer = FunctionTestSupport.baseComputer()
        val fileSystem = computer.fileSystem
        val packetAssignment = computer.packetAssignment
        whenever(fileSystem.deleteDirectory("/tmp/non-empty")).thenReturn(false)

        DeleteFolder(computer).execute(
            ApplicationData("deletefolder", "/tmp/non-empty", 0, "source")
        )

        verify(computer).sendPacket()
        verify(computer).addMessage(MessageHandler.DELETE_FAIL_NON_EMPTY_FOLDER)
        verify(packetAssignment).setRequestPrimary(true, 1)
    }
}
