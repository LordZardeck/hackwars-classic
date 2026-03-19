package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.MessageHandler

/**
 * Represents a function that removes a directory from the computer's file system.
 *
 * This function processes the directory path from the application data and:
 *
 * - Sends a packet so the client can refresh state.
 * - Attempts to delete the requested directory.
 * - Adds an error message if the directory cannot be deleted (for example, it is not empty).
 * - Flags a primary directory refresh request in the packet assignment.
 *
 * @constructor Initializes `DeleteFolder` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class DeleteFolder(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        computer.sendPacket()

        val directory = applicationData.parameters as String?
        if (!computer.fileSystem.deleteDirectory(directory))
            computer.addMessage(MessageHandler.DELETE_FAIL_NON_EMPTY_FOLDER)

        computer.packetAssignment.setRequestPrimary(true, 1)
    }
}
