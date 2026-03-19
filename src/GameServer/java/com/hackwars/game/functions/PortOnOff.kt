package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.MessageHandler
import game.Port

/**
 * Represents a function that toggles a port's power state.
 *
 * Depending on the requested state, this function:
 *
 * - Finds the target port by number.
 * - Validates CPU limits before turning a port on.
 * - Validates runtime safety rules before turning a port off.
 * - Adds a contextual message when toggling is not allowed.
 * - Dispatches a `fetchports` refresh request after processing.
 *
 * @constructor Initializes `PortOnOff` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class PortOnOff(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val targetPort = applicationData.port
        val turnOn = applicationData.parameters as? Boolean ?: return

        for (portEntry in computer.ports.values) {
            val port = portEntry as? Port ?: continue
            if (port.number != targetPort) continue

            var allow = true
            var message: Array<out Any>? = null

            if (turnOn && !port.on) {
                port.on = true

                val cpuCheck = computer.cpuLoad + port.cpuCost
                val maxCpu = Computer.CPU_CHART[computer.cpuType] + computer.equipmentSheet.cpuBonus

                if (cpuCheck > maxCpu) {
                    allow = false
                    message = MessageHandler.PORT_ON_FAIL_EXCEED_CPU
                }

                port.on = false
            }

            if (!turnOn && port.on) {
                when {
                    port.accessing.isNotEmpty() -> {
                        allow = false
                        message = MessageHandler.PORT_OFF_FAIL_UNDER_ATTACK
                    }

                    port.attacking -> {
                        allow = false
                        message = MessageHandler.PORT_OFF_FAIL_ATTACKING
                    }

                    port.overHeated -> {
                        allow = false
                        message = MessageHandler.PORT_OFF_FAIL_OVERHEATED
                    }

                    port.health != 100.0f -> {
                        allow = false
                        message = MessageHandler.PORT_OFF_FAIL_DAMAGED
                    }
                }
            }

            if (allow) {
                port.on = turnOn
            } else if (message != null) {
                computer.addMessage(message)
            }
        }

        computer.computerHandler.addData(ApplicationData("fetchports", null, 0, computer.getIP()), computer.getIP())
    }
}
