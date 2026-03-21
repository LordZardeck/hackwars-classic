package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData
import game.MessageHandler
import game.payload.CancelAttackPayload

class RequestCancelAttackHandler : AttackFunctionHandler {
    override val functionName: String = com.hackwars.rpc.GameCommandWires.REQUESTCANCELATTACK

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        val isAuthorizedSource = applicationData.getSourceIP() == program.parentPort!!.IP ||
                (program.zombie && program.maliciousIP == applicationData.getSourceIP())

        if (!program.parentPort!!.attacking || !isAuthorizedSource) {
            return
        }

        if (!program.zombie) {
            program.computerHandler!!.addData(
                ApplicationData(CancelAttackPayload(null), program.getTargetPort(), program.sourceIP),
                program.getTargetIP()
            )
            program.computer!!.addMessage(
                MessageHandler.ATTACK_CANCELLED,
                arrayOf<Any?>(),
                arrayOf<Any?>(program.windowHandle, program.sourceIP)
            )
        } else {
            program.computerHandler!!.addData(
                ApplicationData(CancelAttackPayload(null), program.getTargetPort(), program.maliciousIP),
                program.getTargetIP()
            )
            program.computer!!.addMessage(
                MessageHandler.ATTACK_CANCELLED,
                arrayOf<Any?>(),
                arrayOf<Any?>(program.windowHandle, program.maliciousIP)
            )
        }

        program.attacking = false
    }
}
