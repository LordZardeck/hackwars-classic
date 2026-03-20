package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.payload.LaunchNetworkAttackPayload
import game.payload.TriggerWatchByNotePayload
import game.payloadAs
import hackscript.model.TypeInteger
import hackscript.model.TypeString
import hackscript.model.Variable

/**
 * Represents a function that requests an NPC-driven scripted network attack.
 *
 * This function constructs trigger parameters describing the current player's defaults and:
 *
 * - Reads the target NPC IP from positional parameters.
 * - Builds Hackscript-compatible trigger variables.
 * - Packages a `"netbomb"` trigger payload.
 * - Dispatches a `requesttriggernote` application data event to the NPC.
 *
 * @constructor Initializes `LaunchNetworkAttack` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class LaunchNetworkAttack(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val payload = applicationData.payloadAs<LaunchNetworkAttackPayload>()
        val npcIp = payload.npcIp
        val triggerParameters = HashMap<String, Variable>()

        triggerParameters["playerip"] = TypeString(computer.getIP())
        triggerParameters["defaultattack"] = TypeInteger(computer.getDefaultAttack())
        triggerParameters["defaultbank"] = TypeInteger(computer.getDefaultBank())
        triggerParameters["defaulthttp"] = TypeInteger(computer.getDefaultHTTP())
        triggerParameters["defaultredirecting"] = TypeInteger(computer.getDefaultShipping())

        println("Launching attack with NPC $npcIp against ${computer.getIP()}.")
        computer.computerHandler.addData(
            ApplicationData(
                TriggerWatchByNotePayload("netbomb", HashMap(triggerParameters as Map<Any, Any>), computer.getIP()),
                0,
                computer.getIP()
            ),
            npcIp
        )
    }
}
