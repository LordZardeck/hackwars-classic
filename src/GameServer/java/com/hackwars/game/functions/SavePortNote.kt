package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.Port

/**
 * Represents a function that updates the note attached to a specific port.
 *
 * This function finds the port matching the application target port and applies
 * the provided note text to that port.
 *
 * @constructor Initializes `SavePortNote` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class SavePortNote(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val targetPort = applicationData.port
        val note = applicationData.parameters as? String ?: return

        for (portEntry in computer.ports.values) {
            val port = portEntry as? Port ?: continue
            if (port.number == targetPort) {
                port.note = note
            }
        }
    }
}
