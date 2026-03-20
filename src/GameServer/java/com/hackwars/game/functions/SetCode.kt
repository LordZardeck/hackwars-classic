package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.payload.MapCommandPayload
import game.payloadAs

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

        computer.packetAssignment.peakCode = applicationData.payloadAs<MapCommandPayload>().values
    }
}
