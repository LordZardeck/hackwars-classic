package game.computer.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbuseProtectionTickServiceTest {
    private val service = AbuseProtectionTickService()

    @Test
    fun tick_requestsCaptchaWhenThresholdIsMet() {
        val state = baseRuntimeState()
        state.lockCount = 5
        state.captchaThreshold = 5
        state.locked = false
        state.resendCaptcha = false

        val events = service.tick(state)

        assertTrue(events.any { it is RuntimeTickEvent.CaptchaRequested })
        assertTrue(state.locked)
        assertEquals("12345", state.unlockKey)
        assertFalse(state.resendCaptcha)
    }

    @Test
    fun tick_grantsFilesOnTheConfiguredInterval() {
        val state = baseRuntimeState()
        state.grantFilesInterval = 2

        val first = service.tick(state)
        val second = service.tick(state)

        assertTrue(first.none { it is RuntimeTickEvent.GrantFilesRequested })
        assertTrue(second.any { it is RuntimeTickEvent.GrantFilesRequested })
    }
}
