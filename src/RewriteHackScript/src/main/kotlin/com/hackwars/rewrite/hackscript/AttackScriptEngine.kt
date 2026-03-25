package com.hackwars.rewrite.hackscript

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AttackScriptEngine(
    private val maxLoopIterations: Int = 10_000,
) {
    private val core = HackScriptEngineCore(maxLoopIterations)

    fun execute(
        script: String,
        input: AttackExecutionInput,
    ): AttackScriptOutcome {
        val state = AttackEngineState(input = input)
        if (script.isBlank()) {
            return AttackScriptOutcome(
                result = AttackExecutionResult(effects = emptyList()),
                diagnostics = emptyList(),
            )
        }

        val host = AttackScriptHost(state)
        val outcome = core.execute(script = script, host = host)
        return if (outcome.success) {
            AttackScriptOutcome(
                result = AttackExecutionResult(state.effects.toList()),
                diagnostics = outcome.diagnostics,
            )
        } else {
            AttackScriptOutcome(
                result = null,
                diagnostics = outcome.diagnostics,
            )
        }
    }
}

enum class AttackExecutionPhase {
    INITIALIZE,
    CONTINUE,
    FINALIZE,
}

data class AttackExecutionInput(
    val phase: AttackExecutionPhase,
    val sourceIp: String,
    val sourcePort: Int,
    val targetIp: String,
    val targetPort: Int,
    val targetHealth: Double,
    val targetCpuCost: Double,
    val targetWatchPresent: Boolean,
    val targetPettyCash: Double,
    val hostHealth: Double,
    val currentCpuLoad: Double,
    val maximumCpuLoad: Double,
    val iterations: Int,
)

@Serializable
sealed interface AttackRuntimeEffect

@Serializable
@SerialName("attack_append_host_log")
data class AttackAppendHostLogEffect(
    val message: String,
) : AttackRuntimeEffect

@Serializable
@SerialName("attack_edit_target_logs")
data class AttackEditTargetLogsEffect(
    val data: String,
    val replace: String,
) : AttackRuntimeEffect

@Serializable
@SerialName("attack_delete_target_logs")
data class AttackDeleteTargetLogsEffect(
    val sourceIp: String,
) : AttackRuntimeEffect

@Serializable
@SerialName("attack_cancel_current_attack")
data object AttackCancelCurrentAttackEffect : AttackRuntimeEffect

@Serializable
@SerialName("attack_freeze_target_port")
data object AttackFreezeTargetPortEffect : AttackRuntimeEffect

@Serializable
@SerialName("attack_berserk")
data object AttackBerserkEffect : AttackRuntimeEffect

@Serializable
@SerialName("attack_switch_target")
data object AttackSwitchTargetEffect : AttackRuntimeEffect

@Serializable
@SerialName("attack_destroy_target_watches")
data object AttackDestroyTargetWatchesEffect : AttackRuntimeEffect

@Serializable
@SerialName("attack_empty_target_petty_cash")
data object AttackEmptyTargetPettyCashEffect : AttackRuntimeEffect

data class AttackExecutionResult(
    val effects: List<AttackRuntimeEffect> = emptyList(),
)

data class AttackScriptOutcome(
    val result: AttackExecutionResult?,
    val diagnostics: List<HackScriptDiagnostic> = emptyList(),
)

private data class AttackEngineState(
    val input: AttackExecutionInput,
    val diagnostics: MutableList<HackScriptDiagnostic> = mutableListOf(),
    val effects: MutableList<AttackRuntimeEffect> = mutableListOf(),
    var berserkTriggered: Boolean = false,
)

private class AttackScriptHost(
    private val state: AttackEngineState,
) : HackScriptHost {
    override val diagnostics: MutableList<HackScriptDiagnostic> = state.diagnostics

    override fun invokeFunction(name: String, arguments: List<HackValue>): HackValue {
        return when (name) {
            "getSourceIP" -> HackValue.StringValue(state.input.sourceIp)
            "getSourcePort" -> HackValue.IntValue(state.input.sourcePort)
            "getTargetIP" -> HackValue.StringValue(state.input.targetIp)
            "getTargetPort" -> HackValue.IntValue(state.input.targetPort)
            "getTargetHP" -> HackValue.FloatValue(state.input.targetHealth)
            "getTargetCPUCost" -> HackValue.FloatValue(state.input.targetCpuCost)
            "checkForWatch" -> HackValue.BooleanValue(state.input.targetWatchPresent)
            "checkPettyCash" -> HackValue.FloatValue(state.input.targetPettyCash)
            "getHP" -> HackValue.FloatValue(state.input.hostHealth)
            "getCPULoad" -> HackValue.FloatValue(state.input.currentCpuLoad)
            "getMaximumCPULoad" -> HackValue.FloatValue(state.input.maximumCpuLoad)
            "getIterations" -> HackValue.IntValue(state.input.iterations)
            "logMessage" -> {
                ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "logMessage expects 1 argument.")
                state.effects += AttackAppendHostLogEffect(arguments[0].asString())
                HackValue.IntValue(0)
            }

            "editLogs" -> {
                ensure(arguments.size == 2, "BAD_ARGUMENT_COUNT", "editLogs expects 2 arguments.")
                if (state.input.phase !in setOf(AttackExecutionPhase.CONTINUE, AttackExecutionPhase.FINALIZE)) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "editLogs is only supported during CONTINUE and FINALIZE.",
                    )
                } else {
                    state.effects += AttackEditTargetLogsEffect(
                        data = arguments[0].asString(),
                        replace = arguments[1].asString(),
                    )
                }
                HackValue.IntValue(0)
            }

            "deleteLogs" -> {
                ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "deleteLogs expects 1 argument.")
                if (state.input.phase !in setOf(AttackExecutionPhase.CONTINUE, AttackExecutionPhase.FINALIZE)) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "deleteLogs is only supported during CONTINUE and FINALIZE.",
                    )
                } else {
                    state.effects += AttackDeleteTargetLogsEffect(arguments[0].asString())
                }
                HackValue.IntValue(0)
            }

            "cancelAttack" -> {
                ensure(arguments.isEmpty(), "BAD_ARGUMENT_COUNT", "cancelAttack expects 0 arguments.")
                if (state.input.phase != AttackExecutionPhase.CONTINUE) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "cancelAttack is only supported during CONTINUE.",
                    )
                } else {
                    state.effects += AttackCancelCurrentAttackEffect
                }
                HackValue.IntValue(0)
            }

            "freeze" -> {
                ensure(arguments.isEmpty(), "BAD_ARGUMENT_COUNT", "freeze expects 0 arguments.")
                if (state.input.phase != AttackExecutionPhase.CONTINUE) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "freeze is only supported during CONTINUE.",
                    )
                } else {
                    state.effects += AttackFreezeTargetPortEffect
                }
                HackValue.IntValue(0)
            }

            "berserk" -> {
                ensure(arguments.isEmpty(), "BAD_ARGUMENT_COUNT", "berserk expects 0 arguments.")
                if (state.input.phase != AttackExecutionPhase.CONTINUE) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "berserk is only supported during CONTINUE.",
                    )
                } else if (state.berserkTriggered) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "HELPER_LIMIT_REACHED",
                        message = "berserk may only be used once per execution.",
                    )
                } else {
                    state.effects += AttackBerserkEffect
                    state.berserkTriggered = true
                }
                HackValue.IntValue(0)
            }

            "switchAttack" -> {
                ensure(arguments.isEmpty(), "BAD_ARGUMENT_COUNT", "switchAttack expects 0 arguments.")
                if (state.input.phase != AttackExecutionPhase.CONTINUE) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "switchAttack is only supported during CONTINUE.",
                    )
                } else {
                    state.effects += AttackSwitchTargetEffect
                }
                HackValue.IntValue(0)
            }

            "destroyWatches" -> {
                ensure(arguments.isEmpty(), "BAD_ARGUMENT_COUNT", "destroyWatches expects 0 arguments.")
                if (state.input.phase !in setOf(AttackExecutionPhase.CONTINUE, AttackExecutionPhase.FINALIZE)) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "destroyWatches is only supported during CONTINUE and FINALIZE.",
                    )
                } else {
                    state.effects += AttackDestroyTargetWatchesEffect
                }
                HackValue.IntValue(0)
            }

            "emptyPettyCash" -> {
                ensure(arguments.isEmpty(), "BAD_ARGUMENT_COUNT", "emptyPettyCash expects 0 arguments.")
                if (state.input.phase !in setOf(AttackExecutionPhase.CONTINUE, AttackExecutionPhase.FINALIZE)) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "emptyPettyCash is only supported during CONTINUE and FINALIZE.",
                    )
                } else {
                    state.effects += AttackEmptyTargetPettyCashEffect
                }
                HackValue.IntValue(0)
            }

            else -> throw ScriptFailure("UNSUPPORTED_FUNCTION", "Unsupported function $name.")
        }
    }

    private fun ensure(condition: Boolean, code: String, message: String) {
        if (!condition) {
            throw ScriptFailure(code, message)
        }
    }
}
