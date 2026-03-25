package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.HookValue
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

private const val ATTACK_START_COST: Double = 10.0
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
        if (!resolvedTargetPort.isValidAttackTarget()) {
            return failure(
                attackerState = attackerState,
                code = AttackStartFailureCode.INVALID_TARGET_PORT,
                message = "Target port $targetPort is not attackable.",
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
        val session = AttackSessionState(
            programId = programId,
            sourcePort = sourcePort,
            targetStateId = targetStateId,
            targetPort = targetPort,
            windowHandle = windowHandle,
            secondaryPorts = loadout.secondaryPorts,
            maliciousScripts = loadout.maliciousScripts,
            extraInfo = loadout.extraInfo,
            startedAtEpochMillis = clock(),
            iterationCount = 0,
        )
        val updated = context.appendEvents(
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
            newPettyCash = updated.economy.pettyCash,
        )

        val handle = context.schedule(
            AttackProgramCommand(
                attackerStateId = attackerStateId,
                sourcePort = sourcePort,
                programId = programId,
                attackProgramRegistry = attackProgramRegistry,
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
            session = finalState.combat.activeAttacksBySourcePort[sourcePort],
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
        val updatedState = if (cancelled) {
            context.requireExistingState(attackerStateId)
        } else {
            attackProgramRegistry.unregister(session.programId)
            cleanupAttackRuntime(
                context = context,
                state = attackerState,
                sourcePort = sourcePort,
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
            context.requireExistingState(attackerStateId)
        }

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

        val updatedCombat = state.combat.copy(activeAttacksBySourcePort = liveSessions)
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
                        if (updatedCombat != state.combat) {
                            add("combat.activeAttacksBySourcePort")
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

class AttackProgramCommand(
    private val attackerStateId: GameStateId,
    private val sourcePort: Int,
    override val programId: String,
    private val attackProgramRegistry: AttackProgramRegistry = NoOpAttackProgramRegistry,
) : ProgramCommand {
    override val name: String = "attack-program"
    override val lifetime: CommandLifetime = CommandLifetime(ATTACK_PROGRAM_LIFETIME)
    override val targetStateIds: Set<GameStateId> = setOf(attackerStateId)
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
                relatedStateIds = setOf(attackerStateId),
            )
        } else {
            ProgramExecutionStep(
                status = ProgramLifecycleStatus.RUNNING,
                progress = ProgramProgress(message = "running", completedSteps = session.iterationCount),
                relatedStateIds = setOf(attackerStateId),
            )
        }
    }

    override suspend fun onTick(context: CommandContext): ProgramExecutionStep {
        val state = context.requireExistingState(attackerStateId)
        val session = state.combat.activeAttacksBySourcePort[sourcePort]
        if (session == null) {
            attackProgramRegistry.unregister(programId)
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.CANCELLED,
                progress = ProgramProgress(message = "attack session missing"),
                relatedStateIds = setOf(attackerStateId),
            )
        }

        val updated = context.appendEvents(
            id = attackerStateId,
            events = listOf(
                CombatStateUpdatedEvent(
                    changedPathList = setOf("combat.activeAttacksBySourcePort.$sourcePort.iterationCount"),
                    deltaKeyList = setOf("combat"),
                    combat = state.combat.withSession(session.copy(iterationCount = session.iterationCount + 1)),
                    ports = state.ports,
                    currentCpuLoad = state.runtime.currentCpuLoad,
                    includePorts = false,
                    includeRuntime = false,
                ),
            ),
        )
        val updatedSession = updated.combat.activeAttacksBySourcePort[sourcePort]
        return ProgramExecutionStep(
            status = ProgramLifecycleStatus.RUNNING,
            progress = ProgramProgress(
                message = "running",
                completedSteps = updatedSession?.iterationCount ?: session.iterationCount + 1,
            ),
            relatedStateIds = setOf(attackerStateId),
        )
    }

    override suspend fun onCancel(context: CommandContext, reason: String) {
        val state = context.loadState(attackerStateId) ?: return
        cleanupAttackRuntime(context = context, state = state, sourcePort = sourcePort)
        attackProgramRegistry.unregister(programId)
    }
}

internal suspend fun cleanupAttackRuntime(
    context: CommandContext,
    state: ComputerState,
    sourcePort: Int,
): ComputerState {
    val updatedCombat = state.combat.removeSession(sourcePort)
    val updatedPorts = state.ports.markAttacking(sourcePort, false)
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
        id = state.id,
        events = listOf(
            CombatStateUpdatedEvent(
                changedPathList = buildSet {
                    if (updatedCombat != state.combat) {
                        add("combat.activeAttacksBySourcePort.$sourcePort")
                    }
                    if (updatedPorts != state.ports) {
                        add("ports.$sourcePort.attacking")
                    }
                    if (normalizedCpuLoad != state.runtime.currentCpuLoad) {
                        add("runtime.currentCpuLoad")
                    }
                },
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
                },
                combat = updatedCombat,
                ports = updatedPorts,
                currentCpuLoad = normalizedCpuLoad,
                includePorts = updatedPorts != state.ports,
                includeRuntime = normalizedCpuLoad != state.runtime.currentCpuLoad,
            ),
        ),
    )
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

private fun PortState.isValidAttackTarget(): Boolean = enabled && !dummy

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

private fun ComputerState.expectedRuntimeCpuLoad(): Double {
    return watches.watches
        .filter { it.enabled }
        .sumOf { it.cpuCost } +
        combat.activeAttacksBySourcePort.keys.sumOf { sourcePort ->
            port(sourcePort)?.currentAttackCpuCost() ?: 0.0
        }
}

private fun ComputerState.isOverheated(): Boolean {
    return hardware.cpuMax > 0.0 && runtime.currentCpuLoad > hardware.cpuMax
}
