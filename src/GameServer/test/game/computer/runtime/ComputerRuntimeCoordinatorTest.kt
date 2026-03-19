package game.computer.runtime

import game.Port
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
        state.currentCPU = 10f
        state.reportCPU = 10f
        state.cpuMaximum = 200f
        state.currentWatchCost = 0f
        state.watchCostSupplier = { 7f }
        state.ports.add(
            runtimePort(
                number = 3,
                type = Port.ATTACK,
                attacking = true,
                cpuCost = 15f,
                baseCpuCostTotal = 12f,
                targetPort = 9
            )
        )

        val events = ComputerRuntimeCoordinator().tick(state)

        assertTrue(events.any { it is RuntimeTickEvent.PersistRequested })
        assertTrue(events.filterIsInstance<RuntimeTickEvent.PlaySessionRecorded>().isNotEmpty())
        assertTrue(events.any { it is RuntimeTickEvent.DailyPayIssued })
        assertTrue(events.any { it is RuntimeTickEvent.AttackContinueRequested })
        assertTrue(events.any { it is RuntimeTickEvent.EquipmentRefreshRequested })
        assertTrue(events.any { it is RuntimeTickEvent.CaptchaRequested })
    }
}
