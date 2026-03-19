package game.computer.runtime

import game.Port
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        state.ports.add(runtimePort(number = 3, type = Port.ATTACK, attacking = true, cpuCost = 15f, baseCpuCostTotal = 12f, targetPort = 9))

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
            runtimePort(
                number = 4,
                type = Port.ATTACK,
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

    @Test
    fun tick_beforeAttackWindowExpires_keepsReportedCpuPinnedForExistingOverheatedPorts() {
        val state = baseRuntimeState(now = 1_500L)
        state.lastAttack = 1_000L
        state.attackRateMs = 1_000L
        state.currentWatchCost = 5f
        state.cpuMaximum = 20f
        state.ports.add(runtimePort(number = 1, cpuCost = 4f, overHeated = true))
        state.ports.add(runtimePort(number = 2, cpuCost = 3f))

        val events = service.tick(state)

        assertTrue(events.isEmpty())
        assertEquals(12f, state.currentCPU, 0.0001f)
        assertEquals(21f, state.reportCPU, 0.0001f)
    }

    @Test
    fun tick_turningPortOff_clearsAttackingWithoutQueuingAttackContinue() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastAttack = 0
        state.attackRateMs = 1_000L
        state.currentCPU = 10f
        state.reportCPU = 10f
        state.cpuMaximum = 100f
        val port = runtimePort(
            number = 7,
            type = Port.ATTACK,
            on = false,
            attacking = true,
            cpuCost = 7f,
            baseCpuCostTotal = 5f,
            targetPort = 99
        )
        state.ports.add(port)

        val events = service.tick(state)

        assertFalse(port.attacking)
        assertFalse(events.any { it is RuntimeTickEvent.AttackContinueRequested })
        assertTrue(events.contains(RuntimeTickEvent.EquipmentRefreshRequested))
        assertEquals(5f, state.currentWatchCost, 0.0001f)
        assertEquals(12f, state.currentCPU, 0.0001f)
        assertEquals(12f, state.reportCPU, 0.0001f)
    }

    @Test
    fun tick_afterOverheatCooldown_clearsPortFlagsAndRequestsRefresh() {
        val state = baseRuntimeState(now = 5_000L)
        state.lastAttack = 0
        state.attackRateMs = 1_000L
        state.cpuMaximum = 20f
        state.currentCPU = 10f
        state.reportCPU = 21f
        state.overheatStart = 1_000L
        state.overHeatTimeMs = 1_000L
        state.sentOverHeatedMessage = true
        val port = runtimePort(number = 8, type = Port.ATTACK, overHeated = true, health = 40f, cpuCost = 8f, baseCpuCostTotal = 6f)
        state.ports.add(port)

        val events = service.tick(state)

        assertEquals(-1L, state.overheatStart)
        assertFalse(state.sentOverHeatedMessage)
        assertFalse(port.overHeated)
        assertTrue(events.contains(RuntimeTickEvent.EquipmentRefreshRequested))
        assertEquals(5f, state.currentWatchCost, 0.0001f)
        assertEquals(13f, state.currentCPU, 0.0001f)
        assertEquals(13f, state.reportCPU, 0.0001f)
    }
}
