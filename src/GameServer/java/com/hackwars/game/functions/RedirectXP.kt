package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.payload.FloatCommandPayload
import game.payload.IntCommandPayload
import game.payloadAs

/**
 * Represents a function that rewards redirecting experience.
 *
 * This function supports both direct float XP values and commodity-type indices by:
 *
 * - Converting the input parameter into an XP amount.
 * - Applying the configured multiplier.
 * - Accumulating into the `"Redirecting"` stat.
 * - Sending a damage packet after the stat update.
 *
 * @constructor Initializes `RedirectXP` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class RedirectXP(computer: Computer) : Function(computer) {
    companion object {
        private const val XP_MULTIPLIER = 1.0f
        private const val MIN_XP = 300.0f
    }

    override fun execute(applicationData: ApplicationData) {
        val amount = when (val payload = applicationData.payload) {
            is IntCommandPayload -> Computer.commodityXP[payload.value]
            is FloatCommandPayload -> payload.value
            else -> return
        }

        val currentXp = (computer.stats["Redirecting"] as? Float) ?: 0f
        val updatedXp = (amount * XP_MULTIPLIER + currentXp).coerceAtLeast(
            if (XP_MULTIPLIER < 0) MIN_XP else Float.NEGATIVE_INFINITY
        )

        computer.stats["Redirecting"] = updatedXp
        computer.sendDamagePacket()
    }
}
