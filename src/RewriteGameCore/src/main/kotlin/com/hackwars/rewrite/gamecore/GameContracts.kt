package com.hackwars.rewrite.gamecore

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@JvmInline
value class GameStateId(val value: String)

data class GameStateView(
    val id: GameStateId,
    val version: Long,
    val payload: ByteArray,
)

data class GameStateMutation(
    val id: GameStateId,
    val changedPaths: Set<String>,
    val payload: ByteArray,
)

data class GameStateDelta(
    val id: GameStateId,
    val version: Long,
    val changedPaths: Set<String>,
    val payload: ByteArray,
)

data class ProgramUpdate(
    val id: GameStateId,
    val programId: String,
    val stage: String,
    val payload: ByteArray,
)

data class CommandLifetime(val timeout: Duration) {
    companion object {
        val defaultFireAndForget: CommandLifetime = CommandLifetime(5.seconds)
        val defaultRequest: CommandLifetime = CommandLifetime(30.seconds)
    }
}

interface Command {
    val name: String
    val lifetime: CommandLifetime
}

interface FireAndForgetCommand : Command {
    suspend fun execute(context: CommandContext)
}

interface RequestCommand<R> : Command {
    suspend fun execute(context: CommandContext, reply: (R) -> Unit)
}

interface ProgramHandle {
    val programId: String
    suspend fun cancel(reason: String)
}

interface ProgramCommand : Command {
    suspend fun start(context: CommandContext): ProgramHandle
}

interface GameStateStore {
    suspend fun load(id: GameStateId): GameStateView?
    suspend fun mutate(id: GameStateId, mutation: GameStateMutation): GameStateView
}

interface InterestRegistry {
    suspend fun register(connectionId: String, stateId: GameStateId)
    suspend fun unregister(connectionId: String, stateId: GameStateId)
    suspend fun subscribersFor(stateId: GameStateId): Set<String>
}

interface DeltaPublisher {
    suspend fun publishSnapshot(connectionIds: Set<String>, snapshot: GameStateView)
    suspend fun publishDelta(connectionIds: Set<String>, delta: GameStateDelta)
    suspend fun publishProgramUpdate(connectionIds: Set<String>, update: ProgramUpdate)
}

interface ProgramScheduler {
    suspend fun schedule(command: ProgramCommand, context: CommandContext): ProgramHandle
}

interface CommandContext {
    val connectionId: String?
    val requestId: String?

    suspend fun loadState(id: GameStateId): GameStateView?
    suspend fun mutateState(mutation: GameStateMutation): GameStateView
    suspend fun dispatch(command: FireAndForgetCommand)
    suspend fun <R> request(command: RequestCommand<R>, reply: (R) -> Unit)
    suspend fun publishDelta(delta: GameStateDelta)
    suspend fun publishProgramUpdate(update: ProgramUpdate)
}
