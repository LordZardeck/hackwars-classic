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
    val isZombie: Boolean = false,
    val allowedZombieIps: Set<String> = emptySet(),
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

@Serializable
@SerialName("attack_steal_target_file")
data object AttackStealTargetFileEffect : AttackRuntimeEffect

@Serializable
@SerialName("attack_install_target_script")
data object AttackInstallTargetScriptEffect : AttackRuntimeEffect

@Serializable
@SerialName("attack_change_daily_pay")
data class AttackChangeDailyPayEffect(
    val targetIp: String,
) : AttackRuntimeEffect

@Serializable
@SerialName("attack_send_message")
data class AttackSendMessageEffect(
    val targetIp: String,
    val message: String,
) : AttackRuntimeEffect

@Serializable
@SerialName("attack_authorize_zombie")
data class AttackAuthorizeZombieEffect(
    val targetIp: String,
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
    var berserkTriggered: Boolean = false,
    var messageSent: Boolean = false,
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
            "isZombie" -> HackValue.BooleanValue(state.input.isZombie)
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

            "stealFile" -> {
                ensure(arguments.isEmpty(), "BAD_ARGUMENT_COUNT", "stealFile expects 0 arguments.")
                if (state.input.phase !in setOf(AttackExecutionPhase.CONTINUE, AttackExecutionPhase.FINALIZE)) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "stealFile is only supported during CONTINUE and FINALIZE.",
                    )
                } else {
                    state.effects += AttackStealTargetFileEffect
                }
                HackValue.IntValue(0)
            }

            "installScript" -> {
                ensure(arguments.isEmpty(), "BAD_ARGUMENT_COUNT", "installScript expects 0 arguments.")
                if (state.input.phase !in setOf(AttackExecutionPhase.CONTINUE, AttackExecutionPhase.FINALIZE)) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "installScript is only supported during CONTINUE and FINALIZE.",
                    )
                } else {
                    state.effects += AttackInstallTargetScriptEffect
                }
                HackValue.IntValue(0)
            }

            "changeDailyPay" -> {
                if (arguments.size != 1) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "BAD_ARGUMENT_COUNT",
                        message = "changeDailyPay expects 1 string argument.",
                    )
                } else if (arguments.single() !is HackValue.StringValue) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "BAD_ARGUMENT_TYPE",
                        message = "changeDailyPay expects 1 string argument.",
                    )
                } else if (state.input.phase !in setOf(AttackExecutionPhase.CONTINUE, AttackExecutionPhase.FINALIZE)) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "UNSUPPORTED_PHASE",
                        message = "changeDailyPay is only supported during CONTINUE and FINALIZE.",
                    )
                } else {
                    val targetIp = (arguments.single() as HackValue.StringValue).value
                    if (targetIp.isBlank()) {
                        state.diagnostics += HackScriptDiagnostic(
                            code = "INVALID_TARGET_IP",
                            message = "changeDailyPay target ip must not be blank.",
                        )
                    } else {
                        state.effects += AttackChangeDailyPayEffect(targetIp = targetIp)
                    }
                }
                HackValue.IntValue(0)
            }

            "message" -> {
                if (arguments.size != 2) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "BAD_ARGUMENT_COUNT",
                        message = "message expects 2 string arguments.",
                    )
                } else if (arguments.any { it !is HackValue.StringValue }) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "BAD_ARGUMENT_TYPE",
                        message = "message expects 2 string arguments.",
                    )
                } else if (state.messageSent) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "HELPER_LIMIT_REACHED",
                        message = "message may only be used once per execution.",
                    )
                } else {
                    val targetIp = (arguments[0] as HackValue.StringValue).value
                    val message = (arguments[1] as HackValue.StringValue).value
                    when {
                        targetIp != state.input.sourceIp && targetIp != state.input.targetIp -> {
                            state.diagnostics += HackScriptDiagnostic(
                                code = "INVALID_TARGET_IP",
                                message = "message target must be the current attacker or target ip.",
                            )
                        }

                        message.length >= 256 -> {
                            state.diagnostics += HackScriptDiagnostic(
                                code = "MESSAGE_TOO_LONG",
                                message = "message text must be shorter than 256 characters.",
                            )
                        }

                        else -> {
                            state.effects += AttackSendMessageEffect(
                                targetIp = targetIp,
                                message = message,
                            )
                            state.messageSent = true
                        }
                    }
                }
                HackValue.IntValue(0)
            }

            "zombie" -> {
                if (arguments.size != 1) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "BAD_ARGUMENT_COUNT",
                        message = "zombie expects 1 string argument.",
                    )
                } else if (arguments.single() !is HackValue.StringValue) {
                    state.diagnostics += HackScriptDiagnostic(
                        code = "BAD_ARGUMENT_TYPE",
                        message = "zombie expects 1 string argument.",
                    )
                } else {
                    val targetIp = (arguments.single() as HackValue.StringValue).value
                    if (targetIp.isBlank() || targetIp !in state.input.allowedZombieIps) {
                        state.diagnostics += HackScriptDiagnostic(
                            code = "INVALID_TARGET_IP",
                            message = "zombie target must match an allowed controller ip.",
                        )
                    } else {
                        state.effects += AttackAuthorizeZombieEffect(targetIp)
                    }
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
