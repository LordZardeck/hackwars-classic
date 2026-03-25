package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.AttackExecutionPhase
import com.hackwars.rewrite.hackscript.AttackExecutionInput
import com.hackwars.rewrite.hackscript.AttackAppendHostLogEffect
import com.hackwars.rewrite.hackscript.AttackBerserkEffect
import com.hackwars.rewrite.hackscript.AttackCancelCurrentAttackEffect
import com.hackwars.rewrite.hackscript.AttackDeleteTargetLogsEffect
import com.hackwars.rewrite.hackscript.AttackDestroyTargetWatchesEffect
import com.hackwars.rewrite.hackscript.AttackEditTargetLogsEffect
import com.hackwars.rewrite.hackscript.AttackFreezeTargetPortEffect
import com.hackwars.rewrite.hackscript.AttackSwitchTargetEffect
import com.hackwars.rewrite.hackscript.HookValue
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

private const val ATTACK_START_COST: Double = 10.0
private const val ATTACK_FREEZE_DURATION_MILLIS: Long = 10_000L
private val ATTACK_PROGRAM_TICK_INTERVAL = 180.seconds
private val ATTACK_PROGRAM_LIFETIME = 450.seconds

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
@SerialName("combat_state_updated")
data class CombatStateUpdatedEvent(
    private val changedPathList: Set<String>,
    private val deltaKeyList: Set<String>,
    val combat: CombatState,
    val ports: List<PortState>,
    val currentCpuLoad: Double,
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
            runtime = state.runtime.withCpuLoad(currentCpuLoad).withMutationVersion(nextVersion),
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
                attackerState = attackerState,
                code = AttackStartFailureCode.SOURCE_IP_MISMATCH,
                message = "Source ip $sourceIp does not match ${attackerStateId.value}.",
            )
        }
        if (targetStateId == attackerStateId) {
            return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.SELF_TARGET,
                message = "You cannot attack your own state.",
            )
        }

        val source = attackerState.port(sourcePort)
            ?: return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.SOURCE_PORT_NOT_FOUND,
                message = "Port $sourcePort does not exist on ${attackerStateId.value}.",
            )
        if (!source.isValidAttackSource()) {
            return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.INVALID_SOURCE_PORT,
                message = "Port $sourcePort is not an active attack port.",
            )
        }
        if (source.attacking || attackerState.combat.activeAttacksBySourcePort.containsKey(sourcePort)) {
            return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.SOURCE_ALREADY_ATTACKING,
                message = "Port $sourcePort is already attacking.",
            )
        }

        val targetState = context.loadState(targetStateId)
            ?: return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.TARGET_NOT_FOUND,
                message = "Target ${targetStateId.value} does not exist.",
            )
        val resolvedTargetPort = targetState.port(targetPort)
            ?: return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.TARGET_PORT_NOT_FOUND,
                message = "Target port $targetPort does not exist on ${targetStateId.value}.",
            )
        if (!resolvedTargetPort.isValidAttackTarget(now = now, allowFrozen = false)) {
            return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.INVALID_TARGET_PORT,
                message = "Target port $targetPort is not attackable.",
            )
        }
        if (targetState.combat.incomingAttacksByTargetPort.containsKey(targetPort)) {
            return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.TARGET_ALREADY_UNDER_ATTACK,
                message = "Target port $targetPort is already under attack.",
            )
        }
        if (!attackerState.hasActiveDefaultBankPort()) {
            return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.ACTIVE_BANK_REQUIRED,
                message = "Attacking requires an active default banking port.",
            )
        }
        if (attackerState.economy.pettyCash < ATTACK_START_COST) {
            return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.INSUFFICIENT_PETTY_CASH,
                message = "Attacking requires at least \$10 petty cash.",
            )
        }
        if (attackerState.isOverheated()) {
            return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.OVERHEATED,
                message = "You cannot start an attack while overheated.",
            )
        }

        val reservedCpu = source.currentAttackCpuCost()
        val nextCpuLoad = attackerState.runtime.currentCpuLoad + reservedCpu
        if (nextCpuLoad > attackerState.hardware.cpuMax) {
            return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.CPU_HEADROOM_EXCEEDED,
                message = "Starting this attack would exceed the current CPU limit.",
            )
        }

        val programId = "attack-${attackerStateId.value}-$sourcePort-${UUID.randomUUID()}"
        val initializeResult = context.request(
            AttackInitializeCommand(
                attackerStateId = attackerStateId,
                targetStateId = targetStateId,
                sourcePort = sourcePort,
                targetPort = targetPort,
                programId = programId,
                loadout = loadout,
                windowHandle = windowHandle,
                reservedCpu = reservedCpu,
                attackRuntimeExecutor = DefaultAttackRuntimeExecutor,
                clock = clock,
            ),
        )

        val handle = context.schedule(
            AttackProgramCommand(
                attackerStateId = attackerStateId,
                targetStateId = targetStateId,
                sourcePort = sourcePort,
                programId = programId,
                attackProgramRegistry = attackProgramRegistry,
                clock = clock,
            ),
        )
        attackProgramRegistry.register(attackerStateId, sourcePort, programId, handle)

        val finalState = context.requireExistingState(attackerStateId)
        return AttackStartResponse(
            attackerStateId = attackerStateId,
            sourcePort = sourcePort,
            targetStateId = targetStateId,
            targetPort = targetPort,
            accepted = true,
            message = "attack-started",
            chargedAmount = ATTACK_START_COST,
            pettyCashAfter = finalState.economy.pettyCash,
            currentCpuLoadAfter = finalState.runtime.currentCpuLoad,
            session = finalState.combat.activeAttacksBySourcePort[sourcePort] ?: initializeResult.session,
            version = finalState.version,
        )
    }

    private fun failure(
        attackerState: ComputerState,
        code: AttackStartFailureCode,
        message: String,
    ): AttackStartResponse {
        return AttackStartResponse(
            attackerStateId = attackerState.id,
            sourcePort = sourcePort,
            targetStateId = targetStateId,
            targetPort = targetPort,
            accepted = false,
            failureCode = code,
            message = message,
            version = attackerState.version,
        )
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
            reason = "requestcancelattack",
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
                    programType = "attack",
                    status = ProgramLifecycleStatus.CANCELLED,
                    relatedStateIds = setOf(attackerStateId),
                    progress = ProgramProgress(message = "requestcancelattack"),
                ),
            )
        }

        val updatedState = context.requireExistingState(attackerStateId)
        return AttackCancelResponse(
            stateId = attackerStateId,
            sourcePort = sourcePort,
            accepted = true,
            hadActiveSession = true,
            message = "attack-cancelled",
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

internal class AttackInitializeCommand(
    private val attackerStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourcePort: Int,
    private val targetPort: Int,
    private val programId: String,
    private val loadout: AttackLoadout,
    private val windowHandle: Int,
    private val reservedCpu: Double,
    private val attackRuntimeExecutor: AttackRuntimeExecutor = DefaultAttackRuntimeExecutor,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<AttackInitializeResult> {
    override val name: String = "attackinitialize"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(attackerStateId, targetStateId)

    override suspend fun execute(context: CommandContext): AttackInitializeResult {
        val attackerState = context.requireExistingState(attackerStateId)
        val targetState = context.requireExistingState(targetStateId)
        val startedAt = clock()
        val targetView = targetState.buildAttackTargetView(targetPort, lastAppliedDamage = 0.0, completed = false)
        val session = AttackSessionState(
            programId = programId,
            sourcePort = sourcePort,
            targetStateId = targetStateId,
            targetPort = targetPort,
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
        val nextCpuLoad = attackerState.runtime.currentCpuLoad + reservedCpu
        val updatedAttacker = context.appendEvents(
            id = attackerStateId,
            events = listOf(
                EconomyBalanceAdjustedEvent(pettyCashDelta = -ATTACK_START_COST),
                CombatStateUpdatedEvent(
                    changedPathList = setOf(
                        "combat.activeAttacksBySourcePort.$sourcePort",
                        "ports.$sourcePort.attacking",
                        "runtime.currentCpuLoad",
                    ),
                    deltaKeyList = setOf("economy", "ports", "combat", "runtime"),
                    combat = attackerState.combat.withSession(session),
                    ports = attackerState.ports.markAttacking(sourcePort, true),
                    currentCpuLoad = nextCpuLoad,
                    includePorts = true,
                    includeRuntime = true,
                ),
            ),
        )
        context.evaluatePassivePettyCashChange(
            targetStateId = attackerStateId,
            previousPettyCash = attackerState.economy.pettyCash,
            newPettyCash = updatedAttacker.economy.pettyCash,
        )
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
        attackRuntimeExecutor.execute(
            context = context,
            attackerState = updatedAttacker,
            sourcePort = sourcePort,
            phase = AttackScriptPhase.INITIALIZE,
            input = updatedAttacker.toAttackExecutionInput(
                phase = AttackExecutionPhase.INITIALIZE,
                sourcePort = sourcePort,
                targetView = session.targetView,
                iterations = 0,
            ),
        )
        return AttackInitializeResult(session = session)
    }
}

internal class AttackTickCommand(
    private val attackerStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val sourcePort: Int,
    private val attackRuntimeExecutor: AttackRuntimeExecutor = DefaultAttackRuntimeExecutor,
    private val firewallCombatResolver: FirewallCombatResolver = DefaultFirewallCombatResolver,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<ProgramExecutionStep> {
    override val name: String = "attackcontinue"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(attackerStateId, targetStateId)

    override suspend fun execute(context: CommandContext): ProgramExecutionStep {
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
                relatedStateIds = setOf(attackerStateId),
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
                relatedStateIds = setOf(attackerStateId),
            )
        }

        val targetState = context.loadState(targetStateId)
        val targetPortState = targetState?.port(session.targetPort)
        val incoming = targetState?.combat?.incomingAttacksByTargetPort?.get(session.targetPort)
        if (
            targetState == null ||
            targetPortState == null ||
            !targetPortState.isValidAttackTarget(now = clock(), allowFrozen = true) ||
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
                relatedStateIds = setOf(attackerStateId),
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
                relatedStateIds = setOf(attackerStateId),
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
        )
        val continueOutcome = attackRuntimeExecutor.evaluate(
            attackerState = attackerState,
            sourcePort = sourcePort,
            phase = AttackScriptPhase.CONTINUE,
            input = continueInput,
        )
        val baseDamage = attackerState.attackBaseDamage()
        val nextIteration = initialSession.iterationCount + 1
        var currentSession: AttackSessionState = initialSession
        var currentTargetCombat: CombatState = initialTargetState.combat
        var currentTargetPorts: List<PortState> = initialTargetState.ports
        var currentTargetWatches: WatchManagerState = initialTargetState.watches
        var currentTargetRuntimeCpuLoad: Double = initialTargetState.runtime.currentCpuLoad
        var currentTargetState: ComputerState = initialTargetState
        var currentTargetPortState: PortState = initialTargetPortState
        var currentAttackerPorts: List<PortState> = attackerState.ports
        var currentSourcePortState: PortState = sourcePortState
        var cancelRequested = false
        var suppressNormalDamage = false
        var attackXpDelta = 0.0
        var completed = false
        var lastAppliedDamage = 0.0

        fun refreshTargetState() {
            currentTargetState = targetState.copy(
                combat = currentTargetCombat,
                ports = currentTargetPorts,
                watches = currentTargetWatches,
                runtime = targetState.runtime.copy(currentCpuLoad = currentTargetRuntimeCpuLoad),
            )
        }

        fun replaceTargetCombat(combat: CombatState) {
            currentTargetCombat = combat
            refreshTargetState()
        }

        fun replaceTargetPort(port: PortState) {
            currentTargetPortState = port
            currentTargetPorts = currentTargetPorts.upsertPort(port, targetState.economy.defaultBankPort)
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

        fun replaceSourcePort(port: PortState) {
            currentSourcePortState = port
            currentAttackerPorts = currentAttackerPorts.upsertPort(port, attackerState.economy.defaultBankPort)
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

        suspend fun destroyCurrentTargetWatches() {
            if (currentTargetState.identity.isNpc || currentTargetState.isDestroyWatchesImmune()) {
                return
            }

            val removedWatches = currentTargetWatches.watches.filter { watch ->
                watch.installPort == currentSession.targetPort &&
                    watch.enabled &&
                    watch.kind != WatchKind.SCAN
            }
            if (removedWatches.isEmpty()) {
                return
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
                    sourceIp = attackerStateId.value,
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
                    sourceIpForTrigger = attackerStateId.value,
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
                        context.appendEvents(
                            id = targetStateId,
                            events = listOf(
                                HostLogRenderedTextReplacedEvent(
                                    data = effect.data,
                                    replace = effect.replace,
                                ),
                            ),
                        )
                        cancelRequested = true
                    }

                    is AttackDeleteTargetLogsEffect -> {
                        context.appendEvents(
                            id = targetStateId,
                            events = listOf(HostLogsDeletedBySourceIpEvent(sourceIp = effect.sourceIp)),
                        )
                        cancelRequested = true
                    }

                    is AttackDestroyTargetWatchesEffect -> {
                        destroyCurrentTargetWatches()
                        cancelRequested = true
                    }

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
                            context.appendEvents(
                                id = targetStateId,
                                events = listOf(
                                    HostLogRenderedTextReplacedEvent(
                                        data = effect.data,
                                        replace = effect.replace,
                                    ),
                                ),
                            )
                        }

                        is AttackDeleteTargetLogsEffect -> {
                            context.appendEvents(
                                id = targetStateId,
                                events = listOf(HostLogsDeletedBySourceIpEvent(sourceIp = effect.sourceIp)),
                            )
                        }

                        is AttackDestroyTargetWatchesEffect -> {
                            destroyCurrentTargetWatches()
                        }

                        else -> Unit
                    }
                }
            }
        }

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
        context.appendEvents(
            id = attackerStateId,
            events = buildList {
                if (attackXpDelta > 0.0) {
                    add(
                        SkillExperienceAdjustedEvent(
                            family = ScriptFamily.ATTACK,
                            delta = attackXpDelta,
                        ),
                    )
                }
                if (attackerCombatChanged || attackerPortsChangedPaths.isNotEmpty() || runtimeChanged) {
                    add(
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
                                if (attackXpDelta > 0.0) {
                                    add("stats")
                                }
                            }.ifEmpty {
                                if (attackXpDelta > 0.0) setOf("stats") else setOf("combat")
                            },
                            combat = finalAttackerCombat,
                            ports = finalAttackerPorts,
                            currentCpuLoad = finalCpuLoad,
                            includePorts = attackerPortsChangedPaths.isNotEmpty(),
                            includeRuntime = runtimeChanged,
                        ),
                    )
                }
            },
        )

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
            relatedStateIds = setOf(attackerStateId),
        )
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(AttackTickCommand::class.java)
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
            val targetState = context.loadState(targetStateId)
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
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProgramCommand {
    override val name: String = "attack-program"
    override val lifetime: CommandLifetime = CommandLifetime(ATTACK_PROGRAM_LIFETIME)
    override val targetStateIds: Set<GameStateId> = setOf(attackerStateId, targetStateId)
    override val programUpdateStateIds: Set<GameStateId> = setOf(attackerStateId)
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

private fun PortState.isValidAttackTarget(
    now: Long,
    allowFrozen: Boolean,
): Boolean {
    return enabled &&
        !dummy &&
        health > 0.0 &&
        (allowFrozen || !isFrozenAt(now))
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

private fun ComputerState.isOverheated(): Boolean {
    return hardware.cpuMax > 0.0 && runtime.currentCpuLoad > hardware.cpuMax
}

private fun ComputerState.attackBaseDamage(): Double {
    return 2.0 + legacyLevelForXp(stats.skillExperience(ScriptFamily.ATTACK)) * 0.2
}

private fun ComputerState.isFreezeImmune(): Boolean {
    return hardware.equipmentSlots.values.any { it.freezeImmune }
}

private fun ComputerState.isDestroyWatchesImmune(): Boolean {
    return hardware.equipmentSlots.values.any { it.destroyWatchesImmune }
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
        if (!candidateState.isValidAttackTarget(now = now, allowFrozen = false)) {
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
            if (previousPort.freezeExpiresAtEpochMillis != port.freezeExpiresAtEpochMillis) {
                add("ports.${port.number}.freezeExpiresAtEpochMillis")
            }
            if (previousPort.attacking != port.attacking) {
                add("ports.${port.number}.attacking")
            }
        }
    }
}

private fun ComputerState.toAttackExecutionInput(
    phase: AttackExecutionPhase,
    sourcePort: Int,
    targetView: AttackTargetView,
    iterations: Int,
): AttackExecutionInput {
    return AttackExecutionInput(
        phase = phase,
        sourceIp = id.value,
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
    )
}
