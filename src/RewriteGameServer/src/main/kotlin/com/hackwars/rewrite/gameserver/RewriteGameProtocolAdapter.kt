package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.CommandDispatcher
import com.hackwars.rewrite.gamecore.CommandEnvelopeInput
import com.hackwars.rewrite.gamecore.CommandMetadata
import com.hackwars.rewrite.gamecore.CommandRegistry
import com.hackwars.rewrite.gamecore.CompileFileCommand
import com.hackwars.rewrite.gamecore.CompileFilePayload
import com.hackwars.rewrite.gamecore.CompileFileResponse
import com.hackwars.rewrite.gamecore.ComputerDelta
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.CreateFolderCommand
import com.hackwars.rewrite.gamecore.CreateFolderPayload
import com.hackwars.rewrite.gamecore.DecompileFileCommand
import com.hackwars.rewrite.gamecore.DecompileFilePayload
import com.hackwars.rewrite.gamecore.DecompileFileResponse
import com.hackwars.rewrite.gamecore.DeleteFileCommand
import com.hackwars.rewrite.gamecore.DeleteFilePayload
import com.hackwars.rewrite.gamecore.DeleteFolderCommand
import com.hackwars.rewrite.gamecore.DeleteFolderPayload
import com.hackwars.rewrite.gamecore.DeleteMultiCommand
import com.hackwars.rewrite.gamecore.DeleteMultiPayload
import com.hackwars.rewrite.gamecore.DeltaProjection
import com.hackwars.rewrite.gamecore.DirectoryListingResponse
import com.hackwars.rewrite.gamecore.FileContentsResponse
import com.hackwars.rewrite.gamecore.FireAndForgetCommand
import com.hackwars.rewrite.gamecore.GameSessionBootstrapCommand
import com.hackwars.rewrite.gamecore.GameSessionBootstrapResult
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.GameStatePublisher
import com.hackwars.rewrite.gamecore.InstallApplicationCommand
import com.hackwars.rewrite.gamecore.InstallApplicationPayload
import com.hackwars.rewrite.gamecore.InstallApplicationResponse
import com.hackwars.rewrite.gamecore.InstallEquipmentCommand
import com.hackwars.rewrite.gamecore.InstallEquipmentPayload
import com.hackwars.rewrite.gamecore.InstallEquipmentResponse
import com.hackwars.rewrite.gamecore.InstallFirewallCommand
import com.hackwars.rewrite.gamecore.InstallFirewallPayload
import com.hackwars.rewrite.gamecore.InstallFirewallResponse
import com.hackwars.rewrite.gamecore.InterestRegistry
import com.hackwars.rewrite.gamecore.MutationAcceptedResponse
import com.hackwars.rewrite.gamecore.ProgramLifecycleStatus
import com.hackwars.rewrite.gamecore.ProgramUpdate
import com.hackwars.rewrite.gamecore.RequestCommand
import com.hackwars.rewrite.gamecore.RequestDirectoryCommand
import com.hackwars.rewrite.gamecore.RequestDirectoryPayload
import com.hackwars.rewrite.gamecore.RequestFileCommand
import com.hackwars.rewrite.gamecore.RequestFilePayload
import com.hackwars.rewrite.gamecore.RequestSecondaryDirectoryCommand
import com.hackwars.rewrite.gamecore.RequestSecondaryDirectoryPayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.SaveFileCommand
import com.hackwars.rewrite.gamecore.SaveFilePayload
import com.hackwars.rewrite.gamecore.ScanCommand
import com.hackwars.rewrite.gamecore.ScanResponse
import com.hackwars.rewrite.gamecore.SecondaryDirectoryListingResponse
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
                errorResponse(
                    commandId = command.command_id,
                    code = "UNKNOWN_COMMAND",
                    message = "No rewrite command registered for ${command.command_name}.",
                ),
            )

        return try {
            val publisher = protocolPublisher(transport)
            when (typedCommand) {
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
                    errorResponse(
                        commandId = command.command_id,
                        code = "UNSUPPORTED_COMMAND_TYPE",
                        message = "Unsupported command type for ${typedCommand.name}.",
                    ),
                )
            }
        } catch (exception: Throwable) {
            listOf(
                errorResponse(
                    commandId = command.command_id,
                    code = "COMMAND_FAILED",
                    message = exception.message ?: "Command ${command.command_name} failed.",
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
            is DirectoryListingResponse -> RewriteGameJson.encode(DirectoryListingResponse.serializer(), result)
            is SecondaryDirectoryListingResponse -> RewriteGameJson.encode(SecondaryDirectoryListingResponse.serializer(), result)
            is FileContentsResponse -> RewriteGameJson.encode(FileContentsResponse.serializer(), result)
            is MutationAcceptedResponse -> RewriteGameJson.encode(MutationAcceptedResponse.serializer(), result)
            is CompileFileResponse -> RewriteGameJson.encode(CompileFileResponse.serializer(), result)
            is DecompileFileResponse -> RewriteGameJson.encode(DecompileFileResponse.serializer(), result)
            is InstallApplicationResponse -> RewriteGameJson.encode(InstallApplicationResponse.serializer(), result)
            is InstallEquipmentResponse -> RewriteGameJson.encode(InstallEquipmentResponse.serializer(), result)
            is InstallFirewallResponse -> RewriteGameJson.encode(InstallFirewallResponse.serializer(), result)
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
                .register("requestdirectory") { input ->
                    val payload = decodePayload(input, RequestDirectoryPayload.serializer())
                    RequestDirectoryCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                    )
                }
                .register("requestsecondarydirectory") { input ->
                    val payload = decodePayload(input, RequestSecondaryDirectoryPayload.serializer())
                    RequestSecondaryDirectoryCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        targetStateId = GameStateId(payload.targetIp),
                        path = payload.path,
                        portNumber = payload.port,
                    )
                }
                .register("requestfile") { input ->
                    val payload = decodePayload(input, RequestFilePayload.serializer())
                    RequestFileCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        fileName = payload.name,
                    )
                }
                .register("savefile") { input ->
                    val payload = decodePayload(input, SaveFilePayload.serializer())
                    SaveFileCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        file = payload.file,
                    )
                }
                .register("createfolder") { input ->
                    val payload = decodePayload(input, CreateFolderPayload.serializer())
                    CreateFolderCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        folderName = payload.name,
                    )
                }
                .register("deletefolder") { input ->
                    val payload = decodePayload(input, DeleteFolderPayload.serializer())
                    DeleteFolderCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        folderName = payload.name,
                    )
                }
                .register("deletefile") { input ->
                    val payload = decodePayload(input, DeleteFilePayload.serializer())
                    DeleteFileCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        fileName = payload.name,
                    )
                }
                .register("deletemulti") { input ->
                    val payload = decodePayload(input, DeleteMultiPayload.serializer())
                    DeleteMultiCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        fileNames = payload.fileNames,
                        directoryNames = payload.directoryNames,
                    )
                }
                .register("compilefile") { input ->
                    val payload = decodePayload(input, CompileFilePayload.serializer())
                    CompileFileCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        fileName = payload.name,
                    )
                }
                .register("decompilefile") { input ->
                    val payload = decodePayload(input, DecompileFilePayload.serializer())
                    DecompileFileCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        fileName = payload.name,
                    )
                }
                .register("installapplication") { input ->
                    val payload = decodePayload(input, InstallApplicationPayload.serializer())
                    InstallApplicationCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        fileName = payload.name,
                        portNumber = payload.portNumber,
                    )
                }
                .register("installequipment") { input ->
                    val payload = decodePayload(input, InstallEquipmentPayload.serializer())
                    InstallEquipmentCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        fileName = payload.name,
                        slot = payload.slot,
                    )
                }
                .register("installfirewall") { input ->
                    val payload = decodePayload(input, InstallFirewallPayload.serializer())
                    InstallFirewallCommand(
                        stateId = requireSingleStateId(input, input.metadata.authenticatedStateId),
                        path = payload.path,
                        fileName = payload.name,
                        portNumber = payload.portNumber,
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

        fun errorResponse(
            commandId: String,
            code: String,
            message: String,
        ): FrameEnvelope {
            return RewriteFrames.commandResponse(
                commandId = commandId,
                status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                error = ErrorEnvelope(
                    code = code,
                    message = message,
                    retryable = false,
                ),
            )
        }

        fun <T> decodePayload(
            input: CommandEnvelopeInput,
            serializer: kotlinx.serialization.KSerializer<T>,
        ): T {
            return RewriteGameJson.codec.decodeFromString(
                deserializer = serializer,
                string = input.payloadJson ?: error("Missing payload for ${input.commandName}."),
            )
        }
    }
}

private fun ProgramLifecycleStatus.toProtocolStatus(): ProgramStatus = when (this) {
    ProgramLifecycleStatus.RUNNING -> ProgramStatus.PROGRAM_STATUS_RUNNING
    ProgramLifecycleStatus.COMPLETED -> ProgramStatus.PROGRAM_STATUS_COMPLETED
    ProgramLifecycleStatus.CANCELLED -> ProgramStatus.PROGRAM_STATUS_CANCELLED
    ProgramLifecycleStatus.FAILED -> ProgramStatus.PROGRAM_STATUS_FAILED
}
