package com.hackwars.game.functions

import assignments.PacketPort
import game.ApplicationData
import game.Computer

/**
 * Represents a function that sets one of the computer's default service ports.
 *
 * Based on the provided port type, this function updates the corresponding default
 * port field and then dispatches a `fetchports` request so clients refresh port metadata.
 *
 * @constructor Initializes `SetDefaultPort` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class SetDefaultPort(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val type = applicationData.parameters as? Int ?: return
        val port = applicationData.port

        when (type) {
            PacketPort.BANKING -> computer.defaultBank = port
            PacketPort.ATTACK -> computer.defaultAttack = port
            PacketPort.FTP -> computer.defaultFTP = port
            PacketPort.HTTP -> computer.defaultHTTP = port
            PacketPort.SHIPPING -> computer.defaultShipping = port
        }

        computer.computerHandler.addData(ApplicationData("fetchports", null, 0, computer.ip), computer.ip)
    }
}
