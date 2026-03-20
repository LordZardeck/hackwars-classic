package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.payloadAs
import com.hackwars.rpc.ChangeWatchPort as ChangeWatchPortPayload
import com.hackwars.rpc.FetchWatches

/**
 * Represents a function to change the port of a specific watch on the computer.
 *
 * This function updates the port for a specified watch identified by its index.
 * If the specified watch exists within the valid range of the computer's watch handler,
 * the port is updated to the new value. Additionally, it triggers the handling of
 * updated watch information by passing appropriate application data to the computer's handler.
 *
 * @constructor Initializes the `ChangeWatchPort` function with the specified computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class ChangeWatchPort(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val payload = applicationData.payloadAs<ChangeWatchPortPayload>()
        val targetWatch = payload.watchId ?: return
        val newPort = payload.portId ?: return

        if (targetWatch < computer.watchHandler.watches.size) {
            computer.watchHandler.getWatch(targetWatch)?.port = newPort
            computer.computerHandler.addData(
                ApplicationData(FetchWatches(computer.getIP()), 0, computer.getIP()),
                computer.getIP()
            )
        }
    }
}
