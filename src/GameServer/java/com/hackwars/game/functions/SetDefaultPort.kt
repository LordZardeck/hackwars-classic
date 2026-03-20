package com.hackwars.game.functions

import assignments.PacketPort
import game.ApplicationData
import game.Computer
import game.payload.IntCommandPayload
import game.payloadAs
import com.hackwars.rpc.FetchPorts

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
        val type = applicationData.payloadAs<IntCommandPayload>().value
        val port = applicationData.port

        when (type) {
            PacketPort.BANKING -> computer.setDefaultBank(port)
            PacketPort.ATTACK -> computer.setDefaultAttack(port)
            PacketPort.FTP -> computer.setDefaultFTP(port)
            PacketPort.HTTP -> computer.setDefaultHTTP(port)
            PacketPort.SHIPPING -> computer.setDefaultShipping(port)
        }

        computer.computerHandler.addData(ApplicationData(FetchPorts(computer.getIP()), 0, computer.getIP()), computer.getIP())
    }
}
