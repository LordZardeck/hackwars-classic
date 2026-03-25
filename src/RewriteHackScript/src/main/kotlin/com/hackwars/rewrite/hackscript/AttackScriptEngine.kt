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

data class AttackExecutionInput(
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

            else -> throw ScriptFailure("UNSUPPORTED_FUNCTION", "Unsupported function $name.")
        }
    }

    private fun ensure(condition: Boolean, code: String, message: String) {
        if (!condition) {
            throw ScriptFailure(code, message)
        }
    }
}
