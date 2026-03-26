package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.AttackCancelCurrentAttackEffect
import com.hackwars.rewrite.hackscript.AttackBerserkEffect
import com.hackwars.rewrite.hackscript.AttackDeleteTargetLogsEffect
import com.hackwars.rewrite.hackscript.AttackDestroyTargetWatchesEffect
import com.hackwars.rewrite.hackscript.AttackEmptyTargetPettyCashEffect
import com.hackwars.rewrite.hackscript.AttackEditTargetLogsEffect
import com.hackwars.rewrite.hackscript.AttackFreezeTargetPortEffect
import com.hackwars.rewrite.hackscript.AttackInstallTargetScriptEffect
import com.hackwars.rewrite.hackscript.AttackSelectRedirectCommodityEffect
import com.hackwars.rewrite.hackscript.AttackAppendHostLogEffect
import com.hackwars.rewrite.hackscript.AttackChangeDailyPayEffect
import com.hackwars.rewrite.hackscript.AttackSendMessageEffect
import com.hackwars.rewrite.hackscript.AttackShowChoicesEffect
import com.hackwars.rewrite.hackscript.AttackStealTargetFileEffect
import com.hackwars.rewrite.hackscript.AttackAuthorizeZombieEffect
import com.hackwars.rewrite.hackscript.AttackExecutionInput
import com.hackwars.rewrite.hackscript.AttackRuntimeEffect
import com.hackwars.rewrite.hackscript.AttackScriptEngine
import com.hackwars.rewrite.hackscript.AttackScriptOutcome
import com.hackwars.rewrite.hackscript.AttackSwitchTargetEffect
import org.slf4j.LoggerFactory

internal enum class AttackScriptPhase(
    val slot: ProgramScriptSlot,
) {
    INITIALIZE(ProgramScriptSlot.INITIALIZE),
    CONTINUE(ProgramScriptSlot.CONTINUE),
    FINALIZE(ProgramScriptSlot.FINALIZE),
}

internal data class AttackRuntimeApplyResult(
    val cancelRequested: Boolean = false,
    val suppressDamage: Boolean = false,
    val freezeRequested: Boolean = false,
    val berserkRequested: Boolean = false,
    val switchTargetRequested: Boolean = false,
    val selectedRedirectCommodityId: Int? = null,
    val authorizedZombieStateId: GameStateId? = null,
)

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
        targetStateId: GameStateId,
        phase: AttackScriptPhase,
        input: AttackExecutionInput,
        outcome: AttackScriptOutcome,
    ): AttackRuntimeApplyResult {
        logDiagnostics(
            attackerStateId = attackerStateId,
            phase = phase,
            input = input,
            outcome = outcome,
        )
        val result = outcome.result
        if (result == null) {
            return AttackRuntimeApplyResult()
        }

        var cancelRequested = false
        var suppressDamage = false
        var freezeRequested = false
        var berserkRequested = false
        var switchTargetRequested = false
        var selectedRedirectCommodityId: Int? = null
        var authorizedZombieStateId: GameStateId? = null
        result.effects.forEach { effect ->
            val helperCancelsAttack = phase == AttackScriptPhase.CONTINUE && (
                effect is AttackEditTargetLogsEffect ||
                    effect is AttackDeleteTargetLogsEffect
                )
            val effectResult = applyEffect(
                effect = effect,
                context = context,
                attackerStateId = attackerStateId,
                targetStateId = targetStateId,
            )
            cancelRequested = effectResult.cancelRequested || helperCancelsAttack || cancelRequested
            suppressDamage = effectResult.suppressDamage || suppressDamage
            freezeRequested = effectResult.freezeRequested || freezeRequested
            berserkRequested = effectResult.berserkRequested || berserkRequested
            switchTargetRequested = effectResult.switchTargetRequested || switchTargetRequested
            selectedRedirectCommodityId = effectResult.selectedRedirectCommodityId ?: selectedRedirectCommodityId
            authorizedZombieStateId = effectResult.authorizedZombieStateId ?: authorizedZombieStateId
        }
        return AttackRuntimeApplyResult(
            cancelRequested = cancelRequested,
            suppressDamage = suppressDamage,
            freezeRequested = freezeRequested,
            berserkRequested = berserkRequested,
            switchTargetRequested = switchTargetRequested,
            selectedRedirectCommodityId = selectedRedirectCommodityId,
            authorizedZombieStateId = authorizedZombieStateId,
        )
    }

    suspend fun execute(
        context: CommandContext,
        attackerState: ComputerState,
        sourcePort: Int,
        phase: AttackScriptPhase,
        input: AttackExecutionInput,
    ): AttackRuntimeApplyResult {
        val outcome = evaluate(
            attackerState = attackerState,
            sourcePort = sourcePort,
            phase = phase,
            input = input,
        ) ?: return AttackRuntimeApplyResult()
        return apply(
            context = context,
            attackerStateId = attackerState.id,
            targetStateId = GameStateId(input.targetIp),
            phase = phase,
            input = input,
            outcome = outcome,
        )
    }

    fun logDiagnostics(
        attackerStateId: GameStateId,
        phase: AttackScriptPhase,
        input: AttackExecutionInput,
        outcome: AttackScriptOutcome,
    ) {
        if (outcome.diagnostics.isEmpty()) {
            return
        }
        logger.warn(
            "Rewrite attack {} script produced diagnostics for attacker={} sourcePort={} target={}#{}: {}",
            phase.name.lowercase(),
            attackerStateId.value,
            input.sourcePort,
            input.targetIp,
            input.targetPort,
            outcome.diagnostics.joinToString { "${it.code}:${it.message}" },
        )
    }

    private suspend fun applyEffect(
        effect: AttackRuntimeEffect,
        context: CommandContext,
        attackerStateId: GameStateId,
        targetStateId: GameStateId,
    ): AttackRuntimeApplyResult {
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
                return AttackRuntimeApplyResult()
            }

            is AttackEditTargetLogsEffect -> {
                context.appendEvents(
                    id = targetStateId,
                    events = listOf(
                        HostLogRenderedTextReplacedEvent(
                            data = effect.data,
                            replace = effect.replace,
                        ),
                    ),
                )
                return AttackRuntimeApplyResult()
            }

            is AttackDeleteTargetLogsEffect -> {
                context.appendEvents(
                    id = targetStateId,
                    events = listOf(HostLogsDeletedBySourceIpEvent(sourceIp = effect.sourceIp)),
                )
                return AttackRuntimeApplyResult()
            }

            is AttackCancelCurrentAttackEffect -> return AttackRuntimeApplyResult(cancelRequested = true)
            is AttackFreezeTargetPortEffect -> {
                return AttackRuntimeApplyResult(
                    suppressDamage = true,
                    freezeRequested = true,
                )
            }
            is AttackBerserkEffect -> return AttackRuntimeApplyResult(berserkRequested = true)
            is AttackSwitchTargetEffect -> return AttackRuntimeApplyResult(switchTargetRequested = true)
            is AttackDestroyTargetWatchesEffect -> return AttackRuntimeApplyResult()
            is AttackEmptyTargetPettyCashEffect -> return AttackRuntimeApplyResult()
            is AttackStealTargetFileEffect -> return AttackRuntimeApplyResult()
            is AttackInstallTargetScriptEffect -> return AttackRuntimeApplyResult()
            is AttackChangeDailyPayEffect -> return AttackRuntimeApplyResult()
            is AttackShowChoicesEffect -> return AttackRuntimeApplyResult()
            is AttackSelectRedirectCommodityEffect -> {
                return AttackRuntimeApplyResult(selectedRedirectCommodityId = effect.commodityId)
            }
            is AttackSendMessageEffect -> {
                context.publishUiEvent(
                    targetStateIds = setOf(GameStateId(effect.targetIp)),
                    event = TextMessageUiEvent(effect.message),
                )
                return AttackRuntimeApplyResult()
            }
            is AttackAuthorizeZombieEffect -> {
                return AttackRuntimeApplyResult(
                    authorizedZombieStateId = GameStateId(effect.targetIp),
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
