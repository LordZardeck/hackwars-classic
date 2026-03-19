package game.computer.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class ComputerRuntimeCoordinatorTest {
    @Test
    fun tick_aggregatesResultsFromTheRuntimeCutoverServices() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastAccessed = 19_000L
        state.computerTimeoutMs = 10_000L
        state.lastSave = 17_500L
        state.autoSaveMs = 1_000L
        state.lastPingTime = 1_000L
        state.logInTime = 500L
        state.lastClientPacketTime = 1_200L
        state.lastPaid = 5_000L
        state.payPeriodMs = 10_000L
        state.lockCount = 5
        state.captchaThreshold = 5

        val events = ComputerRuntimeCoordinator().tick(state)

        assertTrue(events.any { it is RuntimeTickEvent.PersistRequested })
        assertTrue(events.filterIsInstance<RuntimeTickEvent.PlaySessionRecorded>().isNotEmpty())
        assertTrue(events.any { it is RuntimeTickEvent.DailyPayIssued })
        assertTrue(events.any { it is RuntimeTickEvent.CaptchaRequested })
    }
}
