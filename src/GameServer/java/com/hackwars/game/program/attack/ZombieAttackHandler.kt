package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData
import game.MessageHandler
import game.payload.ATTACK_COMMAND
import game.payload.RedirectedPortEntryPayload
import game.payload.StructuredMessagePayload
import game.payload.ZombieAttackPayload
import game.payloadAs

class ZombieAttackHandler : AttackFunctionHandler {
    override val functionName: String = "zombieattack"

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        if (!program.parentPort!!.attacking && !program.parentPort!!.overHeated) {
            program.switching = false

            val payload = applicationData.payloadAs<ZombieAttackPayload>()
            program.targetIP = payload.targetIp
            program.targetPort = payload.targetPort
            program.maliciousIP = applicationData.sourceIP

            payload.secondaryPorts?.let { secondaryPorts ->
                program.resetSecondaryTargets(program.targetPort, secondaryPorts)
            }

            payload.scripts?.let { scripts ->
                program.maliciousCode = scripts
            }

            payload.extraInfo?.let { extraInfo ->
                program.maliciousParameters = extraInfo
                program.pettyCashTarget = program.maliciousParameters!![3] as Float
            }

            program.initializeScript?.let(program::runScript)

            if (program.zombieIP == program.maliciousIP) {
                program.zombie = true
                val request = ApplicationData(
                    RedirectedPortEntryPayload(
                        ATTACK_COMMAND,
                        program.parentPort!!.IP,
                        program.computer!!.network
                    ),
                    program.targetPort,
                    applicationData.sourceIP
                ).withSourcePort(program.parentPort!!.number)
                program.computerHandler!!.addData(request, program.targetIP)
                return
            }

            program.maliciousIP = ""
            program.zombieIP = ""
            sendMessage(
                program,
                applicationData.sourceIP,
                StructuredMessagePayload(
                    arrayOf<Any?>(MessageHandler.ZOMBIE_ATTEMPT_FAIL),
                    emptyArray<Any?>(),
                    arrayOf<Any?>(program.windowHandle, program.sourceIP)
                )
            )
            return
        }


        // Notify the user that the attack failed due to it being already attacked
        if (program.parentPort!!.attacking)
            return sendMessage(
                program,
                applicationData.sourceIP,
                StructuredMessagePayload(
                    arrayOf<Any?>(MessageHandler.PORT_ALREADY_ATTACKING),
                    arrayOf<Any?>(program.parentPort!!.number),
                    arrayOf<Any?>(program.windowHandle, program.sourceIP)
                )
            )

        // Notify the user that the attack failed due to overheating
        sendMessage(
            program,
            applicationData.sourceIP,
            StructuredMessagePayload(
                arrayOf<Any?>(MessageHandler.ATTACK_FAIL_OVERHEATED),
                emptyArray<Any?>(),
                arrayOf<Any?>(program.windowHandle)
            )
        )
    }

    private fun sendMessage(program: AttackProgram, sourceIp: String, payload: StructuredMessagePayload) {
        program.computerHandler!!.addData(
            ApplicationData(payload, 0, program.sourceIP),
            sourceIp
        )
    }
}
