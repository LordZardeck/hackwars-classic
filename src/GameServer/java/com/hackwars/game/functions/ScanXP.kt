package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.payload.FloatCommandPayload
import game.payloadAs

/**
 * Represents a function that rewards scanning experience.
 *
 * This function:
 *
 * - Sends a damage packet update.
 * - Reads scanning XP from application parameters.
 * - Applies the configured multiplier and stat accumulation.
 * - Writes the updated value to the `"Scanning"` stat.
 *
 * @constructor Initializes `ScanXP` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class ScanXP(computer: Computer) : Function(computer) {
    companion object {
        private const val XP_MULTIPLIER = 1.0f
        private const val MIN_XP = 300.0f
    }

    override fun execute(applicationData: ApplicationData) {
        computer.sendDamagePacket()

        val amount = applicationData.payloadAs<FloatCommandPayload>().value
        val currentXp = (computer.stats["Scanning"] as? Float) ?: 0f
        val updatedXp = (amount * XP_MULTIPLIER + currentXp).coerceAtLeast(
            if (XP_MULTIPLIER < 0) MIN_XP else Float.NEGATIVE_INFINITY
        )

        computer.stats["Scanning"] = updatedXp
    }
}
