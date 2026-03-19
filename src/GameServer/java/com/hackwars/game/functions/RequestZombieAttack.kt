package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.MessageHandler

/**
 * Represents a function that initiates the zombie attack handshake flow.
 *
 * The function validates bank/money requirements and then:
 *
 * - Charges the zombie attack cost from petty cash.
 * - Dispatches the `zombieattack` request to the target IP.
 * - Emits a user-facing failure message when funds are insufficient.
 *
 * @constructor Initializes `RequestZombieAttack` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class RequestZombieAttack(computer: Computer) : Function(computer) {
    companion object {
        private const val ZOMBIE_ATTACK_COST = 20.0f
    }

    override fun execute(applicationData: ApplicationData) {
        if (!computer.checkBank()) return

        if (computer.pettyCash >= ZOMBIE_ATTACK_COST) {
            val computerHandler = computer.computerHandler
            computerHandler.addData(
                ApplicationData("pettycash", -ZOMBIE_ATTACK_COST, 0, computer.ip),
                computer.ip
            )

            val targetIp = getPositionalParameter<String>(applicationData, 5)
            computerHandler.addData(
                ApplicationData("zombieattack", applicationData.parameters, applicationData.port, computer.ip),
                targetIp
            )
            return
        }

        computer.addMessage(MessageHandler.ZOMBIE_FAIL_NOT_ENOUGH_MONEY)
        computer.sendPacket()
    }
}
