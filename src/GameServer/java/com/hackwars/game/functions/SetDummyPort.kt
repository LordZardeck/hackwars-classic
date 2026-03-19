package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.Port

/**
 * Represents a function that toggles the dummy-port flag for a port.
 *
 * The function validates current port state (attack/access/overheat/CPU limits as needed),
 * applies the dummy flag when permitted, and then requests a port list refresh.
 *
 * @constructor Initializes `SetDummyPort` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class SetDummyPort(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val targetPort = applicationData.port
        val shouldBeDummy = applicationData.parameters as? Boolean ?: return
        val port = computer.ports[targetPort] as? Port

        if (port != null && !port.attacking && port.accessing.isEmpty()) {
            if (!port.overHeated) {
                if (!shouldBeDummy && port.dummy && port.on) {
                    val cpuCheck = computer.cpuLoad + port.actualCPUCost
                    val maxCpu = Computer.CPU_CHART[computer.cpuType] + computer.equipmentSheet.cpuBonus
                    if (cpuCheck <= maxCpu) {
                        port.dummy = shouldBeDummy
                    }
                } else {
                    port.dummy = shouldBeDummy
                }
            }
        }

        computer.computerHandler.addData(ApplicationData("fetchports", null, 0, computer.getIP()), computer.getIP())
    }
}
