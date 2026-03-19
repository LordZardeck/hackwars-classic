package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

/**
 * Represents a function that requests a full FTP directory refresh.
 *
 * This function marks both primary and secondary FTP directory requests in the
 * packet assignment and then sends a packet so the client pulls fresh directory data.
 *
 * @constructor Initializes `RequestFTPUpdate` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class RequestFTPUpdate(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val packetAssignment = computer.packetAssignment
        packetAssignment.setRequestPrimary(true, 8)
        packetAssignment.setRequestSecondary(true, 8)
        computer.sendPacket()
    }
}
