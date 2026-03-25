package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.AttackAppendHostLogEffect
import com.hackwars.rewrite.hackscript.AttackExecutionInput
import com.hackwars.rewrite.hackscript.AttackRuntimeEffect
import com.hackwars.rewrite.hackscript.AttackScriptEngine
import com.hackwars.rewrite.hackscript.AttackScriptOutcome
import org.slf4j.LoggerFactory

internal enum class AttackScriptPhase(
    val slot: ProgramScriptSlot,
) {
    INITIALIZE(ProgramScriptSlot.INITIALIZE),
    CONTINUE(ProgramScriptSlot.CONTINUE),
    FINALIZE(ProgramScriptSlot.FINALIZE),
}

internal class AttackRuntimeExecutor(
    private val engine: AttackScriptEngine = AttackScriptEngine(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun evaluate(
        attackerState: ComputerState,
        sourcePort: Int,
        phase: AttackScriptPhase,
        input: AttackExecutionInput,
    ): AttackScriptOutcome? {
        val script = attackerState.attackScript(sourcePort = sourcePort, slot = phase.slot)
        if (script.isBlank()) {
            return null
        }
        return engine.execute(script = script, input = input)
    }

    suspend fun apply(
        context: CommandContext,
        attackerStateId: GameStateId,
        phase: AttackScriptPhase,
        input: AttackExecutionInput,
        outcome: AttackScriptOutcome,
    ) {
        val result = outcome.result
        if (result == null) {
            logger.warn(
                "Rewrite attack {} script failed for attacker={} sourcePort={} target={}#{}: {}",
                phase.name.lowercase(),
                attackerStateId.value,
                input.sourcePort,
                input.targetIp,
                input.targetPort,
                outcome.diagnostics.joinToString { "${it.code}:${it.message}" },
            )
            return
        }

        result.effects.forEach { effect ->
            applyEffect(
                effect = effect,
                context = context,
                attackerStateId = attackerStateId,
            )
        }
    }

    suspend fun execute(
        context: CommandContext,
        attackerState: ComputerState,
        sourcePort: Int,
        phase: AttackScriptPhase,
        input: AttackExecutionInput,
    ) {
        val outcome = evaluate(
            attackerState = attackerState,
            sourcePort = sourcePort,
            phase = phase,
            input = input,
        ) ?: return
        apply(
            context = context,
            attackerStateId = attackerState.id,
            phase = phase,
            input = input,
            outcome = outcome,
        )
    }

    private suspend fun applyEffect(
        effect: AttackRuntimeEffect,
        context: CommandContext,
        attackerStateId: GameStateId,
    ) {
        when (effect) {
            is AttackAppendHostLogEffect -> {
                val createdAt = clock()
                context.appendEvents(
                    id = attackerStateId,
                    events = listOf(
                        HostLogAppendedEvent(
                            entry = ComputerLogEntry(
                                createdAtEpochMillis = createdAt,
                                renderedLine = renderLegacyLogLine(createdAt, effect.message),
                                sourceIp = attackerStateId.value,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(AttackRuntimeExecutor::class.java)
    }
}

internal val DefaultAttackRuntimeExecutor = AttackRuntimeExecutor()

internal fun ComputerState.attackScript(
    sourcePort: Int,
    slot: ProgramScriptSlot,
): String {
    return port(sourcePort)?.installedApplication?.scriptBundle?.script(slot).orEmpty()
}
