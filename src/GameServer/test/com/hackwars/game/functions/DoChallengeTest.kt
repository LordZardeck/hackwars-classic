package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Test
import org.mockito.kotlin.verify

class DoChallengeTest {
    @Test
    fun execute_runsChallengeAndSendsPacket() {
        val computer = FunctionTestSupport.baseComputer()

        DoChallenge(computer).execute(
            ApplicationData("dochallenge", arrayOf<Any>("challenge-file", "challenge-id"), 0, "source")
        )

        verify(computer).doChallengeRPC("challenge-id", "challenge-file")
        verify(computer).sendPacket()
    }
}
