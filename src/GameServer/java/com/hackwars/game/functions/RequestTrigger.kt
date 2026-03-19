package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

/**
 * Represents a function that explicitly fires a watch by index.
 *
 * This function extracts the watch index, trigger parameter map, and target IP from
 * application data, then forwards them to the computer's watch handler.
 *
 * @constructor Initializes `RequestTrigger` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class RequestTrigger(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val parameters = applicationData.parameters as? Array<*> ?: return
        val watchNumber = parameters.getOrNull(0) as? Int ?: return
        @Suppress("UNCHECKED_CAST")
        val triggerParameters = parameters.getOrNull(1) as? HashMap<Any, Any>
        val targetIp = parameters.getOrNull(2) as? String ?: return

        computer.watchHandler.triggerWatch(watchNumber, targetIp, triggerParameters)
    }
}
