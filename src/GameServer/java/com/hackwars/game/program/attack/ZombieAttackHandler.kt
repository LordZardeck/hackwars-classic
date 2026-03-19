package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData
import game.MessageHandler

class ZombieAttackHandler : AttackFunctionHandler {
    override val functionName: String = "zombieattack"

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        if (!program.parentPort.attacking && !program.parentPort.overHeated) {
            program.switching = false

            val parameters = applicationData.parameters as Array<Any?>
            program.targetIP = parameters[0] as String
            program.targetPort = parameters[1] as Int
            program.maliciousIP = applicationData.sourceIP

            if (parameters.size > 2) {
                program.resetSecondaryTargets(program.targetPort, parameters[2] as Array<Int?>)
            }

            if (parameters.size > 3) {
                program.MaliciousCode = parameters[3] as Array<Array<String?>?>
            }

            if (parameters.size > 4) {
                program.MaliciousParameters = parameters[4] as Array<Any?>
                program.pettyCashTarget = program.MaliciousParameters!![3] as Float
            }

            program.initializeScript?.let(program::runScript)

            if (program.zombieIP == program.maliciousIP) {
                program.zombie = true
                val request = ApplicationData(
                    "attack",
                    arrayOf<String?>(program.parentPort.ip, program.computer.network),
                    program.targetPort,
                    applicationData.sourceIP
                )
                request.sourcePort = program.parentPort.number
                program.computerHandler.addData(request, program.targetIP)
                return
            }

            program.maliciousIP = ""
            program.zombieIP = ""
            sendMessage(
                program, applicationData.sourceIP, arrayOf(
                    MessageHandler.ZOMBIE_ATTEMPT_FAIL,
                    arrayOf<Any?>(),
                    arrayOf<Any?>(program.windowHandle, program.sourceIP)
                )
            )
            return
        }


        // Notify the user that the attack failed due to it being already attacked
        if (program.parentPort.attacking)
            return sendMessage(
                program, applicationData.sourceIP, arrayOf(
                    MessageHandler.PORT_ALREADY_ATTACKING,
                    arrayOf<Any>(program.parentPort.number),
                    arrayOf<Any?>(program.windowHandle, program.sourceIP)
                )
            )

        // Notify the user that the attack failed due to overheating
        sendMessage(
            program, applicationData.sourceIP, arrayOf(
                MessageHandler.ATTACK_FAIL_OVERHEATED,
                arrayOf<Any?>(),
                arrayOf<Any>(program.windowHandle)
            )
        )
    }

    private fun sendMessage(program: AttackProgram, sourceIp: String, parameters: Array<Any>) {
        program.computerHandler.addData(
            ApplicationData("message", parameters, 0, program.sourceIP),
            sourceIp
        )
    }
}
