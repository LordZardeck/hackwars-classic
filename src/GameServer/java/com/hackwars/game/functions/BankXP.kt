package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

/**
 * Represents a function that rewards a player with "Bank" experience points (XP).
 *
 * This function updates the "Bank" statistic of the associated computer with
 * the specified amount of XP, scaled by a multiplier. It also triggers a damage
 * packet to be sent.
 *
 * @constructor Initializes the BankXP function with the specified computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class BankXP(computer: Computer) : Function(computer) {
    companion object {
        private const val XP_MULTIPLIER = 1.0f
    }

    override fun execute(applicationData: ApplicationData) {
        computer.sendDamagePacket()
        computer.stats["Bank"] = (applicationData.parameters as? Float)?.let { amount ->
            amount * XP_MULTIPLIER + ((computer.stats["Bank"] as? Float) ?: 0f)
        }
    }
}
