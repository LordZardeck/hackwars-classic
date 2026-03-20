package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData
import game.payload.DamagePayload

class AttackContinueHandler : AttackFunctionHandler {
    override val functionName: String = "attackcontinue"

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        program.iterations++
        program.continueScript?.let(program::runScript)

        if (program.dealDamage) {
            val damageData = ApplicationData(
                DamagePayload(
                    damage = program.computer!!.getDamage("Attack") + program.computer!!.equipmentSheet.getDamageBonus(),
                    targetIp = program.parentPort!!.IP,
                    targetPort = program.parentPort!!.number,
                    damageFromFireWall = false,
                    zombieSource = program.parentPort!!.IP.takeIf { program.zombie },
                    windowHandle = program.windowHandle,
                    commodityId = -1
                ),
                program.targetPort,
                program.maliciousIP.takeIf { program.zombie } ?: program.computer!!.ip,
            ).withSourcePort(program.parentPort!!.number)
            program.computerHandler!!.addData(damageData, program.targetIP)
        }
        program.dealDamage = true
    }
}
