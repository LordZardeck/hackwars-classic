package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.payload.AddShowChoicesPayload
import game.payloadAs

/**
 * Represents a function that adds a new set of choices to the computer system's "show choices" array.
 * This function operates within the context of the provided application data and modifies the
 * internal state of the associated computer by:
 *
 * - Sending a generic packet to notify other components.
 * - Sending a damage packet to reflect some form of state change.
 * - Adding the application's parameters to the "show choices" array.
 *
 * @param computer The computer instance on which this function operates.
 */
class AddShowChoices(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val payload = applicationData.payloadAs<AddShowChoicesPayload>()
        computer.sendPacket()
        computer.sendDamagePacket()
        computer.showChoicesArray.add(payload.choices)
    }
}
