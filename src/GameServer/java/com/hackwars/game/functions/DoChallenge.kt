package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.payload.DoChallengePayload
import game.payloadAs

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
        val payload = applicationData.payloadAs<DoChallengePayload>()

        computer.doChallengeRPC(payload.challengeId, payload.challengeFile)
        computer.sendPacket()
    }
}
