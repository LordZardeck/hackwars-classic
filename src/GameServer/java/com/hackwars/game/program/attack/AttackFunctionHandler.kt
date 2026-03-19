package com.hackwars.game.program.attack

import com.hackwars.game.program.AttackProgram
import game.ApplicationData

interface AttackFunctionHandler {
    val functionName: String

    fun execute(program: AttackProgram, applicationData: ApplicationData)
}
