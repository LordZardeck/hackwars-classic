package com.hackwars.rewrite.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RewriteLoginAuthGatewayTest {
    @Test
    fun deterministicGatewayAcceptsKnownDefaultAccountAndClearsPassword() = runTest {
        val gateway = DeterministicRewriteLoginAuthGateway()
        val password = "password1234".toCharArray()

        val result = gateway.authenticate("localuser", password)

        assertTrue(result.isSuccessful)
        assertEquals("PF-LOCALUSER", result.playFabId)
        assertEquals("SESSION-LOCALUSER", result.sessionTicket)
        assertTrue(password.all { it == '\u0000' })
    }
}
