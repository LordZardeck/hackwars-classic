package com.hackwars.game.functions

import game.ApplicationData
import game.payload.TriggerWatchByNotePayload
import hackscript.model.TypeInteger
import hackscript.model.TypeString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

class LaunchNetworkAttackTest {
    @Test
    fun execute_dispatchesRequestTriggerNoteToNpc() {
        val computer = FunctionTestSupport.baseComputer("2.2.2.2")
        val networkSwitch = computer.computerHandler

        LaunchNetworkAttack(computer).execute(FunctionTestSupport.launchNetworkAttack("9.9.9.9"))

        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch).addData(appCaptor.capture(), eq("9.9.9.9"))
        val dispatched = appCaptor.firstValue
        assertEquals("requesttriggernote", dispatched.command.wireName())
        val payload = dispatched.payload as TriggerWatchByNotePayload
        assertEquals("netbomb", payload.watchNote)
        val trigger = payload.triggerParameters as HashMap<*, *>
        assertTrue(trigger["playerip"] is TypeString)
        assertTrue(trigger["defaultattack"] is TypeInteger)
        assertTrue(trigger["defaultbank"] is TypeInteger)
        assertTrue(trigger["defaulthttp"] is TypeInteger)
        assertTrue(trigger["defaultredirecting"] is TypeInteger)
    }
}
