package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

/**
 * Represents a function that rewards watch experience.
 *
 * This function reads XP from application data, updates the `"Watch"` stat,
 * and sends a damage packet so clients observe the updated values.
 *
 * @constructor Initializes `WatchXP` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class WatchXP(computer: Computer) : Function(computer) {
    companion object {
        private const val XP_MULTIPLIER = 1.0f
        private const val MIN_XP = 300.0f
    }

    override fun execute(applicationData: ApplicationData) {
        val amount = applicationData.parameters as? Float ?: return
        val currentXp = (computer.stats["Watch"] as? Float) ?: 0f
        val updatedXp = (amount * XP_MULTIPLIER + currentXp).coerceAtLeast(
            if (XP_MULTIPLIER < 0) MIN_XP else Float.NEGATIVE_INFINITY
        )

        computer.stats["Watch"] = updatedXp
        computer.sendDamagePacket()
    }
}
