package game.computer.runtime

import game.Port

class CombatCpuOverheatTickService {
    fun tick(state: RuntimeTickState): List<RuntimeTickEvent> {
        val events = mutableListOf<RuntimeTickEvent>()

        if (!state.loaded || state.loading) {
            updateCpuWithoutAttackTick(state)
            return events
        }

        if (state.now - state.lastAttack <= state.attackRateMs) {
            updateCpuWithoutAttackTick(state)
            return events
        }

        if (!state.cpuLoadCalculated) {
            state.currentWatchCost = state.watchCostSupplier?.invoke() ?: state.currentWatchCost
        }
        state.cpuLoadCalculated = true

        val startCPU = state.currentCPU
        val startReportCPU = state.reportCPU
        val shouldHeal = state.healMod != 0 && state.healCounter % state.healMod.toLong() == 0L
        val overheated = when {
            state.currentCPU > state.cpuMaximum -> {
                if (state.overheatStart == -1L) {
                    state.overheatStart = state.now
                }
                true
            }
            state.overheatStart != -1L && state.now - state.overheatStart > state.overHeatTimeMs -> {
                state.overheatStart = -1
                false
            }
            state.overheatStart != -1L -> true
            else -> false
        }

        var tempCPULoad = 0.0f
        var tempBaseCPULoad = 0.0f

        state.ports.forEach { port ->
            if (shouldHeal && port.health < 100f) {
                port.health = (port.health + 1.0f).coerceAtMost(100f)
            }

            if (overheated) {
                if (!port.overHeated && port.health != 100f) {
                    if (port.type == PortType.ATTACK || port.type == PortType.SHIPPING) {
                        if (port.isZombie) {
                            events += RuntimeTickEvent.ZombieOverheated(state.ip, port.maliciousTarget)
                        }
                        if (port.targetIp.isNotBlank()) {
                            events += RuntimeTickEvent.OpponentOverheated(port.targetIp, port.lastDamageWindowHandle, port.accessing)
                        }
                    }
                    if (!state.sentOverHeatedMessage) {
                        events += RuntimeTickEvent.OverheatAnnounced(state.ip)
                        state.sentOverHeatedMessage = true
                    }
                }
                port.overHeated = true
            }

            tempCPULoad += port.cpuCost
            tempBaseCPULoad += port.baseCpuCostTotal

            if (!port.on) {
                port.attacking = false
            }

            if ((port.type == PortType.ATTACK || port.type == PortType.SHIPPING) && port.attacking) {
                port.targetPort.takeIf { it >= 0 }?.let {
                    events += RuntimeTickEvent.AttackContinueRequested(port.number, it)
                }
            }
        }

        state.baseCPU = tempBaseCPULoad + state.currentWatchCost
        state.currentCPU = tempCPULoad + state.currentWatchCost
        state.lastAttack = state.now
        state.healCounter += 1

        state.reportCPU = if (overheated && state.currentCPU <= state.cpuMaximum) {
            state.cpuMaximum + 1
        } else if (!overheated) {
            state.ports.forEach { it.overHeated = false }
            state.sentOverHeatedMessage = false
            state.currentCPU
        } else {
            state.currentCPU
        }

        if (state.currentCPU != startCPU || state.reportCPU != startReportCPU) {
            events += RuntimeTickEvent.EquipmentRefreshRequested
        }

        return events
    }

    private fun updateCpuWithoutAttackTick(state: RuntimeTickState) {
        state.currentCPU = state.ports.sumOf { it.cpuCost.toDouble() }.toFloat() + state.currentWatchCost
        val overheated = state.ports.any { it.overHeated }
        state.reportCPU = if (overheated && state.currentCPU <= state.cpuMaximum) {
            state.cpuMaximum + 1
        } else {
            state.currentCPU
        }
    }
}

private object PortType {
    const val ATTACK = Port.ATTACK
    const val SHIPPING = Port.SHIPPING
}
