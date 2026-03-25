package com.hackwars.rewrite.hackscript

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class WatchScriptEngine(
    private val maxLoopIterations: Int = 10_000,
) {
    private val core = HackScriptEngineCore(maxLoopIterations)

    fun execute(
        script: String,
        input: WatchExecutionInput,
    ): WatchScriptOutcome {
        val state = WatchEngineState(input = input)
        if (script.isBlank()) {
            return WatchScriptOutcome(
                result = WatchExecutionResult(effects = emptyList()),
                diagnostics = emptyList(),
            )
        }

        val host = WatchScriptHost(state)
        val outcome = core.execute(script = script, host = host)
        return if (outcome.success) {
            WatchScriptOutcome(
                result = WatchExecutionResult(state.effects.toList()),
                diagnostics = outcome.diagnostics,
            )
        } else {
            WatchScriptOutcome(
                result = null,
                diagnostics = outcome.diagnostics,
            )
        }
    }
}

data class WatchExecutionInput(
    val hostIp: String,
    val targetIp: String,
    val sourceIp: String,
    val targetPort: Int = 0,
    val installPort: Int,
    val defaultBankPort: Int?,
    val pettyCash: Double,
    val transactionAmount: Double = 0.0,
    val searchFirewallName: String = "",
    val currentCpuLoad: Double,
    val maximumCpuLoad: Double,
    val triggered: Boolean,
    val external: Boolean,
    val triggerParameters: Map<String, HookValue> = emptyMap(),
)

@Serializable
sealed interface WatchRuntimeEffect

@Serializable
@SerialName("append_host_log")
data class AppendHostLogEffect(
    val message: String,
) : WatchRuntimeEffect

@Serializable
@SerialName("deposit_petty_cash")
data class DepositPettyCashEffect(
    val amount: Double? = null,
) : WatchRuntimeEffect

data class WatchExecutionResult(
    val effects: List<WatchRuntimeEffect> = emptyList(),
)

data class WatchScriptOutcome(
    val result: WatchExecutionResult?,
    val diagnostics: List<HackScriptDiagnostic> = emptyList(),
)

private data class WatchEngineState(
    val input: WatchExecutionInput,
    val diagnostics: MutableList<HackScriptDiagnostic> = mutableListOf(),
    val effects: MutableList<WatchRuntimeEffect> = mutableListOf(),
)

private class WatchScriptHost(
    private val state: WatchEngineState,
) : HackScriptHost {
    override val diagnostics: MutableList<HackScriptDiagnostic> = state.diagnostics

    override fun invokeFunction(name: String, arguments: List<HackValue>): HackValue {
        return when (name) {
            "getPort" -> HackValue.IntValue(state.input.installPort)
            "getTargetIP" -> HackValue.StringValue(state.input.targetIp)
            "getTargetPort" -> HackValue.IntValue(state.input.targetPort)
            "getSourceIP" -> HackValue.StringValue(state.input.hostIp)
            "getDefaultBank" -> HackValue.IntValue(state.input.defaultBankPort ?: 0)
            "isTriggered" -> HackValue.BooleanValue(state.input.triggered)
            "isTriggerParameterSet" -> HackValue.BooleanValue(triggerParameter(arguments).let(::isTriggerParameterSet))
            "getTriggerParameter" -> triggerParameter(arguments) ?: HackValue.StringValue("")
            "checkPettyCash" -> HackValue.FloatValue(state.input.pettyCash)
            "getTransactionAmount" -> HackValue.FloatValue(state.input.transactionAmount)
            "getSearchFireWall" -> HackValue.StringValue(state.input.searchFirewallName)
            "getCPULoad" -> HackValue.FloatValue(state.input.currentCpuLoad)
            "getMaximumCPULoad" -> HackValue.FloatValue(state.input.maximumCpuLoad)
            "logMessage" -> {
                ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "logMessage expects 1 argument.")
                state.effects += AppendHostLogEffect(arguments[0].asString())
                HackValue.IntValue(0)
            }

            "depositPettyCash" -> {
                ensure(arguments.size <= 1, "BAD_ARGUMENT_COUNT", "depositPettyCash expects 0 or 1 argument.")
                if (arguments.singleOrNull() is HackValue.ArrayValue) {
                    throw ScriptFailure(
                        code = "BAD_ARGUMENT_SHAPE",
                        message = "depositPettyCash amount must be a scalar value.",
                    )
                }
                state.effects += DepositPettyCashEffect(arguments.singleOrNull()?.asDouble())
                HackValue.IntValue(0)
            }

            else -> throw ScriptFailure("UNSUPPORTED_FUNCTION", "Unsupported function $name.")
        }
    }

    private fun triggerParameter(arguments: List<HackValue>): HackValue? {
        ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "Trigger parameter lookup expects 1 argument.")
        val key = arguments[0].asString()
        return state.input.triggerParameters[key]?.toHackValue()
    }

    private fun isTriggerParameterSet(value: HackValue?): Boolean {
        return when (value) {
            null -> false
            is HackValue.StringValue -> value.value.isNotEmpty()
            else -> true
        }
    }

    private fun ensure(condition: Boolean, code: String, message: String) {
        if (!condition) {
            throw ScriptFailure(code, message)
        }
    }
}
