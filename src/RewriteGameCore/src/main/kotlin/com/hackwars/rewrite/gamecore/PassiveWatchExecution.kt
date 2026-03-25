package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.WatchExecutionInput

sealed interface PassiveWatchTrigger {
    val targetStateId: GameStateId
    val sourceIp: String
    val external: Boolean

    data class PettyCashChanged(
        override val targetStateId: GameStateId,
        override val sourceIp: String,
        override val external: Boolean,
        val previousPettyCash: Double,
        val newPettyCash: Double,
    ) : PassiveWatchTrigger

    data class ScanSucceeded(
        override val targetStateId: GameStateId,
        override val sourceIp: String,
        override val external: Boolean,
    ) : PassiveWatchTrigger

    data class HealthChanged(
        override val targetStateId: GameStateId,
        override val sourceIp: String,
        override val external: Boolean,
        val portNumber: Int,
        val previousHealth: Double,
        val newHealth: Double,
    ) : PassiveWatchTrigger
}

internal class PassiveWatchCoordinator(
    private val runtimeExecutor: WatchRuntimeExecutor = WatchRuntimeExecutor(),
) : PassiveWatchTriggerSink {
    override suspend fun emit(
        context: CommandContext,
        trigger: PassiveWatchTrigger,
    ) {
        when (trigger) {
            is PassiveWatchTrigger.PettyCashChanged -> evaluatePettyCashChanged(context, trigger)
            is PassiveWatchTrigger.ScanSucceeded -> evaluateScanSucceeded(context, trigger)
            is PassiveWatchTrigger.HealthChanged -> Unit
        }
    }

    private suspend fun evaluatePettyCashChanged(
        context: CommandContext,
        trigger: PassiveWatchTrigger.PettyCashChanged,
    ) {
        val state = context.loadState(trigger.targetStateId) ?: return
        val updatedWatches = state.watches.watches.toMutableList()
        var watchesChanged = false
        var xpAward: Double? = null

        state.watches.watches.withIndex().forEach { (index, watch) ->
            if (!watch.enabled || watch.kind != WatchKind.PETTY_CASH) {
                return@forEach
            }

            val port = state.port(watch.installPort)
            if (port.isInstallPortOverheated(state)) {
                return@forEach
            }

            val transactionAmount = trigger.newPettyCash - watch.baselineQuantity
            val crossedThreshold =
                watch.baselineQuantity < watch.quantityThreshold &&
                    trigger.newPettyCash >= watch.quantityThreshold
            val bankingGateSatisfied = port != null && port.enabled && !port.dummy && port.isBankingApplication()
            if (crossedThreshold && bankingGateSatisfied) {
                if (xpAward == null) {
                    xpAward = transactionAmount / 50.0
                }
                runtimeExecutor.execute(
                    context = context,
                    targetStateId = trigger.targetStateId,
                    sourceIp = trigger.sourceIp,
                    matchedIndex = index,
                    watch = watch,
                    input = state.toWatchExecutionInput(
                        watch = watch,
                        targetIp = trigger.sourceIp,
                        targetPort = watch.installPort,
                        transactionAmount = transactionAmount,
                        external = trigger.external,
                    ),
                )
            }

            if (watch.baselineQuantity != trigger.newPettyCash) {
                updatedWatches[index] = watch.copy(baselineQuantity = trigger.newPettyCash)
                watchesChanged = true
            }
        }

        persistPassiveWatchUpdates(
            context = context,
            targetStateId = trigger.targetStateId,
            state = state,
            updatedWatches = updatedWatches,
            watchesChanged = watchesChanged,
            xpAward = xpAward,
        )
    }

    private suspend fun evaluateScanSucceeded(
        context: CommandContext,
        trigger: PassiveWatchTrigger.ScanSucceeded,
    ) {
        val state = context.loadState(trigger.targetStateId) ?: return
        var attemptedExecution = false

        state.watches.watches.withIndex().forEach { (index, watch) ->
            if (!watch.enabled || watch.kind != WatchKind.SCAN) {
                return@forEach
            }

            attemptedExecution = true
            runtimeExecutor.execute(
                context = context,
                targetStateId = trigger.targetStateId,
                sourceIp = trigger.sourceIp,
                matchedIndex = index,
                watch = watch,
                input = state.toWatchExecutionInput(
                    watch = watch,
                    targetIp = trigger.sourceIp,
                    targetPort = 0,
                    transactionAmount = 0.0,
                    external = trigger.external,
                ),
            )
        }

        if (attemptedExecution) {
            context.appendEvents(
                id = trigger.targetStateId,
                events = listOf(
                    SkillExperienceAdjustedEvent(
                        family = ScriptFamily.WATCH,
                        delta = state.stats.watchLevel().toDouble() / 4.0,
                    ),
                ),
            )
        }
    }

    private suspend fun persistPassiveWatchUpdates(
        context: CommandContext,
        targetStateId: GameStateId,
        state: ComputerState,
        updatedWatches: List<InstalledWatch>,
        watchesChanged: Boolean,
        xpAward: Double?,
    ) {
        val events = buildList {
            if (watchesChanged) {
                val latestState = context.loadState(targetStateId) ?: state
                add(
                    WatchManagerUpdatedEvent(
                        changedPathList = setOf("watches.watches"),
                        deltaKeyList = setOf("watches"),
                        watches = latestState.watches.copy(watches = updatedWatches),
                        currentCpuLoad = latestState.runtime.currentCpuLoad,
                        includeRuntime = false,
                    ),
                )
            }
            if (xpAward != null) {
                add(
                    SkillExperienceAdjustedEvent(
                        family = ScriptFamily.WATCH,
                        delta = xpAward,
                    ),
                )
            }
        }
        if (events.isNotEmpty()) {
            context.appendEvents(targetStateId, events)
        }
    }
}

val DefaultPassiveWatchCoordinator: PassiveWatchTriggerSink = PassiveWatchCoordinator()

internal suspend fun CommandContext.evaluatePassivePettyCashChange(
    targetStateId: GameStateId,
    previousPettyCash: Double,
    newPettyCash: Double,
    sourceIp: String = targetStateId.value,
    external: Boolean = sourceIp != targetStateId.value,
) {
    if (previousPettyCash == newPettyCash) {
        return
    }
    emitPassiveWatchTrigger(
        trigger = PassiveWatchTrigger.PettyCashChanged(
            targetStateId = targetStateId,
            sourceIp = sourceIp,
            external = external,
            previousPettyCash = previousPettyCash,
            newPettyCash = newPettyCash,
        ),
    )
}

internal suspend fun CommandContext.evaluatePassiveScanSuccess(
    targetStateId: GameStateId,
    sourceIp: String,
    external: Boolean = sourceIp != targetStateId.value,
) {
    emitPassiveWatchTrigger(
        trigger = PassiveWatchTrigger.ScanSucceeded(
            targetStateId = targetStateId,
            sourceIp = sourceIp,
            external = external,
        ),
    )
}

internal suspend fun CommandContext.emitPassiveHealthChange(
    targetStateId: GameStateId,
    sourceIp: String,
    portNumber: Int,
    previousHealth: Double,
    newHealth: Double,
    external: Boolean = sourceIp != targetStateId.value,
) {
    if (previousHealth == newHealth) {
        return
    }
    emitPassiveWatchTrigger(
        PassiveWatchTrigger.HealthChanged(
            targetStateId = targetStateId,
            sourceIp = sourceIp,
            external = external,
            portNumber = portNumber,
            previousHealth = previousHealth,
            newHealth = newHealth,
        ),
    )
}

internal fun PlayerStatsState.watchLevel(): Int = legacyLevelForXp(skillExperience(ScriptFamily.WATCH))

internal fun ComputerState.port(portNumber: Int): PortState? = ports.firstOrNull { it.number == portNumber }

private fun PortState?.isInstallPortOverheated(state: ComputerState): Boolean {
    return this != null && state.hardware.cpuMax > 0.0 && state.runtime.currentCpuLoad > state.hardware.cpuMax
}

private fun PortState.isBankingApplication(): Boolean {
    return installedApplication?.banking == true || installedApplication?.kind == ApplicationKind.BANKING
}

private fun ComputerState.toWatchExecutionInput(
    watch: InstalledWatch,
    targetIp: String,
    targetPort: Int,
    transactionAmount: Double,
    external: Boolean,
): WatchExecutionInput {
    return WatchExecutionInput(
        hostIp = id.value,
        targetIp = targetIp,
        sourceIp = id.value,
        targetPort = targetPort,
        installPort = watch.installPort,
        defaultBankPort = economy.defaultBankPort,
        pettyCash = economy.pettyCash,
        transactionAmount = transactionAmount,
        searchFirewallName = legacyWatchFirewallName(watch.searchFirewallType),
        currentCpuLoad = runtime.currentCpuLoad,
        maximumCpuLoad = hardware.cpuMax,
        triggered = true,
        external = external,
        triggerParameters = emptyMap(),
    )
}

internal fun legacyWatchFirewallName(rawType: Int): String {
    return LEGACY_WATCH_FIREWALL_NAMES.getOrElse(rawType) { "" }
}

private val LEGACY_WATCH_FIREWALL_NAMES: List<String> = listOf(
    "None",
    "PortProtector",
    "PwnPreventer",
    "DataShield",
    "PacketBuster",
    "TrafficTender",
    "DigitalFortress",
    "ForceField",
    "RubyGuardian",
    "DiamondDefender",
    "ADNArmour",
)
