package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData
import game.MakeBounty
import game.payload.AttackFinalizePayload
import game.payloadAs

class AttackFinalizeHandler : AttackFunctionHandler {
    override val functionName: String = "attackfinalize"

    override fun execute(program: AttackProgram, applicationData: ApplicationData) {
        if (!program.parentPort!!.attacking) {
            return
        }

        program.targetPortType = applicationData.payloadAs<AttackFinalizePayload>().portType
        program.targetPort = applicationData.sourcePort
        program.removeSecondaryTarget(program.targetPort)

        program.finalizeScript?.let(program::runScript)

        program.attacking = false
        program.computer!!.incrementSuccessfulHacks()
        program.checkBounty(null, MakeBounty.KILL)
    }
}
