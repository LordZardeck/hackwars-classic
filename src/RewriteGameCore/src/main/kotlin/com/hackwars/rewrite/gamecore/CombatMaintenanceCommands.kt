package com.hackwars.rewrite.gamecore

import kotlin.math.max
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private const val PASSIVE_HEAL_STEP: Double = 1.0
private const val PASSIVE_HEAL_MODIFIER_BASE: Int = 4
private const val WEAKENED_ACCESS_TIMEOUT_MILLIS: Long = 30_000L
private const val OVERHEAT_COOLDOWN_MILLIS: Long = 60_000L
private val COMBAT_MAINTENANCE_REFRESH_INTERVAL = 2.seconds
private val COMBAT_MAINTENANCE_LIFETIME = 365.days

internal fun ComputerState.effectivePassiveHealModifier(): Int {
    val delta = hardware.equipmentSlots.values.sumOf { it.healModifierDelta }
    return max(1, PASSIVE_HEAL_MODIFIER_BASE + delta)
}

internal fun ComputerState.isCurrentlyOverheated(now: Long): Boolean {
    val startedAt = runtime.overheatStartedAtEpochMillis ?: return false
    return now < startedAt + OVERHEAT_COOLDOWN_MILLIS
}

private fun ComputerState.shouldStartOverheat(now: Long): Boolean {
    if (isCurrentlyOverheated(now)) {
        return false
    }
    return hardware.cpuMax > 0.0 && runtime.currentCpuLoad > hardware.cpuMax
}

private class ApplyCombatMaintenanceRuntimeCommand(
    private val stateId: GameStateId,
    private val forceStartOverheat: Boolean,
    private val now: Long,
) : RequestCommand<Unit> {
    override val name: String = "applycombatmaintenanceruntime"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext) {
        var currentState = context.loadState(stateId) ?: return
        var currentPorts = currentState.ports
        var currentRuntime = currentState.runtime

        suspend fun applyPortsAndRuntime(
            nextPorts: List<PortState>,
            nextRuntime: RuntimeState,
        ) {
            val portsChangedPaths = nextPorts.maintenanceChangedPaths(currentPorts)
            val runtimeChangedPaths = buildSet {
                if (currentRuntime.currentCpuLoad != nextRuntime.currentCpuLoad) {
                    add("runtime.currentCpuLoad")
                }
                if (currentRuntime.healCounter != nextRuntime.healCounter) {
                    add("runtime.healCounter")
                }
                if (currentRuntime.overheatStartedAtEpochMillis != nextRuntime.overheatStartedAtEpochMillis) {
                    add("runtime.overheatStartedAtEpochMillis")
                }
            }
            if (portsChangedPaths.isEmpty() && runtimeChangedPaths.isEmpty()) {
                return
            }
            currentState = context.appendEvents(
                id = stateId,
                events = listOf(
                    CombatStateUpdatedEvent(
                        changedPathList = buildSet {
                            addAll(portsChangedPaths)
                            addAll(runtimeChangedPaths)
                        },
                        deltaKeyList = buildSet {
                            if (portsChangedPaths.isNotEmpty()) {
                                add("ports")
                            }
                            if (runtimeChangedPaths.isNotEmpty()) {
                                add("runtime")
                            }
                        },
                        combat = currentState.combat,
                        ports = nextPorts,
                        currentCpuLoad = nextRuntime.currentCpuLoad,
                        runtimeState = nextRuntime,
                        includePorts = portsChangedPaths.isNotEmpty(),
                        includeRuntime = runtimeChangedPaths.isNotEmpty(),
                    ),
                ),
            )
            currentPorts = currentState.ports
            currentRuntime = currentState.runtime
        }

        if (forceStartOverheat && !currentState.isCurrentlyOverheated(now)) {
            applyPortsAndRuntime(
                nextPorts = currentPorts.map { port ->
                    if (port.overheated) {
                        port
                    } else {
                        port.copy(overheated = true)
                    }
                },
                nextRuntime = currentRuntime.copy(overheatStartedAtEpochMillis = now),
            )
        }

        val overheatStartedAt = currentRuntime.overheatStartedAtEpochMillis
        if (overheatStartedAt != null && now >= overheatStartedAt + OVERHEAT_COOLDOWN_MILLIS) {
            applyPortsAndRuntime(
                nextPorts = currentPorts.map { port ->
                    if (port.overheated) {
                        port.copy(overheated = false)
                    } else {
                        port
                    }
                },
                nextRuntime = currentRuntime.copy(overheatStartedAtEpochMillis = null),
            )
            for (port in currentState.ports.filter { it.health != MAX_PORT_HEALTH || it.healCount != 0 || it.weakenedAccess != null }) {
                currentState = currentState.applyWeakenedPortReset(
                    context = context,
                    stateId = stateId,
                    portNumber = port.number,
                )
                currentPorts = currentState.ports
                currentRuntime = currentState.runtime
            }
        }

        for (port in currentState.ports) {
            val weakenedAccess = port.weakenedAccess ?: continue
            if (now - weakenedAccess.lastAccessedAtEpochMillis < WEAKENED_ACCESS_TIMEOUT_MILLIS) {
                continue
            }
            currentState = currentState.applyWeakenedPortReset(
                context = context,
                stateId = stateId,
                portNumber = port.number,
            )
            currentPorts = currentState.ports
            currentRuntime = currentState.runtime
        }

        val passiveHealDue = currentRuntime.healCounter % currentState.effectivePassiveHealModifier().toLong() == 0L
        if (passiveHealDue) {
            val previousPorts = currentPorts
            val healedPorts = currentPorts.map { port ->
                if (!port.enabled || port.dummy || port.health >= MAX_PORT_HEALTH) {
                    port
                } else {
                    port.copy(health = (port.health + PASSIVE_HEAL_STEP).coerceAtMost(MAX_PORT_HEALTH))
                }
            }
            applyPortsAndRuntime(
                nextPorts = healedPorts,
                nextRuntime = currentRuntime,
            )
            for ((previousPort, healedPort) in previousPorts.zip(healedPorts)) {
                if (previousPort.health == healedPort.health) {
                    continue
                }
                currentState = currentState.appendHealthBaselineUpdateIfNeeded(
                    context = context,
                    stateId = stateId,
                    portNumber = healedPort.number,
                    baseline = healedPort.health,
                )
                currentPorts = currentState.ports
                currentRuntime = currentState.runtime
            }
        }

        applyPortsAndRuntime(
            nextPorts = currentPorts,
            nextRuntime = currentRuntime.copy(healCounter = currentRuntime.healCounter + 1),
        )
    }
}

internal class RefreshCombatMaintenanceRuntimeCommand(
    private val stateId: GameStateId,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val interestRegistry: InterestRegistry? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<Unit> {
    override val name: String = "refreshcombatmaintenanceruntime"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = emptySet()

    override suspend fun execute(context: CommandContext) {
        if (interestRegistry != null && interestRegistry.subscribersFor(stateId).isEmpty()) {
            return
        }
        val state = context.loadState(stateId) ?: return
        val now = clock()
        val forceStartOverheat = state.shouldStartOverheat(now)
        if (forceStartOverheat) {
            state.combat.activeAttacksBySourcePort.keys.sorted().forEach { sourcePort ->
                attackProgramRegistry.cancel(stateId, sourcePort, "overheated")
            }
        }
        context.request(
            ApplyCombatMaintenanceRuntimeCommand(
                stateId = stateId,
                forceStartOverheat = forceStartOverheat,
                now = now,
            ),
        )
    }
}

class CombatMaintenanceProgramCommand(
    private val stateId: GameStateId,
    override val programId: String,
    private val combatMaintenanceProgramRegistry: CombatMaintenanceProgramRegistry = NoOpCombatMaintenanceProgramRegistry,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val interestRegistry: InterestRegistry? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProgramCommand {
    override val name: String = "combat-maintenance-program"
    override val lifetime: CommandLifetime = CommandLifetime(COMBAT_MAINTENANCE_LIFETIME)
    override val targetStateIds: Set<GameStateId> = emptySet()
    override val programUpdateStateIds: Set<GameStateId> = emptySet()
    override val programType: String = "combat-maintenance"
    override val tickInterval = COMBAT_MAINTENANCE_REFRESH_INTERVAL

    override suspend fun onStart(context: CommandContext): ProgramExecutionStep {
        context.request(
            RefreshCombatMaintenanceRuntimeCommand(
                stateId = stateId,
                attackProgramRegistry = attackProgramRegistry,
                interestRegistry = interestRegistry,
                clock = clock,
            ),
        )
        return ProgramExecutionStep(
            status = ProgramLifecycleStatus.RUNNING,
            relatedStateIds = emptySet(),
        )
    }

    override suspend fun onTick(context: CommandContext): ProgramExecutionStep {
        if (!combatMaintenanceProgramRegistry.hasProgram(stateId)) {
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.COMPLETED,
                relatedStateIds = emptySet(),
            )
        }
        context.request(
            RefreshCombatMaintenanceRuntimeCommand(
                stateId = stateId,
                attackProgramRegistry = attackProgramRegistry,
                interestRegistry = interestRegistry,
                clock = clock,
            ),
        )
        return ProgramExecutionStep(
            status = ProgramLifecycleStatus.RUNNING,
            relatedStateIds = emptySet(),
        )
    }

    override suspend fun onCancel(context: CommandContext, reason: String) {
        combatMaintenanceProgramRegistry.unregister(programId)
    }
}

private fun List<PortState>.maintenanceChangedPaths(previous: List<PortState>): Set<String> {
    val previousByPort = previous.associateBy { it.number }
    return buildSet {
        for (port in this@maintenanceChangedPaths) {
            val previousPort = previousByPort[port.number] ?: continue
            if (previousPort.health != port.health) {
                add("ports.${port.number}.health")
            }
            if (previousPort.healCount != port.healCount) {
                add("ports.${port.number}.healCount")
            }
            if (previousPort.weakenedAccess != port.weakenedAccess) {
                add("ports.${port.number}.weakenedAccess")
            }
            if (previousPort.overheated != port.overheated) {
                add("ports.${port.number}.overheated")
            }
        }
    }
}
