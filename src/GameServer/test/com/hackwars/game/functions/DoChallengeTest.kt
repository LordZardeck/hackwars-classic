package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Test
import org.mockito.kotlin.verify

class DoChallengeTest {
    @Test
    fun execute_runsChallengeAndSendsPacket() {
        val computer = FunctionTestSupport.baseComputer()

        DoChallenge(computer).execute(FunctionTestSupport.doChallenge("challenge-file", "challenge-id"))

        verify(computer).doChallengeRPC("challenge-id", "challenge-file")
        verify(computer).sendPacket()
    }
}
