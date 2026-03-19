package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

/**
 * Represents a function that updates the computer FTP password.
 *
 * This function reads a string parameter from application data and writes it
 * to the computer password field.
 *
 * @constructor Initializes `SetFTPPassword` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class SetFTPPassword(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        computer.setPassword(applicationData.parameters as? String)
    }
}
