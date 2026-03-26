package com.hackwars.rewrite.gamecore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val MAX_PORT_HEALTH: Double = 100.0
private const val HEAL_LIMIT: Int = 9

@Serializable
data class HealPortPayload(
    val ip: String,
    val port: Int,
)

@Serializable
data class FinalizeCancelledPayload(
    val ip: String,
    @SerialName("targetIP")
    val targetIp: String,
    val targetPort: Int,
)

class HealPortCommand(
    private val stateId: GameStateId,
    private val portNumber: Int,
) : RequestCommand<HealPortResponse> {
    override val name: String = "healport"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): HealPortResponse {
        val state = context.requireExistingState(stateId)
        val portState = state.port(portNumber)
            ?: return HealPortResponse(
                stateId = stateId,
                portNumber = portNumber,
                accepted = false,
                outcome = HealPortOutcome.PORT_NOT_FOUND,
                message = "Port $portNumber does not exist on ${stateId.value}.",
                chargedAmount = 0.0,
                pettyCashAfter = state.economy.pettyCash,
                version = state.version,
            )
        if (!portState.enabled || portState.dummy) {
            return HealPortResponse(
                stateId = stateId,
                portNumber = portNumber,
                accepted = false,
                outcome = HealPortOutcome.INVALID_PORT,
                message = "Port $portNumber is not available for healing.",
                chargedAmount = 0.0,
                pettyCashAfter = state.economy.pettyCash,
                healthAfter = portState.health,
                healCountAfter = portState.healCount,
                version = state.version,
            )
        }
        if (!state.hasActiveDefaultBankPort()) {
            return HealPortResponse(
                stateId = stateId,
                portNumber = portNumber,
                accepted = false,
                outcome = HealPortOutcome.ACTIVE_BANK_REQUIRED,
                message = "An active default bank port is required to heal $portNumber.",
                chargedAmount = 0.0,
                pettyCashAfter = state.economy.pettyCash,
                healthAfter = portState.health,
                healCountAfter = portState.healCount,
                version = state.version,
            )
        }
        if (state.isOverheatedForCleanup()) {
            return HealPortResponse(
                stateId = stateId,
                portNumber = portNumber,
                accepted = false,
                outcome = HealPortOutcome.OVERHEATED,
                message = "Computer ${stateId.value} is overheated.",
                chargedAmount = 0.0,
                pettyCashAfter = state.economy.pettyCash,
                healthAfter = portState.health,
                healCountAfter = portState.healCount,
                version = state.version,
            )
        }
        if (portState.isWeakened()) {
            return HealPortResponse(
                stateId = stateId,
                portNumber = portNumber,
                accepted = false,
                outcome = HealPortOutcome.WEAKENED,
                message = "Port $portNumber is weakened and cannot be healed directly.",
                chargedAmount = 0.0,
                pettyCashAfter = state.economy.pettyCash,
                healthAfter = portState.health,
                healCountAfter = portState.healCount,
                version = state.version,
            )
        }
        if (portState.healCount >= HEAL_LIMIT) {
            return HealPortResponse(
                stateId = stateId,
                portNumber = portNumber,
                accepted = false,
                outcome = HealPortOutcome.HEAL_LIMIT_REACHED,
                message = "Port $portNumber has reached the heal limit.",
                chargedAmount = 0.0,
                pettyCashAfter = state.economy.pettyCash,
                healthAfter = portState.health,
                healCountAfter = portState.healCount,
                version = state.version,
            )
        }

        val healCost = ((MAX_PORT_HEALTH - portState.health).coerceAtLeast(0.0) * 2.0) * state.effectiveHealCostMultiplier()
        if (state.economy.pettyCash < healCost) {
            return HealPortResponse(
                stateId = stateId,
                portNumber = portNumber,
                accepted = false,
                outcome = HealPortOutcome.INSUFFICIENT_PETTY_CASH,
                message = "Not enough petty cash to heal port $portNumber.",
                chargedAmount = 0.0,
                pettyCashAfter = state.economy.pettyCash,
                healthAfter = portState.health,
                healCountAfter = portState.healCount,
                version = state.version,
            )
        }

        var currentState = state
        if (healCost > 0.0) {
            currentState = context.appendEvents(
                id = stateId,
                events = listOf(
                    EconomyBalanceAdjustedEvent(pettyCashDelta = -healCost),
                ),
            )
            context.evaluatePassivePettyCashChange(
                targetStateId = stateId,
                previousPettyCash = state.economy.pettyCash,
                newPettyCash = currentState.economy.pettyCash,
                sourceIp = stateId.value,
                external = false,
            )
        }

        val currentPortState = requireNotNull(currentState.port(portNumber)) {
            "Port $portNumber disappeared during healport for ${stateId.value}."
        }
        val healedPortState = currentPortState.copy(
            health = MAX_PORT_HEALTH,
            healCount = currentPortState.healCount + 1,
        )
        if (healedPortState != currentPortState) {
            currentState = context.appendEvents(
                id = stateId,
                events = listOf(
                    CombatStateUpdatedEvent(
                        changedPathList = buildSet {
                            if (currentPortState.health != healedPortState.health) {
                                add("ports.$portNumber.health")
                            }
                            if (currentPortState.healCount != healedPortState.healCount) {
                                add("ports.$portNumber.healCount")
                            }
                        }.ifEmpty { setOf("ports.$portNumber") },
                        deltaKeyList = setOf("ports"),
                        combat = currentState.combat,
                        ports = currentState.ports.upsertPort(healedPortState, currentState.economy.defaultBankPort),
                        currentCpuLoad = currentState.runtime.currentCpuLoad,
                        includePorts = true,
                    ),
                ),
            )
        }

        currentState = currentState.appendHealthBaselineResetIfNeeded(
            context = context,
            stateId = stateId,
            portNumber = portNumber,
        )

        val responsePort = requireNotNull(currentState.port(portNumber))
        return HealPortResponse(
            stateId = stateId,
            portNumber = portNumber,
            accepted = true,
            outcome = HealPortOutcome.SUCCESS,
            message = "healport-succeeded",
            chargedAmount = healCost,
            pettyCashAfter = currentState.economy.pettyCash,
            healthAfter = responsePort.health,
            healCountAfter = responsePort.healCount,
            version = currentState.version,
        )
    }
}

class FinalizeCancelledCommand(
    private val actorStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val targetPort: Int,
) : RequestCommand<FinalizeCancelledResponse> {
    override val name: String = "finalizecancelled"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(actorStateId, targetStateId)

    override suspend fun execute(context: CommandContext): FinalizeCancelledResponse {
        context.requireExistingState(actorStateId)
        val targetState = context.loadState(targetStateId)
            ?: return FinalizeCancelledResponse(
                actorStateId = actorStateId,
                targetStateId = targetStateId,
                targetPort = targetPort,
                accepted = false,
                outcome = FinalizeCancelledOutcome.TARGET_NOT_FOUND,
                message = "Target ${targetStateId.value} does not exist.",
            )
        val targetPortState = targetState.port(targetPort)
            ?: return FinalizeCancelledResponse(
                actorStateId = actorStateId,
                targetStateId = targetStateId,
                targetPort = targetPort,
                accepted = false,
                outcome = FinalizeCancelledOutcome.INVALID_TARGET_PORT,
                message = "Port $targetPort does not exist on ${targetStateId.value}.",
                targetVersion = targetState.version,
            )
        if (!targetPortState.enabled || targetPortState.dummy) {
            return FinalizeCancelledResponse(
                actorStateId = actorStateId,
                targetStateId = targetStateId,
                targetPort = targetPort,
                accepted = false,
                outcome = FinalizeCancelledOutcome.INVALID_TARGET_PORT,
                message = "Port $targetPort is not available on ${targetStateId.value}.",
                targetHealthAfter = targetPortState.health,
                targetHealCountAfter = targetPortState.healCount,
                targetVersion = targetState.version,
            )
        }
        if (!targetPortState.isWeakened()) {
            return FinalizeCancelledResponse(
                actorStateId = actorStateId,
                targetStateId = targetStateId,
                targetPort = targetPort,
                accepted = true,
                outcome = FinalizeCancelledOutcome.NOT_WEAKENED,
                message = "Port $targetPort is not weakened.",
                targetHealthAfter = targetPortState.health,
                targetHealCountAfter = targetPortState.healCount,
                targetVersion = targetState.version,
            )
        }
        if (targetPortState.weakenedAccess?.actorStateId != actorStateId) {
            return FinalizeCancelledResponse(
                actorStateId = actorStateId,
                targetStateId = targetStateId,
                targetPort = targetPort,
                accepted = true,
                outcome = FinalizeCancelledOutcome.ACCESS_DENIED,
                message = "State ${actorStateId.value} does not own weakened access to ${targetStateId.value}:$targetPort.",
                targetHealthAfter = targetPortState.health,
                targetHealCountAfter = targetPortState.healCount,
                targetVersion = targetState.version,
            )
        }

        val updatedTarget = targetState.applyWeakenedPortReset(
            context = context,
            stateId = targetStateId,
            portNumber = targetPort,
        )
        val updatedPort = requireNotNull(updatedTarget.port(targetPort))
        return FinalizeCancelledResponse(
            actorStateId = actorStateId,
            targetStateId = targetStateId,
            targetPort = targetPort,
            accepted = true,
            outcome = FinalizeCancelledOutcome.SUCCESS,
            message = "finalizecancelled-succeeded",
            targetHealthAfter = updatedPort.health,
            targetHealCountAfter = updatedPort.healCount,
            targetVersion = updatedTarget.version,
        )
    }
}

internal data class HealthWatchBaselineReset(
    val watches: WatchManagerState,
    val changedIndices: Set<Int>,
)

internal fun ComputerState.effectiveHealCostMultiplier(): Double {
    return hardware.equipmentSlots.values.fold(1.0) { total, equipment ->
        total * equipment.healCostMultiplier.coerceAtLeast(0.0)
    }
}

internal fun ComputerState.isOverheatedForCleanup(): Boolean {
    return isCurrentlyOverheated(System.currentTimeMillis())
}

internal fun PortState.isWeakened(): Boolean = weakenedAccess != null

internal fun WatchManagerState.updateHealthBaselinesForPort(
    portNumber: Int,
    baselineQuantity: Double,
): HealthWatchBaselineReset? {
    val updatedWatches = watches.toMutableList()
    val changedIndices = linkedSetOf<Int>()
    watches.forEachIndexed { index, watch ->
        if (
            watch.kind == WatchKind.HEALTH &&
            watch.installPort == portNumber &&
            watch.baselineQuantity != baselineQuantity
        ) {
            updatedWatches[index] = watch.copy(baselineQuantity = baselineQuantity)
            changedIndices += index
        }
    }
    if (changedIndices.isEmpty()) {
        return null
    }
    return HealthWatchBaselineReset(
        watches = copy(watches = updatedWatches),
        changedIndices = changedIndices,
    )
}

internal fun WatchManagerState.resetHealthBaselinesForPort(portNumber: Int): HealthWatchBaselineReset? {
    return updateHealthBaselinesForPort(
        portNumber = portNumber,
        baselineQuantity = MAX_PORT_HEALTH,
    )
}

internal suspend fun ComputerState.appendHealthBaselineResetIfNeeded(
    context: CommandContext,
    stateId: GameStateId,
    portNumber: Int,
): ComputerState {
    return appendHealthBaselineUpdateIfNeeded(
        context = context,
        stateId = stateId,
        portNumber = portNumber,
        baseline = MAX_PORT_HEALTH,
    )
}

internal suspend fun ComputerState.appendHealthBaselineUpdateIfNeeded(
    context: CommandContext,
    stateId: GameStateId,
    portNumber: Int,
    baseline: Double,
): ComputerState {
    val baselineReset = watches.updateHealthBaselinesForPort(
        portNumber = portNumber,
        baselineQuantity = baseline,
    ) ?: return this
    return context.appendEvents(
        id = stateId,
        events = listOf(
            WatchManagerUpdatedEvent(
                changedPathList = baselineReset.changedIndices.mapTo(linkedSetOf()) { index ->
                    "watches.watches.$index.baselineQuantity"
                },
                deltaKeyList = setOf("watches"),
                watches = baselineReset.watches,
                currentCpuLoad = runtime.currentCpuLoad,
                includeRuntime = false,
            ),
        ),
    )
}

internal suspend fun ComputerState.applyWeakenedPortReset(
    context: CommandContext,
    stateId: GameStateId,
    portNumber: Int,
): ComputerState {
    val currentPort = requireNotNull(port(portNumber)) {
        "Port $portNumber does not exist on ${stateId.value} for weakened cleanup."
    }
    var currentState = this
    val resetPort = currentPort.copy(
        health = MAX_PORT_HEALTH,
        healCount = 0,
        weakenedAccess = null,
    )
    if (resetPort != currentPort) {
        currentState = context.appendEvents(
            id = stateId,
            events = listOf(
                CombatStateUpdatedEvent(
                    changedPathList = buildSet {
                        if (currentPort.health != resetPort.health) {
                            add("ports.$portNumber.health")
                        }
                        if (currentPort.healCount != resetPort.healCount) {
                            add("ports.$portNumber.healCount")
                        }
                        if (currentPort.weakenedAccess != resetPort.weakenedAccess) {
                            add("ports.$portNumber.weakenedAccess")
                        }
                    }.ifEmpty { setOf("ports.$portNumber") },
                    deltaKeyList = setOf("ports"),
                    combat = combat,
                    ports = ports.upsertPort(resetPort, economy.defaultBankPort),
                    currentCpuLoad = runtime.currentCpuLoad,
                    includePorts = true,
                ),
            ),
        )
    }
    return currentState.appendHealthBaselineResetIfNeeded(
        context = context,
        stateId = stateId,
        portNumber = portNumber,
    )
}
