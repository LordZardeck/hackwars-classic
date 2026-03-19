package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

/**
 * Represents a function that rewards repair experience.
 *
 * This function reads the XP amount from application data and:
 *
 * - Applies the configured XP multiplier.
 * - Adds the amount to the `"Repair"` stat.
 * - Sends a damage packet so clients receive updated values.
 *
 * @constructor Initializes `RepairXP` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class RepairXP(computer: Computer) : Function(computer) {
    companion object {
        private const val XP_MULTIPLIER = 1.0f
        private const val MIN_XP = 300.0f
    }

    override fun execute(applicationData: ApplicationData) {
        val amount = applicationData.parameters as? Float ?: return
        val currentXp = (computer.stats["Repair"] as? Float) ?: 0f
        val updatedXp = (amount * XP_MULTIPLIER + currentXp).coerceAtLeast(
            if (XP_MULTIPLIER < 0) MIN_XP else Float.NEGATIVE_INFINITY
        )

        computer.stats["Repair"] = updatedXp
        computer.sendDamagePacket()
    }
}
