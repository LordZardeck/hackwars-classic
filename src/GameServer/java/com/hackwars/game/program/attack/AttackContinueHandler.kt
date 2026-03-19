package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData

class AttackContinueHandler : AttackFunctionHandler {
    override val functionName: String = "attackcontinue"

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        program.iterations++
        program.continueScript?.let(program::runScript)

        if (program.dealDamage) {
            val payload: Array<Any?> = arrayOf(
                program.computer!!.getDamage("Attack") + program.computer!!.equipmentSheet.getDamageBonus(),
                program.parentPort!!.ip,
                program.parentPort!!.number,
                false,
                program.parentPort!!.ip.takeIf { program.zombie },
                program.windowHandle,
                -1
            )
            val damageData = ApplicationData(
                "damage",
                payload,
                program.targetPort,
                program.maliciousIP.takeIf { program.zombie } ?: program.computer!!.ip,
            )

            damageData.sourcePort = program.parentPort!!.number
            program.computerHandler!!.addData(damageData, program.targetIP)
        }
        program.dealDamage = true
    }
}
