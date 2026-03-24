package com.hackwars.rewrite.gamecore

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class CommandMetadata(
    val connectionId: String? = null,
    val requestId: String? = null,
    val authenticatedPlayFabId: String? = null,
    val authenticatedStateId: GameStateId? = null,
)

data class CommandEnvelopeInput(
    val commandId: String,
    val commandName: String,
    val targetStateIds: Set<GameStateId>,
    val payloadJson: String?,
    val expectsResponse: Boolean,
    val metadata: CommandMetadata,
)

interface GameCommand {
    val name: String
    val lifetime: CommandLifetime
    val targetStateIds: Set<GameStateId>
}

interface FireAndForgetCommand : GameCommand {
    suspend fun execute(context: CommandContext)
}

interface RequestCommand<R> : GameCommand {
    suspend fun execute(context: CommandContext): R
}

data class ProgramExecutionStep(
    val status: ProgramLifecycleStatus,
    val progress: ProgramProgress = ProgramProgress(),
    val relatedStateIds: Set<GameStateId> = emptySet(),
)

interface ProgramCommand : GameCommand {
    val programId: String
    val programType: String
    val tickInterval: Duration

    suspend fun onStart(context: CommandContext): ProgramExecutionStep = ProgramExecutionStep(
        status = ProgramLifecycleStatus.RUNNING,
        progress = ProgramProgress(message = "started"),
        relatedStateIds = targetStateIds,
    )

    suspend fun onTick(context: CommandContext): ProgramExecutionStep

    suspend fun onCancel(context: CommandContext, reason: String) {
    }
}

interface ProgramHandle {
    val programId: String
    suspend fun cancel(reason: String)
}

fun interface CommandFactory {
    fun create(input: CommandEnvelopeInput): GameCommand
}

class CommandRegistry(
    private val factories: Map<String, CommandFactory> = emptyMap(),
) {
    fun register(
        wireName: String,
        factory: CommandFactory,
    ): CommandRegistry {
        return CommandRegistry(factories + (wireName to factory))
    }

    fun create(input: CommandEnvelopeInput): GameCommand? = factories[input.commandName]?.create(input)

    fun requireCreate(input: CommandEnvelopeInput): GameCommand {
        return requireNotNull(create(input)) {
            "No command factory registered for ${input.commandName}"
        }
    }

    fun registeredNames(): Set<String> = factories.keys
}

interface InterestRegistry {
    suspend fun register(connectionId: String, stateId: GameStateId)
    suspend fun unregister(connectionId: String, stateId: GameStateId)
    suspend fun unregisterConnection(connectionId: String)
    suspend fun subscribersFor(stateId: GameStateId): Set<String>
    suspend fun subscriptionsFor(connectionId: String): Set<GameStateId>
}

interface GameStatePublisher {
    suspend fun publishSnapshot(connectionIds: Set<String>, snapshot: ComputerState)
    suspend fun publishDelta(connectionIds: Set<String>, delta: ComputerDelta)
    suspend fun publishProgramUpdate(connectionIds: Set<String>, update: ProgramUpdate)
    suspend fun publishUiEvent(connectionIds: Set<String>, event: GameUiEvent)
}

object NoOpGameStatePublisher : GameStatePublisher {
    override suspend fun publishSnapshot(connectionIds: Set<String>, snapshot: ComputerState) = Unit

    override suspend fun publishDelta(connectionIds: Set<String>, delta: ComputerDelta) = Unit

    override suspend fun publishProgramUpdate(connectionIds: Set<String>, update: ProgramUpdate) = Unit

    override suspend fun publishUiEvent(connectionIds: Set<String>, event: GameUiEvent) = Unit
}

interface ComputerStateRepository {
    suspend fun load(id: GameStateId): ComputerState?
    suspend fun loadStates(ids: Collection<GameStateId>): Map<GameStateId, ComputerState>
    suspend fun appendEvents(id: GameStateId, events: List<ComputerEvent>): ComputerState
}

class SnapshotCoordinator(
    val eventThreshold: Int = 50,
    val timeThreshold: Duration = 5.seconds,
) {
    fun shouldSnapshot(
        eventsSinceLastSnapshot: Int,
        elapsedSinceLastSnapshot: Duration,
    ): Boolean {
        return eventsSinceLastSnapshot >= eventThreshold || elapsedSinceLastSnapshot >= timeThreshold
    }
}

interface ProgramScheduler {
    suspend fun schedule(
        command: ProgramCommand,
        metadata: CommandMetadata = CommandMetadata(),
        publisher: GameStatePublisher = NoOpGameStatePublisher,
    ): ProgramHandle
}

interface HookSideEffectSink {
    suspend fun emitWatchTrigger(intent: WatchTriggerIntent)
}

object NoOpHookSideEffectSink : HookSideEffectSink {
    override suspend fun emitWatchTrigger(intent: WatchTriggerIntent) = Unit
}

interface CommandDispatcher : ProgramScheduler {
    suspend fun dispatch(
        command: FireAndForgetCommand,
        metadata: CommandMetadata = CommandMetadata(),
        publisher: GameStatePublisher = NoOpGameStatePublisher,
    )

    suspend fun <R> request(
        command: RequestCommand<R>,
        metadata: CommandMetadata = CommandMetadata(),
        publisher: GameStatePublisher = NoOpGameStatePublisher,
    ): R
}

interface CommandContext {
    val connectionId: String?
    val requestId: String?

    suspend fun loadState(id: GameStateId): ComputerState?
    suspend fun loadStates(ids: Collection<GameStateId>): Map<GameStateId, ComputerState>
    suspend fun appendEvents(id: GameStateId, events: List<ComputerEvent>): ComputerState
    suspend fun dispatch(command: FireAndForgetCommand)
    suspend fun <R> request(command: RequestCommand<R>): R
    suspend fun publishDelta(delta: ComputerDelta)
    suspend fun publishProgramUpdate(update: ProgramUpdate)
    suspend fun publishUiEvent(event: GameUiEvent)
}
