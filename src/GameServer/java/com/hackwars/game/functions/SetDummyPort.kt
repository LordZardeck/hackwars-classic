package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.Port
import game.payload.BooleanCommandPayload
import game.payloadAs
import com.hackwars.rpc.FetchPorts

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
        val shouldBeDummy = applicationData.payloadAs<BooleanCommandPayload>().value
        val port = computer.ports[targetPort] as? Port

        if (port != null && !port.attacking && port.accessing.isEmpty()) {
            if (!port.overHeated) {
                if (!shouldBeDummy && port.dummy && port.on) {
                    val cpuCheck = computer.cpuLoad + port.getActualCPUCost()
                    val maxCpu = Computer.CPU_CHART[computer.cpuType] + computer.equipmentSheet.getCPUBonus()
                    if (cpuCheck <= maxCpu) {
                        port.setDummy(shouldBeDummy)
                    }
                } else {
                    port.setDummy(shouldBeDummy)
                }
            }
        }

        computer.computerHandler.addData(ApplicationData(FetchPorts(computer.getIP()), 0, computer.getIP()), computer.getIP())
    }
}
