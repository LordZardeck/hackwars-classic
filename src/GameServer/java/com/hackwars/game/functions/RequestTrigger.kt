package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.payload.TriggerWatchByIndexPayload
import game.payloadAs

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
        val payload = applicationData.payloadAs<TriggerWatchByIndexPayload>()
        computer.watchHandler.triggerWatch(payload.watchNumber, payload.targetIp, payload.triggerParameters)
    }
}
