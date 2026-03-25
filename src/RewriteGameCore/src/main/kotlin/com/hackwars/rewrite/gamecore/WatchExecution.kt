package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.AppendHostLogEffect
import com.hackwars.rewrite.hackscript.DepositPettyCashEffect
import com.hackwars.rewrite.hackscript.WatchExecutionInput
import com.hackwars.rewrite.hackscript.WatchRuntimeEffect
import com.hackwars.rewrite.hackscript.WatchScriptEngine
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
    private val clock: () -> Long = { System.currentTimeMillis() },
) : WatchExecutionCoordinator {
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
                currentCpuLoad = targetState.runtime.currentCpuLoad,
                maximumCpuLoad = targetState.hardware.cpuMax,
                triggered = true,
                external = intent.external,
                triggerParameters = intent.parameters,
            ),
        )
        val result = outcome.result
        if (result == null) {
            logger.warn(
                "Rewrite watch trigger failed for target={} selector={} source={}: {}",
                intent.targetStateId.value,
                intent.selector,
                intent.sourceIp,
                outcome.diagnostics.joinToString { "${it.code}:${it.message}" },
            )
            return WatchExecutionResult(matchedWatchIndex = matchedIndex, executed = false)
        }

        var currentState = targetState
        result.effects.forEach { effect ->
            currentState = applyEffect(
                effect = effect,
                context = context,
                targetStateId = intent.targetStateId,
                sourceIp = intent.sourceIp,
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
        }
    }

    private fun ComputerState.matchWatch(selector: TriggerSelector): Pair<Int, InstalledWatch>? {
        return when (selector) {
            is TriggerSelector.ByIndex -> watches.watches.getOrNull(selector.index)?.let { selector.index to it }
            is TriggerSelector.ByNote -> watches.watches
                .withIndex()
                .firstOrNull { (_, watch) -> watch.note == selector.note }
                ?.let { it.index to it.value }
        }
    }

    private fun InstalledWatch.executableScript(): String {
        return scriptBundle?.script(ProgramScriptSlot.FIRE)
            ?.takeUnless { it.isBlank() }
            ?: contents
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(DefaultWatchExecutionCoordinator::class.java)
    }
}
