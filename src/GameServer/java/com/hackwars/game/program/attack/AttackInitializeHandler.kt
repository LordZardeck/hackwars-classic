package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData

class AttackInitializeHandler : AttackFunctionHandler {
    override val functionName: String = "attackinitialize"

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        if (program.switching || program.parentPort.attacking) {
            return
        }

        program.computerHandler.addData(
            ApplicationData("pettycash", -10.0f, 0, program.computer.ip),
            program.computer.ip
        )

        program.choicesShown = false
        program.attackStart = program.computer.currentTime
        program.iterations = 0

        val parameters = applicationData.parameters as Array<Any?>
        val targetStats = parameters[0] as Array<Float?>
        val targetWatch = parameters[1] as Boolean

        if (parameters.size > 2) {
            program.isNPC = parameters[2] as Boolean
        }

        program.parentPort.targetHP = targetStats[1]!!
        program.parentPort.targetPettyCash = targetStats[2]!!
        program.parentPort.targetCPUCost = targetStats[3]!!
        program.parentPort.targetWatch = targetWatch

        program.targetIP = applicationData.sourceIP
        program.targetPort = applicationData.sourcePort
        program.attacking = true

        program.initializeScript?.let(program::runScript)

        if (!program.zombie && !program.computer.checkBank()) {
            program.computerHandler.addData(
                ApplicationData("cancelattack", null, program.getTargetPort(), program.sourceIP),
                program.getTargetIP()
            )
            program.attacking = false
        }
    }
}
