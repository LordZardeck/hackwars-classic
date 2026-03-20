package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.MessageHandler
import game.payload.StringCommandPayload
import game.payloadAs

/**
 * Represents a function that creates a folder or directory in the file system of a computer.
 *
 * This function interacts with the computer's file system to add a new directory using the
 * specified parameters provided in the application data. If the directory creation fails,
 * a failure message is recorded. It also sends a packet to notify the system about
 * the change and sets a request for a primary assignment.
 *
 * @constructor Initializes the `CreateFolder` function with the specified computer.
 *
 * @param computer The `Computer` instance on which the function operates.
 */
class CreateFolder(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val payload = applicationData.payloadAs<StringCommandPayload>()
        computer.sendPacket()

        val directory = payload.value
        if (!computer.fileSystem.addDirectory(directory))
            computer.addMessage(MessageHandler.SAVE_FAIL_HD_FULL)

        computer.packetAssignment.setRequestPrimary(true, 1)
    }
}
