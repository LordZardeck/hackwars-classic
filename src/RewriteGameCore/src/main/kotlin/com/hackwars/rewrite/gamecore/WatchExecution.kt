package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.AppendHostLogEffect
import com.hackwars.rewrite.hackscript.DepositPettyCashEffect
import com.hackwars.rewrite.hackscript.FloatHookValue
import com.hackwars.rewrite.hackscript.WatchExecutionInput
import com.hackwars.rewrite.hackscript.WatchRuntimeEffect
import com.hackwars.rewrite.hackscript.WatchScriptEngine
import com.hackwars.rewrite.hackscript.WatchZombieAttackEffect
import com.hackwars.rewrite.hackscript.StringHookValue
import org.slf4j.LoggerFactory

data class WatchExecutionResult(
    val matchedWatchIndex: Int? = null,
    val executed: Boolean = false,
)

fun interface WatchExecutionCoordinator {
    suspend fun execute(context: CommandContext, intent: WatchTriggerIntent): WatchExecutionResult
}

object NoOpWatchExecutionCoordinator : WatchExecutionCoordinator {
    override suspend fun execute(context: CommandContext, intent: WatchTriggerIntent): WatchExecutionResult {
        return WatchExecutionResult()
    }
}

class DefaultWatchExecutionCoordinator(
    private val engine: WatchScriptEngine = WatchScriptEngine(),
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : WatchExecutionCoordinator {
    private val runtimeExecutor = WatchRuntimeExecutor(
        engine = engine,
        attackProgramRegistry = attackProgramRegistry,
        clock = clock,
    )

    override suspend fun execute(context: CommandContext, intent: WatchTriggerIntent): WatchExecutionResult {
        val targetState = context.loadState(intent.targetStateId) ?: return WatchExecutionResult()
        val match = targetState.matchWatch(intent.selector) ?: return WatchExecutionResult()
        val (matchedIndex, watch) = match
        if (!watch.enabled) {
            return WatchExecutionResult(matchedWatchIndex = matchedIndex, executed = false)
        }

        val outcome = engine.execute(
            script = watch.executableScript(),
            input = WatchExecutionInput(
                hostIp = targetState.id.value,
                targetIp = intent.sourceIp,
                sourceIp = targetState.id.value,
                targetPort = 0,
                installPort = watch.installPort,
                defaultBankPort = targetState.economy.defaultBankPort,
                pettyCash = targetState.economy.pettyCash,
                transactionAmount = 0.0,
                searchFirewallName = "",
                currentCpuLoad = targetState.runtime.currentCpuLoad,
                maximumCpuLoad = targetState.hardware.cpuMax,
                triggered = true,
                external = intent.external,
                triggerParameters = intent.parameters,
            ),
        )
        return runtimeExecutor.execute(
            context = context,
            targetStateId = intent.targetStateId,
            sourceIp = intent.sourceIp,
            matchedIndex = matchedIndex,
            watch = watch,
            outcome = outcome,
            selector = intent.selector,
        )
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(DefaultWatchExecutionCoordinator::class.java)
    }
}

internal class WatchRuntimeExecutor(
    private val engine: WatchScriptEngine = WatchScriptEngine(),
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun execute(
        context: CommandContext,
        targetStateId: GameStateId,
        sourceIp: String,
        matchedIndex: Int,
        watch: InstalledWatch,
        input: WatchExecutionInput,
        selector: TriggerSelector? = null,
    ): WatchExecutionResult {
        val outcome = engine.execute(
            script = watch.executableScript(),
            input = input,
        )
        return execute(
            context = context,
            targetStateId = targetStateId,
            sourceIp = sourceIp,
            matchedIndex = matchedIndex,
            watch = watch,
            outcome = outcome,
            selector = selector,
        )
    }

    suspend fun execute(
        context: CommandContext,
        targetStateId: GameStateId,
        sourceIp: String,
        matchedIndex: Int,
        watch: InstalledWatch,
        outcome: com.hackwars.rewrite.hackscript.WatchScriptOutcome,
        selector: TriggerSelector? = null,
    ): WatchExecutionResult {
        val result = outcome.result
        if (result == null) {
            logger.warn(
                "Rewrite watch trigger failed for target={} selector={} source={}: {}",
                targetStateId.value,
                selector ?: "passive[$matchedIndex]",
                sourceIp,
                outcome.diagnostics.joinToString { "${it.code}:${it.message}" },
            )
            return WatchExecutionResult(matchedWatchIndex = matchedIndex, executed = false)
        }

        var currentState = context.loadState(targetStateId) ?: ComputerState.empty(id = targetStateId, playerIp = targetStateId.value)
        result.effects.forEach { effect ->
            currentState = applyEffect(
                effect = effect,
                context = context,
                targetStateId = targetStateId,
                sourceIp = sourceIp,
                watch = watch,
                currentState = currentState,
            )
        }

        return WatchExecutionResult(matchedWatchIndex = matchedIndex, executed = true)
    }

    private suspend fun applyEffect(
        effect: WatchRuntimeEffect,
        context: CommandContext,
        targetStateId: GameStateId,
        sourceIp: String,
        watch: InstalledWatch,
        currentState: ComputerState,
    ): ComputerState {
        return when (effect) {
            is AppendHostLogEffect -> {
                val createdAt = clock()
                context.appendEvents(
                    id = targetStateId,
                    events = listOf(
                        HostLogAppendedEvent(
                            entry = ComputerLogEntry(
                                createdAtEpochMillis = createdAt,
                                renderedLine = renderLegacyLogLine(createdAt, effect.message),
                                sourceIp = sourceIp,
                            ),
                        ),
                    ),
                )
            }

            is DepositPettyCashEffect -> {
                val portNumber = watch.installPort
                if (!currentState.hasBankPort(portNumber)) {
                    currentState
                } else {
                    val requestedAmount = effect.amount ?: currentState.economy.pettyCash
                    val appliedAmount = requestedAmount.coerceAtLeast(0.0).coerceAtMost(currentState.economy.pettyCash)
                    if (appliedAmount <= 0.0) {
                        currentState
                    } else {
                        context.appendEvents(
                            id = targetStateId,
                            events = listOf(
                                EconomyBalanceAdjustedEvent(
                                    pettyCashDelta = -appliedAmount,
                                    bankMoneyDelta = appliedAmount,
                                ),
                            ),
                        )
                    }
                }
            }

            is WatchZombieAttackEffect -> {
                StartZombieAttackSessionCommand(
                    controllerStateId = targetStateId,
                    zombieStateId = targetStateId,
                    targetStateId = GameStateId(effect.targetIp),
                    sourcePort = effect.sourcePort,
                    targetPort = effect.targetPort,
                    loadout = legacyWatchZombieAttackLoadout(),
                    attackProgramRegistry = attackProgramRegistry,
                    clock = clock,
                ).execute(context).also { result ->
                    val updatedHost = context.loadState(targetStateId) ?: currentState
                    publishZombieAttackUiEvents(
                        context = context,
                        response = ZombieAttackStartResponse(
                            controllerStateId = targetStateId,
                            zombieStateId = targetStateId,
                            sourcePort = effect.sourcePort,
                            targetStateId = GameStateId(effect.targetIp),
                            targetPort = effect.targetPort,
                            accepted = result.accepted,
                            failureCode = result.failureCode,
                            message = result.message,
                            chargedAmount = if (result.accepted) 20.0 else 0.0,
                            controllerPettyCashAfter = updatedHost.economy.pettyCash,
                            zombieCpuLoadAfter = updatedHost.runtime.currentCpuLoad,
                            session = result.session,
                            controllerVersion = updatedHost.version,
                            zombieVersion = updatedHost.version,
                        ),
                    )
                }
                context.loadState(targetStateId) ?: currentState
            }
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(WatchRuntimeExecutor::class.java)
    }
}

private fun legacyWatchZombieAttackLoadout(): AttackLoadout {
    return AttackLoadout(
        secondaryPorts = emptyList(),
        maliciousScripts = emptyList(),
        extraInfo = listOf(
            StringHookValue(""),
            FloatHookValue(0.0),
            StringHookValue(""),
            FloatHookValue(0.0),
            StringHookValue(""),
        ),
    )
}

internal fun ComputerState.matchWatch(selector: TriggerSelector): Pair<Int, InstalledWatch>? {
    return when (selector) {
        is TriggerSelector.ByIndex -> watches.watches.getOrNull(selector.index)?.let { selector.index to it }
        is TriggerSelector.ByNote -> watches.watches
            .withIndex()
            .firstOrNull { (_, watch) -> watch.note == selector.note }
            ?.let { it.index to it.value }
    }
}

internal fun InstalledWatch.executableScript(): String {
    return scriptBundle?.script(ProgramScriptSlot.FIRE)
        ?.takeUnless { it.isBlank() }
        ?: contents
}
