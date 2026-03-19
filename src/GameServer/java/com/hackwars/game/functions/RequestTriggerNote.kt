package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

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
        val parameters = applicationData.parameters as? Array<*> ?: return
        val watchNote = parameters.getOrNull(0) as? String ?: return
        @Suppress("UNCHECKED_CAST")
        val triggerParameters = parameters.getOrNull(1) as? HashMap<Any, Any>
        val targetIp = parameters.getOrNull(2) as? String ?: return

        computer.watchHandler.triggerWatch(watchNote, targetIp, triggerParameters)
    }
}
