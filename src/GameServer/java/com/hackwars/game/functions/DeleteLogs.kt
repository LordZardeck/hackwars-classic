package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

/**
 * Represents a function that clears the player's log history.
 *
 * This function performs two immediate actions:
 *
 * - Resets stored log entries on the computer.
 * - Sends a packet so the client receives the updated log state.
 *
 * @constructor Initializes `DeleteLogs` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class DeleteLogs(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        computer.resetLogs()
        computer.sendPacket()
    }
}
