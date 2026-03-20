package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.MessageHandler
import game.payload.PettyCashDeltaPayload
import game.payload.ZombieAttackPayload
import game.payloadAs
import com.hackwars.rpc.RequestZombieAttack as RequestZombieAttackPayload

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
        val payload = applicationData.payloadAs<RequestZombieAttackPayload>()

        if (computer.getPettyCash() >= ZOMBIE_ATTACK_COST) {
            val computerHandler = computer.computerHandler
            computerHandler.addData(
                ApplicationData(PettyCashDeltaPayload(-ZOMBIE_ATTACK_COST), 0, computer.getIP()),
                computer.getIP()
            )

            computerHandler.addData(
                ApplicationData(
                    ZombieAttackPayload(
                        payload.targetIP,
                        payload.targetPort,
                        payload.sourceIP ?: computer.getIP(),
                        payload.sourcePort,
                        payload.I,
                        payload.S,
                        payload.O,
                        payload.parentIP
                    ),
                    applicationData.port,
                    computer.getIP()
                ),
                payload.targetIP
            )
            return
        }

        computer.addMessage(MessageHandler.ZOMBIE_FAIL_NOT_ENOUGH_MONEY)
        computer.sendPacket()
    }
}
