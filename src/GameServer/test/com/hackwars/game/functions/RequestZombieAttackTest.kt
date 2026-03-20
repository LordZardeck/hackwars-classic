package com.hackwars.game.functions

import game.payload.PettyCashDeltaPayload
import game.payload.ZombieAttackPayload
import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RequestZombieAttackTest {
    @Test
    fun execute_whenAffordable_dispatchesPettycashAndZombieattack() {
        val computer = FunctionTestSupport.baseComputer("6.6.6.6")
        val networkSwitch = computer.computerHandler
        whenever(computer.checkBank()).thenReturn(true)
        whenever(computer.getPettyCash()).thenReturn(100f)
        val parentIp = "parent"

        RequestZombieAttack(computer).execute(
            FunctionTestSupport.requestZombieAttack(
                com.hackwars.rpc.RequestZombieAttack("9.9.9.9", 45, "source", 22, null, null, null, parentIp)
            )
        )

        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch, times(2)).addData(appCaptor.capture(), any())
        val first = appCaptor.allValues[0]
        val second = appCaptor.allValues[1]
        assertEquals("pettycash", first.command.wireName())
        assertEquals(-20.0f, (first.payload as PettyCashDeltaPayload).amount)
        assertEquals("zombieattack", second.command.wireName())
        val payload = second.payload as ZombieAttackPayload
        assertEquals("9.9.9.9", payload.targetIp)
        assertEquals(45, payload.targetPort)
        assertEquals("source", payload.sourceIp)
        assertEquals(22, payload.sourcePort)
        assertNull(payload.secondaryPorts)
        assertNull(payload.scripts)
        assertNull(payload.extraInfo)
        assertEquals(parentIp, payload.parentIp)
    }
}
