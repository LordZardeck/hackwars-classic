package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData
import game.MessageHandler

class RequestCancelAttackHandler : AttackFunctionHandler {
    override val functionName: String = "requestcancelattack"

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        val isAuthorizedSource = applicationData.getSourceIP() == program.parentPort.getIP() ||
            (program.zombie && program.maliciousIP == applicationData.getSourceIP())

        if (!program.parentPort.getAttacking() || !isAuthorizedSource) {
            return
        }

        if (!program.zombie) {
            program.computerHandler.addData(
                ApplicationData("cancelattack", null, program.getTargetPort(), program.sourceIP),
                program.getTargetIP()
            )
            program.computer.addMessage(
                MessageHandler.ATTACK_CANCELLED,
                arrayOf<Any?>(),
                arrayOf<Any?>(program.windowHandle, program.sourceIP)
            )
        } else {
            program.computerHandler.addData(
                ApplicationData("cancelattack", null, program.getTargetPort(), program.maliciousIP),
                program.getTargetIP()
            )
            program.computer.addMessage(
                MessageHandler.ATTACK_CANCELLED,
                arrayOf<Any?>(),
                arrayOf<Any?>(program.windowHandle, program.maliciousIP)
            )
        }

        program.attacking = false
    }
}
