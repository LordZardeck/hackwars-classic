package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.CommandDispatcher
import com.hackwars.rewrite.gamecore.CommandEnvelopeInput
import com.hackwars.rewrite.gamecore.CommandMetadata
import com.hackwars.rewrite.gamecore.CommandRegistry
import com.hackwars.rewrite.gamecore.ComputerDelta
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.DeltaProjection
import com.hackwars.rewrite.gamecore.FireAndForgetCommand
import com.hackwars.rewrite.gamecore.GameSessionBootstrapCommand
import com.hackwars.rewrite.gamecore.GameSessionBootstrapResult
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.GameStatePublisher
import com.hackwars.rewrite.gamecore.InterestRegistry
import com.hackwars.rewrite.gamecore.ProgramLifecycleStatus
import com.hackwars.rewrite.gamecore.ProgramUpdate
import com.hackwars.rewrite.gamecore.RequestCommand
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.ScanCommand
import com.hackwars.rewrite.gamecore.ScanResponse
import com.hackwars.rewrite.gamecore.SetPreferenceCommand
import com.hackwars.rewrite.gamecore.SetPreferenceCommandResponse
import com.hackwars.rewrite.gamecore.SetPreferencePayload
import com.hackwars.rewrite.protocol.RewriteFrames
import hackwars.rewrite.v1.CommandEnvelope
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.ErrorEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import hackwars.rewrite.v1.ProgramStatus

data class AuthenticatedGameSession(
    val connectionId: String,
    val playFabId: String,
    val playerIp: String,
)

fun interface GameConnectionTransport {
    suspend fun send(connectionId: String, frame: FrameEnvelope)
}

class RewriteGameProtocolAdapter(
    private val dispatcher: CommandDispatcher,
    private val interestRegistry: InterestRegistry,
    private val registry: CommandRegistry = defaultRegistry(),
) {
    suspend fun onSessionStarted(
        session: AuthenticatedGameSession,
        transport: GameConnectionTransport,
    ): List<FrameEnvelope> {
        val bootstrap = dispatcher.request(
            command = GameSessionBootstrapCommand(
                stateId = GameStateId(session.playerIp),
                playFabId = session.playFabId,
                interestRegistry = interestRegistry,
            ),
            metadata = metadataFor(session),
            publisher = protocolPublisher(transport),
        )

        return listOf(
            RewriteFrames.snapshot(
                gameStateId = bootstrap.state.id.value,
                sequence = bootstrap.state.version,
                payload = RewriteGameJson.encode(ComputerState.serializer(), bootstrap.state),
            ),
        )
    }

    suspend fun onCommand(
        session: AuthenticatedGameSession,
        command: CommandEnvelope,
        transport: GameConnectionTransport,
    ): List<FrameEnvelope> {
        val input = CommandEnvelopeInput(
            commandId = command.command_id,
            commandName = command.command_name,
            targetStateIds = command.target_game_state_ids.mapTo(linkedSetOf(), ::GameStateId),
            payloadJson = command.payload.toByteArray().takeIf { it.isNotEmpty() }?.decodeToString(),
            expectsResponse = command.expects_response,
            metadata = metadataFor(
                session = session,
                requestId = command.command_id,
            ),
        )
        val typedCommand = registry.create(input)
            ?: return listOf(
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                    error = ErrorEnvelope(
                        code = "UNKNOWN_COMMAND",
                        message = "No rewrite command registered for ${command.command_name}.",
                        retryable = false,
                    ),
                ),
            )

        val publisher = protocolPublisher(transport)
        return when (typedCommand) {
            is RequestCommand<*> -> {
                val payload = encodeRequestResult(
                    result = dispatcher.request(
                        command = typedCommand,
                        metadata = input.metadata,
                        publisher = publisher,
                    ),
                )
                if (command.expects_response) {
                    listOf(
                        RewriteFrames.commandResponse(
                            commandId = command.command_id,
                            status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK,
                            payload = payload,
                        ),
                    )
                } else {
                    emptyList()
                }
            }

            is FireAndForgetCommand -> {
                dispatcher.dispatch(
                    command = typedCommand,
                    metadata = input.metadata,
                    publisher = publisher,
                )
                if (command.expects_response) {
                    listOf(
                        RewriteFrames.commandResponse(
                            commandId = command.command_id,
                            status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK,
                        ),
                    )
                } else {
                    emptyList()
                }
            }

            else -> listOf(
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                    error = ErrorEnvelope(
                        code = "UNSUPPORTED_COMMAND_TYPE",
                        message = "Unsupported command type for ${typedCommand.name}.",
                        retryable = false,
                    ),
                ),
            )
        }
    }

    private fun metadataFor(
        session: AuthenticatedGameSession,
        requestId: String? = null,
    ): CommandMetadata {
        return CommandMetadata(
            connectionId = session.connectionId,
            requestId = requestId,
            authenticatedPlayFabId = session.playFabId,
            authenticatedStateId = GameStateId(session.playerIp),
        )
    }

    private fun protocolPublisher(transport: GameConnectionTransport): GameStatePublisher {
        return object : GameStatePublisher {
            override suspend fun publishSnapshot(connectionIds: Set<String>, snapshot: ComputerState) {
                val frame = RewriteFrames.snapshot(
                    gameStateId = snapshot.id.value,
                    sequence = snapshot.version,
                    payload = RewriteGameJson.encode(ComputerState.serializer(), snapshot),
                )
                connectionIds.forEach { connectionId ->
                    transport.send(connectionId, frame)
                }
            }

            override suspend fun publishDelta(connectionIds: Set<String>, delta: ComputerDelta) {
                val frame = RewriteFrames.delta(
                    gameStateId = delta.gameStateId.value,
                    sequence = delta.sequence,
                    changedPaths = delta.changedPaths.toList(),
                    deltaKeys = delta.deltaKeys.toList(),
                    payload = RewriteGameJson.encode(DeltaProjection.serializer(), delta.projection),
                )
                connectionIds.forEach { connectionId ->
                    transport.send(connectionId, frame)
                }
            }

            override suspend fun publishProgramUpdate(connectionIds: Set<String>, update: ProgramUpdate) {
                val frame = RewriteFrames.programUpdate(
                    programId = update.programId,
                    programType = update.programType,
                    status = update.status.toProtocolStatus(),
                    relatedGameStateIds = update.relatedStateIds.map { it.value },
                    payload = RewriteGameJson.encode(ProgramUpdate.serializer(), update),
                )
                connectionIds.forEach { connectionId ->
                    transport.send(connectionId, frame)
                }
            }
        }
    }

    private fun encodeRequestResult(result: Any?): ByteArray {
        return when (result) {
            null -> ByteArray(0)
            is ScanResponse -> RewriteGameJson.encode(ScanResponse.serializer(), result)
            is SetPreferenceCommandResponse -> RewriteGameJson.encode(SetPreferenceCommandResponse.serializer(), result)
            is GameSessionBootstrapResult -> RewriteGameJson.encode(GameSessionBootstrapResult.serializer(), result)
            else -> error("Unsupported rewrite command response type: ${result::class.qualifiedName}")
        }
    }

    private companion object {
        fun defaultRegistry(): CommandRegistry {
            return CommandRegistry()
                .register("requestscan") { input ->
                    ScanCommand(
                        targetStateId = requireSingleStateId(
                            input = input,
                            fallback = input.metadata.authenticatedStateId,
                        ),
                    )
                }
                .register("setpreferences") { input ->
                    val payload = RewriteGameJson.codec.decodeFromString(
                        deserializer = SetPreferencePayload.serializer(),
                        string = input.payloadJson ?: error("Missing preference payload."),
                    )
                    SetPreferenceCommand(
                        stateId = requireSingleStateId(
                            input = input,
                            fallback = input.metadata.authenticatedStateId,
                        ),
                        key = payload.key,
                        value = payload.value,
                    )
                }
        }

        fun requireSingleStateId(
            input: CommandEnvelopeInput,
            fallback: GameStateId?,
        ): GameStateId {
            return input.targetStateIds.singleOrNull()
                ?: fallback
                ?: error("Expected exactly one target state for ${input.commandName}.")
        }
    }
}

private fun ProgramLifecycleStatus.toProtocolStatus(): ProgramStatus = when (this) {
    ProgramLifecycleStatus.RUNNING -> ProgramStatus.PROGRAM_STATUS_RUNNING
    ProgramLifecycleStatus.COMPLETED -> ProgramStatus.PROGRAM_STATUS_COMPLETED
    ProgramLifecycleStatus.CANCELLED -> ProgramStatus.PROGRAM_STATUS_CANCELLED
    ProgramLifecycleStatus.FAILED -> ProgramStatus.PROGRAM_STATUS_FAILED
}
