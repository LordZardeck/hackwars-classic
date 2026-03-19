package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData
import game.MessageHandler

class RequestAttackHandler : AttackFunctionHandler {
    override val functionName: String = "requestattack"

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        val parameters = applicationData.parameters as Array<Any?>
        val windowHandle = parameters[5] as Int

        if (program.parentPort.attacking) {
            program.computerHandler.addData(
                ApplicationData(
                    "message",
                    arrayOf<Any>(
                        MessageHandler.PORT_ALREADY_ATTACKING,
                        arrayOf<Any>(program.parentPort.number),
                        arrayOf<Any?>(windowHandle, program.sourceIP)
                    ),
                    0,
                    program.sourceIP
                ),
                program.sourceIP
            )
            return
        }

        if (program.parentPort.overHeated) {
            program.computerHandler.addData(
                ApplicationData(
                    "message",
                    arrayOf<Any>(
                        MessageHandler.ATTACK_FAIL_OVERHEATED,
                        arrayOf<Any?>(),
                        arrayOf<Any?>(windowHandle, program.sourceIP)
                    ),
                    0,
                    program.sourceIP
                ),
                program.sourceIP
            )
            return
        }

        program.windowHandle = windowHandle
        program.switching = false

        if (!program.computer.checkBank()) {
            program.computerHandler.addData(
                ApplicationData("message", MessageHandler.ACTIVE_BANK_NOT_FOUND, 0, program.sourceIP),
                program.sourceIP
            )
            return
        }

        if (program.computer.pettyCash < 10.0f) {
            program.computer.addMessage(MessageHandler.ATTACK_FAIL_NOT_ENOUGH_MONEY)
            return
        }

        program.targetIP = parameters[0] as String
        program.targetPort = parameters[1] as Int

        if (parameters.size > 2) {
            program.resetSecondaryTargets(program.targetPort, parameters[2] as Array<Int?>)
        }

        if (parameters.size > 3) {
            program.MaliciousCode = parameters[3] as Array<Array<String?>?>
        }

        if (parameters[4] != null) {
            program.MaliciousParameters = parameters[4] as Array<Any?>
            program.pettyCashTarget = program.MaliciousParameters!![3] as Float
        }

        val request = ApplicationData("attack", program.computer.network, program.targetPort, applicationData.sourceIP)
        request.sourcePort = program.parentPort.number
        program.computerHandler.addData(request, program.targetIP)
    }
}
