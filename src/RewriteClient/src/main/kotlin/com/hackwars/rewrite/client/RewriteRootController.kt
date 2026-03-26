package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.economy.RewriteCreateBountyDialog
import com.hackwars.rewrite.client.economy.RewriteDepositWindow
import com.hackwars.rewrite.client.economy.RewriteTransferWindow
import com.hackwars.rewrite.client.economy.RewriteWithdrawWindow
import com.hackwars.rewrite.client.files.RewriteFilePropertiesWindow
import com.hackwars.rewrite.client.files.RewriteHomeWindow
import com.hackwars.rewrite.client.files.RewriteLocalFileOpenTarget
import com.hackwars.rewrite.client.files.RewriteScriptEditorWindow
import com.hackwars.rewrite.client.files.routeLocalFileTarget
import com.hackwars.rewrite.client.shell.RewritePlaceholderInternalFrame
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.shell.RewriteShellDialogCoordinator
import com.hackwars.rewrite.client.shell.RewriteShellDialogHost
import com.hackwars.rewrite.client.shell.RewriteShellWindowCoordinator
import com.hackwars.rewrite.client.shell.RewriteShellWindowHost
import com.hackwars.rewrite.client.systems.RewriteEquipmentManagerWindow
import com.hackwars.rewrite.client.systems.RewriteFirewallManagerWindow
import com.hackwars.rewrite.client.systems.RewritePortManagementWindow
import com.hackwars.rewrite.client.systems.RewriteWatchManagerWindow
import com.hackwars.rewrite.client.web.RewriteSiteEditorWindow
import com.hackwars.rewrite.client.web.RewriteWebBrowserWindow
import com.hackwars.rewrite.clientmodel.RewriteClientBootstrapState
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.clientmodel.RewriteClientState
import com.hackwars.rewrite.clientmodel.RewriteClientStore
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameState
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameUiNotice
import com.hackwars.rewrite.clientmodel.RewriteServiceState
import com.hackwars.rewrite.protocol.ClientBankTransactionResponse
import com.hackwars.rewrite.protocol.ClientBountyCreatedResponse
import com.hackwars.rewrite.protocol.ClientCompileFilePayload
import com.hackwars.rewrite.protocol.ClientCompileFileResponse
import com.hackwars.rewrite.protocol.ClientDecompileFilePayload
import com.hackwars.rewrite.protocol.ClientDecompileFileResponse
import com.hackwars.rewrite.protocol.ClientDepositPayload
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientExitWebpagePayload
import com.hackwars.rewrite.protocol.ClientFileContentsResponse
import com.hackwars.rewrite.protocol.ClientFilesystemState
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientMakeBountyPayload
import com.hackwars.rewrite.protocol.ClientHealPortPayload
import com.hackwars.rewrite.protocol.ClientHealPortResponse
import com.hackwars.rewrite.protocol.ClientInstallApplicationPayload
import com.hackwars.rewrite.protocol.ClientInstallApplicationResponse
import com.hackwars.rewrite.protocol.ClientInstallEquipmentPayload
import com.hackwars.rewrite.protocol.ClientInstallEquipmentResponse
import com.hackwars.rewrite.protocol.ClientInstallFirewallPayload
import com.hackwars.rewrite.protocol.ClientInstallFirewallResponse
import com.hackwars.rewrite.protocol.ClientMutationAcceptedResponse
import com.hackwars.rewrite.protocol.ClientPageEditorResponse
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.ClientRequestDirectoryPayload
import com.hackwars.rewrite.protocol.ClientRequestFilePayload
import com.hackwars.rewrite.protocol.ClientRequestPagePayload
import com.hackwars.rewrite.protocol.ClientRequestPurchasePayload
import com.hackwars.rewrite.protocol.ClientRequestWebpagePayload
import com.hackwars.rewrite.protocol.ClientPurchaseResponse
import com.hackwars.rewrite.protocol.ClientSaveFilePayload
import com.hackwars.rewrite.protocol.ClientSavePagePayload
import com.hackwars.rewrite.protocol.ClientSavePageResponse
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientSubmitWebpagePayload
import com.hackwars.rewrite.protocol.ClientWatchListResponse
import com.hackwars.rewrite.protocol.ClientWatchManagerState
import com.hackwars.rewrite.protocol.ClientWatchMutationResponse
import com.hackwars.rewrite.protocol.ClientFetchWatchesPayload
import com.hackwars.rewrite.protocol.ClientInstallWatchPayload
import com.hackwars.rewrite.protocol.ClientSetWatchNotePayload
import com.hackwars.rewrite.protocol.ClientSetWatchOnOffPayload
import com.hackwars.rewrite.protocol.ClientSetWatchObservedPortsPayload
import com.hackwars.rewrite.protocol.ClientSetWatchQuantityPayload
import com.hackwars.rewrite.protocol.ClientSetWatchSearchFirewallPayload
import com.hackwars.rewrite.protocol.ClientChangeWatchPortPayload
import com.hackwars.rewrite.protocol.ClientChangeWatchTypePayload
import com.hackwars.rewrite.protocol.ClientDeleteWatchPayload
import com.hackwars.rewrite.protocol.ClientTransferPayload
import com.hackwars.rewrite.protocol.ClientTransferResponse
import com.hackwars.rewrite.protocol.ClientVotePayload
import com.hackwars.rewrite.protocol.ClientVoteResponse
import com.hackwars.rewrite.protocol.ClientWebsiteRenderResponse
import com.hackwars.rewrite.protocol.ClientWithdrawPayload
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import java.awt.Window
import javax.swing.JDialog
import javax.swing.JInternalFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class RewriteRootController(
    private val gameConnectionConfig: RewriteGameConnectionConfig = RewriteGameConnectionConfig(),
    private val authGateway: RewriteLoginAuthGateway = PlayFabRewriteLoginAuthGateway(),
    private val sessionGateway: RewriteServiceSessionGateway = RewriteTcpServiceSessionGateway(gameConnectionConfig),
    val store: RewriteClientStore = RewriteClientStore(),
    private val clock: () -> Instant = { Instant.now() },
    private val workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val sessions = mutableMapOf<RewriteService, RewriteServiceSession>()
    private val gameCommandBroker = RewriteGameCommandBroker(
        sendFrame = { frame -> send(RewriteService.GAME, frame) },
    )
    private val shellWindows = RewriteShellWindowCoordinator(::createShellWindow)
    private val shellDialogs = RewriteShellDialogCoordinator(::createShellDialog)
    private val bootstrapLock = Any()
    private val filePropertiesWindowsByPath = mutableMapOf<String, RewriteFilePropertiesWindow>()
    private var loginAttemptId: Long = 0
    private var activeLoginJob: Job? = null
    private var pendingGameBootstrap: PendingGameBootstrap? = null
    private var shellHost: RewriteShellWindowHost? = null

    fun snapshot(): RewriteClientState = store.snapshot()

    fun <Selected> selector(project: (RewriteClientState) -> Selected): Flow<Selected> {
        return store.selector(project)
    }

    fun bootstrapState(): RewriteClientBootstrapState = snapshot().bootstrap

    fun bootstrapStateSelector(): Flow<RewriteClientBootstrapState> {
        return store.bootstrapSelector()
    }

    fun route(): RewriteClientRoute = snapshot().bootstrap.route

    fun routeSelector(): Flow<RewriteClientRoute> {
        return store.routeSelector()
    }

    fun loginErrorSelector(): Flow<String?> {
        return store.loginErrorSelector()
    }

    fun serviceState(service: RewriteService): RewriteServiceState {
        return snapshot().services.getValue(service)
    }

    fun gameDecodedState(): RewriteDecodedGameState = snapshot().game.decodedGame

    fun gameDecodedStateSelector(): Flow<RewriteDecodedGameState> {
        return store.serviceDecodedGameSelector(RewriteService.GAME)
    }

    fun gameShellState(): ClientGameSnapshot? = snapshot().game.decodedGame.shellState

    fun gameShellStateSelector(): Flow<ClientGameSnapshot?> {
        return store.serviceShellStateSelector(RewriteService.GAME)
    }

    fun gameFilesystemState(): ClientFilesystemState? = gameShellState()?.filesystem

    fun gameFilesystemStateSelector(): Flow<ClientFilesystemState?> {
        return store.serviceFilesystemStateSelector(RewriteService.GAME)
    }

    fun gameWatchState(): ClientWatchManagerState? = gameShellState()?.watches

    fun gameWatchStateSelector(): Flow<ClientWatchManagerState?> {
        return store.serviceWatchStateSelector(RewriteService.GAME)
    }

    fun gameProgramUpdatesSelector(): Flow<Map<String, ClientProgramUpdate>> {
        return store.serviceProgramUpdatesSelector(RewriteService.GAME)
    }

    fun gameUiNoticesSelector(): Flow<List<RewriteDecodedGameUiNotice>> {
        return store.serviceGameUiNoticesSelector(RewriteService.GAME)
    }

    fun gameAcceptedPlayerIpSelector(): Flow<String?> {
        return selector { it.game.latestAcceptedSession?.playerIp }
    }

    fun attachShellHost(host: RewriteShellWindowHost?) {
        shellHost = host
        shellWindows.attachHost(host)
    }

    internal fun attachDialogHost(host: RewriteShellDialogHost?) {
        shellDialogs.attachHost(host)
    }

    fun launchShellCommand(
        command: RewriteShellCommand,
        preferredPort: Int? = null,
    ) {
        if (command == RewriteShellCommand.CREATE_BOUNTY) {
            shellDialogs.open(command)
        } else {
            shellWindows.open(command, preferredPort)
        }
    }

    internal suspend fun requestDeposit(
        amount: Double,
        portNumber: Int,
    ): RewriteGameCommandResult<ClientBankTransactionResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "deposit",
            payloadSerializer = ClientDepositPayload.serializer(),
            payload = ClientDepositPayload(
                amount = amount,
                ip = playerIp,
                port = portNumber,
            ),
            responseSerializer = ClientBankTransactionResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestWithdraw(
        amount: Double,
        portNumber: Int,
    ): RewriteGameCommandResult<ClientBankTransactionResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "withdraw",
            payloadSerializer = ClientWithdrawPayload.serializer(),
            payload = ClientWithdrawPayload(
                amount = amount,
                ip = playerIp,
                port = portNumber,
            ),
            responseSerializer = ClientBankTransactionResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestTransfer(
        amount: Double,
        targetIp: String,
        portNumber: Int,
    ): RewriteGameCommandResult<ClientTransferResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "transfer",
            payloadSerializer = ClientTransferPayload.serializer(),
            payload = ClientTransferPayload(
                amount = amount,
                ip = playerIp,
                targetIp = targetIp,
                port = portNumber,
            ),
            responseSerializer = ClientTransferResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp),
        )
    }

    internal suspend fun requestHealPort(
        portNumber: Int,
    ): RewriteGameCommandResult<ClientHealPortResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "healport",
            payloadSerializer = ClientHealPortPayload.serializer(),
            payload = ClientHealPortPayload(
                ip = playerIp,
                port = portNumber,
            ),
            responseSerializer = ClientHealPortResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestInstallApplication(
        path: String?,
        name: String,
        portNumber: Int,
    ): RewriteGameCommandResult<ClientInstallApplicationResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "installapplication",
            payloadSerializer = ClientInstallApplicationPayload.serializer(),
            payload = ClientInstallApplicationPayload(
                path = path,
                name = name,
                portNumber = portNumber,
            ),
            responseSerializer = ClientInstallApplicationResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestInstallEquipment(
        path: String?,
        name: String,
        slot: com.hackwars.rewrite.protocol.ClientEquipmentSlot,
    ): RewriteGameCommandResult<ClientInstallEquipmentResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "installequipment",
            payloadSerializer = ClientInstallEquipmentPayload.serializer(),
            payload = ClientInstallEquipmentPayload(
                path = path,
                name = name,
                slot = slot,
            ),
            responseSerializer = ClientInstallEquipmentResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestInstallFirewall(
        path: String?,
        name: String,
        portNumber: Int,
    ): RewriteGameCommandResult<ClientInstallFirewallResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "installfirewall",
            payloadSerializer = ClientInstallFirewallPayload.serializer(),
            payload = ClientInstallFirewallPayload(
                path = path,
                name = name,
                portNumber = portNumber,
            ),
            responseSerializer = ClientInstallFirewallResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestFetchWatches(): RewriteGameCommandResult<ClientWatchListResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "fetchwatches",
            payloadSerializer = ClientFetchWatchesPayload.serializer(),
            payload = ClientFetchWatchesPayload(ip = playerIp),
            responseSerializer = ClientWatchListResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestInstallWatch(
        path: String?,
        name: String,
        type: Int,
        portNumber: Int,
    ): RewriteGameCommandResult<ClientWatchMutationResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "installwatch",
            payloadSerializer = ClientInstallWatchPayload.serializer(),
            payload = ClientInstallWatchPayload(
                ip = playerIp,
                path = path,
                name = name,
                type = type,
                port = portNumber,
            ),
            responseSerializer = ClientWatchMutationResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestSetWatchNote(
        watchId: Int,
        note: String,
    ): RewriteGameCommandResult<ClientWatchMutationResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "setwatchnote",
            payloadSerializer = ClientSetWatchNotePayload.serializer(),
            payload = ClientSetWatchNotePayload(
                ip = playerIp,
                watchId = watchId,
                note = note,
            ),
            responseSerializer = ClientWatchMutationResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestSetWatchOnOff(
        watchId: Int,
        state: Boolean,
    ): RewriteGameCommandResult<ClientWatchMutationResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "setwatchonoff",
            payloadSerializer = ClientSetWatchOnOffPayload.serializer(),
            payload = ClientSetWatchOnOffPayload(
                ip = playerIp,
                watchId = watchId,
                state = state,
            ),
            responseSerializer = ClientWatchMutationResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestSetWatchQuantity(
        watchId: Int,
        quantity: Double,
    ): RewriteGameCommandResult<ClientWatchMutationResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "setwatchquantity",
            payloadSerializer = ClientSetWatchQuantityPayload.serializer(),
            payload = ClientSetWatchQuantityPayload(
                ip = playerIp,
                watchId = watchId,
                quantity = quantity,
            ),
            responseSerializer = ClientWatchMutationResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestSetWatchObservedPorts(
        watchId: Int,
        observedPorts: List<Int>,
    ): RewriteGameCommandResult<ClientWatchMutationResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "setwatchobservedports",
            payloadSerializer = ClientSetWatchObservedPortsPayload.serializer(),
            payload = ClientSetWatchObservedPortsPayload(
                ip = playerIp,
                watchId = watchId,
                observedPorts = observedPorts,
            ),
            responseSerializer = ClientWatchMutationResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestSetWatchSearchFirewall(
        watchId: Int,
        searchFirewall: Int,
    ): RewriteGameCommandResult<ClientWatchMutationResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "setwatchsearchfirewall",
            payloadSerializer = ClientSetWatchSearchFirewallPayload.serializer(),
            payload = ClientSetWatchSearchFirewallPayload(
                ip = playerIp,
                watchId = watchId,
                searchFirewall = searchFirewall,
            ),
            responseSerializer = ClientWatchMutationResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestChangeWatchPort(
        watchId: Int,
        portId: Int,
    ): RewriteGameCommandResult<ClientWatchMutationResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "changewatchport",
            payloadSerializer = ClientChangeWatchPortPayload.serializer(),
            payload = ClientChangeWatchPortPayload(
                ip = playerIp,
                watchId = watchId,
                portId = portId,
            ),
            responseSerializer = ClientWatchMutationResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestChangeWatchType(
        watchId: Int,
        newType: Int,
    ): RewriteGameCommandResult<ClientWatchMutationResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "changewatchtype",
            payloadSerializer = ClientChangeWatchTypePayload.serializer(),
            payload = ClientChangeWatchTypePayload(
                ip = playerIp,
                watchId = watchId,
                newType = newType,
            ),
            responseSerializer = ClientWatchMutationResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestDeleteWatch(
        watchId: Int,
    ): RewriteGameCommandResult<ClientWatchMutationResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "deletewatch",
            payloadSerializer = ClientDeleteWatchPayload.serializer(),
            payload = ClientDeleteWatchPayload(
                ip = playerIp,
                watchId = watchId,
            ),
            responseSerializer = ClientWatchMutationResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestDirectory(
        path: String?,
    ): RewriteGameCommandResult<ClientDirectoryListingResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestdirectory",
            payloadSerializer = ClientRequestDirectoryPayload.serializer(),
            payload = ClientRequestDirectoryPayload(path = path),
            responseSerializer = ClientDirectoryListingResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestFile(
        path: String?,
        name: String,
    ): RewriteGameCommandResult<ClientFileContentsResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestfile",
            payloadSerializer = ClientRequestFilePayload.serializer(),
            payload = ClientRequestFilePayload(
                path = path,
                name = name,
            ),
            responseSerializer = ClientFileContentsResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestSaveFile(
        path: String?,
        file: ClientStoredFile,
    ): RewriteGameCommandResult<ClientMutationAcceptedResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "savefile",
            payloadSerializer = ClientSaveFilePayload.serializer(),
            payload = ClientSaveFilePayload(
                path = path,
                file = file,
            ),
            responseSerializer = ClientMutationAcceptedResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestCompileFile(
        path: String?,
        name: String,
    ): RewriteGameCommandResult<ClientCompileFileResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "compilefile",
            payloadSerializer = ClientCompileFilePayload.serializer(),
            payload = ClientCompileFilePayload(
                path = path,
                name = name,
            ),
            responseSerializer = ClientCompileFileResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestDecompileFile(
        path: String?,
        name: String,
    ): RewriteGameCommandResult<ClientDecompileFileResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "decompilefile",
            payloadSerializer = ClientDecompileFilePayload.serializer(),
            payload = ClientDecompileFilePayload(
                path = path,
                name = name,
            ),
            responseSerializer = ClientDecompileFileResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestMakeBounty(
        anonymous: Boolean,
        target: String,
        type: Int,
        fileName: String?,
        folder: String?,
        iterations: Int,
        reward: Double,
    ): RewriteGameCommandResult<ClientBountyCreatedResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "makebounty",
            payloadSerializer = ClientMakeBountyPayload.serializer(),
            payload = ClientMakeBountyPayload(
                sourceIp = playerIp,
                anonymous = anonymous,
                target = target,
                type = type,
                fname = fileName,
                folder = folder,
                iterations = iterations,
                reward = reward,
            ),
            responseSerializer = ClientBountyCreatedResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestWebpage(
        targetIp: String,
        parameters: Map<String, String> = emptyMap(),
    ): RewriteGameCommandResult<ClientWebsiteRenderResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestwebpage",
            payloadSerializer = ClientRequestWebpagePayload.serializer(),
            payload = ClientRequestWebpagePayload(
                targetIp = targetIp,
                sourceIp = playerIp,
                parameters = parameters,
            ),
            responseSerializer = ClientWebsiteRenderResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp),
        )
    }

    internal suspend fun requestPage(): RewriteGameCommandResult<ClientPageEditorResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestpage",
            payloadSerializer = ClientRequestPagePayload.serializer(),
            payload = ClientRequestPagePayload(ip = playerIp),
            responseSerializer = ClientPageEditorResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun savePage(
        title: String,
        body: String,
    ): RewriteGameCommandResult<ClientSavePageResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "savepage",
            payloadSerializer = ClientSavePagePayload.serializer(),
            payload = ClientSavePagePayload(
                ip = playerIp,
                title = title,
                body = body,
            ),
            responseSerializer = ClientSavePageResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun submitWebpage(
        targetIp: String?,
        parameters: Map<String, String> = emptyMap(),
    ): RewriteGameCommandResult<ClientWebsiteRenderResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "submit",
            payloadSerializer = ClientSubmitWebpagePayload.serializer(),
            payload = ClientSubmitWebpagePayload(
                targetIp = targetIp,
                sourceIp = playerIp,
                parameters = parameters,
            ),
            responseSerializer = ClientWebsiteRenderResponse.serializer(),
            targetStateIds = listOfNotNull(playerIp, targetIp),
        )
    }

    internal suspend fun voteForWebsite(
        targetIp: String,
    ): RewriteGameCommandResult<ClientVoteResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "vote",
            payloadSerializer = ClientVotePayload.serializer(),
            payload = ClientVotePayload(
                targetIp = targetIp,
                sourceIp = playerIp,
            ),
            responseSerializer = ClientVoteResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp),
        )
    }

    internal suspend fun requestPurchase(
        targetIp: String,
        fileName: String,
        quantity: Int,
    ): RewriteGameCommandResult<ClientPurchaseResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestpurchase",
            payloadSerializer = ClientRequestPurchasePayload.serializer(),
            payload = ClientRequestPurchasePayload(
                targetIp = targetIp,
                sourceIp = playerIp,
                fileName = fileName,
                quantity = quantity,
            ),
            responseSerializer = ClientPurchaseResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp),
        )
    }

    internal fun exitWebpage(
        targetIp: String?,
    ) {
        val playerIp = authenticatedPlayerIp() ?: return
        workerScope.launch {
            runCatching {
                send(
                    RewriteService.GAME,
                    RewriteFrames.command(
                        commandId = UUID.randomUUID().toString(),
                        commandName = "exit",
                        targetGameStateIds = listOfNotNull(playerIp, targetIp),
                        payload = RewriteClientJson.encode(
                            ClientExitWebpagePayload.serializer(),
                            ClientExitWebpagePayload(
                                targetIp = targetIp,
                                sourceIp = playerIp,
                            ),
                        ),
                        expectsResponse = false,
                    ),
                )
            }
        }
    }

    fun submitLogin(email: String, password: CharArray) {
        val attemptId = synchronized(bootstrapLock) {
            loginAttemptId += 1
            loginAttemptId
        }
        val trimmedEmail = email.trim()
        val passwordSnapshot = password.copyOf()
        password.fill('\u0000')

        resetForNewLoginAttempt()

        if (trimmedEmail.isBlank()) {
            passwordSnapshot.fill('\u0000')
            store.showLoginScreen("Username is required.")
            return
        }

        store.beginGameBootstrap()
        activeLoginJob = workerScope.launch {
            val authResult = try {
                authGateway.authenticate(trimmedEmail, passwordSnapshot)
            } catch (throwable: Throwable) {
                RewriteLoginAuthResult.failure(throwable.message ?: throwable.javaClass.simpleName)
            } finally {
                passwordSnapshot.fill('\u0000')
            }

            if (!isCurrentAttempt(attemptId)) {
                return@launch
            }
            if (!authResult.isSuccessful) {
                failGameBootstrapAttempt(attemptId, authResult.error ?: "Authentication failed.")
                return@launch
            }

            bootstrapGameSession(
                attemptId = attemptId,
                authResult = authResult,
            )
        }
    }

    fun connect(service: RewriteService): RewriteServiceSession {
        sessions[service]?.let { return it }

        lateinit var sessionRef: RewriteServiceSession
        sessionRef = sessionGateway.open(service) { frame ->
            if (sessions[service] === sessionRef) {
                accept(service, frame)
            }
        }
        sessions[service] = sessionRef
        store.noteConnected(service)
        return sessionRef
    }

    suspend fun send(service: RewriteService, frame: FrameEnvelope) {
        connect(service).send(frame)
    }

    fun accept(service: RewriteService, frame: FrameEnvelope) {
        store.recordInboundFrame(service = service, frame = frame, receivedAt = clock())
        if (service == RewriteService.GAME) {
            gameCommandBroker.accept(frame)
            handleGameBootstrapFrame(frame)
        }
    }

    fun shutdown() {
        activeLoginJob?.cancel()
        activeLoginJob = null
        clearPendingBootstrap()
        closeFilePropertiesWindows()
        shellWindows.closeAll()
        shellDialogs.closeAll()
        sessions.keys.toList().forEach(::closeService)
        workerScope.cancel()
    }

    fun close() = shutdown()

    private suspend fun bootstrapGameSession(
        attemptId: Long,
        authResult: RewriteLoginAuthResult,
    ) {
        val session = try {
            connect(RewriteService.GAME)
        } catch (throwable: Throwable) {
            failGameBootstrapAttempt(attemptId, "Unable to connect to the rewrite game server.")
            return
        }

        val completion = CompletableDeferred<GameBootstrapOutcome>()
        synchronized(bootstrapLock) {
            if (!isCurrentAttemptLocked(attemptId)) {
                completion.cancel()
                return
            }
            pendingGameBootstrap = PendingGameBootstrap(
                attemptId = attemptId,
                completion = completion,
            )
        }

        val authRequest = RewriteFrames.authRequest(
            service = RewriteService.GAME,
            sessionTicket = authResult.sessionTicket.orEmpty(),
            clientBuild = gameConnectionConfig.clientBuild,
            playFabIdHint = authResult.playFabId,
            requestedIp = null,
        )
        try {
            session.send(authRequest)
        } catch (throwable: Throwable) {
            clearPendingBootstrap(attemptId)
            failGameBootstrapAttempt(attemptId, "Unable to connect to the rewrite game server.")
            return
        }

        val outcome = withTimeoutOrNull(gameConnectionConfig.bootstrapTimeout) {
            completion.await()
        } ?: GameBootstrapOutcome.Failed("The rewrite game server did not finish bootstrapping in time.")

        if (!isCurrentAttempt(attemptId)) {
            return
        }
        when (outcome) {
            GameBootstrapOutcome.Ready -> {
                activeLoginJob = null
                store.showDesktop()
            }

            is GameBootstrapOutcome.Failed -> {
                failGameBootstrapAttempt(attemptId, outcome.message)
            }
        }
    }

    private fun handleGameBootstrapFrame(frame: FrameEnvelope) {
        val completion: Pair<CompletableDeferred<GameBootstrapOutcome>, GameBootstrapOutcome>? = synchronized(bootstrapLock) {
            val pending = pendingGameBootstrap ?: return
            when {
                frame.auth_response?.rejected != null -> {
                    pendingGameBootstrap = null
                    pending.completion to GameBootstrapOutcome.Failed(
                        frame.auth_response!!.rejected!!.reason_text.ifBlank { "Authentication failed." },
                    )
                }

                frame.error != null -> {
                    pendingGameBootstrap = null
                    pending.completion to GameBootstrapOutcome.Failed(
                        sanitizeGameTransportFailure(
                            code = frame.error!!.code,
                            message = frame.error!!.message,
                        ),
                    )
                }

                frame.auth_response?.accepted != null -> {
                    pending.authAccepted = true
                    if (pending.snapshotReceived) {
                        pendingGameBootstrap = null
                        pending.completion to GameBootstrapOutcome.Ready
                    } else {
                        null
                    }
                }

                frame.snapshot != null -> {
                    pending.snapshotReceived = true
                    if (pending.authAccepted) {
                        pendingGameBootstrap = null
                        pending.completion to GameBootstrapOutcome.Ready
                    } else {
                        null
                    }
                }

                else -> null
            }
        }

        completion?.let { (deferred, outcome) ->
            if (!deferred.isCompleted) {
                deferred.complete(outcome)
            }
        }
    }

    private fun failGameBootstrapAttempt(attemptId: Long, message: String) {
        if (!isCurrentAttempt(attemptId)) {
            return
        }
        activeLoginJob = null
        clearPendingBootstrap(attemptId)
        closeService(RewriteService.GAME)
        store.showLoginScreen(message)
    }

    private fun resetForNewLoginAttempt() {
        activeLoginJob?.cancel()
        activeLoginJob = null
        clearPendingBootstrap()
        closeFilePropertiesWindows()
        shellWindows.closeAll()
        shellDialogs.closeAll()
        closeService(RewriteService.GAME)
        store.resetService(RewriteService.GAME)
        store.showLoginScreen(error = null)
    }

    private fun clearPendingBootstrap(expectedAttemptId: Long? = null) {
        val pending = synchronized(bootstrapLock) {
            val current = pendingGameBootstrap
            if (current != null && (expectedAttemptId == null || current.attemptId == expectedAttemptId)) {
                pendingGameBootstrap = null
                current
            } else {
                null
            }
        }
        if (pending != null && !pending.completion.isCompleted) {
            pending.completion.cancel()
        }
    }

    private fun closeService(service: RewriteService) {
        val session = sessions.remove(service) ?: return
        runCatching { session.close() }
        if (service == RewriteService.GAME) {
            gameCommandBroker.failAll("The rewrite game server closed the connection.", "SERVER_DISCONNECTED")
        }
        store.noteClosed(service)
    }

    private fun isCurrentAttempt(attemptId: Long): Boolean = synchronized(bootstrapLock) {
        isCurrentAttemptLocked(attemptId)
    }

    private fun isCurrentAttemptLocked(attemptId: Long): Boolean {
        return attemptId == loginAttemptId
    }

    private fun authenticatedPlayerIp(): String? {
        return snapshot().game.latestAcceptedSession?.playerIp?.takeIf { it.isNotBlank() }
    }

    internal fun currentAuthenticatedPlayerIp(): String? = authenticatedPlayerIp()

    internal fun openLocalFile(file: ClientStoredFile) {
        when (routeLocalFileTarget(file)) {
            RewriteLocalFileOpenTarget.SCRIPT_EDITOR -> openFileInScriptEditor(file)
            RewriteLocalFileOpenTarget.FILE_PROPERTIES -> openFileProperties(file)
        }
    }

    internal fun openFileProperties(file: ClientStoredFile) {
        val currentHost = shellHost ?: return
        val existing = filePropertiesWindowsByPath[file.path]
        if (existing != null && !existing.isClosed) {
            existing.updateFile(file)
            currentHost.focusWindow(existing)
            return
        }

        val frame = RewriteFilePropertiesWindow(file)
        frame.addInternalFrameListener(object : javax.swing.event.InternalFrameAdapter() {
            override fun internalFrameClosed(event: javax.swing.event.InternalFrameEvent) {
                filePropertiesWindowsByPath.remove(file.path, frame)
            }
        })
        filePropertiesWindowsByPath[file.path] = frame
        currentHost.showWindow(frame)
        currentHost.focusWindow(frame)
    }

    internal fun openFileInScriptEditor(file: ClientStoredFile) {
        shellWindows.open(RewriteShellCommand.SCRIPT_EDITOR)
        val editorWindow = shellWindows.openWindow(RewriteShellCommand.SCRIPT_EDITOR) as? RewriteScriptEditorWindow
            ?: return
        editorWindow.openFile(file)
        shellHost?.focusWindow(editorWindow)
    }

    private fun createShellWindow(
        command: RewriteShellCommand,
        preferredPort: Int?,
    ): JInternalFrame = when (command) {
        RewriteShellCommand.DEPOSIT -> RewriteDepositWindow(
            controller = this,
            preferredPort = preferredPort,
        )

        RewriteShellCommand.WITHDRAW -> RewriteWithdrawWindow(
            controller = this,
            preferredPort = preferredPort,
        )

        RewriteShellCommand.TRANSFER -> RewriteTransferWindow(
            controller = this,
            preferredPort = preferredPort,
        )

        RewriteShellCommand.HOME -> RewriteHomeWindow(
            controller = this,
        )

        RewriteShellCommand.PORT_MANAGEMENT -> RewritePortManagementWindow(
            controller = this,
            preferredPort = preferredPort,
            onOpenAuxiliaryWindow = { window ->
                shellHost?.let { currentHost ->
                    currentHost.showWindow(window)
                    currentHost.focusWindow(window)
                }
            },
            onFocusAuxiliaryWindow = { window ->
                shellHost?.focusWindow(window)
            },
        )

        RewriteShellCommand.EQUIPMENT_MANAGER -> RewriteEquipmentManagerWindow(
            controller = this,
            onOpenAuxiliaryWindow = { window ->
                shellHost?.let { currentHost ->
                    currentHost.showWindow(window)
                    currentHost.focusWindow(window)
                }
            },
            onFocusAuxiliaryWindow = { window ->
                shellHost?.focusWindow(window)
            },
        )

        RewriteShellCommand.FIREWALL_MANAGER -> RewriteFirewallManagerWindow(
            controller = this,
            onOpenAuxiliaryWindow = { window ->
                shellHost?.let { currentHost ->
                    currentHost.showWindow(window)
                    currentHost.focusWindow(window)
                }
            },
            onFocusAuxiliaryWindow = { window ->
                shellHost?.focusWindow(window)
            },
        )

        RewriteShellCommand.WATCH_MANAGER -> RewriteWatchManagerWindow(
            controller = this,
            onOpenAuxiliaryWindow = { window ->
                shellHost?.let { currentHost ->
                    currentHost.showWindow(window)
                    currentHost.focusWindow(window)
                }
            },
            onFocusAuxiliaryWindow = { window ->
                shellHost?.focusWindow(window)
            },
        )

        RewriteShellCommand.SCRIPT_EDITOR -> RewriteScriptEditorWindow(
            controller = this,
            onOpenAuxiliaryWindow = { window ->
                shellHost?.let { currentHost ->
                    currentHost.showWindow(window)
                    currentHost.focusWindow(window)
                }
            },
            onFocusAuxiliaryWindow = { window ->
                shellHost?.focusWindow(window)
            },
        )

        RewriteShellCommand.WEB_BROWSER,
        RewriteShellCommand.STORE -> RewriteWebBrowserWindow(
            controller = this,
            command = command,
        )

        RewriteShellCommand.SITE_EDITOR -> RewriteSiteEditorWindow(
            controller = this,
        )

        else -> RewritePlaceholderInternalFrame(command)
    }

    private fun closeFilePropertiesWindows() {
        val windows = filePropertiesWindowsByPath.values.toList()
        filePropertiesWindowsByPath.clear()
        windows.forEach { frame ->
            runCatching { frame.dispose() }
        }
    }

    private fun createShellDialog(
        command: RewriteShellCommand,
        ownerWindow: Window?,
    ): JDialog = when (command) {
        RewriteShellCommand.CREATE_BOUNTY -> RewriteCreateBountyDialog(
            owner = ownerWindow,
            controller = this,
            onOpenChooser = { chooser ->
                val currentHost = shellHost ?: return@RewriteCreateBountyDialog
                currentHost.showWindow(chooser)
                currentHost.focusWindow(chooser)
            },
            onFocusChooser = { chooser ->
                shellHost?.focusWindow(chooser)
            },
        )

        else -> error("No dialog registered for ${command.name}")
    }

    private data class PendingGameBootstrap(
        val attemptId: Long,
        val completion: CompletableDeferred<GameBootstrapOutcome>,
        var authAccepted: Boolean = false,
        var snapshotReceived: Boolean = false,
    )

    private sealed interface GameBootstrapOutcome {
        data object Ready : GameBootstrapOutcome

        data class Failed(
            val message: String,
        ) : GameBootstrapOutcome
    }
}
