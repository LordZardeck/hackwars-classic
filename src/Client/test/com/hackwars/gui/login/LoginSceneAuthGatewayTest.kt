package com.hackwars.gui.login

import com.hackwars.client.ClientAuthGateway
import com.hackwars.client.ClientAuthResult
import com.hackwars.client.DeterministicClientAuthGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LoginSceneAuthGatewayTest {
    @Test
    fun submitCredentials_usesInjectedGateway_andClearsPassword() {
        val gateway = object : ClientAuthGateway {
            override fun authenticate(email: String, password: CharArray): ClientAuthResult {
                assertEquals("localuser", email)
                assertEquals("password1234", String(password))
                return ClientAuthResult.success("PF-TEST", "SESSION-TEST")
            }
        }
        val scene = LoginScene(gateway)
        val latch = CountDownLatch(1)
        val authenticated = AtomicReference<Pair<String, String>>()
        scene.onPlayFabAuthenticated = { playFabId, sessionTicket ->
            authenticated.set(playFabId to sessionTicket)
            latch.countDown()
        }

        val password = "password1234".toCharArray()
        scene.submitCredentials("localuser", password)

        assertTrue(password.all { it == '\u0000' })
        assertTrue("Expected auth callback to fire", latch.await(3, TimeUnit.SECONDS))
        assertEquals("PF-TEST", authenticated.get().first)
        assertEquals("SESSION-TEST", authenticated.get().second)
    }

    @Test
    fun deterministicGateway_acceptsKnownDefaultAccount() {
        val gateway = DeterministicClientAuthGateway()
        val password = "password1234".toCharArray()

        val result = gateway.authenticate("localuser", password)

        assertTrue(result.isSuccessful)
        assertEquals("PF-LOCALUSER", result.playFabId)
        assertEquals("SESSION-LOCALUSER", result.sessionTicket)
        assertTrue(password.all { it == '\u0000' })
    }
}
