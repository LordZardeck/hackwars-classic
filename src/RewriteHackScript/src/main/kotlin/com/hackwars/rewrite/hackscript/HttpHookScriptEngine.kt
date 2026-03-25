package com.hackwars.rewrite.hackscript

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class HttpHookScriptEngine(
    private val maxLoopIterations: Int = 10_000,
) {
    private val core = HackScriptEngineCore(maxLoopIterations)

    fun execute(
        script: String,
        input: HttpHookExecutionInput,
    ): HttpHookScriptOutcome {
        if (script.isBlank()) {
            return HttpHookScriptOutcome(
                result = HttpHookExecutionResult(
                    body = input.initialBody,
                    includeStore = input.initialIncludeStore,
                    effects = emptyList(),
                ),
            )
        }

        val state = HttpHookEngineState(input = input)
        val host = HttpHookHost(state)
        val outcome = core.execute(script = script, host = host)
        return if (outcome.success) {
            HttpHookScriptOutcome(
                result = HttpHookExecutionResult(
                    body = state.body,
                    includeStore = state.includeStore,
                    effects = state.effects.toList(),
                ),
                diagnostics = outcome.diagnostics,
            )
        } else {
            HttpHookScriptOutcome(
                result = null,
                diagnostics = outcome.diagnostics,
            )
        }
    }
}

data class HttpHookExecutionInput(
    val visitorIp: String,
    val hostIp: String,
    val hostIsNpc: Boolean = false,
    val initialBody: String,
    val initialIncludeStore: Boolean = true,
    val queryParameters: Map<String, String> = emptyMap(),
    val formParameters: Map<String, String> = emptyMap(),
)

@Serializable
sealed interface HttpHookEffect

@Serializable
@SerialName("append_host_log")
data class AppendHostLog(
    val message: String,
) : HttpHookEffect

@Serializable
@SerialName("popup_to_visitor")
data class PopupToVisitor(
    val message: String,
) : HttpHookEffect

@Serializable
@SerialName("trigger_local_watch")
data class TriggerLocalWatch(
    val index: Int,
    val parameters: Map<String, HookValue>,
) : HttpHookEffect

@Serializable
@SerialName("trigger_remote_watch")
data class TriggerRemoteWatch(
    val index: Int,
    val targetIp: String,
    val parameters: Map<String, HookValue>,
) : HttpHookEffect

@Serializable
data class HttpHookExecutionResult(
    val body: String,
    val includeStore: Boolean = true,
    val effects: List<HttpHookEffect> = emptyList(),
)

data class HttpHookScriptOutcome(
    val result: HttpHookExecutionResult?,
    val diagnostics: List<HackScriptDiagnostic> = emptyList(),
)

private data class HttpHookEngineState(
    val input: HttpHookExecutionInput,
    var body: String = input.initialBody,
    var includeStore: Boolean = input.initialIncludeStore,
    val diagnostics: MutableList<HackScriptDiagnostic> = mutableListOf(),
    val effects: MutableList<HttpHookEffect> = mutableListOf(),
    var popupCount: Int = 0,
)

private class HttpHookHost(
    private val state: HttpHookEngineState,
) : HackScriptHost {
    override val diagnostics: MutableList<HackScriptDiagnostic> = state.diagnostics

    override fun invokeFunction(name: String, arguments: List<HackValue>): HackValue {
        return when (name) {
            "getVisitorIP" -> HackValue.StringValue(state.input.visitorIp)
            "getHostIP" -> HackValue.StringValue(state.input.hostIp)
            "getParameter" -> HackValue.StringValue(parameter(arguments))
            "isParameterSet" -> HackValue.BooleanValue(parameter(arguments).isNotEmpty())
            "fetchGetVariable" -> HackValue.StringValue(queryParameter(arguments))
            "isGetVariableSet" -> HackValue.BooleanValue(queryParameter(arguments).isNotEmpty())
            "replaceContent" -> {
                ensure(arguments.size == 2, "BAD_ARGUMENT_COUNT", "replaceContent expects 2 arguments.")
                val key = arguments[0].asString()
                val value = arguments[1].asString()
                state.body = state.body.replace("<?$key?>", value)
                HackValue.IntValue(0)
            }

            "hideStore" -> {
                ensure(arguments.isEmpty(), "BAD_ARGUMENT_COUNT", "hideStore expects no arguments.")
                state.includeStore = false
                HackValue.IntValue(0)
            }

            "replaceAll" -> {
                ensure(arguments.size == 3, "BAD_ARGUMENT_COUNT", "replaceAll expects 3 arguments.")
                HackValue.StringValue(
                    arguments[0].asString().replace(
                        oldValue = arguments[1].asString(),
                        newValue = arguments[2].asString(),
                    ),
                )
            }

            "split" -> {
                ensure(arguments.size == 2, "BAD_ARGUMENT_COUNT", "split expects 2 arguments.")
                val delimiter = arguments[1].asString()
                HackValue.ArrayValue(
                    if (delimiter.isEmpty()) {
                        arguments[0].asString().map { HackValue.StringValue(it.toString()) }
                    } else {
                        arguments[0].asString().split(delimiter).map { HackValue.StringValue(it) }
                    },
                )
            }

            "length" -> {
                ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "length expects 1 argument.")
                HackValue.IntValue(
                    when (val argument = arguments[0]) {
                        is HackValue.StringValue -> argument.value.length
                        is HackValue.ArrayValue -> argument.values.size
                        else -> argument.asString().length
                    },
                )
            }

            "parseFloat" -> {
                ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "parseFloat expects 1 argument.")
                HackValue.FloatValue(arguments[0].asString().toDoubleOrNull() ?: 0.0)
            }

            "parseInt" -> {
                ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "parseInt expects 1 argument.")
                HackValue.IntValue(arguments[0].asString().toIntOrNull() ?: 0)
            }

            "logMessage" -> {
                if (arguments.size != 1) {
                    diagnostics += HackScriptDiagnostic(
                        code = "BAD_ARGUMENT_SHAPE",
                        message = "logMessage expects 1 argument.",
                    )
                    return HackValue.IntValue(0)
                }
                state.effects += AppendHostLog(arguments[0].asString())
                HackValue.IntValue(0)
            }

            "popUp" -> {
                if (arguments.size != 1) {
                    diagnostics += HackScriptDiagnostic(
                        code = "BAD_ARGUMENT_SHAPE",
                        message = "popUp expects 1 argument.",
                    )
                    return HackValue.IntValue(0)
                }
                if (state.popupCount >= 4) {
                    diagnostics += HackScriptDiagnostic(
                        code = "POPUP_LIMIT_EXCEEDED",
                        message = "popUp reached the per-slot limit of 4 events.",
                    )
                    return HackValue.IntValue(0)
                }
                state.popupCount += 1
                state.effects += PopupToVisitor(arguments[0].asString())
                HackValue.IntValue(0)
            }

            "triggerWatch" -> {
                val effect = buildLocalWatchEffect(arguments)
                if (effect != null) {
                    state.effects += effect
                }
                HackValue.IntValue(0)
            }

            "triggerWatchRemote" -> {
                if (!state.input.hostIsNpc) {
                    diagnostics += HackScriptDiagnostic(
                        code = "REMOTE_WATCH_REQUIRES_NPC_HOST",
                        message = "triggerWatchRemote only works when the host is NPC-controlled.",
                    )
                    return HackValue.IntValue(0)
                }
                val effect = buildRemoteWatchEffect(arguments)
                if (effect != null) {
                    state.effects += effect
                }
                HackValue.IntValue(0)
            }

            else -> throw unsupportedFunction(name)
        }
    }

    private fun buildLocalWatchEffect(arguments: List<HackValue>): TriggerLocalWatch? {
        if (arguments.isEmpty() || arguments.size % 2 == 0) {
            diagnostics += HackScriptDiagnostic(
                code = "BAD_ARGUMENT_SHAPE",
                message = "triggerWatch expects an index followed by key/value pairs.",
            )
            return null
        }
        if (arguments[0] is HackValue.ArrayValue) {
            diagnostics += HackScriptDiagnostic(
                code = "BAD_ARGUMENT_SHAPE",
                message = "triggerWatch index must be a scalar value.",
            )
            return null
        }
        val parameters = linkedMapOf<String, HookValue>()
        var index = 1
        while (index < arguments.size) {
            val key = (arguments[index] as? HackValue.StringValue)?.value
            val value = arguments.getOrNull(index + 1)?.toHookValue()
            if (key == null || value == null) {
                diagnostics += HackScriptDiagnostic(
                    code = "BAD_ARGUMENT_SHAPE",
                    message = "triggerWatch parameters must be alternating string keys and scalar or array values.",
                )
                return null
            }
            parameters[key] = value
            index += 2
        }
        return TriggerLocalWatch(arguments[0].asInt(), parameters)
    }

    private fun buildRemoteWatchEffect(arguments: List<HackValue>): TriggerRemoteWatch? {
        if (arguments.size < 2 || arguments.size % 2 != 0) {
            diagnostics += HackScriptDiagnostic(
                code = "BAD_ARGUMENT_SHAPE",
                message = "triggerWatchRemote expects an index, target IP, and key/value pairs.",
            )
            return null
        }
        if (arguments[0] is HackValue.ArrayValue) {
            diagnostics += HackScriptDiagnostic(
                code = "BAD_ARGUMENT_SHAPE",
                message = "triggerWatchRemote index must be a scalar value.",
            )
            return null
        }
        val targetIp = (arguments[1] as? HackValue.StringValue)?.value
        if (targetIp == null) {
            diagnostics += HackScriptDiagnostic(
                code = "BAD_ARGUMENT_SHAPE",
                message = "triggerWatchRemote target IP must be a string.",
            )
            return null
        }
        val parameters = linkedMapOf<String, HookValue>()
        var index = 2
        while (index < arguments.size) {
            val key = (arguments[index] as? HackValue.StringValue)?.value
            val value = arguments.getOrNull(index + 1)?.toHookValue()
            if (key == null || value == null) {
                diagnostics += HackScriptDiagnostic(
                    code = "BAD_ARGUMENT_SHAPE",
                    message = "triggerWatchRemote parameters must be alternating string keys and scalar or array values.",
                )
                return null
            }
            parameters[key] = value
            index += 2
        }
        return TriggerRemoteWatch(arguments[0].asInt(), targetIp, parameters)
    }

    private fun parameter(arguments: List<HackValue>): String {
        ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "Parameter lookup expects 1 argument.")
        return state.input.formParameters[arguments[0].asString()].orEmpty()
    }

    private fun queryParameter(arguments: List<HackValue>): String {
        ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "Query lookup expects 1 argument.")
        return state.input.queryParameters[arguments[0].asString()].orEmpty()
    }

    private fun ensure(condition: Boolean, code: String, message: String) {
        if (!condition) {
            throw ScriptFailure(code, message)
        }
    }

    private fun unsupportedFunction(name: String): Nothing {
        throw ScriptFailure("UNSUPPORTED_FUNCTION", "Unsupported function $name.")
    }
}
