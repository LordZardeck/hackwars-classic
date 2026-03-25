package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.CommandDispatcher
import com.hackwars.rewrite.gamecore.CommandEnvelopeInput
import com.hackwars.rewrite.gamecore.CommandMetadata
import com.hackwars.rewrite.gamecore.CommandRegistry
import com.hackwars.rewrite.gamecore.AttackCancelResponse
import com.hackwars.rewrite.gamecore.AttackProgramRegistry
import com.hackwars.rewrite.gamecore.AttackStartResponse
import com.hackwars.rewrite.gamecore.BankTransactionResponse
import com.hackwars.rewrite.gamecore.BountyCreatedResponse
import com.hackwars.rewrite.gamecore.ChangeNetworkCommand
import com.hackwars.rewrite.gamecore.ChangeNetworkPayload
import com.hackwars.rewrite.gamecore.ChangeDailyPayCommand
import com.hackwars.rewrite.gamecore.ChangeDailyPayPayload
import com.hackwars.rewrite.gamecore.ChangeDailyPayResponse
import com.hackwars.rewrite.gamecore.ClueDataAcceptedResponse
import com.hackwars.rewrite.gamecore.ClueDataCommand
import com.hackwars.rewrite.gamecore.ClueDataPayload
import com.hackwars.rewrite.gamecore.CompileFileCommand
import com.hackwars.rewrite.gamecore.CompileFilePayload
import com.hackwars.rewrite.gamecore.CompileFileResponse
import com.hackwars.rewrite.gamecore.ComputerDelta
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.CreateFolderCommand
import com.hackwars.rewrite.gamecore.CreateFolderPayload
import com.hackwars.rewrite.gamecore.DepositCommand
import com.hackwars.rewrite.gamecore.DepositPayload
import com.hackwars.rewrite.gamecore.DecompileFileCommand
import com.hackwars.rewrite.gamecore.DecompileFilePayload
import com.hackwars.rewrite.gamecore.DecompileFileResponse
import com.hackwars.rewrite.gamecore.DailyIncomeProgramCommand
import com.hackwars.rewrite.gamecore.DailyIncomeProgramRegistry
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
import com.hackwars.rewrite.gamecore.GameUiEvent
import com.hackwars.rewrite.gamecore.HackScriptHttpHookRuntime
import com.hackwars.rewrite.gamecore.ExitWebpageCommand
import com.hackwars.rewrite.gamecore.ExitWebpagePayload
import com.hackwars.rewrite.gamecore.FacebookDepositPayload
import com.hackwars.rewrite.gamecore.FacebookTransferPayload
import com.hackwars.rewrite.gamecore.FacebookWithdrawPayload
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
import com.hackwars.rewrite.gamecore.MakeBountyCommand
import com.hackwars.rewrite.gamecore.MakeBountyPayload
import com.hackwars.rewrite.gamecore.MutationAcceptedResponse
import com.hackwars.rewrite.gamecore.HookSideEffectSink
import com.hackwars.rewrite.gamecore.HttpHookRuntime
import com.hackwars.rewrite.gamecore.InMemoryAttackProgramRegistry
import com.hackwars.rewrite.gamecore.InMemoryDailyIncomeProgramRegistry
import com.hackwars.rewrite.gamecore.NetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.NetworkSwitchResponse
import com.hackwars.rewrite.gamecore.NoOpGameStatePublisher
import com.hackwars.rewrite.gamecore.NoOpHookSideEffectSink
import com.hackwars.rewrite.gamecore.PageEditorResponse
import com.hackwars.rewrite.gamecore.PurchaseResponse
import com.hackwars.rewrite.gamecore.ProgramLifecycleStatus
import com.hackwars.rewrite.gamecore.ProgramUpdate
import com.hackwars.rewrite.gamecore.RequestCommand
import com.hackwars.rewrite.gamecore.RequestAttackCommand
import com.hackwars.rewrite.gamecore.RequestAttackPayload
import com.hackwars.rewrite.gamecore.RequestCancelAttackCommand
import com.hackwars.rewrite.gamecore.RequestCancelAttackPayload
import com.hackwars.rewrite.gamecore.RequestZombieAttackCommand
import com.hackwars.rewrite.gamecore.RequestZombieAttackPayload
import com.hackwars.rewrite.gamecore.RequestZombieCancelAttackCommand
import com.hackwars.rewrite.gamecore.RequestZombieCancelAttackPayload
import com.hackwars.rewrite.gamecore.RequestPageCommand
import com.hackwars.rewrite.gamecore.RequestPagePayload
import com.hackwars.rewrite.gamecore.RequestSaveCommand
import com.hackwars.rewrite.gamecore.RequestSavePayload
import com.hackwars.rewrite.gamecore.RequestTaskCommand
import com.hackwars.rewrite.gamecore.RequestTaskPayload
import com.hackwars.rewrite.gamecore.RequestTriggerCommand
import com.hackwars.rewrite.gamecore.RequestTriggerPayload
import com.hackwars.rewrite.gamecore.RequestWebpageCommand
import com.hackwars.rewrite.gamecore.RequestWebpagePayload
import com.hackwars.rewrite.gamecore.RequestDirectoryCommand
import com.hackwars.rewrite.gamecore.RequestDirectoryPayload
import com.hackwars.rewrite.gamecore.RequestFileCommand
import com.hackwars.rewrite.gamecore.RequestFilePayload
import com.hackwars.rewrite.gamecore.RequestSecondaryDirectoryCommand
import com.hackwars.rewrite.gamecore.RequestSecondaryDirectoryPayload
import com.hackwars.rewrite.gamecore.RequestPurchaseCommand
import com.hackwars.rewrite.gamecore.RequestPurchasePayload
import com.hackwars.rewrite.gamecore.RequestScanCommand
import com.hackwars.rewrite.gamecore.RequestScanPayload
import com.hackwars.rewrite.gamecore.RequestSearchCommand
import com.hackwars.rewrite.gamecore.RequestSearchPayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.SaveFileCommand
import com.hackwars.rewrite.gamecore.SaveFilePayload
import com.hackwars.rewrite.gamecore.SaveFileRequestResponse
import com.hackwars.rewrite.gamecore.ScanResponse
import com.hackwars.rewrite.gamecore.SearchCatalogRepository
import com.hackwars.rewrite.gamecore.SearchResultsResponse
import com.hackwars.rewrite.gamecore.SecondaryDirectoryListingResponse
import com.hackwars.rewrite.gamecore.SellFileCommand
import com.hackwars.rewrite.gamecore.SellFileCommandPayload
import com.hackwars.rewrite.gamecore.SellFileMultiCommand
import com.hackwars.rewrite.gamecore.SellFileMultiCommandPayload
import com.hackwars.rewrite.gamecore.SellFileMultiResponse
import com.hackwars.rewrite.gamecore.SellFileResponse
import com.hackwars.rewrite.gamecore.SetPreferenceCommand
import com.hackwars.rewrite.gamecore.SetPreferenceCommandResponse
import com.hackwars.rewrite.gamecore.SetPreferencePayload
import com.hackwars.rewrite.gamecore.SavePageCommand
import com.hackwars.rewrite.gamecore.SavePagePayload
import com.hackwars.rewrite.gamecore.SavePageResponse
import com.hackwars.rewrite.gamecore.SubmitWebpageCommand
import com.hackwars.rewrite.gamecore.SubmitWebpagePayload
import com.hackwars.rewrite.gamecore.TransferCommand
import com.hackwars.rewrite.gamecore.TransferPayload
import com.hackwars.rewrite.gamecore.TransferResponse
import com.hackwars.rewrite.gamecore.TaskProgressResponse
import com.hackwars.rewrite.gamecore.TriggerRequestResponse
import com.hackwars.rewrite.gamecore.ChangeWatchPortCommand
import com.hackwars.rewrite.gamecore.ChangeWatchPortPayload
import com.hackwars.rewrite.gamecore.ChangeWatchTypeCommand
import com.hackwars.rewrite.gamecore.ChangeWatchTypePayload
import com.hackwars.rewrite.gamecore.DeleteWatchCommand
import com.hackwars.rewrite.gamecore.DeleteWatchPayload
import com.hackwars.rewrite.gamecore.FetchWatchesCommand
import com.hackwars.rewrite.gamecore.FetchWatchesPayload
import com.hackwars.rewrite.gamecore.InstallWatchCommand
import com.hackwars.rewrite.gamecore.InstallWatchPayload
import com.hackwars.rewrite.gamecore.SetWatchNoteCommand
import com.hackwars.rewrite.gamecore.SetWatchNotePayload
import com.hackwars.rewrite.gamecore.SetWatchOnOffCommand
import com.hackwars.rewrite.gamecore.SetWatchOnOffPayload
import com.hackwars.rewrite.gamecore.SetWatchObservedPortsCommand
import com.hackwars.rewrite.gamecore.SetWatchObservedPortsPayload
import com.hackwars.rewrite.gamecore.SetWatchQuantityCommand
import com.hackwars.rewrite.gamecore.SetWatchQuantityPayload
import com.hackwars.rewrite.gamecore.SetWatchSearchFirewallCommand
import com.hackwars.rewrite.gamecore.SetWatchSearchFirewallPayload
import com.hackwars.rewrite.gamecore.VoteForWebsiteCommand
import com.hackwars.rewrite.gamecore.VotePayload
import com.hackwars.rewrite.gamecore.VoteResponse
import com.hackwars.rewrite.gamecore.WatchListResponse
import com.hackwars.rewrite.gamecore.WatchMutationResponse
import com.hackwars.rewrite.gamecore.WebsiteRenderResponse
import com.hackwars.rewrite.gamecore.WithdrawCommand
import com.hackwars.rewrite.gamecore.WithdrawPayload
import com.hackwars.rewrite.gamecore.ZombieAttackCancelResponse
import com.hackwars.rewrite.gamecore.ZombieAttackStartResponse
import com.hackwars.rewrite.gamecore.attackLoadoutFromLegacyPayload
import com.hackwars.rewrite.persistence.JdbcNetworkDirectoryRepository
import com.hackwars.rewrite.persistence.JdbcSearchCatalogRepository
import com.hackwars.rewrite.persistence.RewritePostgresConnectionFactory
import com.hackwars.rewrite.protocol.RewriteFrames
import hackwars.rewrite.v1.CommandEnvelope
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.ErrorEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import hackwars.rewrite.v1.ProgramStatus
import java.util.UUID

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
    private val serverId: String = "1",
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val httpHookRuntime: HttpHookRuntime = HackScriptHttpHookRuntime(),
    private val hookSideEffectSink: HookSideEffectSink = NoOpHookSideEffectSink,
    private val networkDirectoryRepository: NetworkDirectoryRepository = JdbcNetworkDirectoryRepository(
        connectionFactory = RewritePostgresConnectionFactory.fromEnvironment(),
    ),
    private val searchCatalogRepository: SearchCatalogRepository = JdbcSearchCatalogRepository(
        connectionFactory = RewritePostgresConnectionFactory.fromEnvironment(),
    ),
    private val attackProgramRegistry: AttackProgramRegistry = InMemoryAttackProgramRegistry(),
    private val dailyIncomeProgramRegistry: DailyIncomeProgramRegistry = InMemoryDailyIncomeProgramRegistry(),
    private val registry: CommandRegistry = defaultRegistry(
        serverId,
        clock,
        httpHookRuntime,
        networkDirectoryRepository,
        searchCatalogRepository,
        attackProgramRegistry,
    ),
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
                networkDirectoryRepository = networkDirectoryRepository,
                attackProgramRegistry = attackProgramRegistry,
                dailyIncomeProgramRegistry = dailyIncomeProgramRegistry,
                clock = clock,
            ),
            metadata = metadataFor(session),
            publisher = NoOpGameStatePublisher,
        )

        val stateId = GameStateId(session.playerIp)
        if (!dailyIncomeProgramRegistry.hasProgram(stateId)) {
            val publisher = protocolPublisher(transport)
            val programId = "daily-income-${stateId.value}"
            val handle = dispatcher.schedule(
                command = DailyIncomeProgramCommand(
                    stateId = stateId,
                    programId = programId,
                    dailyIncomeProgramRegistry = dailyIncomeProgramRegistry,
                    interestRegistry = interestRegistry,
                    clock = clock,
                ),
                metadata = metadataFor(session),
                publisher = publisher,
            )
            dailyIncomeProgramRegistry.register(
                stateId = stateId,
                programId = programId,
                handle = handle,
            )
        }

        return listOf(
            RewriteFrames.snapshot(
                gameStateId = bootstrap.state.id.value,
                sequence = bootstrap.state.version,
                payload = RewriteGameJson.encode(ComputerState.serializer(), bootstrap.state),
            ),
        )
    }

    suspend fun onSessionEnded(session: AuthenticatedGameSession) {
        val stateId = GameStateId(session.playerIp)
        interestRegistry.unregisterConnection(session.connectionId)
        val remainingSubscribers = interestRegistry.subscribersFor(stateId)
        if (remainingSubscribers.isEmpty()) {
            dailyIncomeProgramRegistry.cancel(stateId, "session-ended")
        }
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

            override suspend fun publishUiEvent(connectionIds: Set<String>, event: GameUiEvent) {
                val frame = RewriteFrames.gameUiEvent(
                    eventId = UUID.randomUUID().toString(),
                    eventType = event.protocolEventType(),
                    payload = RewriteGameJson.encode(GameUiEvent.serializer(), event),
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
            is TaskProgressResponse -> RewriteGameJson.encode(TaskProgressResponse.serializer(), result)
            is SaveFileRequestResponse -> RewriteGameJson.encode(SaveFileRequestResponse.serializer(), result)
            is ClueDataAcceptedResponse -> RewriteGameJson.encode(ClueDataAcceptedResponse.serializer(), result)
            is BountyCreatedResponse -> RewriteGameJson.encode(BountyCreatedResponse.serializer(), result)
            is TriggerRequestResponse -> RewriteGameJson.encode(TriggerRequestResponse.serializer(), result)
            is AttackStartResponse -> RewriteGameJson.encode(AttackStartResponse.serializer(), result)
            is AttackCancelResponse -> RewriteGameJson.encode(AttackCancelResponse.serializer(), result)
            is ZombieAttackStartResponse -> RewriteGameJson.encode(ZombieAttackStartResponse.serializer(), result)
            is ZombieAttackCancelResponse -> RewriteGameJson.encode(ZombieAttackCancelResponse.serializer(), result)
            is ChangeDailyPayResponse -> RewriteGameJson.encode(ChangeDailyPayResponse.serializer(), result)
            is MutationAcceptedResponse -> RewriteGameJson.encode(MutationAcceptedResponse.serializer(), result)
            is CompileFileResponse -> RewriteGameJson.encode(CompileFileResponse.serializer(), result)
            is DecompileFileResponse -> RewriteGameJson.encode(DecompileFileResponse.serializer(), result)
            is InstallApplicationResponse -> RewriteGameJson.encode(InstallApplicationResponse.serializer(), result)
            is InstallEquipmentResponse -> RewriteGameJson.encode(InstallEquipmentResponse.serializer(), result)
            is InstallFirewallResponse -> RewriteGameJson.encode(InstallFirewallResponse.serializer(), result)
            is BankTransactionResponse -> RewriteGameJson.encode(BankTransactionResponse.serializer(), result)
            is TransferResponse -> RewriteGameJson.encode(TransferResponse.serializer(), result)
            is SellFileResponse -> RewriteGameJson.encode(SellFileResponse.serializer(), result)
            is SellFileMultiResponse -> RewriteGameJson.encode(SellFileMultiResponse.serializer(), result)
            is PurchaseResponse -> RewriteGameJson.encode(PurchaseResponse.serializer(), result)
            is NetworkSwitchResponse -> RewriteGameJson.encode(NetworkSwitchResponse.serializer(), result)
            is ScanResponse -> RewriteGameJson.encode(ScanResponse.serializer(), result)
            is SearchResultsResponse -> RewriteGameJson.encode(SearchResultsResponse.serializer(), result)
            is WatchListResponse -> RewriteGameJson.encode(WatchListResponse.serializer(), result)
            is WatchMutationResponse -> RewriteGameJson.encode(WatchMutationResponse.serializer(), result)
            is SetPreferenceCommandResponse -> RewriteGameJson.encode(SetPreferenceCommandResponse.serializer(), result)
            is PageEditorResponse -> RewriteGameJson.encode(PageEditorResponse.serializer(), result)
            is SavePageResponse -> RewriteGameJson.encode(SavePageResponse.serializer(), result)
            is WebsiteRenderResponse -> RewriteGameJson.encode(WebsiteRenderResponse.serializer(), result)
            is VoteResponse -> RewriteGameJson.encode(VoteResponse.serializer(), result)
            is GameSessionBootstrapResult -> RewriteGameJson.encode(GameSessionBootstrapResult.serializer(), result)
            else -> error("Unsupported rewrite command response type: ${result::class.qualifiedName}")
        }
    }

    private companion object {
        fun defaultRegistry(
            serverId: String,
            clock: () -> Long,
            httpHookRuntime: HttpHookRuntime,
            networkDirectoryRepository: NetworkDirectoryRepository,
            searchCatalogRepository: SearchCatalogRepository,
            attackProgramRegistry: AttackProgramRegistry,
        ): CommandRegistry {
            return CommandRegistry()
                .register("requestpage") { input ->
                    val payload = decodePayload(input, RequestPagePayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    RequestPageCommand(stateId = authenticatedStateId)
                }
                .register("savepage") { input ->
                    val payload = decodePayload(input, SavePagePayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    SavePageCommand(
                        stateId = authenticatedStateId,
                        title = payload.title.orEmpty(),
                        body = payload.body.orEmpty(),
                    )
                }
                .register("requestwebpage") { input ->
                    val payload = decodePayload(input, RequestWebpagePayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.sourceIp, input.commandName)
                    RequestWebpageCommand(
                        sourceStateId = authenticatedStateId,
                        targetStateId = resolveWebsiteTarget(payload.targetIp, serverId),
                        parameters = payload.parameters,
                        httpHookRuntime = httpHookRuntime,
                    )
                }
                .register("submit") { input ->
                    val payload = decodePayload(input, SubmitWebpagePayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.sourceIp, input.commandName)
                    SubmitWebpageCommand(
                        sourceStateId = authenticatedStateId,
                        targetStateId = payload.targetIp
                            ?.takeUnless { it.isBlank() }
                            ?.let { resolveWebsiteTarget(it, serverId) }
                            ?: authenticatedStateId,
                        parameters = payload.parameters,
                        httpHookRuntime = httpHookRuntime,
                    )
                }
                .register("exit") { input ->
                    val payload = decodePayload(input, ExitWebpagePayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.sourceIp, input.commandName)
                    ExitWebpageCommand(
                        sourceStateId = authenticatedStateId,
                        targetStateId = payload.targetIp
                            ?.takeUnless { it.isBlank() }
                            ?.let { resolveWebsiteTarget(it, serverId) }
                            ?: authenticatedStateId,
                        httpHookRuntime = httpHookRuntime,
                    )
                }
                .register("vote") { input ->
                    val payload = decodePayload(input, VotePayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.sourceIp, input.commandName)
                    VoteForWebsiteCommand(
                        voterStateId = authenticatedStateId,
                        targetStateId = GameStateId(
                            payload.targetIp?.takeUnless { it.isBlank() }
                                ?: error("Vote target is required."),
                        ),
                    )
                }
                .register("deposit") { input ->
                    val payload = decodePayload(input, DepositPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    DepositCommand(
                        stateId = authenticatedStateId,
                        amount = payload.amount,
                        portNumber = payload.port,
                    )
                }
                .register("withdraw") { input ->
                    val payload = decodePayload(input, WithdrawPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    WithdrawCommand(
                        stateId = authenticatedStateId,
                        amount = payload.amount,
                        portNumber = payload.port,
                    )
                }
                .register("transfer") { input ->
                    val payload = decodePayload(input, TransferPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    TransferCommand(
                        sourceStateId = authenticatedStateId,
                        targetStateId = GameStateId(payload.targetIp),
                        amount = payload.amount,
                        portNumber = payload.port,
                    )
                }
                .register("facebookdeposit") { input ->
                    val payload = decodePayload(input, FacebookDepositPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    DepositCommand(
                        stateId = authenticatedStateId,
                        amount = payload.amount,
                        portNumber = payload.defaultPort,
                    )
                }
                .register("facebookwithdraw") { input ->
                    val payload = decodePayload(input, FacebookWithdrawPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    WithdrawCommand(
                        stateId = authenticatedStateId,
                        amount = payload.amount,
                        portNumber = payload.defaultPort,
                    )
                }
                .register("facebooktransfer") { input ->
                    val payload = decodePayload(input, FacebookTransferPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    TransferCommand(
                        sourceStateId = authenticatedStateId,
                        targetStateId = GameStateId(payload.targetIp),
                        amount = payload.amount,
                        portNumber = payload.defaultPort,
                    )
                }
                .register("changenetwork") { input ->
                    val payload = decodePayload(input, ChangeNetworkPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    ChangeNetworkCommand(
                        stateId = authenticatedStateId,
                        targetNetworkName = payload.network,
                        networkDirectoryRepository = networkDirectoryRepository,
                    )
                }
                .register("requestscan") { input ->
                    val payload = decodePayload(input, RequestScanPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    RequestScanCommand(
                        requesterStateId = authenticatedStateId,
                        targetStateId = GameStateId(
                            payload.targetIp?.takeUnless { it.isBlank() }
                                ?: error("Target ip is required for ${input.commandName}."),
                        ),
                    )
                }
                .register("requestattack") { input ->
                    val payload = decodePayload(input, RequestAttackPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.sourceIp, input.commandName)
                    RequestAttackCommand(
                        attackerStateId = authenticatedStateId,
                        targetStateId = GameStateId(
                            payload.targetIp.takeUnless { it.isBlank() }
                                ?: error("Target ip is required for ${input.commandName}."),
                        ),
                        sourceIp = payload.sourceIp,
                        sourcePort = payload.sourcePort,
                        targetPort = payload.targetPort,
                        loadout = attackLoadoutFromLegacyPayload(
                            secondaryPorts = payload.secondaryPorts,
                            scripts = payload.scripts,
                            extraInfo = payload.extraInfo,
                        ),
                        windowHandle = payload.windowHandle ?: 0,
                        attackProgramRegistry = attackProgramRegistry,
                        clock = clock,
                    )
                }
                .register("requestcancelattack") { input ->
                    val payload = decodePayload(input, RequestCancelAttackPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    RequestCancelAttackCommand(
                        attackerStateId = authenticatedStateId,
                        sourceIp = payload.ip,
                        sourcePort = payload.port,
                        attackProgramRegistry = attackProgramRegistry,
                    )
                }
                .register("requestzombieattack") { input ->
                    val payload = decodePayload(input, RequestZombieAttackPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    RequestZombieAttackCommand(
                        controllerStateId = authenticatedStateId,
                        controllerIp = payload.parentIp,
                        zombieStateId = GameStateId(payload.sourceIp ?: authenticatedStateId.value),
                        targetStateId = GameStateId(payload.targetIp),
                        sourcePort = payload.sourcePort,
                        targetPort = payload.targetPort,
                        loadout = attackLoadoutFromLegacyPayload(
                            secondaryPorts = payload.secondaryPorts,
                            scripts = payload.scripts,
                            extraInfo = payload.extraInfo,
                        ),
                        attackProgramRegistry = attackProgramRegistry,
                        clock = clock,
                    )
                }
                .register("requestzombiecancelattack") { input ->
                    val payload = decodePayload(input, RequestZombieCancelAttackPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    RequestZombieCancelAttackCommand(
                        controllerStateId = authenticatedStateId,
                        controllerIp = payload.targetIp,
                        zombieStateId = GameStateId(payload.ip ?: authenticatedStateId.value),
                        sourcePort = payload.port,
                        attackProgramRegistry = attackProgramRegistry,
                    )
                }
                .register("changedailypay") { input ->
                    val payload = decodePayload(input, ChangeDailyPayPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    ChangeDailyPayCommand(
                        actorStateId = authenticatedStateId,
                        targetStateId = GameStateId(payload.ip),
                        targetPort = payload.port,
                        requestedRevenueTargetStateId = GameStateId(
                            payload.change?.takeUnless { it.isBlank() }
                                ?: error("Change target is required for ${input.commandName}."),
                        ),
                    )
                }
                .register("requestsearch") { input ->
                    val payload = decodePayload(input, RequestSearchPayload.serializer())
                    RequestSearchCommand(
                        requesterStateId = requireAuthenticatedStateId(input),
                        query = payload.query.orEmpty(),
                        startIndex = payload.startIndex,
                        searchCatalogRepository = searchCatalogRepository,
                        clock = clock,
                    )
                }
                .register("fetchwatches") { input ->
                    val payload = decodePayload(input, FetchWatchesPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    FetchWatchesCommand(stateId = authenticatedStateId)
                }
                .register("installwatch") { input ->
                    val payload = decodePayload(input, InstallWatchPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    InstallWatchCommand(
                        stateId = authenticatedStateId,
                        path = payload.path,
                        fileName = payload.name ?: error("Watch file name is required for ${input.commandName}."),
                        typeCode = payload.type ?: error("Watch type is required for ${input.commandName}."),
                        portNumber = payload.port ?: error("Watch port is required for ${input.commandName}."),
                    )
                }
                .register("setwatchnote") { input ->
                    val payload = decodePayload(input, SetWatchNotePayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    SetWatchNoteCommand(
                        stateId = authenticatedStateId,
                        watchIndex = payload.watchId ?: error("Watch index is required for ${input.commandName}."),
                        note = payload.note.orEmpty(),
                    )
                }
                .register("setwatchonoff") { input ->
                    val payload = decodePayload(input, SetWatchOnOffPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    SetWatchOnOffCommand(
                        stateId = authenticatedStateId,
                        watchIndex = payload.watchId ?: error("Watch index is required for ${input.commandName}."),
                        enabled = payload.state ?: error("Watch state is required for ${input.commandName}."),
                    )
                }
                .register("setwatchquantity") { input ->
                    val payload = decodePayload(input, SetWatchQuantityPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    SetWatchQuantityCommand(
                        stateId = authenticatedStateId,
                        watchIndex = payload.watchId ?: error("Watch index is required for ${input.commandName}."),
                        quantity = payload.quantity ?: error("Watch quantity is required for ${input.commandName}."),
                    )
                }
                .register("setwatchobservedports") { input ->
                    val payload = decodePayload(input, SetWatchObservedPortsPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    SetWatchObservedPortsCommand(
                        stateId = authenticatedStateId,
                        watchIndex = payload.watchId ?: error("Watch index is required for ${input.commandName}."),
                        observedPorts = payload.observedPorts,
                    )
                }
                .register("setwatchsearchfirewall") { input ->
                    val payload = decodePayload(input, SetWatchSearchFirewallPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    SetWatchSearchFirewallCommand(
                        stateId = authenticatedStateId,
                        watchIndex = payload.watchId ?: error("Watch index is required for ${input.commandName}."),
                        searchFirewallType = payload.searchFirewall
                            ?: error("Search firewall type is required for ${input.commandName}."),
                    )
                }
                .register("changewatchport") { input ->
                    val payload = decodePayload(input, ChangeWatchPortPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    ChangeWatchPortCommand(
                        stateId = authenticatedStateId,
                        watchIndex = payload.watchId ?: error("Watch index is required for ${input.commandName}."),
                        portNumber = payload.portId ?: error("Port id is required for ${input.commandName}."),
                    )
                }
                .register("changewatchtype") { input ->
                    val payload = decodePayload(input, ChangeWatchTypePayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    ChangeWatchTypeCommand(
                        stateId = authenticatedStateId,
                        watchIndex = payload.watchId ?: error("Watch index is required for ${input.commandName}."),
                        typeCode = payload.newType ?: error("Watch type is required for ${input.commandName}."),
                    )
                }
                .register("deletewatch") { input ->
                    val payload = decodePayload(input, DeleteWatchPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    DeleteWatchCommand(
                        stateId = authenticatedStateId,
                        watchIndex = payload.watchId ?: error("Watch index is required for ${input.commandName}."),
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
                .register("requesttask") { input ->
                    val payload = decodePayload(input, RequestTaskPayload.serializer())
                    RequestTaskCommand(
                        stateId = payload.targetIp
                            ?.takeUnless { it.isBlank() }
                            ?.let(::GameStateId)
                            ?: requireAuthenticatedStateId(input),
                        fileName = payload.fileName,
                        questId = payload.questId,
                        taskName = payload.taskName,
                    )
                }
                .register("requestsave") { input ->
                    val payload = decodePayload(input, RequestSavePayload.serializer())
                    RequestSaveCommand(
                        stateId = payload.targetIp
                            ?.takeUnless { it.isBlank() }
                            ?.let(::GameStateId)
                            ?: requireAuthenticatedStateId(input),
                        fileName = payload.fileName,
                        triggerParameters = payload.triggerParameters,
                    )
                }
                .register("cluedata") { input ->
                    val payload = decodePayload(input, ClueDataPayload.serializer())
                    ClueDataCommand(
                        stateId = GameStateId(payload.ip),
                        targetIp = payload.ip,
                        data = payload.data,
                    )
                }
                .register("makebounty") { input ->
                    val payload = decodePayload(input, MakeBountyPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.sourceIp, input.commandName)
                    MakeBountyCommand(
                        creatorStateId = authenticatedStateId,
                        storeStateId = canonicalStoreStateId(serverId),
                        anonymous = payload.anonymous ?: false,
                        target = payload.target,
                        type = payload.type ?: 0,
                        fileName = payload.fname,
                        folder = payload.folder,
                        iterations = payload.iterations ?: 0,
                        reward = payload.reward ?: 0.0,
                    )
                }
                .register("requesttrigger") { input ->
                    val payload = decodePayload(input, RequestTriggerPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    val sourceIp = payload.sourceIp?.takeUnless { it.isBlank() } ?: authenticatedStateId.value
                    require(sourceIp == authenticatedStateId.value) {
                        "Payload source ip $sourceIp does not match authenticated state ${authenticatedStateId.value} for ${input.commandName}."
                    }
                    require(payload.targetIp.isNotBlank()) {
                        "Target ip is required for ${input.commandName}."
                    }
                    RequestTriggerCommand(
                        stateId = authenticatedStateId,
                        targetStateId = GameStateId(payload.targetIp),
                        selector = payload.selector,
                        sourceIp = sourceIp,
                        triggerParameters = payload.triggerParameters,
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
                .register("sellfile") { input ->
                    val payload = decodePayload(input, SellFileCommandPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    SellFileCommand(
                        stateId = authenticatedStateId,
                        path = payload.location,
                        fileName = payload.fileName,
                        compileCost = payload.compileCost,
                    )
                }
                .register("sellfilemulti") { input ->
                    val payload = decodePayload(input, SellFileMultiCommandPayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.ip, input.commandName)
                    SellFileMultiCommand(
                        stateId = authenticatedStateId,
                        storeStateId = canonicalStoreStateId(serverId),
                        entries = payload.allFiles,
                    )
                }
                .register("requestpurchase") { input ->
                    val payload = decodePayload(input, RequestPurchasePayload.serializer())
                    val authenticatedStateId = requireAuthenticatedStateId(input)
                    requirePayloadIpMatches(authenticatedStateId, payload.sourceIp, input.commandName)
                    RequestPurchaseCommand(
                        buyerStateId = authenticatedStateId,
                        sellerStateId = resolvePurchaseTarget(payload.targetIp, serverId),
                        fileName = payload.fileName,
                        requestedQuantity = payload.quantity,
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

        fun requireAuthenticatedStateId(input: CommandEnvelopeInput): GameStateId {
            return input.metadata.authenticatedStateId
                ?: error("Command ${input.commandName} requires an authenticated state id.")
        }

        fun requirePayloadIpMatches(
            authenticatedStateId: GameStateId,
            payloadIp: String,
            commandName: String,
        ) {
            require(authenticatedStateId.value == payloadIp) {
                "Payload ip $payloadIp does not match authenticated state ${authenticatedStateId.value} for $commandName."
            }
        }

        fun resolvePurchaseTarget(
            targetIp: String,
            serverId: String,
        ): GameStateId {
            return if (targetIp.startsWith("store")) {
                canonicalStoreStateId(serverId)
            } else {
                GameStateId(targetIp)
            }
        }

        fun resolveWebsiteTarget(
            targetIp: String,
            serverId: String,
        ): GameStateId {
            return if (targetIp.startsWith("store")) {
                canonicalStoreStateId(serverId)
            } else {
                GameStateId(targetIp)
            }
        }

        fun canonicalStoreStateId(serverId: String): GameStateId = GameStateId("store$serverId")

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

private fun GameUiEvent.protocolEventType(): String = when (this) {
    is com.hackwars.rewrite.gamecore.PopupUiEvent -> "popup"
    is com.hackwars.rewrite.gamecore.TextMessageUiEvent -> "message"
    is com.hackwars.rewrite.gamecore.AttackMessageUiEvent -> "attack_message"
    is com.hackwars.rewrite.gamecore.ZombieAttackUiEvent -> "zombie_attack"
}

private fun ProgramLifecycleStatus.toProtocolStatus(): ProgramStatus = when (this) {
    ProgramLifecycleStatus.RUNNING -> ProgramStatus.PROGRAM_STATUS_RUNNING
    ProgramLifecycleStatus.COMPLETED -> ProgramStatus.PROGRAM_STATUS_COMPLETED
    ProgramLifecycleStatus.CANCELLED -> ProgramStatus.PROGRAM_STATUS_CANCELLED
    ProgramLifecycleStatus.FAILED -> ProgramStatus.PROGRAM_STATUS_FAILED
}
