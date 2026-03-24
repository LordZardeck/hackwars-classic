package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import com.hackwars.rpc.GameCommands
import com.hackwars.rpc.RequestAttack
import game.ApplicationData
import game.MessageHandler
import game.messageData
import game.payload.LocalPortEntryPayload
import game.payload.StructuredMessagePayload
import game.payloadAs

class RequestAttackHandler : AttackFunctionHandler {
    override val functionName: String = com.hackwars.rpc.GameCommandWires.REQUESTATTACK

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        val payload = applicationData.payloadAs<RequestAttack>()
        val windowHandle = payload.windowHandle ?: 0

        if (program.parentPort!!.attacking) {
            program.computerHandler!!.addData(
                ApplicationData(
                    StructuredMessagePayload(
                        arrayOf<Any?>(MessageHandler.PORT_ALREADY_ATTACKING),
                        arrayOf<Any?>(program.parentPort!!.number),
                        arrayOf<Any?>(windowHandle, program.sourceIP)
                    ),
                    0,
                    program.sourceIP
                ),
                program.sourceIP
            )
            return
        }

        if (program.parentPort!!.overHeated) {
            program.computerHandler!!.addData(
                ApplicationData(
                    StructuredMessagePayload(
                        arrayOf<Any?>(MessageHandler.ATTACK_FAIL_OVERHEATED),
                        emptyArray<Any?>(),
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

        if (!program.computer!!.checkBank()) {
            program.computerHandler!!.addData(
                messageData(MessageHandler.ACTIVE_BANK_NOT_FOUND, program.sourceIP),
                program.sourceIP
            )
            return
        }

        if (program.computer!!.pettyCash < 10.0f) {
            program.computer!!.addMessage(MessageHandler.ATTACK_FAIL_NOT_ENOUGH_MONEY)
            return
        }

        program.targetIP = payload.targetIP
        program.targetPort = payload.targetPort

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

        val request = ApplicationData(
            LocalPortEntryPayload(GameCommands.ATTACK.command, program.computer!!.network),
            program.targetPort,
            applicationData.sourceIP
        )
            .withSourcePort(program.parentPort!!.number)
        program.computerHandler!!.addData(request, program.targetIP)
    }
}
