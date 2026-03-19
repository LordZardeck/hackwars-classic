package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

/**
 * Represents a function that sets the peeked code payload in packet assignment.
 *
 * This function sends a packet and updates `PacketAssignment.peakCode` with the
 * map supplied through application parameters.
 *
 * @constructor Initializes `SetCode` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class SetCode(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        computer.sendPacket()

        @Suppress("UNCHECKED_CAST")
        val peakCode = applicationData.parameters as? HashMap<Any, Any> ?: return
        computer.packetAssignment.peakCode = peakCode
    }
}
