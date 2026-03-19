package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

/**
 * Represents a function that launches a challenge run from script-provided parameters.
 *
 * The function expects positional parameters in the application payload and:
 *
 * - Reads the challenge file identifier.
 * - Reads the challenge id.
 * - Invokes `Computer.doChallengeRPC` with those values.
 * - Sends a packet to update client-visible state.
 *
 * @constructor Initializes `DoChallenge` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class DoChallenge(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val challengeParameters = applicationData.parameters as? Array<*> ?: return
        val challengeId = challengeParameters.getOrNull(1) as? String ?: return
        val challengeFile = challengeParameters.getOrNull(0) as? String ?: return

        computer.doChallengeRPC(challengeId, challengeFile)
        computer.sendPacket()
    }
}
