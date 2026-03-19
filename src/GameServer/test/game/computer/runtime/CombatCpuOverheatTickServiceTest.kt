package game.computer.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatCpuOverheatTickServiceTest {
    private val service = CombatCpuOverheatTickService()

    @Test
    fun tick_requestsAttackContinueAndRecomputesCpu() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastAttack = 0
        state.attackRateMs = 1_000L
        state.watchCostSupplier = { 7f }
        state.currentWatchCost = 0f
        state.currentCPU = 10f
        state.reportCPU = 10f
        state.cpuMaximum = 200f
        state.ports.add(
            RuntimePortSnapshot(
                number = 3,
                type = 0,
                attacking = true,
                cpuCost = 15f,
                baseCpuCostTotal = 12f,
                targetPort = 9
            )
        )

        val events = service.tick(state)

        assertTrue(events.contains(RuntimeTickEvent.AttackContinueRequested(3, 9)))
        assertEquals(22f, state.currentCPU, 0.0001f)
        assertEquals(19f, state.baseCPU, 0.0001f)
        assertEquals(22f, state.reportCPU, 0.0001f)
    }

    @Test
    fun tick_announcesOverheatAndRaisesReportedCpu() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastAttack = 0
        state.attackRateMs = 1_000L
        state.cpuMaximum = 20f
        state.currentCPU = 25f
        state.reportCPU = 25f
        state.sentOverHeatedMessage = false
        state.ports.add(
            RuntimePortSnapshot(
                number = 4,
                type = 0,
                attacking = false,
                cpuCost = 10f,
                baseCpuCostTotal = 10f,
                health = 50f,
                targetIp = "9.9.9.9",
                lastDamageWindowHandle = 17
            )
        )

        val events = service.tick(state)

        assertTrue(events.any { it is RuntimeTickEvent.OverheatAnnounced })
        assertTrue(state.reportCPU > state.cpuMaximum)
    }
}
