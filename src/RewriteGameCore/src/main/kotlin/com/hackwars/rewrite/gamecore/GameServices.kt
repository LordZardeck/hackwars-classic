package com.hackwars.rewrite.gamecore

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class InMemoryInterestRegistry : InterestRegistry {
    private val subscriptionsByConnection = mutableMapOf<String, MutableSet<GameStateId>>()
    private val connectionsByState = mutableMapOf<GameStateId, MutableSet<String>>()
    private val mutex = Mutex()

    override suspend fun register(connectionId: String, stateId: GameStateId) {
        mutex.withLock {
            subscriptionsByConnection.getOrPut(connectionId) { linkedSetOf() }.add(stateId)
            connectionsByState.getOrPut(stateId) { linkedSetOf() }.add(connectionId)
        }
    }

    override suspend fun unregister(connectionId: String, stateId: GameStateId) {
        mutex.withLock {
            subscriptionsByConnection[connectionId]?.remove(stateId)
            if (subscriptionsByConnection[connectionId].isNullOrEmpty()) {
                subscriptionsByConnection.remove(connectionId)
            }
            connectionsByState[stateId]?.remove(connectionId)
            if (connectionsByState[stateId].isNullOrEmpty()) {
                connectionsByState.remove(stateId)
            }
        }
    }

    override suspend fun unregisterConnection(connectionId: String) {
        mutex.withLock {
            val stateIds = subscriptionsByConnection.remove(connectionId).orEmpty()
            stateIds.forEach { stateId ->
                connectionsByState[stateId]?.remove(connectionId)
                if (connectionsByState[stateId].isNullOrEmpty()) {
                    connectionsByState.remove(stateId)
                }
            }
        }
    }

    override suspend fun subscribersFor(stateId: GameStateId): Set<String> = mutex.withLock {
        connectionsByState[stateId]?.toSet().orEmpty()
    }

    override suspend fun subscriptionsFor(connectionId: String): Set<GameStateId> = mutex.withLock {
        subscriptionsByConnection[connectionId]?.toSet().orEmpty()
    }
}

class InMemoryComputerStateRepository(
    seededStates: Map<GameStateId, ComputerState> = emptyMap(),
) : ComputerStateRepository {
    private val states = seededStates.toMutableMap()
    private val persistedEvents = mutableMapOf<GameStateId, MutableList<ComputerEvent>>()
    private val mutex = Mutex()

    override suspend fun load(id: GameStateId): ComputerState? = mutex.withLock {
        states[id]
    }

    override suspend fun loadStates(ids: Collection<GameStateId>): Map<GameStateId, ComputerState> = mutex.withLock {
        ids.distinct().mapNotNull { id -> states[id]?.let { id to it } }.toMap()
    }

    override suspend fun appendEvents(id: GameStateId, events: List<ComputerEvent>): ComputerState = mutex.withLock {
        val currentState = states[id] ?: ComputerState.empty(id = id, playerIp = id.value)
        val updatedState = currentState.applyEvents(events)
        states[id] = updatedState
        persistedEvents.getOrPut(id) { mutableListOf() }.addAll(events)
        updatedState
    }

    suspend fun stateCount(): Int = mutex.withLock { states.size }

    suspend fun eventsFor(id: GameStateId): List<ComputerEvent> = mutex.withLock {
        persistedEvents[id]?.toList().orEmpty()
    }
}

class DefaultCommandDispatcher(
    private val repository: ComputerStateRepository,
    private val interestRegistry: InterestRegistry,
    private val watchExecutionCoordinator: WatchExecutionCoordinator = DefaultWatchExecutionCoordinator(),
    private val watchTriggerIntentSink: WatchTriggerIntentSink = NoOpWatchTriggerIntentSink,
    private val programScheduler: ProgramScheduler = CoroutineProgramScheduler(
        dispatcher = null,
        interestRegistry = interestRegistry,
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ),
) : CommandDispatcher {
    private val stateLocks = ConcurrentHashMap<GameStateId, Mutex>()

    override suspend fun dispatch(
        command: FireAndForgetCommand,
        metadata: CommandMetadata,
        publisher: GameStatePublisher,
    ) {
        withTimeout(command.lifetime.timeout) {
            executeCommand(command.targetStateIds, metadata, publisher, emptySet()) { context ->
                command.execute(context)
            }
        }
    }

    override suspend fun <R> request(
        command: RequestCommand<R>,
        metadata: CommandMetadata,
        publisher: GameStatePublisher,
    ): R {
        return withTimeout(command.lifetime.timeout) {
            executeCommand(command.targetStateIds, metadata, publisher, emptySet()) { context ->
                command.execute(context)
            }
        }
    }

    override suspend fun schedule(
        command: ProgramCommand,
        metadata: CommandMetadata,
        publisher: GameStatePublisher,
    ): ProgramHandle {
        val scheduler = when (programScheduler) {
            is CoroutineProgramScheduler -> programScheduler.withDispatcher(this)
            else -> programScheduler
        }
        return scheduler.schedule(command, metadata, publisher)
    }

    private suspend fun <R> executeCommand(
        targetStateIds: Set<GameStateId>,
        metadata: CommandMetadata,
        publisher: GameStatePublisher,
        inheritedLocks: Set<GameStateId>,
        block: suspend (DispatcherCommandContext) -> R,
    ): R {
        validateNestedLockOrder(inheritedLocks, targetStateIds)
        val newlyRequiredLocks = stableLockOrder(targetStateIds - inheritedLocks)
        return withLocks(newlyRequiredLocks) {
            val heldLocks = inheritedLocks + newlyRequiredLocks
            val context = DispatcherCommandContext(
                metadata = metadata,
                repository = repository,
                interestRegistry = interestRegistry,
                publisher = publisher,
                dispatcher = this,
                heldLocks = heldLocks,
                watchExecutionCoordinator = watchExecutionCoordinator,
                watchTriggerIntentSink = watchTriggerIntentSink,
            )

            var thrown: Throwable? = null
            try {
                return@withLocks block(context)
            } catch (exception: Throwable) {
                thrown = exception
                throw exception
            } finally {
                if (thrown !is CancellationException) {
                    context.flush()
                }
            }
        }
    }

    internal suspend fun nestedDispatch(
        command: FireAndForgetCommand,
        metadata: CommandMetadata,
        publisher: GameStatePublisher,
        inheritedLocks: Set<GameStateId>,
    ) {
        withTimeout(command.lifetime.timeout) {
            executeCommand(command.targetStateIds, metadata, publisher, inheritedLocks) { context ->
                command.execute(context)
            }
        }
    }

    internal suspend fun <R> nestedRequest(
        command: RequestCommand<R>,
        metadata: CommandMetadata,
        publisher: GameStatePublisher,
        inheritedLocks: Set<GameStateId>,
    ): R {
        return withTimeout(command.lifetime.timeout) {
            executeCommand(command.targetStateIds, metadata, publisher, inheritedLocks) { context ->
                command.execute(context)
            }
        }
    }

    internal fun stableLockOrder(stateIds: Collection<GameStateId>): List<GameStateId> {
        return stateIds.distinct().sortedBy { it.value }
    }

    private fun validateNestedLockOrder(
        heldLocks: Set<GameStateId>,
        requestedStateIds: Set<GameStateId>,
    ) {
        if (heldLocks.isEmpty()) {
            return
        }

        val maxHeld = heldLocks.maxByOrNull { it.value } ?: return
        val minNew = (requestedStateIds - heldLocks).minByOrNull { it.value } ?: return
        require(minNew.value >= maxHeld.value) {
            "Nested command requested state ${minNew.value} after holding ${maxHeld.value}, which violates stable lock ordering."
        }
    }

    private suspend fun <R> withLocks(
        orderedStateIds: List<GameStateId>,
        block: suspend () -> R,
    ): R {
        suspend fun acquire(index: Int): R {
            if (index >= orderedStateIds.size) {
                return block()
            }

            val mutex = stateLocks.computeIfAbsent(orderedStateIds[index]) { Mutex() }
            return mutex.withLock {
                acquire(index + 1)
            }
        }

        return acquire(0)
    }

    private class DispatcherCommandContext(
        private val metadata: CommandMetadata,
        private val repository: ComputerStateRepository,
        private val interestRegistry: InterestRegistry,
        private val publisher: GameStatePublisher,
        private val dispatcher: DefaultCommandDispatcher,
        private val heldLocks: Set<GameStateId>,
        private val watchExecutionCoordinator: WatchExecutionCoordinator,
        private val watchTriggerIntentSink: WatchTriggerIntentSink,
    ) : CommandContext {
        override val connectionId: String? = metadata.connectionId
        override val requestId: String? = metadata.requestId

        private val pendingStateChanges = linkedMapOf<GameStateId, PendingStateChange>()
        private val manualDeltas = mutableListOf<ComputerDelta>()
        private val pendingProgramUpdates = mutableListOf<ProgramUpdate>()
        private val pendingUiEvents = mutableListOf<GameUiEvent>()

        override suspend fun loadState(id: GameStateId): ComputerState? = repository.load(id)

        override suspend fun loadStates(ids: Collection<GameStateId>): Map<GameStateId, ComputerState> {
            return repository.loadStates(ids)
        }

        override suspend fun appendEvents(
            id: GameStateId,
            events: List<ComputerEvent>,
        ): ComputerState {
            if (events.isEmpty()) {
                return repository.load(id) ?: ComputerState.empty(id = id, playerIp = id.value)
            }

            val updatedState = repository.appendEvents(id, events)
            val pendingChange = pendingStateChanges.getOrPut(id) {
                PendingStateChange(stateId = id, updatedState = updatedState)
            }
            pendingChange.updatedState = updatedState
            pendingChange.events += events
            return updatedState
        }

        override suspend fun dispatch(command: FireAndForgetCommand) {
            dispatcher.nestedDispatch(
                command = command,
                metadata = metadata,
                publisher = publisher,
                inheritedLocks = heldLocks,
            )
        }

        override suspend fun <R> request(command: RequestCommand<R>): R {
            return dispatcher.nestedRequest(
                command = command,
                metadata = metadata,
                publisher = publisher,
                inheritedLocks = heldLocks,
            )
        }

        override suspend fun emitWatchTrigger(intent: WatchTriggerIntent): WatchExecutionResult {
            watchTriggerIntentSink.emitWatchTrigger(intent)
            return watchExecutionCoordinator.execute(this, intent)
        }

        override suspend fun publishDelta(delta: ComputerDelta) {
            manualDeltas += delta
        }

        override suspend fun publishProgramUpdate(update: ProgramUpdate) {
            pendingProgramUpdates += update
        }

        override suspend fun publishUiEvent(event: GameUiEvent) {
            pendingUiEvents += event
        }

        suspend fun flush() {
            pendingStateChanges.values.forEach { pendingChange ->
                val recipients = interestRegistry.subscribersFor(pendingChange.stateId)
                if (recipients.isNotEmpty()) {
                    publisher.publishDelta(
                        connectionIds = recipients,
                        delta = buildComputerDelta(
                            stateId = pendingChange.stateId,
                            updatedState = pendingChange.updatedState,
                            events = pendingChange.events,
                        ),
                    )
                }
            }

            manualDeltas.forEach { delta ->
                val recipients = interestRegistry.subscribersFor(delta.gameStateId)
                if (recipients.isNotEmpty()) {
                    publisher.publishDelta(recipients, delta)
                }
            }

            pendingProgramUpdates.forEach { update ->
                val recipients = update.relatedStateIds
                    .flatMapTo(linkedSetOf()) { interestRegistry.subscribersFor(it) }
                if (recipients.isNotEmpty()) {
                    publisher.publishProgramUpdate(recipients, update)
                }
            }

            val connectionId = connectionId
            if (connectionId != null) {
                pendingUiEvents.forEach { event ->
                    publisher.publishUiEvent(setOf(connectionId), event)
                }
            }

            pendingStateChanges.clear()
            manualDeltas.clear()
            pendingProgramUpdates.clear()
            pendingUiEvents.clear()
        }

        private data class PendingStateChange(
            val stateId: GameStateId,
            var updatedState: ComputerState,
            val events: MutableList<ComputerEvent> = mutableListOf(),
        )
    }
}

class CoroutineProgramScheduler(
    private val dispatcher: DefaultCommandDispatcher?,
    private val interestRegistry: InterestRegistry,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ProgramScheduler {
    fun withDispatcher(dispatcher: DefaultCommandDispatcher): CoroutineProgramScheduler {
        return if (this.dispatcher === dispatcher) {
            this
        } else {
            CoroutineProgramScheduler(
                dispatcher = dispatcher,
                interestRegistry = interestRegistry,
                coroutineScope = coroutineScope,
            )
        }
    }

    override suspend fun schedule(
        command: ProgramCommand,
        metadata: CommandMetadata,
        publisher: GameStatePublisher,
    ): ProgramHandle {
        val job = coroutineScope.launch {
            runProgram(command, metadata, publisher)
        }
        return DefaultProgramHandle(
            programId = command.programId,
            job = job,
            cancelAction = { reason ->
                val cancelContext = ProgramCommandContext(
                    metadata = metadata,
                    dispatcher = requireNotNull(dispatcher) {
                        "CoroutineProgramScheduler requires a dispatcher before programs can run."
                    },
                    interestRegistry = interestRegistry,
                    publisher = publisher,
                    heldLocks = emptySet(),
                    watchExecutionCoordinator = NoOpWatchExecutionCoordinator,
                    watchTriggerIntentSink = NoOpWatchTriggerIntentSink,
                )
                command.onCancel(cancelContext, reason)
                cancelContext.publishProgramUpdate(
                    ProgramUpdate(
                        programId = command.programId,
                        programType = command.programType,
                        status = ProgramLifecycleStatus.CANCELLED,
                        relatedStateIds = command.targetStateIds,
                        progress = ProgramProgress(message = reason),
                    ),
                )
                cancelContext.flush()
                job.cancel(CancellationException(reason))
            },
        )
    }

    private suspend fun runProgram(
        command: ProgramCommand,
        metadata: CommandMetadata,
        publisher: GameStatePublisher,
    ) {
        try {
            withTimeout(command.lifetime.timeout) {
                var step = executeStep(command, metadata, publisher, onTick = false)
                while (step.status == ProgramLifecycleStatus.RUNNING) {
                    delay(command.tickInterval)
                    step = executeStep(command, metadata, publisher, onTick = true)
                }
            }
        } catch (exception: TimeoutCancellationException) {
            val context = ProgramCommandContext(
                metadata = metadata,
                dispatcher = requireNotNull(dispatcher) {
                    "CoroutineProgramScheduler requires a dispatcher before programs can run."
                },
                interestRegistry = interestRegistry,
                publisher = publisher,
                heldLocks = emptySet(),
                watchExecutionCoordinator = NoOpWatchExecutionCoordinator,
                watchTriggerIntentSink = NoOpWatchTriggerIntentSink,
            )
            command.onCancel(context, "Command lifetime expired.")
            context.publishProgramUpdate(
                ProgramUpdate(
                    programId = command.programId,
                    programType = command.programType,
                    status = ProgramLifecycleStatus.CANCELLED,
                    relatedStateIds = command.targetStateIds,
                    progress = ProgramProgress(message = "lifetime expired"),
                ),
            )
            context.flush()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val recipients = command.targetStateIds
                .flatMapTo(linkedSetOf()) { interestRegistry.subscribersFor(it) }
            if (recipients.isNotEmpty()) {
                publisher.publishProgramUpdate(
                    recipients,
                    ProgramUpdate(
                        programId = command.programId,
                        programType = command.programType,
                        status = ProgramLifecycleStatus.FAILED,
                        relatedStateIds = command.targetStateIds,
                        progress = ProgramProgress(message = exception.message ?: "program failed"),
                    ),
                )
            }
        }
    }

    private suspend fun executeStep(
        command: ProgramCommand,
        metadata: CommandMetadata,
        publisher: GameStatePublisher,
        onTick: Boolean,
    ): ProgramExecutionStep {
        val step = requireNotNull(dispatcher) {
            "CoroutineProgramScheduler requires a dispatcher before programs can run."
        }.request(
            command = ProgramStepCommand(command, onTick),
            metadata = metadata,
            publisher = publisher,
        )

        val recipients = step.relatedStateIds
            .flatMapTo(linkedSetOf()) { interestRegistry.subscribersFor(it) }
        if (recipients.isNotEmpty()) {
            publisher.publishProgramUpdate(
                recipients,
                ProgramUpdate(
                    programId = command.programId,
                    programType = command.programType,
                    status = step.status,
                    relatedStateIds = step.relatedStateIds.ifEmpty { command.targetStateIds },
                    progress = step.progress,
                ),
            )
        }

        return step
    }

    private class ProgramCommandContext(
        private val metadata: CommandMetadata,
        private val dispatcher: DefaultCommandDispatcher,
        private val interestRegistry: InterestRegistry,
        private val publisher: GameStatePublisher,
        private val heldLocks: Set<GameStateId>,
        private val watchExecutionCoordinator: WatchExecutionCoordinator,
        private val watchTriggerIntentSink: WatchTriggerIntentSink,
    ) : CommandContext {
        private val bufferedProgramUpdates = mutableListOf<ProgramUpdate>()
        private val bufferedDeltas = mutableListOf<ComputerDelta>()
        private val bufferedUiEvents = mutableListOf<GameUiEvent>()

        override val connectionId: String? = metadata.connectionId
        override val requestId: String? = metadata.requestId

        override suspend fun loadState(id: GameStateId): ComputerState? = dispatcher.nestedRequest(
            LoadStateCommand(id),
            metadata,
            NoOpGameStatePublisher,
            heldLocks,
        )

        override suspend fun loadStates(ids: Collection<GameStateId>): Map<GameStateId, ComputerState> = dispatcher.nestedRequest(
            LoadStatesCommand(ids.toSet()),
            metadata,
            NoOpGameStatePublisher,
            heldLocks,
        )

        override suspend fun appendEvents(id: GameStateId, events: List<ComputerEvent>): ComputerState = dispatcher.nestedRequest(
            AppendEventsCommand(id, events),
            metadata,
            NoOpGameStatePublisher,
            heldLocks,
        )

        override suspend fun dispatch(command: FireAndForgetCommand) {
            dispatcher.nestedDispatch(command, metadata, publisher, heldLocks)
        }

        override suspend fun <R> request(command: RequestCommand<R>): R {
            return dispatcher.nestedRequest(command, metadata, publisher, heldLocks)
        }

        override suspend fun emitWatchTrigger(intent: WatchTriggerIntent): WatchExecutionResult {
            watchTriggerIntentSink.emitWatchTrigger(intent)
            return watchExecutionCoordinator.execute(this, intent)
        }

        override suspend fun publishDelta(delta: ComputerDelta) {
            bufferedDeltas += delta
        }

        override suspend fun publishProgramUpdate(update: ProgramUpdate) {
            bufferedProgramUpdates += update
        }

        override suspend fun publishUiEvent(event: GameUiEvent) {
            bufferedUiEvents += event
        }

        suspend fun flush() {
            bufferedDeltas.forEach { delta ->
                val recipients = interestRegistry.subscribersFor(delta.gameStateId)
                if (recipients.isNotEmpty()) {
                    publisher.publishDelta(recipients, delta)
                }
            }
            bufferedProgramUpdates.forEach { update ->
                val recipients = update.relatedStateIds
                    .flatMapTo(linkedSetOf()) { interestRegistry.subscribersFor(it) }
                if (recipients.isNotEmpty()) {
                    publisher.publishProgramUpdate(recipients, update)
                }
            }
            val connectionId = connectionId
            if (connectionId != null) {
                bufferedUiEvents.forEach { event ->
                    publisher.publishUiEvent(setOf(connectionId), event)
                }
            }
            bufferedDeltas.clear()
            bufferedProgramUpdates.clear()
            bufferedUiEvents.clear()
        }
    }

    private class ProgramStepCommand(
        private val delegate: ProgramCommand,
        private val onTick: Boolean,
    ) : RequestCommand<ProgramExecutionStep> {
        override val name: String = delegate.name
        override val lifetime: CommandLifetime = delegate.lifetime
        override val targetStateIds: Set<GameStateId> = delegate.targetStateIds

        override suspend fun execute(context: CommandContext): ProgramExecutionStep {
            return if (onTick) delegate.onTick(context) else delegate.onStart(context)
        }
    }

    private class LoadStateCommand(
        private val stateId: GameStateId,
    ) : RequestCommand<ComputerState?> {
        override val name: String = "load-state"
        override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
        override val targetStateIds: Set<GameStateId> = setOf(stateId)

        override suspend fun execute(context: CommandContext): ComputerState? = context.loadState(stateId)
    }

    private class LoadStatesCommand(
        private val stateIds: Set<GameStateId>,
    ) : RequestCommand<Map<GameStateId, ComputerState>> {
        override val name: String = "load-states"
        override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
        override val targetStateIds: Set<GameStateId> = stateIds

        override suspend fun execute(context: CommandContext): Map<GameStateId, ComputerState> {
            return context.loadStates(stateIds)
        }
    }

    private class AppendEventsCommand(
        private val stateId: GameStateId,
        private val events: List<ComputerEvent>,
    ) : RequestCommand<ComputerState> {
        override val name: String = "append-events"
        override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
        override val targetStateIds: Set<GameStateId> = setOf(stateId)

        override suspend fun execute(context: CommandContext): ComputerState {
            return context.appendEvents(stateId, events)
        }
    }

    private class DefaultProgramHandle(
        override val programId: String,
        private val job: Job,
        private val cancelAction: suspend (String) -> Unit,
    ) : ProgramHandle {
        override suspend fun cancel(reason: String) {
            cancelAction(reason)
        }
    }
}
