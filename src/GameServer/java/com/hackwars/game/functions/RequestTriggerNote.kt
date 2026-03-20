package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.payload.TriggerWatchByNotePayload
import game.payloadAs

/**
 * Represents a function that explicitly fires watches by note value.
 *
 * This function extracts the note, trigger parameter map, and target IP from
 * application data, then forwards them to the computer's watch handler.
 *
 * @constructor Initializes `RequestTriggerNote` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class RequestTriggerNote(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val payload = applicationData.payloadAs<TriggerWatchByNotePayload>()
        computer.watchHandler.triggerWatch(payload.watchNote, payload.targetIp, payload.triggerParameters)
    }
}
