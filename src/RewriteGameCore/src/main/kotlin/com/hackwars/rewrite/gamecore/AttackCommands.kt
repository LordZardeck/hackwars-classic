package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.AttackExecutionPhase
import com.hackwars.rewrite.hackscript.AttackExecutionInput
import com.hackwars.rewrite.hackscript.AttackAppendHostLogEffect
import com.hackwars.rewrite.hackscript.AttackBerserkEffect
import com.hackwars.rewrite.hackscript.AttackCancelCurrentAttackEffect
import com.hackwars.rewrite.hackscript.AttackChangeDailyPayEffect
import com.hackwars.rewrite.hackscript.AttackDeleteTargetLogsEffect
import com.hackwars.rewrite.hackscript.AttackDestroyTargetWatchesEffect
import com.hackwars.rewrite.hackscript.AttackEmptyTargetPettyCashEffect
import com.hackwars.rewrite.hackscript.AttackEditTargetLogsEffect
import com.hackwars.rewrite.hackscript.AttackFreezeTargetPortEffect
import com.hackwars.rewrite.hackscript.AttackInstallTargetScriptEffect
import com.hackwars.rewrite.hackscript.AttackSelectRedirectCommodityEffect
import com.hackwars.rewrite.hackscript.AttackSendMessageEffect
import com.hackwars.rewrite.hackscript.AttackShowChoicesEffect
import com.hackwars.rewrite.hackscript.AttackStealTargetFileEffect
import com.hackwars.rewrite.hackscript.AttackSwitchTargetEffect
import com.hackwars.rewrite.hackscript.FloatHookValue
import com.hackwars.rewrite.hackscript.HookValue
import com.hackwars.rewrite.hackscript.IntHookValue
import com.hackwars.rewrite.hackscript.StringHookValue
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

private const val ATTACK_START_COST: Double = 10.0
private const val ZOMBIE_ATTACK_START_COST: Double = 20.0
private const val ATTACK_FREEZE_DURATION_MILLIS: Long = 10_000L
private val ATTACK_PROGRAM_TICK_INTERVAL = 180.seconds
private val ATTACK_PROGRAM_LIFETIME = 450.seconds
private const val REDIRECT_XP_CAP: Double = 2_000.0
private val REDIRECT_COMMODITY_XP = listOf(20.0, 40.0, 100.0, 400.0, 1_000.0)
private val REDIRECT_COMMODITY_NAMES = listOf("Duct Tape", "Germanium", "Silicon", "YBCO", "Plutonium")
private const val REDIRECT_FAIL_WRONG_TYPE_MESSAGE = "You cannot redirect shipments from a port that is not a redirect port."
private const val REDIRECT_FAIL_NOT_ENOUGH_MONEY_MESSAGE = "You must have \$10 in your petty cash to start a redirect."
private const val REDIRECT_FAIL_ALREADY_REDIRECTING_MESSAGE = "This port is already redirecting shipments."
private const val REDIRECT_FAIL_OVERHEATED_MESSAGE = "Can't redirect when port is overheated."
private const val REDIRECT_TIMEOUT_MESSAGE = "Your redirect application reached its maximum timeout for redirecting off one port."
private const val REDIRECT_FINISHED_PANE_MESSAGE = "Finished redirecting."

@Serializable
data class RequestAttackPayload(
    @SerialName("targetIP")
    val targetIp: String,
    val targetPort: Int,
    @SerialName("sourceIP")
    val sourceIp: String,
    val sourcePort: Int,
    val secondaryPorts: List<Int?>? = null,
    val scripts: List<List<String?>?>? = null,
    val extraInfo: List<HookValue>? = null,
    val windowHandle: Int? = null,
)

@Serializable
data class RequestCancelAttackPayload(
    val ip: String,
    val port: Int,
)

@Serializable
data class RequestZombieAttackPayload(
    @SerialName("targetIP")
    val targetIp: String,
    val targetPort: Int,
    @SerialName("sourceIP")
    val sourceIp: String? = null,
    val sourcePort: Int,
    @SerialName("I")
    val secondaryPorts: List<Int?>? = null,
    @SerialName("S")
    val scripts: List<List<String?>?>? = null,
    @SerialName("O")
    val extraInfo: List<HookValue>? = null,
    @SerialName("parentIP")
    val parentIp: String,
    val windowHandle: Int? = null,
)

@Serializable
data class RequestZombieCancelAttackPayload(
    val ip: String? = null,
    val port: Int,
    @SerialName("targetIP")
    val targetIp: String,
)

@Serializable
enum class AttackStartFailureCode {
    SOURCE_IP_MISMATCH,
    SOURCE_PORT_NOT_FOUND,
    INVALID_SOURCE_PORT,
    SOURCE_ALREADY_ATTACKING,
    SELF_TARGET,
    TARGET_NOT_FOUND,
    TARGET_PORT_NOT_FOUND,
    INVALID_TARGET_PORT,
    TARGET_ALREADY_UNDER_ATTACK,
    ACTIVE_BANK_REQUIRED,
    INSUFFICIENT_PETTY_CASH,
    OVERHEATED,
    CPU_HEADROOM_EXCEEDED,
    DEFAULT_SOURCE_PORT_MISSING,
}

@Serializable
enum class AttackCancelFailureCode {
    SOURCE_IP_MISMATCH,
}

@Serializable
enum class ZombieAttackStartFailureCode {
    CONTROLLER_IP_MISMATCH,
    ZOMBIE_STATE_NOT_FOUND,
    SELF_TARGET,
    SOURCE_PORT_NOT_FOUND,
    INVALID_SOURCE_PORT,
    SOURCE_ALREADY_ATTACKING,
    TARGET_NOT_FOUND,
    TARGET_PORT_NOT_FOUND,
    INVALID_TARGET_PORT,
    TARGET_ALREADY_UNDER_ATTACK,
    ACTIVE_BANK_REQUIRED,
    INSUFFICIENT_PETTY_CASH,
    ZOMBIE_OVERHEATED,
    CPU_HEADROOM_EXCEEDED,
    NOT_AUTHORIZED,
}

@Serializable
enum class ZombieAttackCancelFailureCode {
    CONTROLLER_IP_MISMATCH,
    ZOMBIE_STATE_NOT_FOUND,
}

@Serializable
data class AttackStartResponse(
    val attackerStateId: GameStateId,
    val sourcePort: Int,
    val targetStateId: GameStateId? = null,
    val targetPort: Int? = null,
    val accepted: Boolean,
    val failureCode: AttackStartFailureCode? = null,
    val message: String,
    val chargedAmount: Double = 0.0,
    val pettyCashAfter: Double? = null,
    val currentCpuLoadAfter: Double? = null,
    val session: AttackSessionState? = null,
    val version: Long,
)

@Serializable
data class AttackCancelResponse(
    val stateId: GameStateId,
    val sourcePort: Int,
    val accepted: Boolean,
    val failureCode: AttackCancelFailureCode? = null,
    val hadActiveSession: Boolean,
    val message: String,
    val version: Long,
)

@Serializable
data class ZombieAttackStartResponse(
    val controllerStateId: GameStateId,
    val zombieStateId: GameStateId,
    val sourcePort: Int,
    val targetStateId: GameStateId,
    val targetPort: Int,
    val accepted: Boolean,
    val failureCode: ZombieAttackStartFailureCode? = null,
    val message: String,
    val chargedAmount: Double = 0.0,
    val controllerPettyCashAfter: Double,
    val zombieCpuLoadAfter: Double? = null,
    val session: AttackSessionState? = null,
    val controllerVersion: Long,
    val zombieVersion: Long? = null,
)

@Serializable
data class ZombieAttackCancelResponse(
    val controllerStateId: GameStateId,
    val zombieStateId: GameStateId,
    val sourcePort: Int,
    val accepted: Boolean,
    val failureCode: ZombieAttackCancelFailureCode? = null,
    val hadActiveSession: Boolean,
    val message: String,
    val controllerVersion: Long,
    val zombieVersion: Long? = null,
)

@Serializable
@SerialName("combat_state_updated")
data class CombatStateUpdatedEvent(
    private val changedPathList: Set<String>,
    private val deltaKeyList: Set<String>,
    val combat: CombatState,
    val ports: List<PortState>,
    val currentCpuLoad: Double,
    val runtimeState: RuntimeState? = null,
    val includePorts: Boolean = false,
    val includeRuntime: Boolean = false,
) : ComputerEvent {
    override val changedPaths: Set<String> = changedPathList
    override val deltaKeys: Set<String> = deltaKeyList

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            combat = combat,
            ports = ports,
            runtime = (runtimeState ?: state.runtime.withCpuLoad(currentCpuLoad)).withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            combat = state.combat,
            ports = state.ports.takeIf { includePorts },
            runtime = state.runtime.takeIf { includeRuntime },
        )
    }
}

internal data class AttackInitializeResult(
    val session: AttackSessionState,
)

internal data class ZombieAttackStartResult(
    val accepted: Boolean,
    val message: String,
    val failureCode: ZombieAttackStartFailureCode? = null,
    val session: AttackSessionState? = null,
)

internal data class ZombieAttackCancelResult(
    val accepted: Boolean,
    val hadActiveSession: Boolean,
    val message: String,
    val failureCode: ZombieAttackCancelFailureCode? = null,
)

class RequestAttackCommand(
    private val attackerStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourceIp: String,
    private val sourcePort: Int,
    private val targetPort: Int,
    private val loadout: AttackLoadout,
    private val windowHandle: Int = 0,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<AttackStartResponse> {
    override val name: String = "requestattack"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(attackerStateId, targetStateId)

    override suspend fun execute(context: CommandContext): AttackStartResponse {
        val attackerState = context.requireExistingState(attackerStateId)
        val now = clock()
        if (sourceIp != attackerStateId.value) {
            return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.SOURCE_IP_MISMATCH,
                message = "Source ip $sourceIp does not match ${attackerStateId.value}.",
            )
        }
        if (targetStateId == attackerStateId) {
            return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.SELF_TARGET,
                message = "You cannot attack your own state.",
            )
        }

        val source = attackerState.port(sourcePort)
            ?: return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.SOURCE_PORT_NOT_FOUND,
                message = "Port $sourcePort does not exist on ${attackerStateId.value}.",
            )
        val sourceLooksRedirect = source.installedApplication?.kind == ApplicationKind.REDIRECT ||
            source.type.equals("redirect", ignoreCase = true)
        val sessionKind = when {
            source.isValidAttackSource() -> AttackSessionKind.ATTACK
            source.isValidRedirectSource() -> AttackSessionKind.REDIRECT
            else -> null
        }
        if (sessionKind == null) {
            return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.INVALID_SOURCE_PORT,
                message = "Port $sourcePort is not an active attack or redirect port.",
                intendedRedirect = sourceLooksRedirect,
                redirectWrongType = source.installedApplication?.kind != ApplicationKind.REDIRECT,
                windowHandle = windowHandle,
            )
        }
        if (source.attacking || attackerState.combat.activeAttacksBySourcePort.containsKey(sourcePort)) {
            return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.SOURCE_ALREADY_ATTACKING,
                message = if (sessionKind == AttackSessionKind.REDIRECT) {
                    REDIRECT_FAIL_ALREADY_REDIRECTING_MESSAGE
                } else {
                    "Port $sourcePort is already attacking."
                },
                intendedRedirect = sessionKind == AttackSessionKind.REDIRECT,
                windowHandle = windowHandle,
            )
        }

        val targetState = context.loadState(targetStateId)
            ?: return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.TARGET_NOT_FOUND,
                message = "Target ${targetStateId.value} does not exist.",
                intendedRedirect = sessionKind == AttackSessionKind.REDIRECT,
            )
        val resolvedTargetPort = targetState.port(targetPort)
            ?: return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.TARGET_PORT_NOT_FOUND,
                message = "Target port $targetPort does not exist on ${targetStateId.value}.",
                intendedRedirect = sessionKind == AttackSessionKind.REDIRECT,
            )
        val targetIsValid = when (sessionKind) {
            AttackSessionKind.ATTACK -> resolvedTargetPort.isValidAttackTarget(
                now = now,
                allowFrozen = false,
                allowOverheated = false,
            )
            AttackSessionKind.REDIRECT -> resolvedTargetPort.isValidRedirectTarget()
        }
        if (!targetIsValid) {
            return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.INVALID_TARGET_PORT,
                message = if (sessionKind == AttackSessionKind.REDIRECT &&
                    resolvedTargetPort.installedApplication?.kind != ApplicationKind.REDIRECT
                ) {
                    REDIRECT_FAIL_WRONG_TYPE_MESSAGE
                } else {
                    "Target port $targetPort is not attackable."
                },
                intendedRedirect = sessionKind == AttackSessionKind.REDIRECT,
                redirectWrongType = sessionKind == AttackSessionKind.REDIRECT &&
                    resolvedTargetPort.installedApplication?.kind != ApplicationKind.REDIRECT,
                windowHandle = windowHandle,
            )
        }
        if (targetState.combat.incomingAttacksByTargetPort.containsKey(targetPort)) {
            return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.TARGET_ALREADY_UNDER_ATTACK,
                message = "Target port $targetPort is already under attack.",
                intendedRedirect = sessionKind == AttackSessionKind.REDIRECT,
                windowHandle = windowHandle,
            )
        }
        if (!attackerState.hasActiveDefaultBankPort()) {
            return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.ACTIVE_BANK_REQUIRED,
                message = "Attacking requires an active default banking port.",
                intendedRedirect = sessionKind == AttackSessionKind.REDIRECT,
            )
        }
        if (attackerState.economy.pettyCash < ATTACK_START_COST) {
            return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.INSUFFICIENT_PETTY_CASH,
                message = if (sessionKind == AttackSessionKind.REDIRECT) {
                    REDIRECT_FAIL_NOT_ENOUGH_MONEY_MESSAGE
                } else {
                    "Attacking requires at least \$10 petty cash."
                },
                intendedRedirect = sessionKind == AttackSessionKind.REDIRECT,
            )
        }
        if (attackerState.isCurrentlyOverheated(now)) {
            return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.OVERHEATED,
                message = if (sessionKind == AttackSessionKind.REDIRECT) {
                    REDIRECT_FAIL_OVERHEATED_MESSAGE
                } else {
                    "You cannot start an attack while overheated."
                },
                intendedRedirect = sessionKind == AttackSessionKind.REDIRECT,
                windowHandle = windowHandle,
            )
        }

        val reservedCpu = source.currentAttackCpuCost()
        val nextCpuLoad = attackerState.runtime.currentCpuLoad + reservedCpu
        if (nextCpuLoad > attackerState.hardware.cpuMax) {
            return failure(
                context = context,
                attackerState = attackerState,
                code = AttackStartFailureCode.CPU_HEADROOM_EXCEEDED,
                message = "Starting this attack would exceed the current CPU limit.",
                intendedRedirect = sessionKind == AttackSessionKind.REDIRECT,
            )
        }

        val programId = "${sessionKind.name.lowercase()}-${attackerStateId.value}-$sourcePort-${UUID.randomUUID()}"
        val initializeResult = context.request(
            AttackInitializeCommand(
                attackerStateId = attackerStateId,
                actorStateId = attackerStateId,
                targetStateId = targetStateId,
                sourcePort = sourcePort,
                targetPort = targetPort,
                programId = programId,
                loadout = loadout,
                windowHandle = windowHandle,
                reservedCpu = reservedCpu,
                chargedAmount = ATTACK_START_COST,
                sessionKind = sessionKind,
                attackMode = AttackMode.DIRECT,
                attackRuntimeExecutor = DefaultAttackRuntimeExecutor,
                clock = clock,
            ),
        )

        val handle = context.schedule(
            when (sessionKind) {
                AttackSessionKind.ATTACK -> {
                    AttackProgramCommand(
                        attackerStateId = attackerStateId,
                        targetStateId = targetStateId,
                        sourcePort = sourcePort,
                        programId = programId,
                        controllerStateId = null,
                        attackProgramRegistry = attackProgramRegistry,
                        clock = clock,
                    )
                }
                AttackSessionKind.REDIRECT -> {
                    RedirectProgramCommand(
                        attackerStateId = attackerStateId,
                        targetStateId = targetStateId,
                        sourcePort = sourcePort,
                        programId = programId,
                        attackProgramRegistry = attackProgramRegistry,
                        clock = clock,
                    )
                }
            },
        )
        attackProgramRegistry.register(attackerStateId, sourcePort, programId, handle)

        val finalState = context.requireExistingState(attackerStateId)
        return AttackStartResponse(
            attackerStateId = attackerStateId,
            sourcePort = sourcePort,
            targetStateId = targetStateId,
            targetPort = targetPort,
            accepted = true,
            message = if (sessionKind == AttackSessionKind.REDIRECT) "redirect-started" else "attack-started",
            chargedAmount = ATTACK_START_COST,
            pettyCashAfter = finalState.economy.pettyCash,
            currentCpuLoadAfter = finalState.runtime.currentCpuLoad,
            session = finalState.combat.activeAttacksBySourcePort[sourcePort] ?: initializeResult.session,
            version = finalState.version,
        )
    }

    private suspend fun failure(
        context: CommandContext,
        attackerState: ComputerState,
        code: AttackStartFailureCode,
        message: String,
        intendedRedirect: Boolean = false,
        redirectWrongType: Boolean = false,
        windowHandle: Int? = null,
    ): AttackStartResponse {
        val response = AttackStartResponse(
            attackerStateId = attackerState.id,
            sourcePort = sourcePort,
            targetStateId = targetStateId,
            targetPort = targetPort,
            accepted = false,
            failureCode = code,
            message = message,
            version = attackerState.version,
        )
        if (intendedRedirect) {
            publishRedirectAttackStartUiEvents(
                context = context,
                response = response,
                attackerStateId = attackerState.id,
                sourcePort = sourcePort,
                windowHandle = windowHandle,
                redirectWrongType = redirectWrongType,
            )
        }
        return response
    }
}

class RequestCancelAttackCommand(
    private val attackerStateId: GameStateId,
    private val sourceIp: String,
    private val sourcePort: Int,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
) : RequestCommand<AttackCancelResponse> {
    override val name: String = "requestcancelattack"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = emptySet()

    override suspend fun execute(context: CommandContext): AttackCancelResponse {
        val attackerState = context.requireExistingState(attackerStateId)
        if (sourceIp != attackerStateId.value) {
            return AttackCancelResponse(
                stateId = attackerStateId,
                sourcePort = sourcePort,
                accepted = false,
                failureCode = AttackCancelFailureCode.SOURCE_IP_MISMATCH,
                hadActiveSession = false,
                message = "Source ip $sourceIp does not match ${attackerStateId.value}.",
                version = attackerState.version,
            )
        }

        val session = attackerState.combat.activeAttacksBySourcePort[sourcePort]
        if (session == null) {
            return AttackCancelResponse(
                stateId = attackerStateId,
                sourcePort = sourcePort,
                accepted = true,
                hadActiveSession = false,
                message = "attack-not-running",
                version = attackerState.version,
            )
        }

        val cancelled = attackProgramRegistry.cancel(
            stateId = attackerStateId,
            sourcePort = sourcePort,
            reason = if (session.sessionKind == AttackSessionKind.REDIRECT) {
                "redirect-cancelled"
            } else {
                "requestcancelattack"
            },
        )
        if (!cancelled) {
            attackProgramRegistry.unregister(session.programId)
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = session.targetStateId,
                    sourcePort = sourcePort,
                    targetPort = session.targetPort,
                ),
            )
            context.publishProgramUpdate(
                ProgramUpdate(
                    programId = session.programId,
                    programType = session.programType(),
                    status = ProgramLifecycleStatus.CANCELLED,
                    relatedStateIds = setOf(attackerStateId),
                    progress = ProgramProgress(
                        message = if (session.sessionKind == AttackSessionKind.REDIRECT) {
                            "redirect-cancelled"
                        } else {
                            "requestcancelattack"
                        },
                    ),
                ),
            )
        }

        val updatedState = context.requireExistingState(attackerStateId)
        return AttackCancelResponse(
            stateId = attackerStateId,
            sourcePort = sourcePort,
            accepted = true,
            hadActiveSession = true,
            message = if (session.sessionKind == AttackSessionKind.REDIRECT) {
                "redirect-cancelled"
            } else {
                "attack-cancelled"
            },
            version = updatedState.version,
        )
    }
}

class RequestAttackDefaultCommand(
    private val attackerStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val targetPort: Int,
    private val target: String,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<AttackStartResponse> {
    override val name: String = "requestattackdefault"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(attackerStateId, targetStateId)

    override suspend fun execute(context: CommandContext): AttackStartResponse {
        val attackerState = context.requireExistingState(attackerStateId)
        val sourcePort = when (target) {
            "Bank" -> attackerState.economy.defaultBankPort
            "Attack" -> attackerState.defaultAttackPort()
            else -> null
        } ?: return AttackStartResponse(
            attackerStateId = attackerStateId,
            sourcePort = -1,
            targetStateId = targetStateId,
            targetPort = targetPort,
            accepted = false,
            failureCode = AttackStartFailureCode.DEFAULT_SOURCE_PORT_MISSING,
            message = "No default $target port is available on ${attackerStateId.value}.",
            version = attackerState.version,
        )

        return context.request(
            RequestAttackCommand(
                attackerStateId = attackerStateId,
                targetStateId = targetStateId,
                sourceIp = attackerStateId.value,
                sourcePort = sourcePort,
                targetPort = targetPort,
                loadout = AttackLoadout(
                    secondaryPorts = listOf(0),
                    maliciousScripts = emptyList(),
                    extraInfo = emptyList(),
                ),
                windowHandle = 0,
                attackProgramRegistry = attackProgramRegistry,
                clock = clock,
            ),
        )
    }
}

class RequestZombieAttackCommand(
    private val controllerStateId: GameStateId,
    private val controllerIp: String,
    private val zombieStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourcePort: Int,
    private val targetPort: Int,
    private val loadout: AttackLoadout,
    private val windowHandle: Int = 0,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<ZombieAttackStartResponse> {
    override val name: String = "requestzombieattack"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(controllerStateId, zombieStateId, targetStateId)

    override suspend fun execute(context: CommandContext): ZombieAttackStartResponse {
        val controllerState = context.requireExistingState(controllerStateId)
        if (controllerIp != controllerStateId.value) {
            return zombieAttackStartFailure(
                controllerState = controllerState,
                zombieStateId = zombieStateId,
                targetStateId = targetStateId,
                sourcePort = sourcePort,
                targetPort = targetPort,
                code = ZombieAttackStartFailureCode.CONTROLLER_IP_MISMATCH,
                message = "Controller ip $controllerIp does not match ${controllerStateId.value}.",
            )
        }

        val zombieState = context.loadState(zombieStateId)
        val result = context.request(
            StartZombieAttackSessionCommand(
                controllerStateId = controllerStateId,
                zombieStateId = zombieStateId,
                targetStateId = targetStateId,
                sourcePort = sourcePort,
                targetPort = targetPort,
                loadout = loadout,
                windowHandle = windowHandle,
                attackProgramRegistry = attackProgramRegistry,
                clock = clock,
            ),
        )
        val updatedController = context.requireExistingState(controllerStateId)
        val updatedZombie = context.loadState(zombieStateId)
        val response = ZombieAttackStartResponse(
            controllerStateId = controllerStateId,
            zombieStateId = zombieStateId,
            sourcePort = sourcePort,
            targetStateId = targetStateId,
            targetPort = targetPort,
            accepted = result.accepted,
            failureCode = result.failureCode,
            message = result.message,
            chargedAmount = if (result.accepted) ZOMBIE_ATTACK_START_COST else 0.0,
            controllerPettyCashAfter = updatedController.economy.pettyCash,
            zombieCpuLoadAfter = updatedZombie?.runtime?.currentCpuLoad ?: zombieState?.runtime?.currentCpuLoad,
            session = result.session,
            controllerVersion = updatedController.version,
            zombieVersion = updatedZombie?.version ?: zombieState?.version,
        )
        publishZombieAttackUiEvents(
            context = context,
            response = response,
        )
        return response
    }
}

class RequestZombieCancelAttackCommand(
    private val controllerStateId: GameStateId,
    private val controllerIp: String,
    private val zombieStateId: GameStateId,
    private val sourcePort: Int,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
) : RequestCommand<ZombieAttackCancelResponse> {
    override val name: String = "requestzombiecancelattack"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = emptySet()

    override suspend fun execute(context: CommandContext): ZombieAttackCancelResponse {
        val controllerState = context.requireExistingState(controllerStateId)
        if (controllerIp != controllerStateId.value) {
            return ZombieAttackCancelResponse(
                controllerStateId = controllerStateId,
                zombieStateId = zombieStateId,
                sourcePort = sourcePort,
                accepted = false,
                failureCode = ZombieAttackCancelFailureCode.CONTROLLER_IP_MISMATCH,
                hadActiveSession = false,
                message = "Controller ip $controllerIp does not match ${controllerStateId.value}.",
                controllerVersion = controllerState.version,
            )
        }

        val zombieState = context.loadState(zombieStateId)
        val result = context.request(
            CancelZombieAttackSessionCommand(
                controllerStateId = controllerStateId,
                zombieStateId = zombieStateId,
                sourcePort = sourcePort,
                attackProgramRegistry = attackProgramRegistry,
            ),
        )
        val updatedController = context.requireExistingState(controllerStateId)
        val updatedZombie = context.loadState(zombieStateId)
        return ZombieAttackCancelResponse(
            controllerStateId = controllerStateId,
            zombieStateId = zombieStateId,
            sourcePort = sourcePort,
            accepted = result.accepted,
            failureCode = result.failureCode,
            hadActiveSession = result.hadActiveSession,
            message = result.message,
            controllerVersion = updatedController.version,
            zombieVersion = updatedZombie?.version ?: zombieState?.version,
        )
    }
}

internal class StartZombieAttackSessionCommand(
    private val controllerStateId: GameStateId,
    private val zombieStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourcePort: Int,
    private val targetPort: Int,
    private val loadout: AttackLoadout,
    private val windowHandle: Int = 0,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val attackRuntimeExecutor: AttackRuntimeExecutor = DefaultAttackRuntimeExecutor,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<ZombieAttackStartResult> {
    override val name: String = "startzombieattacksession"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(controllerStateId, zombieStateId, targetStateId)

    override suspend fun execute(context: CommandContext): ZombieAttackStartResult {
        val controllerState = context.requireExistingState(controllerStateId)
        val zombieState = context.loadState(zombieStateId)
            ?: return ZombieAttackStartResult(
                accepted = false,
                message = "zombie-state-missing",
                failureCode = ZombieAttackStartFailureCode.ZOMBIE_STATE_NOT_FOUND,
            )
        if (targetStateId == zombieStateId) {
            return ZombieAttackStartResult(
                accepted = false,
                message = "self-target",
                failureCode = ZombieAttackStartFailureCode.SELF_TARGET,
            )
        }

        val source = zombieState.port(sourcePort)
            ?: return ZombieAttackStartResult(
                accepted = false,
                message = "source-port-missing",
                failureCode = ZombieAttackStartFailureCode.SOURCE_PORT_NOT_FOUND,
            )
        if (!source.isValidAttackSource()) {
            return ZombieAttackStartResult(
                accepted = false,
                message = "invalid-source-port",
                failureCode = ZombieAttackStartFailureCode.INVALID_SOURCE_PORT,
            )
        }
        if (source.attacking || zombieState.combat.activeAttacksBySourcePort.containsKey(sourcePort)) {
            return ZombieAttackStartResult(
                accepted = false,
                message = "source-already-attacking",
                failureCode = ZombieAttackStartFailureCode.SOURCE_ALREADY_ATTACKING,
            )
        }

        val targetState = context.loadState(targetStateId)
            ?: return ZombieAttackStartResult(
                accepted = false,
                message = "target-missing",
                failureCode = ZombieAttackStartFailureCode.TARGET_NOT_FOUND,
            )
        val resolvedTargetPort = targetState.port(targetPort)
            ?: return ZombieAttackStartResult(
                accepted = false,
                message = "target-port-missing",
                failureCode = ZombieAttackStartFailureCode.TARGET_PORT_NOT_FOUND,
            )
        if (!resolvedTargetPort.isValidAttackTarget(now = clock(), allowFrozen = false, allowOverheated = false)) {
            return ZombieAttackStartResult(
                accepted = false,
                message = "invalid-target-port",
                failureCode = ZombieAttackStartFailureCode.INVALID_TARGET_PORT,
            )
        }
        if (targetState.combat.incomingAttacksByTargetPort.containsKey(targetPort)) {
            return ZombieAttackStartResult(
                accepted = false,
                message = "target-already-under-attack",
                failureCode = ZombieAttackStartFailureCode.TARGET_ALREADY_UNDER_ATTACK,
            )
        }
        if (!controllerState.hasActiveDefaultBankPort()) {
            return ZombieAttackStartResult(
                accepted = false,
                message = "controller-active-bank-required",
                failureCode = ZombieAttackStartFailureCode.ACTIVE_BANK_REQUIRED,
            )
        }
        if (controllerState.economy.pettyCash < ZOMBIE_ATTACK_START_COST) {
            return ZombieAttackStartResult(
                accepted = false,
                message = "controller-insufficient-petty-cash",
                failureCode = ZombieAttackStartFailureCode.INSUFFICIENT_PETTY_CASH,
            )
        }
        if (zombieState.isCurrentlyOverheated(clock())) {
            return ZombieAttackStartResult(
                accepted = false,
                message = "zombie-overheated",
                failureCode = ZombieAttackStartFailureCode.ZOMBIE_OVERHEATED,
            )
        }

        val reservedCpu = source.currentAttackCpuCost()
        if (zombieState.runtime.currentCpuLoad + reservedCpu > zombieState.hardware.cpuMax) {
            return ZombieAttackStartResult(
                accepted = false,
                message = "cpu-headroom-exceeded",
                failureCode = ZombieAttackStartFailureCode.CPU_HEADROOM_EXCEEDED,
            )
        }

        val admissionResult = attackRuntimeExecutor.execute(
            context = context,
            attackerState = zombieState,
            sourcePort = sourcePort,
            phase = AttackScriptPhase.INITIALIZE,
            input = zombieState.toAttackExecutionInput(
                phase = AttackExecutionPhase.INITIALIZE,
                sourcePort = sourcePort,
                targetView = targetState.buildAttackTargetView(targetPort, lastAppliedDamage = 0.0, completed = false),
                iterations = 0,
                sourceIpOverride = controllerStateId.value,
                isZombie = false,
                allowedZombieIps = setOf(controllerStateId.value),
            ),
        )
        if (admissionResult.authorizedZombieStateId != controllerStateId) {
            return ZombieAttackStartResult(
                accepted = false,
                message = "zombie-not-authorized",
                failureCode = ZombieAttackStartFailureCode.NOT_AUTHORIZED,
            )
        }

        val programId = "attack-${zombieStateId.value}-$sourcePort-${UUID.randomUUID()}"
        val initializeResult = AttackInitializeCommand(
            attackerStateId = zombieStateId,
            actorStateId = controllerStateId,
            targetStateId = targetStateId,
            sourcePort = sourcePort,
            targetPort = targetPort,
            programId = programId,
            loadout = loadout,
            windowHandle = windowHandle,
            reservedCpu = reservedCpu,
            chargedAmount = ZOMBIE_ATTACK_START_COST,
            attackMode = AttackMode.ZOMBIE,
            controllerStateId = controllerStateId,
            authorizedZombieStateId = controllerStateId,
            runInitializeScript = false,
            attackRuntimeExecutor = attackRuntimeExecutor,
            clock = clock,
        ).execute(context)

        val handle = context.schedule(
            AttackProgramCommand(
                attackerStateId = zombieStateId,
                targetStateId = targetStateId,
                sourcePort = sourcePort,
                programId = programId,
                controllerStateId = controllerStateId,
                attackProgramRegistry = attackProgramRegistry,
                clock = clock,
            ),
        )
        attackProgramRegistry.register(zombieStateId, sourcePort, programId, handle)
        return ZombieAttackStartResult(
            accepted = true,
            message = "zombie-attack-started",
            session = initializeResult.session,
        )
    }
}

internal class CancelZombieAttackSessionCommand(
    private val controllerStateId: GameStateId,
    private val zombieStateId: GameStateId,
    private val sourcePort: Int,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
) : RequestCommand<ZombieAttackCancelResult> {
    override val name: String = "cancelzombieattacksession"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = emptySet()

    override suspend fun execute(context: CommandContext): ZombieAttackCancelResult {
        val zombieState = context.loadState(zombieStateId)
            ?: return ZombieAttackCancelResult(
                accepted = false,
                hadActiveSession = false,
                message = "zombie-state-missing",
                failureCode = ZombieAttackCancelFailureCode.ZOMBIE_STATE_NOT_FOUND,
            )
        val session = zombieState.combat.activeAttacksBySourcePort[sourcePort]
        if (session == null || session.attackMode != AttackMode.ZOMBIE || session.controllerStateId != controllerStateId) {
            return ZombieAttackCancelResult(
                accepted = true,
                hadActiveSession = false,
                message = "zombie-attack-not-running",
            )
        }

        val cancelled = attackProgramRegistry.cancel(
            stateId = zombieStateId,
            sourcePort = sourcePort,
            reason = "cancelzombieattacksession",
        )
        if (!cancelled) {
            attackProgramRegistry.unregister(session.programId)
            context.request(
                AttackReleaseCommand(
                    attackerStateId = zombieStateId,
                    targetStateId = session.targetStateId,
                    sourcePort = sourcePort,
                    targetPort = session.targetPort,
                ),
            )
            context.publishProgramUpdate(
                ProgramUpdate(
                    programId = session.programId,
                    programType = session.programType(),
                    status = ProgramLifecycleStatus.CANCELLED,
                    relatedStateIds = setOf(controllerStateId),
                    progress = ProgramProgress(message = "cancelzombieattacksession"),
                ),
            )
        }

        return ZombieAttackCancelResult(
            accepted = true,
            hadActiveSession = true,
            message = "zombie-attack-cancelled",
        )
    }
}

internal class AttackInitializeCommand(
    private val attackerStateId: GameStateId,
    private val actorStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourcePort: Int,
    private val targetPort: Int,
    private val programId: String,
    private val loadout: AttackLoadout,
    private val windowHandle: Int,
    private val reservedCpu: Double,
    private val chargedAmount: Double,
    private val sessionKind: AttackSessionKind = AttackSessionKind.ATTACK,
    private val attackMode: AttackMode,
    private val controllerStateId: GameStateId? = null,
    private val authorizedZombieStateId: GameStateId? = null,
    private val runInitializeScript: Boolean = true,
    private val attackRuntimeExecutor: AttackRuntimeExecutor = DefaultAttackRuntimeExecutor,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<AttackInitializeResult> {
    override val name: String = "attackinitialize"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(attackerStateId, actorStateId, targetStateId)

    override suspend fun execute(context: CommandContext): AttackInitializeResult {
        val attackerState = context.requireExistingState(attackerStateId)
        val actorState = if (actorStateId == attackerStateId) {
            attackerState
        } else {
            context.requireExistingState(actorStateId)
        }
        val targetState = context.requireExistingState(targetStateId)
        val startedAt = clock()
        val targetView = targetState.buildAttackTargetView(targetPort, lastAppliedDamage = 0.0, completed = false)
        val session = AttackSessionState(
            programId = programId,
            sourcePort = sourcePort,
            targetStateId = targetStateId,
            targetPort = targetPort,
            sessionKind = sessionKind,
            attackMode = attackMode,
            controllerStateId = controllerStateId,
            authorizedZombieStateId = authorizedZombieStateId,
            targetView = targetView,
            targetCyclePorts = buildTargetCyclePorts(
                initialTargetPort = targetPort,
                secondaryPorts = loadout.secondaryPorts,
            ),
            targetCycleCursor = 0,
            windowHandle = windowHandle,
            secondaryPorts = loadout.secondaryPorts,
            maliciousScripts = loadout.maliciousScripts,
            extraInfo = loadout.extraInfo,
            startedAtEpochMillis = startedAt,
            iterationCount = 0,
        )
        if (chargedAmount != 0.0) {
            val updatedActor = context.appendEvents(
                id = actorStateId,
                events = listOf(EconomyBalanceAdjustedEvent(pettyCashDelta = -chargedAmount)),
            )
            context.evaluatePassivePettyCashChange(
                targetStateId = actorStateId,
                previousPettyCash = actorState.economy.pettyCash,
                newPettyCash = updatedActor.economy.pettyCash,
            )
        }
        val updatedAttacker = context.appendEvents(
            id = attackerStateId,
            events = listOf(
                CombatStateUpdatedEvent(
                    changedPathList = setOf(
                        "combat.activeAttacksBySourcePort.$sourcePort",
                        "ports.$sourcePort.attacking",
                        "runtime.currentCpuLoad",
                    ),
                    deltaKeyList = setOf("ports", "combat", "runtime"),
                    combat = attackerState.combat.withSession(session),
                    ports = attackerState.ports.markAttacking(sourcePort, true),
                    currentCpuLoad = attackerState.runtime.currentCpuLoad + reservedCpu,
                    includePorts = true,
                includeRuntime = true,
                ),
            ),
        )
        var persistedSession = session
        var persistedAttackerState = updatedAttacker
        context.appendEvents(
            id = targetStateId,
            events = listOf(
                CombatStateUpdatedEvent(
                    changedPathList = setOf("combat.incomingAttacksByTargetPort.$targetPort"),
                    deltaKeyList = setOf("combat"),
                    combat = targetState.combat.withIncomingAttack(
                        IncomingAttackState(
                            attackerStateId = attackerStateId,
                            attackerSourcePort = sourcePort,
                            targetPort = targetPort,
                            startedAtEpochMillis = startedAt,
                            windowHandle = windowHandle,
                        ),
                    ),
                    ports = targetState.ports,
                    currentCpuLoad = targetState.runtime.currentCpuLoad,
                ),
            ),
        )
        if (runInitializeScript) {
            val initializeResult = attackRuntimeExecutor.execute(
                context = context,
                attackerState = persistedAttackerState,
                sourcePort = sourcePort,
                phase = AttackScriptPhase.INITIALIZE,
                input = persistedAttackerState.toAttackExecutionInput(
                    phase = AttackExecutionPhase.INITIALIZE,
                    sourcePort = sourcePort,
                    targetView = persistedSession.targetView,
                    iterations = 0,
                    sourceIpOverride = actorStateId.value,
                    isZombie = false,
                    allowedZombieIps = controllerStateId?.let { setOf(it.value) }.orEmpty(),
                ),
            )
            val selectedRedirectCommodityId = initializeResult.selectedRedirectCommodityId
            if (
                selectedRedirectCommodityId != null &&
                selectedRedirectCommodityId != persistedSession.redirectCommodityId
            ) {
                persistedSession = persistedSession.copy(redirectCommodityId = selectedRedirectCommodityId)
                persistedAttackerState = context.appendEvents(
                    id = attackerStateId,
                    events = listOf(
                        CombatStateUpdatedEvent(
                            changedPathList = setOf("combat.activeAttacksBySourcePort.$sourcePort"),
                            deltaKeyList = setOf("combat"),
                            combat = persistedAttackerState.combat.withSession(persistedSession),
                            ports = persistedAttackerState.ports,
                            currentCpuLoad = persistedAttackerState.runtime.currentCpuLoad,
                        ),
                    ),
                )
            }
        }
        return AttackInitializeResult(session = persistedSession)
    }
}

internal class AttackTickCommand(
    private val attackerStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourcePort: Int,
    private val controllerStateId: GameStateId? = null,
    private val attackRuntimeExecutor: AttackRuntimeExecutor = DefaultAttackRuntimeExecutor,
    private val attackActorResolver: AttackActorResolver = DefaultAttackActorResolver,
    private val firewallCombatResolver: FirewallCombatResolver = DefaultFirewallCombatResolver,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<ProgramExecutionStep> {
    override val name: String = "attackcontinue"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = buildSet {
        add(attackerStateId)
        add(targetStateId)
        controllerStateId?.let(::add)
    }

    override suspend fun execute(context: CommandContext): ProgramExecutionStep {
        val fallbackProgramUpdateStateIds = controllerStateId?.let { setOf(it) } ?: setOf(attackerStateId)
        val attackerState = context.requireExistingState(attackerStateId)
        val sourcePortState = attackerState.port(sourcePort)
        if (sourcePortState == null) {
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                ),
            )
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "source port missing"),
                relatedStateIds = fallbackProgramUpdateStateIds,
            )
        }
        val session = attackerState.combat.activeAttacksBySourcePort[sourcePort]?.withResolvedTargetCycle()
        if (session == null) {
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                ),
            )
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "attack session missing"),
                relatedStateIds = fallbackProgramUpdateStateIds,
            )
        }
        if (session.sessionKind != AttackSessionKind.ATTACK) {
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                    targetPort = session.targetPort,
                ),
            )
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "wrong session kind"),
                relatedStateIds = fallbackProgramUpdateStateIds,
            )
        }
        val actorContext = attackActorResolver.resolve(attackerStateId, session)
        val actorStateId = actorContext.actorStateId
        val programUpdateStateIds = setOf(actorStateId)
        val actorState = if (actorStateId == attackerStateId) {
            attackerState
        } else {
            context.loadState(actorStateId)
        } ?: run {
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                    targetPort = session.targetPort,
                ),
            )
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "controller missing"),
                relatedStateIds = programUpdateStateIds,
            )
        }

        val targetState = context.loadState(targetStateId)
        val targetPortState = targetState?.port(session.targetPort)
        val incoming = targetState?.combat?.incomingAttacksByTargetPort?.get(session.targetPort)
        if (
            targetState == null ||
            targetPortState == null ||
            !targetPortState.isValidAttackTarget(now = clock(), allowFrozen = true, allowOverheated = true) ||
            incoming == null ||
            incoming.attackerStateId != attackerStateId ||
            incoming.attackerSourcePort != sourcePort
        ) {
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                    targetPort = session.targetPort,
                ),
            )
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "target unavailable"),
                relatedStateIds = programUpdateStateIds,
            )
        }

        if (targetPortState.health <= 0.0) {
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                    targetPort = session.targetPort,
                ),
            )
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.COMPLETED,
                progress = ProgramProgress(
                    message = "completed",
                    completedSteps = session.iterationCount,
                ),
                relatedStateIds = programUpdateStateIds,
            )
        }

        val initialSession: AttackSessionState = session
        val initialTargetState: ComputerState = targetState
        val initialTargetPortState: PortState = targetPortState
        val currentTargetView = initialTargetState.buildAttackTargetView(
            targetPort = initialSession.targetPort,
            lastAppliedDamage = initialSession.targetView.lastAppliedDamage,
            completed = false,
            healthOverride = initialTargetPortState.health,
        )
        val continueInput = attackerState.toAttackExecutionInput(
            phase = AttackExecutionPhase.CONTINUE,
            sourcePort = sourcePort,
            targetView = currentTargetView,
            iterations = initialSession.iterationCount + 1,
            sourceIpOverride = actorStateId.value,
            isZombie = initialSession.attackMode == AttackMode.ZOMBIE,
            allowedZombieIps = initialSession.controllerStateId?.let { setOf(it.value) }.orEmpty(),
        )
        val continueOutcome = attackRuntimeExecutor.evaluate(
            attackerState = attackerState,
            sourcePort = sourcePort,
            phase = AttackScriptPhase.CONTINUE,
            input = continueInput,
        )
        val baseDamage = actorState.attackBaseDamage()
        val nextIteration = initialSession.iterationCount + 1
        var currentSession: AttackSessionState = initialSession
        var currentTargetCombat: CombatState = initialTargetState.combat
        var currentTargetEconomy: EconomyState = initialTargetState.economy
        var currentTargetFilesystem: FilesystemState = initialTargetState.filesystem
        var currentTargetLogs: LogState = initialTargetState.logs
        var currentTargetPorts: List<PortState> = initialTargetState.ports
        var currentTargetWatches: WatchManagerState = initialTargetState.watches
        var currentTargetRuntimeCpuLoad: Double = initialTargetState.runtime.currentCpuLoad
        var currentTargetState: ComputerState = initialTargetState
        var currentTargetPortState: PortState = initialTargetPortState
        var currentActorEconomy: EconomyState = actorState.economy
        var currentActorFilesystem: FilesystemState = actorState.filesystem
        var currentAttackerPorts: List<PortState> = attackerState.ports
        var currentSourcePortState: PortState = sourcePortState
        var cancelRequested = false
        var suppressNormalDamage = false
        var attackXpDelta = 0.0
        var completed = false
        var lastAppliedDamage = 0.0
        var shouldResetTargetPort = false

        fun refreshTargetState() {
            currentTargetState = currentTargetState.copy(
                combat = currentTargetCombat,
                economy = currentTargetEconomy,
                filesystem = currentTargetFilesystem,
                logs = currentTargetLogs,
                ports = currentTargetPorts,
                watches = currentTargetWatches,
                runtime = currentTargetState.runtime.copy(currentCpuLoad = currentTargetRuntimeCpuLoad),
            )
        }

        fun replaceTargetCombat(combat: CombatState) {
            currentTargetCombat = combat
            refreshTargetState()
        }

        fun replaceTargetPort(port: PortState) {
            currentTargetPortState = port
            currentTargetPorts = currentTargetPorts.upsertPort(port, currentTargetEconomy.defaultBankPort)
            refreshTargetState()
        }

        fun replaceTargetWatches(
            watches: WatchManagerState,
            currentCpuLoad: Double,
        ) {
            currentTargetWatches = watches
            currentTargetRuntimeCpuLoad = currentCpuLoad
            refreshTargetState()
        }

        fun replaceTargetLogs(logs: LogState) {
            currentTargetLogs = logs
            refreshTargetState()
        }

        fun replaceTargetEconomy(economy: EconomyState) {
            currentTargetEconomy = economy
            refreshTargetState()
        }

        fun replaceActorEconomy(economy: EconomyState) {
            currentActorEconomy = economy
        }

        fun replaceSourcePort(port: PortState) {
            currentSourcePortState = port
            currentAttackerPorts = currentAttackerPorts.upsertPort(port, currentActorEconomy.defaultBankPort)
        }

        suspend fun applyAttackerHealthLoss(
            sourceIpForTrigger: String,
            sourcePortForTrigger: Int,
            amount: Double,
        ) {
            val previousHealth = currentSourcePortState.health
            val appliedLoss = min(amount, previousHealth)
            val newHealth = (previousHealth - appliedLoss).coerceAtLeast(0.0)
            if (newHealth == previousHealth) {
                return
            }
            replaceSourcePort(currentSourcePortState.copy(health = newHealth))
            context.emitPassiveHealthChange(
                targetStateId = attackerStateId,
                sourceIp = sourceIpForTrigger,
                portNumber = sourcePort,
                sourcePort = sourcePortForTrigger,
                previousHealth = previousHealth,
                newHealth = newHealth,
            )
        }

        suspend fun appendAttackerLog(message: String) {
            val createdAt = clock()
            context.appendEvents(
                id = attackerStateId,
                events = listOf(
                    HostLogAppendedEvent(
                        entry = ComputerLogEntry(
                            createdAtEpochMillis = createdAt,
                            renderedLine = renderLegacyLogLine(createdAt, message),
                            sourceIp = attackerStateId.value,
                        ),
                    ),
                ),
            )
        }

        suspend fun publishAttackMessage(targetIp: String, message: String) {
            context.publishUiEvent(
                targetStateIds = setOf(GameStateId(targetIp)),
                event = TextMessageUiEvent(message),
            )
        }

        suspend fun publishRedirectPaneMessage(message: String) {
            context.publishUiEvent(
                targetStateIds = setOf(attackerStateId),
                event = AttackMessageUiEvent(
                    message = message,
                    port = sourcePort,
                    ip = attackerStateId.value,
                    windowHandle = currentSession.windowHandle,
                    paneType = AttackPaneType.REDIRECT,
                ),
            )
        }

        suspend fun publishRedirectTextMessage(message: String) {
            context.publishUiEvent(
                targetStateIds = setOf(attackerStateId),
                event = TextMessageUiEvent(message),
            )
        }

        suspend fun publishShowChoicesIfNeeded() {
            if (currentSession.choicesShown) {
                return
            }
            val choiceType = currentTargetPortState.showChoicesType() ?: return
            context.publishUiEvent(
                targetStateIds = setOf(actorStateId),
                event = ShowChoicesUiEvent(
                    targetIp = currentTargetState.id.value,
                    targetPort = currentSession.targetPort,
                    choiceType = choiceType,
                    windowHandle = currentSession.windowHandle,
                ),
            )
            currentSession = currentSession.copy(choicesShown = true)
        }

        fun grantWeakenedAccessIfNeeded() {
            val choiceType = currentTargetPortState.showChoicesType() ?: return
            if (choiceType !in setOf(
                    ShowChoicesType.BANK,
                    ShowChoicesType.FTP,
                    ShowChoicesType.ATTACK,
                    ShowChoicesType.HTTP,
                    ShowChoicesType.SHIPPING,
                )
            ) {
                return
            }
            val grantedAt = clock()
            replaceTargetPort(
                currentTargetPortState.copy(
                    weakenedAccess = WeakenedPortAccessState(
                        actorStateId = actorStateId,
                        grantedAtEpochMillis = grantedAt,
                        lastAccessedAtEpochMillis = grantedAt,
                    ),
                ),
            )
        }

        suspend fun resetTargetPortIfNeeded() {
            if (!shouldResetTargetPort) {
                return
            }
            val resetPort = currentTargetPortState.copy(
                health = MAX_PORT_HEALTH,
                healCount = 0,
                weakenedAccess = null,
            )
            if (resetPort != currentTargetPortState) {
                replaceTargetPort(resetPort)
            }
            val baselineReset = currentTargetWatches.resetHealthBaselinesForPort(currentSession.targetPort)
            if (baselineReset != null) {
                replaceTargetWatches(
                    watches = baselineReset.watches,
                    currentCpuLoad = currentTargetRuntimeCpuLoad,
                )
                context.appendEvents(
                    id = targetStateId,
                    events = listOf(
                        WatchManagerUpdatedEvent(
                            changedPathList = baselineReset.changedIndices.mapTo(linkedSetOf()) { index ->
                                "watches.watches.$index.baselineQuantity"
                            },
                            deltaKeyList = setOf("watches"),
                            watches = baselineReset.watches,
                            currentCpuLoad = currentTargetRuntimeCpuLoad,
                            includeRuntime = false,
                        ),
                    ),
                )
            }
        }

        suspend fun editCurrentTargetLogs(
            data: String,
            replace: String,
        ): Boolean {
            val updatedEntries = currentTargetLogs.entries.map { entry ->
                entry.copy(renderedLine = entry.renderedLine.replace(data, replace))
            }
            if (updatedEntries == currentTargetLogs.entries) {
                return false
            }
            replaceTargetLogs(LogState(updatedEntries))
            context.appendEvents(
                id = targetStateId,
                events = listOf(
                    HostLogRenderedTextReplacedEvent(
                        data = data,
                        replace = replace,
                    ),
                ),
            )
            return true
        }

        suspend fun deleteCurrentTargetLogs(sourceIp: String): Boolean {
            val updatedEntries = currentTargetLogs.entries.filterNot { entry ->
                entry.sourceIp == sourceIp
            }
            if (updatedEntries == currentTargetLogs.entries) {
                return false
            }
            replaceTargetLogs(LogState(updatedEntries))
            context.appendEvents(
                id = targetStateId,
                events = listOf(HostLogsDeletedBySourceIpEvent(sourceIp = sourceIp)),
            )
            return true
        }

        suspend fun destroyCurrentTargetWatches(): Boolean {
            if (currentTargetState.identity.isNpc || currentTargetState.isDestroyWatchesImmune()) {
                return false
            }

            val removedWatches = currentTargetWatches.watches.filter { watch ->
                watch.installPort == currentSession.targetPort &&
                    watch.enabled &&
                    watch.kind != WatchKind.SCAN
            }
            if (removedWatches.isEmpty()) {
                return false
            }

            val updatedWatches = WatchManagerState(
                watches = currentTargetWatches.watches.filterNot { watch ->
                    watch.installPort == currentSession.targetPort &&
                        watch.enabled &&
                        watch.kind != WatchKind.SCAN
                },
            )
            val updatedCpuLoad = (currentTargetRuntimeCpuLoad - removedWatches.sumOf { it.cpuCost }).coerceAtLeast(0.0)
            val runtimeChanged = updatedCpuLoad != targetState.runtime.currentCpuLoad

            replaceTargetWatches(
                watches = updatedWatches,
                currentCpuLoad = updatedCpuLoad,
            )

            context.appendEvents(
                id = targetStateId,
                events = listOf(
                    WatchManagerUpdatedEvent(
                        changedPathList = buildSet {
                            add("watches.watches")
                            if (runtimeChanged) {
                                add("runtime.currentCpuLoad")
                            }
                        },
                        deltaKeyList = buildSet {
                            add("watches")
                            if (runtimeChanged) {
                                add("runtime")
                            }
                        },
                        watches = updatedWatches,
                        currentCpuLoad = updatedCpuLoad,
                        includeRuntime = runtimeChanged,
                    ),
                ),
            )
            return true
        }

        suspend fun emptyCurrentTargetPettyCash(): Boolean {
            if (!currentTargetPortState.isBankingApplication()) {
                return false
            }
            val latestActorState = if (actorStateId == attackerStateId) {
                context.loadState(actorStateId) ?: attackerState
            } else {
                context.loadState(actorStateId) ?: actorState
            }
            if (!latestActorState.hasActiveDefaultBankPort()) {
                return false
            }

            val targetPettyCashBefore = currentTargetEconomy.pettyCash
            if (targetPettyCashBefore <= 0.0) {
                return false
            }

            val attackerPettyCashBefore = currentActorEconomy.pettyCash
            val stolenAmount = currentTargetPortState.resolveEmptyPettyCashAmount(targetPettyCashBefore)
            if (stolenAmount == 0.0) {
                return false
            }

            val targetEconomyAfter = currentTargetEconomy.copy(
                pettyCash = (targetPettyCashBefore - stolenAmount).coerceAtLeast(0.0),
            )
            val attackerEconomyAfter = currentActorEconomy.copy(
                pettyCash = attackerPettyCashBefore + stolenAmount,
            )

            replaceTargetEconomy(targetEconomyAfter)
            replaceActorEconomy(attackerEconomyAfter)

            context.appendEvents(
                id = targetStateId,
                events = listOf(
                    EconomyBalanceAdjustedEvent(pettyCashDelta = -stolenAmount),
                ),
            )
            context.appendEvents(
                id = actorStateId,
                events = listOf(
                    EconomyBalanceAdjustedEvent(pettyCashDelta = stolenAmount),
                ),
            )
            context.evaluatePassivePettyCashChange(
                targetStateId = targetStateId,
                sourceIp = actorStateId.value,
                previousPettyCash = targetPettyCashBefore,
                newPettyCash = targetEconomyAfter.pettyCash,
                external = true,
            )
            context.evaluatePassivePettyCashChange(
                targetStateId = actorStateId,
                sourceIp = targetStateId.value,
                previousPettyCash = attackerPettyCashBefore,
                newPettyCash = attackerEconomyAfter.pettyCash,
                external = true,
            )
            return true
        }

        suspend fun stealCurrentTargetFile(): Boolean {
            if (!currentTargetPortState.isFtpApplication()) {
                return false
            }
            if (currentTargetPortState.shouldFailStealFile()) {
                return false
            }

            val stolenSourceFile: StoredFile = currentTargetFilesystem.listDirectory("/Public").files.firstOrNull() ?: return false
            val remainingTargetFile: StoredFile? = if (stolenSourceFile.quantity <= 1) {
                null
            } else {
                stolenSourceFile.copy(quantity = stolenSourceFile.quantity - 1)
            }
            val attackerExistingFile: StoredFile? = currentActorFilesystem.resolveFile("/", stolenSourceFile.name)
            val attackerReceivedFile: StoredFile = if (attackerExistingFile != null) {
                attackerExistingFile.copy(quantity = attackerExistingFile.quantity + 1)
            } else {
                stolenSourceFile.copy(
                    path = buildFilePath("/", stolenSourceFile.name),
                    quantity = 1,
                )
            }

            currentTargetFilesystem = currentTargetFilesystem.deleteFileByPath(stolenSourceFile.path)
            if (remainingTargetFile != null) {
                currentTargetFilesystem = currentTargetFilesystem.saveFile(remainingTargetFile)
            }
            refreshTargetState()

            currentActorFilesystem = currentActorFilesystem.saveFile(attackerReceivedFile)

            context.appendEvents(
                id = targetStateId,
                events = buildList {
                    add(FileDeletedEvent(stolenSourceFile.path))
                    if (remainingTargetFile != null) {
                        add(FileSavedEvent(remainingTargetFile))
                    }
                },
            )
            context.appendEvents(
                id = actorStateId,
                events = listOf(FileSavedEvent(attackerReceivedFile)),
            )
            return true
        }

        suspend fun installCurrentTargetScript(): Boolean {
            val targetApplication = currentTargetPortState.installedApplication ?: return false
            val sourceReference = currentSession.maliciousScripts.firstOrNull { it != null } ?: return false
            val sourceFile = currentActorFilesystem.resolveFile(sourceReference.folder, sourceReference.name) ?: return false
            val sourceMetadata = sourceFile.compiledBinary ?: return false
            if (sourceFile.kind != StoredFileKind.APPLICATION_BINARY) {
                return false
            }
            if (!targetApplication.kind.isInstallScriptSupportedTarget()) {
                return false
            }
            if (sourceMetadata.applicationKind != targetApplication.kind) {
                return false
            }

            val remainingSourceFile = if (sourceFile.quantity <= 1) {
                null
            } else {
                sourceFile.copy(quantity = sourceFile.quantity - 1)
            }
            currentActorFilesystem = currentActorFilesystem.deleteFileByPath(sourceFile.path)
            if (remainingSourceFile != null) {
                currentActorFilesystem = currentActorFilesystem.saveFile(remainingSourceFile)
            }
            context.appendEvents(
                id = actorStateId,
                events = buildList {
                    add(FileDeletedEvent(sourceFile.path))
                    if (remainingSourceFile != null) {
                        add(FileSavedEvent(remainingSourceFile))
                    }
                },
            )

            if (currentTargetState.identity.isNpc) {
                return false
            }
            if (currentTargetPortState.health != 0.0) {
                return false
            }
            if (currentTargetPortState.shouldFailInstallScript()) {
                return false
            }

            val installedBundle = sourceFile.scriptBundle ?: emptyInstallScriptBundleFor(targetApplication.kind)
            val updatedApplication = targetApplication.copy(
                scriptBundle = installedBundle,
                maliciousConfig = currentSession.extraInfo.toMaliciousProgramConfig(),
            )
            replaceTargetPort(currentTargetPortState.copy(installedApplication = updatedApplication))
            return true
        }

        suspend fun changeCurrentTargetDailyPay(requestedRevenueTargetIp: String): Boolean {
            val updatedActorState = context.requireExistingState(actorStateId)
            val response = performChangeDailyPay(
                context = context,
                actorState = updatedActorState,
                targetState = currentTargetState,
                targetPortState = currentTargetPortState,
                requestedRevenueTargetStateId = GameStateId(requestedRevenueTargetIp),
            )
            val persistedTargetState = context.requireExistingState(targetStateId)
            currentTargetState = currentTargetState.copy(
                version = persistedTargetState.version,
                dailyPay = persistedTargetState.dailyPay,
            )
            currentActorEconomy = context.requireExistingState(actorStateId).economy
            return response.outcome == ChangeDailyPayOutcome.SUCCESS ||
                response.outcome == ChangeDailyPayOutcome.ALREADY_CONTROLLED
        }

        suspend fun applyDamagePass(
            resolution: FirewallCombatResolution,
            awardAttackXp: Boolean,
            directSelfDamage: Double = 0.0,
        ): Boolean {
            val previousHealth = currentTargetPortState.health
            val appliedDamage = min(resolution.targetDamage, previousHealth)
            val newHealth = (previousHealth - appliedDamage).coerceAtLeast(0.0)
            if (newHealth != previousHealth) {
                replaceTargetPort(currentTargetPortState.copy(health = newHealth))
                context.emitPassiveHealthChange(
                    targetStateId = targetStateId,
                    sourceIp = actorStateId.value,
                    portNumber = currentSession.targetPort,
                    sourcePort = sourcePort,
                    previousHealth = previousHealth,
                    newHealth = newHealth,
                )
            }
            lastAppliedDamage = appliedDamage
            if (awardAttackXp) {
                attackXpDelta += baseDamage
            }
            if (resolution.attackBackDamage > 0.0) {
                applyAttackerHealthLoss(
                    sourceIpForTrigger = targetStateId.value,
                    sourcePortForTrigger = currentSession.targetPort,
                    amount = resolution.attackBackDamage,
                )
            }
            if (directSelfDamage > 0.0) {
                applyAttackerHealthLoss(
                    sourceIpForTrigger = actorStateId.value,
                    sourcePortForTrigger = sourcePort,
                    amount = directSelfDamage,
                )
            }
            completed = newHealth == 0.0
            return completed
        }

        if (continueOutcome != null) {
            attackRuntimeExecutor.logDiagnostics(
                attackerStateId = attackerStateId,
                phase = AttackScriptPhase.CONTINUE,
                input = continueInput,
                outcome = continueOutcome,
            )
            for (effect in continueOutcome.result?.effects.orEmpty()) {
                when (effect) {
                    is AttackAppendHostLogEffect -> {
                        appendAttackerLog(effect.message)
                    }

                    is AttackEditTargetLogsEffect -> {
                        shouldResetTargetPort = editCurrentTargetLogs(
                            data = effect.data,
                            replace = effect.replace,
                        ) || shouldResetTargetPort
                        cancelRequested = true
                    }

                    is AttackDeleteTargetLogsEffect -> {
                        shouldResetTargetPort = deleteCurrentTargetLogs(effect.sourceIp) || shouldResetTargetPort
                        cancelRequested = true
                    }

                    is AttackDestroyTargetWatchesEffect -> {
                        shouldResetTargetPort = destroyCurrentTargetWatches() || shouldResetTargetPort
                        cancelRequested = true
                    }

                    is AttackEmptyTargetPettyCashEffect -> {
                        shouldResetTargetPort = emptyCurrentTargetPettyCash() || shouldResetTargetPort
                        cancelRequested = true
                    }

                    is AttackStealTargetFileEffect -> {
                        shouldResetTargetPort = stealCurrentTargetFile() || shouldResetTargetPort
                        cancelRequested = true
                    }

                    is AttackInstallTargetScriptEffect -> {
                        shouldResetTargetPort = installCurrentTargetScript() || shouldResetTargetPort
                        cancelRequested = true
                    }

                    is AttackChangeDailyPayEffect -> {
                        shouldResetTargetPort = changeCurrentTargetDailyPay(effect.targetIp) || shouldResetTargetPort
                        cancelRequested = true
                    }

                    is AttackSendMessageEffect -> {
                        publishAttackMessage(effect.targetIp, effect.message)
                    }

                    is AttackShowChoicesEffect -> {
                        publishShowChoicesIfNeeded()
                    }

                    is AttackSelectRedirectCommodityEffect -> Unit

                    is com.hackwars.rewrite.hackscript.AttackAuthorizeZombieEffect -> Unit

                    is AttackCancelCurrentAttackEffect -> {
                        cancelRequested = true
                    }

                    is AttackFreezeTargetPortEffect -> {
                        suppressNormalDamage = true
                        if (!currentTargetState.isFreezeImmune()) {
                            val freezeExpiresAtEpochMillis = clock() + ATTACK_FREEZE_DURATION_MILLIS
                            if (freezeExpiresAtEpochMillis != currentTargetPortState.freezeExpiresAtEpochMillis) {
                                replaceTargetPort(
                                    currentTargetPortState.copy(
                                        freezeExpiresAtEpochMillis = freezeExpiresAtEpochMillis,
                                    ),
                                )
                            }
                        }
                    }

                    is AttackSwitchTargetEffect -> {
                        val nextTarget = currentSession.findNextSwitchTarget(
                            targetState = currentTargetState,
                            attackerStateId = attackerStateId,
                            sourcePort = sourcePort,
                            now = clock(),
                        )
                        if (nextTarget == null) {
                            logger.warn(
                                "Rewrite attack continue script could not switch target for attacker={} sourcePort={} target={}#{}.",
                                attackerStateId.value,
                                sourcePort,
                                targetStateId.value,
                                currentSession.targetPort,
                            )
                        } else {
                            val (nextTargetPort, nextTargetCursor) = nextTarget
                            replaceTargetCombat(
                                currentTargetCombat
                                    .removeIncomingAttack(currentSession.targetPort)
                                    .withIncomingAttack(
                                        IncomingAttackState(
                                            attackerStateId = attackerStateId,
                                            attackerSourcePort = sourcePort,
                                            targetPort = nextTargetPort,
                                            startedAtEpochMillis = currentSession.startedAtEpochMillis,
                                            windowHandle = currentSession.windowHandle,
                                        ),
                                    ),
                            )
                            currentTargetPortState = requireNotNull(currentTargetState.port(nextTargetPort)) {
                                "Target port $nextTargetPort disappeared during switchAttack on ${targetStateId.value}."
                            }
                            lastAppliedDamage = 0.0
                            currentSession = currentSession.copy(
                                targetPort = nextTargetPort,
                                targetCycleCursor = nextTargetCursor,
                                targetView = currentTargetState.buildAttackTargetView(
                                    targetPort = nextTargetPort,
                                    lastAppliedDamage = 0.0,
                                    completed = false,
                                    healthOverride = currentTargetPortState.health,
                                ),
                            )
                        }
                    }

                    is AttackBerserkEffect -> {
                        val berserkResolution = firewallCombatResolver.resolve(
                            sourcePort = currentSourcePortState,
                            targetPort = currentTargetPortState,
                            baseDamage = baseDamage,
                        )
                        val completedByBerserk = applyDamagePass(
                            resolution = berserkResolution,
                            awardAttackXp = true,
                            directSelfDamage = baseDamage / 2.0,
                        )
                        if (completedByBerserk) {
                            break
                        }
                    }
                }
            }
        }

        if (!completed && !suppressNormalDamage) {
            val damageResolution = firewallCombatResolver.resolve(
                sourcePort = currentSourcePortState,
                targetPort = currentTargetPortState,
                baseDamage = baseDamage,
            )
            applyDamagePass(
                resolution = damageResolution,
                awardAttackXp = true,
            )
        }

        if (completed) {
            grantWeakenedAccessIfNeeded()
            replaceTargetCombat(currentTargetCombat.removeIncomingAttack(currentSession.targetPort))
            val finalizeTargetView = currentTargetState.buildAttackTargetView(
                targetPort = currentSession.targetPort,
                lastAppliedDamage = lastAppliedDamage,
                completed = true,
                healthOverride = 0.0,
            )
            val finalizeInput = attackerState.toAttackExecutionInput(
                phase = AttackExecutionPhase.FINALIZE,
                sourcePort = sourcePort,
                targetView = finalizeTargetView,
                iterations = nextIteration,
                sourceIpOverride = actorStateId.value,
                isZombie = currentSession.attackMode == AttackMode.ZOMBIE,
                allowedZombieIps = currentSession.controllerStateId?.let { setOf(it.value) }.orEmpty(),
            )
            val finalizeOutcome = attackRuntimeExecutor.evaluate(
                attackerState = attackerState,
                sourcePort = sourcePort,
                phase = AttackScriptPhase.FINALIZE,
                input = finalizeInput,
            )
            if (finalizeOutcome != null) {
                attackRuntimeExecutor.logDiagnostics(
                    attackerStateId = attackerStateId,
                    phase = AttackScriptPhase.FINALIZE,
                    input = finalizeInput,
                    outcome = finalizeOutcome,
                )
                for (effect in finalizeOutcome.result?.effects.orEmpty()) {
                    when (effect) {
                        is AttackAppendHostLogEffect -> {
                            appendAttackerLog(effect.message)
                        }

                        is AttackEditTargetLogsEffect -> {
                            shouldResetTargetPort = editCurrentTargetLogs(
                                data = effect.data,
                                replace = effect.replace,
                            ) || shouldResetTargetPort
                        }

                        is AttackDeleteTargetLogsEffect -> {
                            shouldResetTargetPort = deleteCurrentTargetLogs(effect.sourceIp) || shouldResetTargetPort
                        }

                        is AttackDestroyTargetWatchesEffect -> {
                            shouldResetTargetPort = destroyCurrentTargetWatches() || shouldResetTargetPort
                        }

                        is AttackEmptyTargetPettyCashEffect -> {
                            shouldResetTargetPort = emptyCurrentTargetPettyCash() || shouldResetTargetPort
                        }

                        is AttackStealTargetFileEffect -> {
                            shouldResetTargetPort = stealCurrentTargetFile() || shouldResetTargetPort
                        }

                        is AttackInstallTargetScriptEffect -> {
                            shouldResetTargetPort = installCurrentTargetScript() || shouldResetTargetPort
                        }

                        is AttackChangeDailyPayEffect -> {
                            shouldResetTargetPort = changeCurrentTargetDailyPay(effect.targetIp) || shouldResetTargetPort
                        }

                        is AttackSendMessageEffect -> {
                            publishAttackMessage(effect.targetIp, effect.message)
                        }

                        is AttackShowChoicesEffect -> {
                            publishShowChoicesIfNeeded()
                        }

                        is AttackSelectRedirectCommodityEffect -> Unit

                        is com.hackwars.rewrite.hackscript.AttackAuthorizeZombieEffect -> Unit

                        else -> Unit
                    }
                }
            }
        }

        resetTargetPortIfNeeded()

        val shouldCancel = cancelRequested || (currentSourcePortState.health == 0.0 && !completed)
        if (shouldCancel) {
            replaceTargetCombat(currentTargetCombat.removeIncomingAttack(currentSession.targetPort))
        }

        val finalTargetPortsChangedPaths = currentTargetPorts.combatChangedPaths(targetState.ports)
        val targetCombatChanged = currentTargetCombat != targetState.combat
        if (finalTargetPortsChangedPaths.isNotEmpty() || targetCombatChanged) {
            context.appendEvents(
                id = targetStateId,
                events = listOf(
                    CombatStateUpdatedEvent(
                        changedPathList = buildSet {
                            addAll(finalTargetPortsChangedPaths)
                            if (targetCombatChanged) {
                                add("combat.incomingAttacksByTargetPort")
                            }
                        }.ifEmpty { setOf("combat") },
                        deltaKeyList = buildSet {
                            if (finalTargetPortsChangedPaths.isNotEmpty()) {
                                add("ports")
                            }
                            if (targetCombatChanged) {
                                add("combat")
                            }
                        }.ifEmpty { setOf("combat") },
                        combat = currentTargetCombat,
                        ports = currentTargetPorts,
                        currentCpuLoad = currentTargetRuntimeCpuLoad,
                        includePorts = finalTargetPortsChangedPaths.isNotEmpty(),
                    ),
                ),
            )
        }

        val finalAttackerCombat = when {
            completed || shouldCancel -> attackerState.combat.removeSession(sourcePort)
            else -> attackerState.combat.withSession(
                currentSession.copy(
                    iterationCount = nextIteration,
                    targetView = currentTargetState.buildAttackTargetView(
                        targetPort = currentSession.targetPort,
                        lastAppliedDamage = lastAppliedDamage,
                        completed = false,
                        healthOverride = currentTargetPortState.health,
                    ),
                ),
            )
        }
        val finalAttackerPorts = when {
            completed || shouldCancel -> currentAttackerPorts.markAttacking(sourcePort, false)
            else -> currentAttackerPorts
        }
        val finalCpuLoad = if (completed || shouldCancel) {
            attackerState.copy(
                combat = finalAttackerCombat,
                ports = finalAttackerPorts,
            ).expectedRuntimeCpuLoad()
        } else {
            attackerState.runtime.currentCpuLoad
        }
        val attackerPortsChangedPaths = finalAttackerPorts.combatChangedPaths(attackerState.ports)
        val attackerCombatChanged = finalAttackerCombat != attackerState.combat
        val runtimeChanged = finalCpuLoad != attackerState.runtime.currentCpuLoad
        if (attackXpDelta > 0.0) {
            context.appendEvents(
                id = actorStateId,
                events = listOf(
                    SkillExperienceAdjustedEvent(
                        family = ScriptFamily.ATTACK,
                        delta = attackXpDelta,
                    ),
                ),
            )
        }
        if (attackerCombatChanged || attackerPortsChangedPaths.isNotEmpty() || runtimeChanged) {
            context.appendEvents(
                id = attackerStateId,
                events = listOf(
                    CombatStateUpdatedEvent(
                        changedPathList = buildSet {
                            if (attackerCombatChanged) {
                                add("combat.activeAttacksBySourcePort.$sourcePort")
                            }
                            addAll(attackerPortsChangedPaths)
                            if (runtimeChanged) {
                                add("runtime.currentCpuLoad")
                            }
                        }.ifEmpty { setOf("combat") },
                        deltaKeyList = buildSet {
                            if (attackerCombatChanged) {
                                add("combat")
                            }
                            if (attackerPortsChangedPaths.isNotEmpty()) {
                                add("ports")
                            }
                            if (runtimeChanged) {
                                add("runtime")
                            }
                        }.ifEmpty { setOf("combat") },
                        combat = finalAttackerCombat,
                        ports = finalAttackerPorts,
                        currentCpuLoad = finalCpuLoad,
                        includePorts = attackerPortsChangedPaths.isNotEmpty(),
                        includeRuntime = runtimeChanged,
                    ),
                ),
            )
        }

        return ProgramExecutionStep(
            status = when {
                completed -> ProgramLifecycleStatus.COMPLETED
                shouldCancel -> ProgramLifecycleStatus.CANCELLED
                else -> ProgramLifecycleStatus.RUNNING
            },
            progress = ProgramProgress(
                message = when {
                    completed -> "completed"
                    shouldCancel -> "cancelled"
                    else -> "running"
                },
                completedSteps = nextIteration,
            ),
            relatedStateIds = programUpdateStateIds,
        )
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(AttackTickCommand::class.java)
    }
}

internal class RedirectTickCommand(
    private val attackerStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourcePort: Int,
    private val attackRuntimeExecutor: AttackRuntimeExecutor = DefaultAttackRuntimeExecutor,
    private val firewallCombatResolver: FirewallCombatResolver = DefaultFirewallCombatResolver,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<ProgramExecutionStep> {
    override val name: String = "redirectcontinue"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(attackerStateId, targetStateId)

    override suspend fun execute(context: CommandContext): ProgramExecutionStep {
        val programUpdateStateIds = setOf(attackerStateId)
        val attackerState = context.requireExistingState(attackerStateId)
        val sourcePortState = attackerState.port(sourcePort)
        if (sourcePortState == null) {
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                ),
            )
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "source port missing"),
                relatedStateIds = programUpdateStateIds,
            )
        }
        val session = attackerState.combat.activeAttacksBySourcePort[sourcePort]
        if (session == null || session.sessionKind != AttackSessionKind.REDIRECT) {
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                ),
            )
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "redirect session missing"),
                relatedStateIds = programUpdateStateIds,
            )
        }

        var currentSession: AttackSessionState = session

        suspend fun publishRedirectPaneMessage(message: String) {
            context.publishUiEvent(
                targetStateIds = setOf(attackerStateId),
                event = AttackMessageUiEvent(
                    message = message,
                    port = sourcePort,
                    ip = attackerStateId.value,
                    windowHandle = currentSession.windowHandle,
                    paneType = AttackPaneType.REDIRECT,
                ),
            )
        }

        suspend fun publishRedirectTextMessage(message: String) {
            context.publishUiEvent(
                targetStateIds = setOf(attackerStateId),
                event = TextMessageUiEvent(message),
            )
        }

        val targetState = context.loadState(targetStateId)
        val targetPortState = targetState?.port(session.targetPort)
        val incoming = targetState?.combat?.incomingAttacksByTargetPort?.get(session.targetPort)
        if (
            targetState == null ||
            targetPortState == null ||
            !targetPortState.isValidRedirectTarget() ||
            incoming == null ||
            incoming.attackerStateId != attackerStateId ||
            incoming.attackerSourcePort != sourcePort
        ) {
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                    targetPort = session.targetPort,
                ),
            )
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "target unavailable"),
                relatedStateIds = programUpdateStateIds,
            )
        }

        if (targetPortState.health <= 0.0) {
            publishRedirectPaneMessage(REDIRECT_FINISHED_PANE_MESSAGE)
            publishRedirectTextMessage("Port $sourcePort finished redirecting.")
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                    targetPort = session.targetPort,
                ),
            )
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.COMPLETED,
                progress = ProgramProgress(
                    message = "redirect-finished",
                    completedSteps = session.iterationCount,
                ),
                relatedStateIds = programUpdateStateIds,
            )
        }

        val initialSession: AttackSessionState = currentSession
        val initialTargetState: ComputerState = targetState
        val initialTargetPortState: PortState = targetPortState
        val nextIteration = initialSession.iterationCount + 1
        var currentTargetState: ComputerState = initialTargetState
        var currentTargetPorts: List<PortState> = initialTargetState.ports
        var currentTargetPortState: PortState = initialTargetPortState
        var currentTargetEconomy: EconomyState = initialTargetState.economy
        var currentAttackerPorts = attackerState.ports
        var currentSourcePortState = sourcePortState
        var currentAttackerEconomy = attackerState.economy
        var redirectXpDelta = 0.0
        var lastAppliedDamage = 0.0
        var cancelRequested = false
        var completed = false

        fun refreshTargetState() {
            currentTargetState = currentTargetState.copy(
                ports = currentTargetPorts,
                economy = currentTargetEconomy,
            )
        }

        fun replaceTargetPort(port: PortState) {
            currentTargetPortState = port
            currentTargetPorts = currentTargetPorts.upsertPort(
                port = port,
                defaultBankPort = currentTargetEconomy.defaultBankPort,
                defaultRedirectPort = currentTargetEconomy.defaultRedirectPort,
            )
            refreshTargetState()
        }

        fun replaceTargetEconomy(economy: EconomyState) {
            currentTargetEconomy = economy
            refreshTargetState()
        }

        fun replaceSourcePort(port: PortState) {
            currentSourcePortState = port
            currentAttackerPorts = currentAttackerPorts.upsertPort(
                port = port,
                defaultBankPort = currentAttackerEconomy.defaultBankPort,
                defaultRedirectPort = currentAttackerEconomy.defaultRedirectPort,
            )
        }

        suspend fun appendAttackerLog(message: String) {
            val createdAt = clock()
            context.appendEvents(
                id = attackerStateId,
                events = listOf(
                    HostLogAppendedEvent(
                        entry = ComputerLogEntry(
                            createdAtEpochMillis = createdAt,
                            renderedLine = renderLegacyLogLine(createdAt, message),
                            sourceIp = attackerStateId.value,
                        ),
                    ),
                ),
            )
        }

        suspend fun publishAttackMessage(targetIp: String, message: String) {
            context.publishUiEvent(
                targetStateIds = setOf(GameStateId(targetIp)),
                event = TextMessageUiEvent(message),
            )
        }

        suspend fun applyAttackerHealthLoss(amount: Double) {
            val previousHealth = currentSourcePortState.health
            val appliedLoss = min(amount, previousHealth)
            val newHealth = (previousHealth - appliedLoss).coerceAtLeast(0.0)
            if (newHealth == previousHealth) {
                return
            }
            replaceSourcePort(currentSourcePortState.copy(health = newHealth))
            context.emitPassiveHealthChange(
                targetStateId = attackerStateId,
                sourceIp = targetStateId.value,
                portNumber = sourcePort,
                sourcePort = currentSession.targetPort,
                previousHealth = previousHealth,
                newHealth = newHealth,
            )
        }

        val continueInput = attackerState.toAttackExecutionInput(
            phase = AttackExecutionPhase.CONTINUE,
            sourcePort = sourcePort,
            targetView = currentTargetState.buildAttackTargetView(
                targetPort = currentSession.targetPort,
                lastAppliedDamage = currentSession.targetView.lastAppliedDamage,
                completed = false,
                healthOverride = currentTargetPortState.health,
            ),
            iterations = nextIteration,
        )
        val continueOutcome = attackRuntimeExecutor.evaluate(
            attackerState = attackerState,
            sourcePort = sourcePort,
            phase = AttackScriptPhase.CONTINUE,
            input = continueInput,
        )
        if (continueOutcome != null) {
            attackRuntimeExecutor.logDiagnostics(
                attackerStateId = attackerStateId,
                phase = AttackScriptPhase.CONTINUE,
                input = continueInput,
                outcome = continueOutcome,
            )
            for (effect in continueOutcome.result?.effects.orEmpty()) {
                when (effect) {
                    is AttackAppendHostLogEffect -> appendAttackerLog(effect.message)
                    is AttackSendMessageEffect -> publishAttackMessage(effect.targetIp, effect.message)
                    is AttackCancelCurrentAttackEffect -> cancelRequested = true
                    is AttackSelectRedirectCommodityEffect -> {
                        currentSession = currentSession.copy(redirectCommodityId = effect.commodityId)
                    }
                    else -> Unit
                }
            }
        }

        val commodityId = currentSession.redirectCommodityId.coerceIn(0, REDIRECT_COMMODITY_XP.lastIndex)
        if (currentTargetState.identity.isNpc && currentTargetPortState.health == MAX_PORT_HEALTH) {
            val currentAmount = currentTargetEconomy.commodities.valueAtCommodity(commodityId)
            if (currentAmount <= 0.0) {
                val respawnAmount = currentTargetEconomy.commodityRespawn.valueAtCommodity(commodityId)
                if (respawnAmount > 0.0) {
                    replaceTargetEconomy(currentTargetEconomy.withCommodityAmount(commodityId, respawnAmount))
                }
            }
        }

        val baseDamage = attackerState.redirectBaseDamage()
        val damageResolution = firewallCombatResolver.resolve(
            sourcePort = currentSourcePortState,
            targetPort = currentTargetPortState,
            baseDamage = baseDamage,
        )
        val previousTargetHealth = currentTargetPortState.health
        val appliedDamage = min(damageResolution.targetDamage, previousTargetHealth)
        val newTargetHealth = (previousTargetHealth - appliedDamage).coerceAtLeast(0.0)
        if (newTargetHealth != previousTargetHealth) {
            replaceTargetPort(currentTargetPortState.copy(health = newTargetHealth))
            context.emitPassiveHealthChange(
                targetStateId = targetStateId,
                sourceIp = attackerStateId.value,
                portNumber = currentSession.targetPort,
                sourcePort = sourcePort,
                previousHealth = previousTargetHealth,
                newHealth = newTargetHealth,
            )
        }
        lastAppliedDamage = appliedDamage
        completed = newTargetHealth == 0.0

        if (damageResolution.attackBackDamage > 0.0) {
            applyAttackerHealthLoss(damageResolution.attackBackDamage)
        }

        val currentAmount = currentTargetEconomy.commodities.valueAtCommodity(commodityId)
        val threshold = if (currentAmount > 0.0) MAX_PORT_HEALTH / currentAmount else Double.POSITIVE_INFINITY
        if (currentAmount > 0.0 && (MAX_PORT_HEALTH - newTargetHealth) >= threshold) {
            val transferAmount = if (currentTargetState.identity.isNpc && completed) {
                currentAmount
            } else {
                1.0
            }.coerceAtMost(currentAmount)
            if (transferAmount > 0.0) {
                replaceTargetEconomy(currentTargetEconomy.adjustCommodityAmount(commodityId, -transferAmount))
                currentAttackerEconomy = currentAttackerEconomy.adjustCommodityAmount(commodityId, transferAmount)
                publishRedirectPaneMessage(
                    "Received ${transferAmount.toRedirectCommodityAmount()} ${commodityId.redirectCommodityName()}.",
                )
                publishRedirectTextMessage(
                    "Received ${transferAmount.toRedirectCommodityAmount()} ${commodityId.redirectCommodityName()} from ${targetStateId.value}.",
                )
                val remainingXpAllowance = (REDIRECT_XP_CAP - currentSession.redirectXpAwardedOnTarget).coerceAtLeast(0.0)
                val awardedXp = (REDIRECT_COMMODITY_XP[commodityId] * transferAmount).coerceAtMost(remainingXpAllowance)
                if (awardedXp > 0.0) {
                    currentSession = currentSession.copy(
                        redirectXpAwardedOnTarget = currentSession.redirectXpAwardedOnTarget + awardedXp,
                    )
                    redirectXpDelta += awardedXp
                } else if (remainingXpAllowance <= 0.0) {
                    publishRedirectPaneMessage(
                        "You can not gain anymore redirect experience off ${targetStateId.value} at this time.",
                    )
                }
            }
        }

        if (completed) {
            val finalizeInput = attackerState.toAttackExecutionInput(
                phase = AttackExecutionPhase.FINALIZE,
                sourcePort = sourcePort,
                targetView = currentTargetState.buildAttackTargetView(
                    targetPort = currentSession.targetPort,
                    lastAppliedDamage = lastAppliedDamage,
                    completed = true,
                    healthOverride = 0.0,
                ),
                iterations = nextIteration,
            )
            val finalizeOutcome = attackRuntimeExecutor.evaluate(
                attackerState = attackerState,
                sourcePort = sourcePort,
                phase = AttackScriptPhase.FINALIZE,
                input = finalizeInput,
            )
            if (finalizeOutcome != null) {
                attackRuntimeExecutor.logDiagnostics(
                    attackerStateId = attackerStateId,
                    phase = AttackScriptPhase.FINALIZE,
                    input = finalizeInput,
                    outcome = finalizeOutcome,
                )
                for (effect in finalizeOutcome.result?.effects.orEmpty()) {
                    when (effect) {
                        is AttackAppendHostLogEffect -> appendAttackerLog(effect.message)
                        is AttackSendMessageEffect -> publishAttackMessage(effect.targetIp, effect.message)
                        is AttackSelectRedirectCommodityEffect -> {
                            currentSession = currentSession.copy(redirectCommodityId = effect.commodityId)
                        }
                        else -> Unit
                    }
                }
            }
        }

        val shouldCancel = cancelRequested || (currentSourcePortState.health == 0.0 && !completed)
        val targetPortsChangedPaths = currentTargetPorts.combatChangedPaths(initialTargetState.ports)
        if (targetPortsChangedPaths.isNotEmpty()) {
            context.appendEvents(
                id = targetStateId,
                events = listOf(
                    CombatStateUpdatedEvent(
                        changedPathList = targetPortsChangedPaths,
                        deltaKeyList = setOf("ports"),
                        combat = currentTargetState.combat,
                        ports = currentTargetPorts,
                        currentCpuLoad = currentTargetState.runtime.currentCpuLoad,
                        includePorts = true,
                    ),
                ),
            )
        }
        if (currentTargetEconomy != initialTargetState.economy) {
            context.appendEvents(
                id = targetStateId,
                events = listOf(
                    EconomyStateUpdatedEvent(
                        changedPathList = initialTargetState.economy.changedPathsTo(currentTargetEconomy),
                        economy = currentTargetEconomy,
                    ),
                ),
            )
        }
        if (currentAttackerEconomy != attackerState.economy) {
            context.appendEvents(
                id = attackerStateId,
                events = listOf(
                    EconomyStateUpdatedEvent(
                        changedPathList = attackerState.economy.changedPathsTo(currentAttackerEconomy),
                        economy = currentAttackerEconomy,
                    ),
                ),
            )
        }
        val attackerPortsChangedPaths = currentAttackerPorts.combatChangedPaths(attackerState.ports)
        if (attackerPortsChangedPaths.isNotEmpty()) {
            context.appendEvents(
                id = attackerStateId,
                events = listOf(
                    CombatStateUpdatedEvent(
                        changedPathList = attackerPortsChangedPaths,
                        deltaKeyList = setOf("ports"),
                        combat = attackerState.combat,
                        ports = currentAttackerPorts,
                        currentCpuLoad = attackerState.runtime.currentCpuLoad,
                        includePorts = true,
                    ),
                ),
            )
        }
        if (redirectXpDelta > 0.0) {
            context.appendEvents(
                id = attackerStateId,
                events = listOf(
                    SkillExperienceAdjustedEvent(
                        family = ScriptFamily.REDIRECT,
                        delta = redirectXpDelta,
                    ),
                ),
            )
        }

        if (completed || shouldCancel) {
            if (completed) {
                publishRedirectPaneMessage(REDIRECT_FINISHED_PANE_MESSAGE)
                publishRedirectTextMessage("Port $sourcePort finished redirecting.")
            }
            context.request(
                AttackReleaseCommand(
                    attackerStateId = attackerStateId,
                    targetStateId = targetStateId,
                    sourcePort = sourcePort,
                    targetPort = currentSession.targetPort,
                ),
            )
            return ProgramExecutionStep(
                status = if (completed) ProgramLifecycleStatus.COMPLETED else ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(
                    message = if (completed) "redirect-finished" else "redirect-cancelled",
                    completedSteps = nextIteration,
                ),
                relatedStateIds = programUpdateStateIds,
            )
        }

        val persistedSession = currentSession.copy(
            iterationCount = nextIteration,
            targetView = currentTargetState.buildAttackTargetView(
                targetPort = currentSession.targetPort,
                lastAppliedDamage = lastAppliedDamage,
                completed = false,
                healthOverride = currentTargetPortState.health,
            ),
        )
        context.appendEvents(
            id = attackerStateId,
            events = listOf(
                CombatStateUpdatedEvent(
                    changedPathList = setOf("combat.activeAttacksBySourcePort.$sourcePort"),
                    deltaKeyList = setOf("combat"),
                    combat = attackerState.combat.withSession(persistedSession),
                    ports = currentAttackerPorts,
                    currentCpuLoad = attackerState.runtime.currentCpuLoad,
                ),
            ),
        )

        return ProgramExecutionStep(
            status = ProgramLifecycleStatus.RUNNING,
            progress = ProgramProgress(
                message = "running",
                completedSteps = nextIteration,
            ),
            relatedStateIds = programUpdateStateIds,
        )
    }
}

internal class AttackReleaseCommand(
    private val attackerStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourcePort: Int,
    private val targetPort: Int? = null,
) : RequestCommand<Unit> {
    override val name: String = "attackrelease"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(attackerStateId, targetStateId)

    override suspend fun execute(context: CommandContext) {
        val attackerState = context.loadState(attackerStateId)
        val session = attackerState?.combat?.activeAttacksBySourcePort?.get(sourcePort)
        val resolvedTargetPort = targetPort ?: session?.targetPort
        val sessionKind = session?.sessionKind ?: AttackSessionKind.ATTACK

        if (attackerState != null) {
            val updatedCombat = attackerState.combat.removeSession(sourcePort)
            val updatedPorts = attackerState.ports.markAttacking(sourcePort, false)
            val normalizedState = attackerState.copy(combat = updatedCombat, ports = updatedPorts)
            val normalizedCpuLoad = normalizedState.expectedRuntimeCpuLoad()
            if (
                updatedCombat != attackerState.combat ||
                updatedPorts != attackerState.ports ||
                normalizedCpuLoad != attackerState.runtime.currentCpuLoad
            ) {
                context.appendEvents(
                    id = attackerStateId,
                    events = listOf(
                        CombatStateUpdatedEvent(
                            changedPathList = buildSet {
                                if (updatedCombat != attackerState.combat) {
                                    add("combat.activeAttacksBySourcePort.$sourcePort")
                                }
                                if (updatedPorts != attackerState.ports) {
                                    add("ports.$sourcePort.attacking")
                                }
                                if (normalizedCpuLoad != attackerState.runtime.currentCpuLoad) {
                                    add("runtime.currentCpuLoad")
                                }
                            }.ifEmpty { setOf("combat") },
                            deltaKeyList = buildSet {
                                if (updatedCombat != attackerState.combat) {
                                    add("combat")
                                }
                                if (updatedPorts != attackerState.ports) {
                                    add("ports")
                                }
                                if (normalizedCpuLoad != attackerState.runtime.currentCpuLoad) {
                                    add("runtime")
                                }
                            }.ifEmpty { setOf("combat") },
                            combat = updatedCombat,
                            ports = updatedPorts,
                            currentCpuLoad = normalizedCpuLoad,
                            includePorts = updatedPorts != attackerState.ports,
                            includeRuntime = normalizedCpuLoad != attackerState.runtime.currentCpuLoad,
                        ),
                    ),
                )
            }
        }

        if (resolvedTargetPort != null) {
            var targetState = context.loadState(targetStateId)
            if (targetState != null && sessionKind == AttackSessionKind.REDIRECT && targetState.port(resolvedTargetPort) != null) {
                targetState = targetState.applyWeakenedPortReset(
                    context = context,
                    stateId = targetStateId,
                    portNumber = resolvedTargetPort,
                )
            }
            val incoming = targetState?.combat?.incomingAttacksByTargetPort?.get(resolvedTargetPort)
            if (
                targetState != null &&
                incoming != null &&
                incoming.attackerStateId == attackerStateId &&
                incoming.attackerSourcePort == sourcePort
            ) {
                context.appendEvents(
                    id = targetStateId,
                    events = listOf(
                        CombatStateUpdatedEvent(
                            changedPathList = setOf("combat.incomingAttacksByTargetPort.$resolvedTargetPort"),
                            deltaKeyList = setOf("combat"),
                            combat = targetState.combat.removeIncomingAttack(resolvedTargetPort),
                            ports = targetState.ports,
                            currentCpuLoad = targetState.runtime.currentCpuLoad,
                        ),
                    ),
                )
            }
        }
    }
}

class RefreshCombatRuntimeCommand(
    private val stateId: GameStateId,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
) : RequestCommand<ComputerState> {
    override val name: String = "refreshcombatruntime"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): ComputerState {
        val state = context.requireExistingState(stateId)
        val liveSessions = linkedMapOf<Int, AttackSessionState>()
        state.combat.activeAttacksBySourcePort.toSortedMap().forEach { (sourcePort, session) ->
            if (attackProgramRegistry.hasProgram(session.programId)) {
                liveSessions[sourcePort] = session
            }
        }
        val liveIncoming = linkedMapOf<Int, IncomingAttackState>()
        state.combat.incomingAttacksByTargetPort.toSortedMap().forEach { (targetPort, incoming) ->
            val programId = attackProgramRegistry.programIdFor(incoming.attackerStateId, incoming.attackerSourcePort)
            if (programId != null && attackProgramRegistry.hasProgram(programId)) {
                liveIncoming[targetPort] = incoming
            }
        }

        val updatedCombat = state.combat.copy(
            activeAttacksBySourcePort = liveSessions,
            incomingAttacksByTargetPort = liveIncoming,
        )
        val updatedPorts = state.ports.map { port ->
            val shouldAttack = liveSessions.containsKey(port.number)
            if (port.attacking == shouldAttack) {
                port
            } else {
                port.copy(attacking = shouldAttack)
            }
        }
        val normalizedState = state.copy(combat = updatedCombat, ports = updatedPorts)
        val normalizedCpuLoad = normalizedState.expectedRuntimeCpuLoad()
        if (
            updatedCombat == state.combat &&
            updatedPorts == state.ports &&
            normalizedCpuLoad == state.runtime.currentCpuLoad
        ) {
            return state
        }

        return context.appendEvents(
            id = stateId,
            events = listOf(
                CombatStateUpdatedEvent(
                    changedPathList = buildSet {
                        if (updatedCombat.activeAttacksBySourcePort != state.combat.activeAttacksBySourcePort) {
                            add("combat.activeAttacksBySourcePort")
                        }
                        if (updatedCombat.incomingAttacksByTargetPort != state.combat.incomingAttacksByTargetPort) {
                            add("combat.incomingAttacksByTargetPort")
                        }
                        updatedPorts.forEachIndexed { index, port ->
                            if (state.ports.getOrNull(index)?.attacking != port.attacking) {
                                add("ports.${port.number}.attacking")
                            }
                        }
                        if (normalizedCpuLoad != state.runtime.currentCpuLoad) {
                            add("runtime.currentCpuLoad")
                        }
                    }.ifEmpty { setOf("combat") },
                    deltaKeyList = buildSet {
                        if (updatedCombat != state.combat) {
                            add("combat")
                        }
                        if (updatedPorts != state.ports) {
                            add("ports")
                        }
                        if (normalizedCpuLoad != state.runtime.currentCpuLoad) {
                            add("runtime")
                        }
                    }.ifEmpty { setOf("combat") },
                    combat = updatedCombat,
                    ports = updatedPorts,
                    currentCpuLoad = normalizedCpuLoad,
                    includePorts = updatedPorts != state.ports,
                    includeRuntime = normalizedCpuLoad != state.runtime.currentCpuLoad,
                ),
            ),
        )
    }
}

internal class AttackProgramCommand(
    private val attackerStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourcePort: Int,
    override val programId: String,
    private val controllerStateId: GameStateId? = null,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProgramCommand {
    override val name: String = "attack-program"
    override val lifetime: CommandLifetime = CommandLifetime(ATTACK_PROGRAM_LIFETIME)
    override val targetStateIds: Set<GameStateId> = buildSet {
        add(attackerStateId)
        add(targetStateId)
        controllerStateId?.let(::add)
    }
    override val programUpdateStateIds: Set<GameStateId> = setOf(controllerStateId ?: attackerStateId)
    override val programType: String = "attack"
    override val tickInterval = ATTACK_PROGRAM_TICK_INTERVAL

    override suspend fun onStart(context: CommandContext): ProgramExecutionStep {
        val state = context.loadState(attackerStateId)
        val session = state?.combat?.activeAttacksBySourcePort?.get(sourcePort)
        return if (session == null) {
            attackProgramRegistry.unregister(programId)
            ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "attack session missing"),
                relatedStateIds = programUpdateStateIds,
            )
        } else {
            ProgramExecutionStep(
                status = ProgramLifecycleStatus.RUNNING,
                progress = ProgramProgress(message = "running", completedSteps = session.iterationCount),
                relatedStateIds = programUpdateStateIds,
            )
        }
    }

    override suspend fun onTick(context: CommandContext): ProgramExecutionStep {
        val step = context.request(
            AttackTickCommand(
                attackerStateId = attackerStateId,
                targetStateId = targetStateId,
                sourcePort = sourcePort,
                controllerStateId = controllerStateId,
                clock = clock,
            ),
        )
        if (step.status != ProgramLifecycleStatus.RUNNING) {
            attackProgramRegistry.unregister(programId)
        }
        return step
    }

    override suspend fun onCancel(context: CommandContext, reason: String) {
        context.request(
            AttackReleaseCommand(
                attackerStateId = attackerStateId,
                targetStateId = targetStateId,
                sourcePort = sourcePort,
            ),
        )
        attackProgramRegistry.unregister(programId)
    }
}

internal class RedirectProgramCommand(
    private val attackerStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourcePort: Int,
    override val programId: String,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProgramCommand {
    override val name: String = "redirect-program"
    override val lifetime: CommandLifetime = CommandLifetime(ATTACK_PROGRAM_LIFETIME)
    override val targetStateIds: Set<GameStateId> = setOf(attackerStateId, targetStateId)
    override val programUpdateStateIds: Set<GameStateId> = setOf(attackerStateId)
    override val programType: String = "redirect"
    override val tickInterval = ATTACK_PROGRAM_TICK_INTERVAL
    override val timeoutProgressMessage: String = "redirect-timeout"

    override suspend fun onStart(context: CommandContext): ProgramExecutionStep {
        val state = context.loadState(attackerStateId)
        val session = state?.combat?.activeAttacksBySourcePort?.get(sourcePort)
        return if (session == null || session.sessionKind != AttackSessionKind.REDIRECT) {
            attackProgramRegistry.unregister(programId)
            ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "redirect session missing"),
                relatedStateIds = programUpdateStateIds,
            )
        } else {
            ProgramExecutionStep(
                status = ProgramLifecycleStatus.RUNNING,
                progress = ProgramProgress(message = "redirect-started", completedSteps = session.iterationCount),
                relatedStateIds = programUpdateStateIds,
            )
        }
    }

    override suspend fun onTick(context: CommandContext): ProgramExecutionStep {
        val step = context.request(
            RedirectTickCommand(
                attackerStateId = attackerStateId,
                targetStateId = targetStateId,
                sourcePort = sourcePort,
                clock = clock,
            ),
        )
        if (step.status != ProgramLifecycleStatus.RUNNING) {
            attackProgramRegistry.unregister(programId)
        }
        return step
    }

    override suspend fun onCancel(context: CommandContext, reason: String) {
        if (reason == "Command lifetime expired.") {
            val session = context.loadState(attackerStateId)?.combat?.activeAttacksBySourcePort?.get(sourcePort)
            if (session?.sessionKind == AttackSessionKind.REDIRECT) {
                context.publishUiEvent(
                    targetStateIds = setOf(attackerStateId),
                    event = AttackMessageUiEvent(
                        message = REDIRECT_TIMEOUT_MESSAGE,
                        port = sourcePort,
                        ip = attackerStateId.value,
                        windowHandle = session.windowHandle,
                        paneType = AttackPaneType.REDIRECT,
                    ),
                )
            }
        }
        context.request(
            AttackReleaseCommand(
                attackerStateId = attackerStateId,
                targetStateId = targetStateId,
                sourcePort = sourcePort,
            ),
        )
        attackProgramRegistry.unregister(programId)
    }
}

private fun zombieAttackStartFailure(
    controllerState: ComputerState,
    zombieStateId: GameStateId,
    targetStateId: GameStateId,
    sourcePort: Int,
    targetPort: Int,
    code: ZombieAttackStartFailureCode,
    message: String,
): ZombieAttackStartResponse {
    return ZombieAttackStartResponse(
        controllerStateId = controllerState.id,
        zombieStateId = zombieStateId,
        sourcePort = sourcePort,
        targetStateId = targetStateId,
        targetPort = targetPort,
        accepted = false,
        failureCode = code,
        message = message,
        controllerPettyCashAfter = controllerState.economy.pettyCash,
        controllerVersion = controllerState.version,
    )
}

internal suspend fun publishZombieAttackUiEvents(
    context: CommandContext,
    response: ZombieAttackStartResponse,
) {
    val events = when (response.failureCode) {
        ZombieAttackStartFailureCode.INSUFFICIENT_PETTY_CASH -> listOf(
            PopupUiEvent(
                message = "It costs \$20 to attempt an attack from a zombie port.",
                style = PopupUiStyle.ERROR,
            ),
        )

        ZombieAttackStartFailureCode.SOURCE_ALREADY_ATTACKING -> listOf(
            ZombieAttackUiEvent(
                message = "Port ${response.sourcePort} is already performing an attack.",
                zombieIp = response.zombieStateId.value,
                sourcePort = response.sourcePort,
            ),
        )

        ZombieAttackStartFailureCode.ZOMBIE_OVERHEATED -> listOf(
            ZombieAttackUiEvent(
                message = "Computer at ${response.zombieStateId.value} just overheated!",
                zombieIp = response.zombieStateId.value,
                sourcePort = response.sourcePort,
            ),
        )

        ZombieAttackStartFailureCode.INVALID_SOURCE_PORT,
        ZombieAttackStartFailureCode.INVALID_TARGET_PORT,
        ZombieAttackStartFailureCode.TARGET_ALREADY_UNDER_ATTACK,
        ZombieAttackStartFailureCode.NOT_AUTHORIZED -> listOf(
            ZombieAttackUiEvent(
                message = "You cannot access this port.",
                zombieIp = response.zombieStateId.value,
                sourcePort = response.sourcePort,
            ),
        )

        else -> emptyList()
    }
    events.forEach { event ->
        context.publishUiEvent(
            targetStateIds = setOf(response.controllerStateId),
            event = event,
        )
    }
}

internal suspend fun publishRedirectAttackStartUiEvents(
    context: CommandContext,
    response: AttackStartResponse,
    attackerStateId: GameStateId,
    sourcePort: Int,
    windowHandle: Int?,
    redirectWrongType: Boolean,
) {
    val events = when (response.failureCode) {
        AttackStartFailureCode.INSUFFICIENT_PETTY_CASH -> listOf(
            PopupUiEvent(
                message = REDIRECT_FAIL_NOT_ENOUGH_MONEY_MESSAGE,
                style = PopupUiStyle.ERROR,
            ),
        )

        AttackStartFailureCode.SOURCE_ALREADY_ATTACKING -> listOf(
            AttackMessageUiEvent(
                message = REDIRECT_FAIL_ALREADY_REDIRECTING_MESSAGE,
                port = sourcePort,
                ip = attackerStateId.value,
                windowHandle = windowHandle,
                paneType = AttackPaneType.REDIRECT,
            ),
        )

        AttackStartFailureCode.OVERHEATED -> listOf(
            AttackMessageUiEvent(
                message = REDIRECT_FAIL_OVERHEATED_MESSAGE,
                port = sourcePort,
                ip = attackerStateId.value,
                windowHandle = windowHandle,
                paneType = AttackPaneType.REDIRECT,
            ),
        )

        AttackStartFailureCode.INVALID_SOURCE_PORT,
        AttackStartFailureCode.INVALID_TARGET_PORT -> {
            if (!redirectWrongType && response.failureCode == AttackStartFailureCode.INVALID_SOURCE_PORT) {
                emptyList()
            } else {
                listOf(
                    AttackMessageUiEvent(
                        message = REDIRECT_FAIL_WRONG_TYPE_MESSAGE,
                        port = sourcePort,
                        ip = attackerStateId.value,
                        windowHandle = windowHandle,
                        paneType = AttackPaneType.REDIRECT,
                    ),
                )
            }
        }

        else -> emptyList()
    }
    events.forEach { event ->
        context.publishUiEvent(
            targetStateIds = setOf(attackerStateId),
            event = event,
        )
    }
}

fun attackLoadoutFromLegacyPayload(
    secondaryPorts: List<Int?>?,
    scripts: List<List<String?>?>?,
    extraInfo: List<HookValue>?,
): AttackLoadout {
    return AttackLoadout(
        secondaryPorts = secondaryPorts.orEmpty().mapNotNull { it },
        maliciousScripts = scripts.orEmpty().map { row ->
            row?.let { values ->
                val folder = values.getOrNull(0)
                val name = values.getOrNull(1)
                if (folder.isNullOrBlank() || name.isNullOrBlank()) {
                    null
                } else {
                    AttackScriptReference(folder = folder, name = name)
                }
            }
        },
        extraInfo = extraInfo.orEmpty(),
    )
}

private fun PortState.isValidAttackSource(): Boolean {
    return enabled &&
        !dummy &&
        installedApplication?.kind == ApplicationKind.ATTACK
}

private fun PortState.isValidRedirectSource(): Boolean {
    return enabled &&
        !dummy &&
        installedApplication?.kind == ApplicationKind.REDIRECT
}

private fun PortState.isValidAttackTarget(
    now: Long,
    allowFrozen: Boolean,
    allowOverheated: Boolean,
): Boolean {
    return enabled &&
        !dummy &&
        !isWeakened() &&
        health > 0.0 &&
        (allowFrozen || !isFrozenAt(now)) &&
        (allowOverheated || !overheated)
}

private fun PortState.isValidRedirectTarget(): Boolean {
    return enabled &&
        !dummy &&
        !isWeakened() &&
        health > 0.0 &&
        installedApplication?.kind == ApplicationKind.REDIRECT
}

private fun PortState.currentAttackCpuCost(): Double {
    return (installedApplication?.cpuCost ?: 0.0) + (installedFirewall?.cpuCost ?: 0.0)
}

private fun ComputerState.defaultAttackPort(): Int? {
    return ports.firstOrNull { port ->
        port.defaultPort &&
            port.enabled &&
            !port.dummy &&
            port.installedApplication?.kind == ApplicationKind.ATTACK
    }?.number
}

internal fun List<PortState>.markAttacking(
    sourcePort: Int,
    attacking: Boolean,
): List<PortState> {
    return map { port ->
        if (port.number == sourcePort) {
            port.copy(attacking = attacking)
        } else {
            port
        }
    }
}

private fun CombatState.withSession(session: AttackSessionState): CombatState {
    return copy(activeAttacksBySourcePort = activeAttacksBySourcePort + (session.sourcePort to session))
}

private fun CombatState.removeSession(sourcePort: Int): CombatState {
    return copy(activeAttacksBySourcePort = activeAttacksBySourcePort - sourcePort)
}

private fun CombatState.withIncomingAttack(incoming: IncomingAttackState): CombatState {
    return copy(incomingAttacksByTargetPort = incomingAttacksByTargetPort + (incoming.targetPort to incoming))
}

private fun CombatState.removeIncomingAttack(targetPort: Int): CombatState {
    return copy(incomingAttacksByTargetPort = incomingAttacksByTargetPort - targetPort)
}

private fun ComputerState.expectedRuntimeCpuLoad(): Double {
    return watches.watches
        .filter { it.enabled }
        .sumOf { it.cpuCost } +
        combat.activeAttacksBySourcePort.keys.sumOf { activeSourcePort ->
            port(activeSourcePort)?.currentAttackCpuCost() ?: 0.0
        }
}

private fun ComputerState.attackBaseDamage(): Double {
    return 2.0 + legacyLevelForXp(stats.skillExperience(ScriptFamily.ATTACK)) * 0.2
}

private fun ComputerState.redirectBaseDamage(): Double {
    return 2.0 + legacyLevelForXp(stats.skillExperience(ScriptFamily.REDIRECT)) * 0.2
}

private fun ComputerState.isFreezeImmune(): Boolean {
    return hardware.equipmentSlots.values.any { it.freezeImmune }
}

private fun ComputerState.isDestroyWatchesImmune(): Boolean {
    return hardware.equipmentSlots.values.any { it.destroyWatchesImmune }
}

private fun PortState.isBankingApplication(): Boolean {
    return installedApplication?.banking == true ||
        installedApplication?.kind == ApplicationKind.BANKING ||
        type.equals("bank", ignoreCase = true) ||
        type.equals("banking", ignoreCase = true)
}

private fun PortState.isFtpApplication(): Boolean {
    return installedApplication?.kind == ApplicationKind.FTP ||
        type.equals("ftp", ignoreCase = true)
}

private fun PortState.resolveEmptyPettyCashAmount(targetPettyCash: Double): Double {
    val actionProfile = installedFirewall?.actionProfile ?: FirewallActionProfile()
    if (actionProfile.emptyPettyCashFailChance > 0.0) {
        return 0.0
    }
    return (targetPettyCash * actionProfile.emptyPettyCashReductionMultiplier).coerceIn(0.0, targetPettyCash)
}

private fun PortState.shouldFailStealFile(): Boolean {
    val actionProfile = installedFirewall?.actionProfile ?: FirewallActionProfile()
    return actionProfile.stealFileFailChance > 0.0
}

private fun PortState.showChoicesType(): ShowChoicesType? {
    return when (installedApplication?.kind) {
        ApplicationKind.BANKING -> ShowChoicesType.BANK
        ApplicationKind.FTP -> ShowChoicesType.FTP
        ApplicationKind.ATTACK -> ShowChoicesType.ATTACK
        ApplicationKind.HTTP -> ShowChoicesType.HTTP
        ApplicationKind.REDIRECT -> ShowChoicesType.SHIPPING
        else -> null
    }
}

private fun AttackSessionState.programType(): String {
    return when (sessionKind) {
        AttackSessionKind.ATTACK -> "attack"
        AttackSessionKind.REDIRECT -> "redirect"
    }
}

private fun List<Double>.valueAtCommodity(index: Int): Double = getOrNull(index) ?: 0.0

private fun Int.redirectCommodityName(): String = REDIRECT_COMMODITY_NAMES.getOrElse(this) { "Commodity" }

private fun Double.toRedirectCommodityAmount(): String {
    return if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        toString()
    }
}

private fun EconomyState.withCommodityAmount(
    index: Int,
    amount: Double,
): EconomyState {
    val normalized = List(commodities.size.coerceAtLeast(5)) { commodityIndex ->
        when (commodityIndex) {
            index -> amount.coerceAtLeast(0.0)
            else -> commodities.getOrNull(commodityIndex) ?: 0.0
        }
    }
    return copy(commodities = normalized)
}

private fun EconomyState.adjustCommodityAmount(
    index: Int,
    delta: Double,
): EconomyState {
    val nextAmount = (commodities.valueAtCommodity(index) + delta).coerceAtLeast(0.0)
    return withCommodityAmount(index, nextAmount)
}

private fun EconomyState.changedPathsTo(updated: EconomyState): Set<String> {
    return buildSet {
        if (pettyCash != updated.pettyCash) {
            add("economy.pettyCash")
        }
        if (bankMoney != updated.bankMoney) {
            add("economy.bankMoney")
        }
        if (defaultBankPort != updated.defaultBankPort) {
            add("economy.defaultBankPort")
        }
        if (defaultRedirectPort != updated.defaultRedirectPort) {
            add("economy.defaultRedirectPort")
        }
        val maxCommodityCount = maxOf(commodities.size, updated.commodities.size)
        repeat(maxCommodityCount) { index ->
            if (commodities.getOrNull(index) != updated.commodities.getOrNull(index)) {
                add("economy.commodities.$index")
            }
        }
        val maxRespawnCount = maxOf(commodityRespawn.size, updated.commodityRespawn.size)
        repeat(maxRespawnCount) { index ->
            if (commodityRespawn.getOrNull(index) != updated.commodityRespawn.getOrNull(index)) {
                add("economy.commodityRespawn.$index")
            }
        }
    }.ifEmpty { setOf("economy") }
}

private fun ComputerState.hasWatchOnPort(portNumber: Int): Boolean {
    return watches.watches.any { it.installPort == portNumber }
}

private fun ComputerState.buildAttackTargetView(
    targetPort: Int,
    lastAppliedDamage: Double,
    completed: Boolean,
    healthOverride: Double? = null,
): AttackTargetView {
    val portState = requireNotNull(port(targetPort)) {
        "Target port $targetPort does not exist on ${id.value}."
    }
    return AttackTargetView(
        targetStateId = id,
        targetPort = targetPort,
        health = healthOverride ?: portState.health,
        pettyCash = economy.pettyCash,
        cpuCost = portState.currentAttackCpuCost(),
        watchPresent = hasWatchOnPort(targetPort),
        npc = identity.isNpc,
        lastAppliedDamage = lastAppliedDamage,
        completed = completed,
    )
}

private fun buildTargetCyclePorts(
    initialTargetPort: Int,
    secondaryPorts: List<Int>,
): List<Int> {
    return buildList {
        add(initialTargetPort)
        addAll(secondaryPorts)
    }
}

private fun AttackSessionState.withResolvedTargetCycle(): AttackSessionState {
    val resolvedCycle = targetCyclePorts.ifEmpty {
        buildTargetCyclePorts(
            initialTargetPort = targetPort,
            secondaryPorts = secondaryPorts,
        )
    }
    val resolvedCursor = resolvedCycle.indexOf(targetPort).takeIf { it >= 0 } ?: 0
    return if (resolvedCycle == targetCyclePorts && resolvedCursor == targetCycleCursor) {
        this
    } else {
        copy(
            targetCyclePorts = resolvedCycle,
            targetCycleCursor = resolvedCursor,
        )
    }
}

private fun AttackSessionState.findNextSwitchTarget(
    targetState: ComputerState,
    attackerStateId: GameStateId,
    sourcePort: Int,
    now: Long,
): Pair<Int, Int>? {
    val resolved = withResolvedTargetCycle()
    if (resolved.targetCyclePorts.size <= 1) {
        return null
    }
    for (offset in 1..resolved.targetCyclePorts.lastIndex) {
        val candidateCursor = (resolved.targetCycleCursor + offset) % resolved.targetCyclePorts.size
        val candidatePort = resolved.targetCyclePorts[candidateCursor]
        if (candidatePort == resolved.targetPort) {
            continue
        }
        val candidateState = targetState.port(candidatePort) ?: continue
        if (!candidateState.isValidAttackTarget(now = now, allowFrozen = false, allowOverheated = false)) {
            continue
        }
        val incoming = targetState.combat.incomingAttacksByTargetPort[candidatePort]
        if (incoming != null) {
            val sameAttack =
                incoming.attackerStateId == attackerStateId &&
                    incoming.attackerSourcePort == sourcePort &&
                    incoming.targetPort == candidatePort
            if (!sameAttack) {
                continue
            }
        }
        return candidatePort to candidateCursor
    }
    return null
}

private fun List<PortState>.combatChangedPaths(
    previous: List<PortState>,
): Set<String> {
    val previousByPort = previous.associateBy { it.number }
    return buildSet {
        for (port in this@combatChangedPaths) {
            val previousPort = previousByPort[port.number]
            if (previousPort == null) {
                add("ports.${port.number}")
                continue
            }
            if (previousPort.health != port.health) {
                add("ports.${port.number}.health")
            }
            if (previousPort.healCount != port.healCount) {
                add("ports.${port.number}.healCount")
            }
            if (previousPort.weakenedAccess != port.weakenedAccess) {
                add("ports.${port.number}.weakenedAccess")
            }
            if (previousPort.freezeExpiresAtEpochMillis != port.freezeExpiresAtEpochMillis) {
                add("ports.${port.number}.freezeExpiresAtEpochMillis")
            }
            if (previousPort.overheated != port.overheated) {
                add("ports.${port.number}.overheated")
            }
            if (previousPort.attacking != port.attacking) {
                add("ports.${port.number}.attacking")
            }
            if (previousPort.installedApplication != port.installedApplication) {
                add("ports.${port.number}.installedApplication")
            }
        }
    }
}

private fun ComputerState.toAttackExecutionInput(
    phase: AttackExecutionPhase,
    sourcePort: Int,
    targetView: AttackTargetView,
    iterations: Int,
    sourceIpOverride: String = id.value,
    isZombie: Boolean = false,
    allowedZombieIps: Set<String> = emptySet(),
): AttackExecutionInput {
    return AttackExecutionInput(
        phase = phase,
        sourceIp = sourceIpOverride,
        sourcePort = sourcePort,
        targetIp = targetView.targetStateId.value,
        targetPort = targetView.targetPort,
        targetHealth = targetView.health,
        targetCpuCost = targetView.cpuCost,
        targetWatchPresent = targetView.watchPresent,
        targetPettyCash = targetView.pettyCash,
        hostHealth = port(sourcePort)?.health ?: 0.0,
        currentCpuLoad = runtime.currentCpuLoad,
        maximumCpuLoad = hardware.cpuMax,
        iterations = iterations,
        isZombie = isZombie,
        allowedZombieIps = allowedZombieIps,
    )
}

private fun ApplicationKind.isInstallScriptSupportedTarget(): Boolean {
    return this == ApplicationKind.BANKING ||
        this == ApplicationKind.FTP ||
        this == ApplicationKind.ATTACK
}

private fun PortState.shouldFailInstallScript(): Boolean {
    val actionProfile = installedFirewall?.actionProfile ?: FirewallActionProfile()
    return actionProfile.installScriptFailChance > 0.0
}

private fun emptyInstallScriptBundleFor(applicationKind: ApplicationKind): ProgramScriptBundle {
    val family = when (applicationKind) {
        ApplicationKind.BANKING -> ScriptFamily.BANKING
        ApplicationKind.ATTACK -> ScriptFamily.ATTACK
        ApplicationKind.FTP -> ScriptFamily.GENERAL
        else -> ScriptFamily.GENERAL
    }
    return ProgramScriptBundle(family = family)
}

private fun List<HookValue>.toMaliciousProgramConfig(): MaliciousProgramConfig {
    val targetIp = (getOrNull(0) as? StringHookValue)?.value
    val pettyCashTarget = when (val value = getOrNull(1)) {
        is FloatHookValue -> value.value
        is IntHookValue -> value.value.toDouble()
        else -> 0.0
    }
    return MaliciousProgramConfig(
        targetIp = targetIp,
        pettyCashTarget = pettyCashTarget,
    )
}
