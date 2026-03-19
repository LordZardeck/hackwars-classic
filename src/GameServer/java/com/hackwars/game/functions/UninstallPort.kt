package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.MessageHandler
import game.Port

/**
 * Represents a function that uninstalls an existing port.
 *
 * This function checks CPU constraints, locates the target port, validates
 * whether uninstall is allowed (not accessing, attacking, or overheated), and:
 *
 * - Removes the port when allowed.
 * - Adds an explanatory failure message when blocked.
 * - Dispatches `fetchports` so clients refresh port information.
 *
 * @constructor Initializes `UninstallPort` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class UninstallPort(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val maxCpu = Computer.CPU_CHART[computer.cpuType] + computer.equipmentSheet.cpuBonus
        if (computer.cpuLoad > maxCpu) return

        val deletePort = applicationData.parameters as? Int ?: return
        val portIterator = computer.ports.entries.iterator()

        while (portIterator.hasNext()) {
            val entry = portIterator.next() as? MutableMap.MutableEntry<*, *> ?: continue
            val port = entry.value as? Port ?: continue
            if (port.number != deletePort) continue

            var allow = true
            var message: Array<out Any>? = null

            if (port.accessing.isNotEmpty()) {
                allow = false
                message = MessageHandler.UNINSTALL_PORT_FAIL_UNDER_ATTACK
            }
            if (port.attacking) {
                allow = false
                message = MessageHandler.UNINSTALL_PORT_FAIL_ATTACKING
            }
            if (port.overHeated) {
                allow = false
                message = MessageHandler.UNINSTALL_PORT_FAIL_OVERHEATED
            }

            if (allow) {
                portIterator.remove()
            } else if (message != null) {
                computer.addMessage(message)
            }
        }

        computer.computerHandler.addData(ApplicationData("fetchports", null, 0, computer.ip), computer.ip)
    }
}
