package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.MessageHandler
import game.payload.FloatCommandPayload
import game.payloadAs

/**
 * Represents a function that rewards a player with HTTP/Webdesign experience.
 *
 * This function handles both normal XP events and special vote events by:
 *
 * - Ignoring NPC computers.
 * - Detecting the vote sentinel value and validating HTTP availability.
 * - Updating vote count when a vote is accepted.
 * - Accumulating XP into the `"Webdesign"` stat.
 * - Sending a damage packet so clients receive refreshed combat/stat information.
 *
 * @constructor Initializes `HttpXP` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class HttpXP(computer: Computer) : Function(computer) {
    companion object {
        private const val XP_MULTIPLIER = 1.0f
        private const val MIN_XP = 300.0f
        private const val VOTE_SENTINEL = 500.7337f
        private const val VOTE_REWARD = 500.0f
    }

    override fun execute(applicationData: ApplicationData) {
        if (computer.getType() == Computer.NPC) return

        val payload = applicationData.payloadAs<FloatCommandPayload>()
        var grantXp = true
        var amount = payload.value

        if (amount == VOTE_SENTINEL) {
            if (computer.checkHTTP()) {
                amount = VOTE_REWARD
                computer.setVoteCount(computer.getVoteCount() + 1)
            } else {
                grantXp = false
                computer.addMessage(MessageHandler.VOTE_FAIL_HTTP_NOT_ON)
            }
        }

        if (grantXp) {
            val currentXp = (computer.stats["Webdesign"] as? Float) ?: 0f
            val updatedXp = (amount * XP_MULTIPLIER + currentXp).coerceAtLeast(
                if (XP_MULTIPLIER < 0) MIN_XP else Float.NEGATIVE_INFINITY
            )
            computer.stats["Webdesign"] = updatedXp
        }

        computer.sendDamagePacket()
    }
}
