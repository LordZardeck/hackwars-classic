package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData
import game.payload.AttackInitializePayload
import game.payload.CancelAttackPayload
import game.payload.PettyCashDeltaPayload
import game.payloadAs

class AttackInitializeHandler : AttackFunctionHandler {
    override val functionName: String = com.hackwars.rpc.GameCommandWires.ATTACKINITIALIZE

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        if (program.switching || program.parentPort!!.attacking) {
            return
        }

        program.computerHandler!!.addData(
            ApplicationData(PettyCashDeltaPayload(-10.0f), 0, program.computer!!.ip),
            program.computer!!.ip
        )

        program.choicesShown = false
        program.attackStart = program.computer!!.currentTime
        program.iterations = 0

        val payload = applicationData.payloadAs<AttackInitializePayload>()
        program.parentPort!!.targetHP = payload.health
        program.parentPort!!.targetPettyCash = payload.pettyCash
        program.parentPort!!.targetCPUCost = payload.cpuCost
        program.parentPort!!.targetWatch = payload.targetWatch
        program.isNPC = payload.npc

        program.targetIP = applicationData.sourceIP
        program.targetPort = applicationData.sourcePort
        program.attacking = true

        program.initializeScript?.let(program::runScript)

        if (!program.zombie && !program.computer!!.checkBank()) {
            program.computerHandler!!.addData(
                ApplicationData(CancelAttackPayload(null), program.getTargetPort(), program.sourceIP),
                program.getTargetIP()
            )
            program.attacking = false
        }
    }
}
