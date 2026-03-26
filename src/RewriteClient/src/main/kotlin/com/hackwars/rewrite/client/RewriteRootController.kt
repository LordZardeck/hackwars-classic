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
import com.hackwars.rewrite.client.mvc.RewriteDialogBinding
import com.hackwars.rewrite.client.mvc.RewriteFrameBinding
import com.hackwars.rewrite.client.network.RewriteAttackWindow
import com.hackwars.rewrite.client.network.RewritePublicFtpWindow
import com.hackwars.rewrite.client.network.RewriteSetPublicFtpPasswordWindow
import com.hackwars.rewrite.client.network.RewriteShopFtpWindow
import com.hackwars.rewrite.client.network.RewriteNetworkWindow
import com.hackwars.rewrite.client.network.RewritePortScanWindow
import com.hackwars.rewrite.client.network.RewriteZombieAttackDialog
import com.hackwars.rewrite.client.network.RewriteZombieAttackWindow
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
import com.hackwars.rewrite.client.utilities.RewriteLogWindow
import com.hackwars.rewrite.client.utilities.RewritePreferencesWindow
import com.hackwars.rewrite.client.utilities.RewriteStartupUtilityCoordinator
import com.hackwars.rewrite.client.web.RewriteSiteEditorWindow
import com.hackwars.rewrite.client.web.RewriteWebBrowserWindow
import com.hackwars.rewrite.client.web.createTutorialWindowBinding
import com.hackwars.rewrite.clientmodel.RewriteClientBootstrapState
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.clientmodel.RewriteClientState
import com.hackwars.rewrite.clientmodel.RewriteClientStore
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameState
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameUiNotice
import com.hackwars.rewrite.clientmodel.RewriteServiceState
import com.hackwars.rewrite.protocol.ClientAttackCancelResponse
import com.hackwars.rewrite.protocol.ClientAttackStartResponse
import com.hackwars.rewrite.protocol.ClientBankTransactionResponse
import com.hackwars.rewrite.protocol.ClientBountyCreatedResponse
import com.hackwars.rewrite.protocol.ClientChangeDailyPayPayload
import com.hackwars.rewrite.protocol.ClientChangeDailyPayResponse
import com.hackwars.rewrite.protocol.ClientCompileFilePayload
import com.hackwars.rewrite.protocol.ClientCompileFileResponse
import com.hackwars.rewrite.protocol.ClientDecompileFilePayload
import com.hackwars.rewrite.protocol.ClientDecompileFileResponse
import com.hackwars.rewrite.protocol.ClientDepositPayload
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientExitWebpagePayload
import com.hackwars.rewrite.protocol.ClientFileContentsResponse
import com.hackwars.rewrite.protocol.ClientFilesystemState
import com.hackwars.rewrite.protocol.ClientFinalizeCancelledPayload
import com.hackwars.rewrite.protocol.ClientFinalizeCancelledResponse
import com.hackwars.rewrite.protocol.ClientFtpTransferResponse
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientGetFilePayload
import com.hackwars.rewrite.protocol.ClientHelpTopicListResponse
import com.hackwars.rewrite.protocol.ClientHookValue
import com.hackwars.rewrite.protocol.ClientLogState
import com.hackwars.rewrite.protocol.ClientMalGetPayload
import com.hackwars.rewrite.protocol.ClientNetworkState
import com.hackwars.rewrite.protocol.ClientChangeNetworkPayload
import com.hackwars.rewrite.protocol.ClientNetworkSwitchResponse
import com.hackwars.rewrite.protocol.ClientPreferenceState
import com.hackwars.rewrite.protocol.ClientRequestAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestCancelAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestScanPayload
import com.hackwars.rewrite.protocol.ClientRequestHelpTopicListPayload
import com.hackwars.rewrite.protocol.ClientRequestZombieAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestZombieCancelAttackPayload
import com.hackwars.rewrite.protocol.ClientScanResponse
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
import com.hackwars.rewrite.protocol.ClientPutFilePayload
import com.hackwars.rewrite.protocol.ClientRequestDirectoryPayload
import com.hackwars.rewrite.protocol.ClientRequestFilePayload
import com.hackwars.rewrite.protocol.ClientRequestSecondaryDirectoryPayload
import com.hackwars.rewrite.protocol.ClientRequestPagePayload
import com.hackwars.rewrite.protocol.ClientRequestPurchasePayload
import com.hackwars.rewrite.protocol.ClientRequestWebpagePayload
import com.hackwars.rewrite.protocol.ClientPurchaseResponse
import com.hackwars.rewrite.protocol.ClientSaveFilePayload
import com.hackwars.rewrite.protocol.ClientSavePagePayload
import com.hackwars.rewrite.protocol.ClientSavePageResponse
import com.hackwars.rewrite.protocol.ClientSellFilePayload
import com.hackwars.rewrite.protocol.ClientSellFileResponse
import com.hackwars.rewrite.protocol.ClientSetFtpPasswordPayload
import com.hackwars.rewrite.protocol.ClientSetFtpPasswordResponse
import com.hackwars.rewrite.protocol.ClientSetPreferencePayload
import com.hackwars.rewrite.protocol.ClientSetPreferenceResponse
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientSecondaryDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientSubmitWebpagePayload
import com.hackwars.rewrite.protocol.ClientTutorialResponse
import com.hackwars.rewrite.protocol.ClientWatchListResponse
import com.hackwars.rewrite.protocol.ClientWatchManagerState
import com.hackwars.rewrite.protocol.ClientWatchMutationResponse
import com.hackwars.rewrite.protocol.ClientFetchWatchesPayload
import com.hackwars.rewrite.protocol.ClientInstallWatchPayload
import com.hackwars.rewrite.protocol.ClientRequestTutorialPayload
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
import com.hackwars.rewrite.protocol.ClientZombieAttackCancelResponse
import com.hackwars.rewrite.protocol.ClientZombieAttackStartResponse
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import java.awt.Window
import javax.swing.JDialog
import javax.swing.JInternalFrame
import javax.swing.SwingUtilities
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
    private val shellWindows = RewriteShellWindowCoordinator(::createShellWindowBinding)
    private val shellDialogs = RewriteShellDialogCoordinator(::createShellDialogBinding)
    private val startupUtilityCoordinator = RewriteStartupUtilityCoordinator { command ->
        launchShellCommand(command)
    }
    private val bootstrapLock = Any()
    private val filePropertiesWindowsByPath = mutableMapOf<String, RewriteFilePropertiesWindow>()
    private val zombieAttackWindowsByKey = mutableMapOf<String, RewriteZombieAttackWindow>()
    private val sessionLock = Any()
    private var loginAttemptId: Long = 0
    private var activeLoginJob: Job? = null
    private var pendingGameBootstrap: PendingGameBootstrap? = null
    private var shellHost: RewriteShellWindowHost? = null
    private val attackWindowHandleLock = Any()
    private var nextAttackWindowHandle: Int = 1

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

    fun gamePreferenceState(): ClientPreferenceState? = gameShellState()?.preferences

    fun gamePreferenceStateSelector(): Flow<ClientPreferenceState?> {
        return store.servicePreferenceStateSelector(RewriteService.GAME)
    }

    fun gameLogState(): ClientLogState? = gameShellState()?.logs

    fun gameLogStateSelector(): Flow<ClientLogState?> {
        return store.serviceLogStateSelector(RewriteService.GAME)
    }

    fun gameNetworkState(): ClientNetworkState? = gameShellState()?.network

    fun gameNetworkStateSelector(): Flow<ClientNetworkState?> {
        return store.serviceNetworkStateSelector(RewriteService.GAME)
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
        maybeLaunchStartupUtilities()
    }

    internal fun attachDialogHost(host: RewriteShellDialogHost?) {
        shellDialogs.attachHost(host)
    }

    fun launchShellCommand(
        command: RewriteShellCommand,
        preferredPort: Int? = null,
    ) {
        if (command == RewriteShellCommand.CREATE_BOUNTY || command == RewriteShellCommand.ZOMBIE_ATTACK) {
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

    internal suspend fun requestChangeNetwork(
        targetNetwork: String?,
    ): RewriteGameCommandResult<ClientNetworkSwitchResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "changenetwork",
            payloadSerializer = ClientChangeNetworkPayload.serializer(),
            payload = ClientChangeNetworkPayload(
                ip = playerIp,
                network = targetNetwork,
            ),
            responseSerializer = ClientNetworkSwitchResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestScan(
        targetIp: String,
    ): RewriteGameCommandResult<ClientScanResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestscan",
            payloadSerializer = ClientRequestScanPayload.serializer(),
            payload = ClientRequestScanPayload(
                ip = playerIp,
                targetIp = targetIp,
            ),
            responseSerializer = ClientScanResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp),
        )
    }

    internal suspend fun requestAttack(
        targetIp: String,
        targetPort: Int,
        sourcePort: Int,
        secondaryPorts: List<Int>,
        scripts: List<List<String?>>,
        extraInfo: List<ClientHookValue>,
        windowHandle: Int,
    ): RewriteGameCommandResult<ClientAttackStartResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestattack",
            payloadSerializer = ClientRequestAttackPayload.serializer(),
            payload = ClientRequestAttackPayload(
                targetIp = targetIp,
                targetPort = targetPort,
                sourceIp = playerIp,
                sourcePort = sourcePort,
                secondaryPorts = secondaryPorts,
                scripts = scripts,
                extraInfo = extraInfo,
                windowHandle = windowHandle,
            ),
            responseSerializer = ClientAttackStartResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp),
        )
    }

    internal suspend fun requestCancelAttack(
        sourcePort: Int,
    ): RewriteGameCommandResult<ClientAttackCancelResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestcancelattack",
            payloadSerializer = ClientRequestCancelAttackPayload.serializer(),
            payload = ClientRequestCancelAttackPayload(
                ip = playerIp,
                port = sourcePort,
            ),
            responseSerializer = ClientAttackCancelResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestZombieAttack(
        targetIp: String,
        targetPort: Int,
        zombieIp: String,
        zombiePort: Int,
        secondaryPorts: List<Int>,
        extraInfo: List<ClientHookValue>,
    ): RewriteGameCommandResult<ClientZombieAttackStartResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestzombieattack",
            payloadSerializer = ClientRequestZombieAttackPayload.serializer(),
            payload = ClientRequestZombieAttackPayload(
                targetIp = targetIp,
                targetPort = targetPort,
                sourceIp = zombieIp,
                sourcePort = zombiePort,
                secondaryPorts = secondaryPorts,
                scripts = null,
                extraInfo = extraInfo,
                parentIp = playerIp,
            ),
            responseSerializer = ClientZombieAttackStartResponse.serializer(),
            targetStateIds = listOf(playerIp, zombieIp, targetIp).distinct(),
        )
    }

    internal suspend fun requestZombieCancelAttack(
        zombieIp: String,
        zombiePort: Int,
    ): RewriteGameCommandResult<ClientZombieAttackCancelResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestzombiecancelattack",
            payloadSerializer = ClientRequestZombieCancelAttackPayload.serializer(),
            payload = ClientRequestZombieCancelAttackPayload(
                ip = playerIp,
                port = zombiePort,
                targetIp = zombieIp,
            ),
            responseSerializer = ClientZombieAttackCancelResponse.serializer(),
            targetStateIds = listOf(playerIp, zombieIp).distinct(),
        )
    }

    internal suspend fun requestSecondaryDirectory(
        path: String?,
        targetIp: String,
        portNumber: Int,
    ): RewriteGameCommandResult<ClientSecondaryDirectoryListingResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requestsecondarydirectory",
            payloadSerializer = ClientRequestSecondaryDirectoryPayload.serializer(),
            payload = ClientRequestSecondaryDirectoryPayload(
                path = path,
                targetIp = targetIp,
                port = portNumber,
            ),
            responseSerializer = ClientSecondaryDirectoryListingResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp).distinct(),
        )
    }

    internal suspend fun requestHelpTopicList(
        topicGroup: String? = null,
    ): RewriteGameCommandResult<ClientHelpTopicListResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requesthelptopiclist",
            payloadSerializer = ClientRequestHelpTopicListPayload.serializer(),
            payload = ClientRequestHelpTopicListPayload(topicGroup = topicGroup),
            responseSerializer = ClientHelpTopicListResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestTutorial(
        tutorialId: String? = null,
    ): RewriteGameCommandResult<ClientTutorialResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "requesttutorial",
            payloadSerializer = ClientRequestTutorialPayload.serializer(),
            payload = ClientRequestTutorialPayload(tutorialId = tutorialId),
            responseSerializer = ClientTutorialResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestSetPreference(
        key: String,
        value: String,
    ): RewriteGameCommandResult<ClientSetPreferenceResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "setpreferences",
            payloadSerializer = ClientSetPreferencePayload.serializer(),
            payload = ClientSetPreferencePayload(
                key = key,
                value = value,
            ),
            responseSerializer = ClientSetPreferenceResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun sellFile(
        path: String?,
        fileName: String,
        compileCost: Double? = null,
        quantity: Int? = null,
    ): RewriteGameCommandResult<ClientSellFileResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "sellfile",
            payloadSerializer = ClientSellFilePayload.serializer(),
            payload = ClientSellFilePayload(
                ip = playerIp,
                location = path,
                fileName = fileName,
                compileCost = compileCost,
                quantity = quantity,
            ),
            responseSerializer = ClientSellFileResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestPutFile(
        targetIp: String,
        portNumber: Int,
        fileName: String,
        localPath: String?,
        remotePath: String?,
        password: String? = null,
        quantity: Int? = null,
    ): RewriteGameCommandResult<ClientFtpTransferResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "put",
            payloadSerializer = ClientPutFilePayload.serializer(),
            payload = ClientPutFilePayload(
                ip = playerIp,
                port = portNumber,
                name = fileName,
                fetchPath = localPath,
                putPath = remotePath,
                targetIp = targetIp,
                password = password,
                quantity = quantity,
            ),
            responseSerializer = ClientFtpTransferResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp).distinct(),
        )
    }

    internal suspend fun requestGetFile(
        targetIp: String,
        portNumber: Int,
        fileName: String,
        localPath: String?,
        remotePath: String?,
        password: String? = null,
        quantity: Int? = null,
    ): RewriteGameCommandResult<ClientFtpTransferResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "get",
            payloadSerializer = ClientGetFilePayload.serializer(),
            payload = ClientGetFilePayload(
                ip = playerIp,
                port = portNumber,
                name = fileName,
                fetchPath = localPath,
                putPath = remotePath,
                targetIp = targetIp,
                password = password,
                quantity = quantity,
            ),
            responseSerializer = ClientFtpTransferResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp).distinct(),
        )
    }

    internal suspend fun requestMalGet(
        targetIp: String,
        portNumber: Int,
        fileName: String?,
        remotePath: String?,
        attackPort: Int,
        localPath: String? = null,
    ): RewriteGameCommandResult<ClientFtpTransferResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "malget",
            payloadSerializer = ClientMalGetPayload.serializer(),
            payload = ClientMalGetPayload(
                ip = targetIp,
                port = portNumber,
                name = fileName,
                fetchPath = remotePath,
                putPath = localPath,
                targetIp = playerIp,
                attackPort = attackPort,
            ),
            responseSerializer = ClientFtpTransferResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp).distinct(),
        )
    }

    internal suspend fun requestSetFtpPassword(
        password: String?,
    ): RewriteGameCommandResult<ClientSetFtpPasswordResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "setftppassword",
            payloadSerializer = ClientSetFtpPasswordPayload.serializer(),
            payload = ClientSetFtpPasswordPayload(
                ip = playerIp,
                password = password,
            ),
            responseSerializer = ClientSetFtpPasswordResponse.serializer(),
            targetStateIds = listOf(playerIp),
        )
    }

    internal suspend fun requestChangeDailyPay(
        targetIp: String,
        targetPort: Int,
        revenueTargetIp: String,
        attackPort: Int,
    ): RewriteGameCommandResult<ClientChangeDailyPayResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "changedailypay",
            payloadSerializer = ClientChangeDailyPayPayload.serializer(),
            payload = ClientChangeDailyPayPayload(
                ip = targetIp,
                port = targetPort,
                change = revenueTargetIp,
                finalizeIp = playerIp,
                attackPort = attackPort,
            ),
            responseSerializer = ClientChangeDailyPayResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp),
        )
    }

    internal suspend fun requestFinalizeCancelled(
        targetIp: String,
        targetPort: Int,
    ): RewriteGameCommandResult<ClientFinalizeCancelledResponse> {
        val playerIp = authenticatedPlayerIp()
            ?: return RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
        return gameCommandBroker.request(
            commandName = "finalizecancelled",
            payloadSerializer = ClientFinalizeCancelledPayload.serializer(),
            payload = ClientFinalizeCancelledPayload(
                ip = playerIp,
                targetIp = targetIp,
                targetPort = targetPort,
            ),
            responseSerializer = ClientFinalizeCancelledResponse.serializer(),
            targetStateIds = listOf(playerIp, targetIp),
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
        synchronized(sessionLock) {
            sessions[service]?.let { return it }

            lateinit var sessionRef: RewriteServiceSession
            sessionRef = sessionGateway.open(service) { frame ->
                val stillActive = synchronized(sessionLock) {
                    sessions[service] === sessionRef
                }
                if (stillActive) {
                    accept(service, frame)
                }
            }
            sessions[service] = sessionRef
            store.noteConnected(service)
            return sessionRef
        }
    }

    suspend fun send(service: RewriteService, frame: FrameEnvelope) {
        connect(service).send(frame)
    }

    fun accept(service: RewriteService, frame: FrameEnvelope) {
        store.recordInboundFrame(service = service, frame = frame, receivedAt = clock())
        if (service == RewriteService.GAME) {
            gameCommandBroker.accept(frame)
            handleGameBootstrapFrame(frame)
            maybeLaunchStartupUtilities()
        }
    }

    fun shutdown() {
        activeLoginJob?.cancel()
        activeLoginJob = null
        clearPendingBootstrap()
        startupUtilityCoordinator.reset()
        closeFilePropertiesWindows()
        closeZombieAttackWindows()
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
                startupUtilityCoordinator.noteAuthenticatedSessionReady(authenticatedPlayerIp())
                store.showDesktop()
                maybeLaunchStartupUtilities()
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
        startupUtilityCoordinator.reset()
        closeFilePropertiesWindows()
        closeZombieAttackWindows()
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
        val session = synchronized(sessionLock) {
            sessions.remove(service)
        } ?: return
        runCatching { session.close() }
        if (service == RewriteService.GAME) {
            startupUtilityCoordinator.reset()
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

    private fun maybeLaunchStartupUtilities() {
        val currentShellHost = shellHost ?: return
        val currentRoute = route()
        val currentPlayerIp = authenticatedPlayerIp()
        val currentShellState = gameShellState()
        SwingUtilities.invokeLater {
            if (shellHost !== currentShellHost) {
                return@invokeLater
            }
            startupUtilityCoordinator.maybeLaunch(
                route = currentRoute,
                playerIp = currentPlayerIp,
                shellState = currentShellState,
            )
        }
    }

    internal fun allocateAttackWindowHandle(): Int = synchronized(attackWindowHandleLock) {
        val current = nextAttackWindowHandle
        nextAttackWindowHandle = if (nextAttackWindowHandle == Int.MAX_VALUE) 1 else nextAttackWindowHandle + 1
        current
    }

    internal fun openZombieAttackPane(
        zombieIp: String,
        zombiePort: Int,
    ) {
        val currentHost = shellHost ?: return
        val key = zombieAttackWindowKey(zombieIp, zombiePort)
        val existing = zombieAttackWindowsByKey[key]
        if (existing != null && !existing.isClosed) {
            currentHost.focusWindow(existing)
            return
        }

        val window = RewriteZombieAttackWindow(
            controller = this,
            zombieIp = zombieIp,
            zombiePort = zombiePort,
        )
        window.addInternalFrameListener(object : javax.swing.event.InternalFrameAdapter() {
            override fun internalFrameClosed(event: javax.swing.event.InternalFrameEvent) {
                zombieAttackWindowsByKey.remove(key, window)
            }
        })
        zombieAttackWindowsByKey[key] = window
        currentHost.showWindow(window)
        currentHost.focusWindow(window)
    }

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

        RewriteShellCommand.SHOP_FTP -> RewriteShopFtpWindow(
            controller = this,
        )

        RewriteShellCommand.PUBLIC_FTP -> RewritePublicFtpWindow(
            controller = this,
        )

        RewriteShellCommand.SET_PUBLIC_FTP_PASSWORD -> RewriteSetPublicFtpPasswordWindow(
            controller = this,
        )

        RewriteShellCommand.NETWORK -> RewriteNetworkWindow(
            controller = this,
        )

        RewriteShellCommand.PORT_SCAN -> RewritePortScanWindow(
            controller = this,
        )

        RewriteShellCommand.ATTACK_PORT,
        RewriteShellCommand.REDIRECT_PORT -> RewriteAttackWindow(
            controller = this,
            command = command,
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

        RewriteShellCommand.LOG_WINDOW -> RewriteLogWindow(
            controller = this,
        )

        RewriteShellCommand.PREFERENCES -> RewritePreferencesWindow(
            controller = this,
        )

        else -> RewritePlaceholderInternalFrame(command)
    }

    private fun createShellWindowBinding(
        command: RewriteShellCommand,
        preferredPort: Int?,
    ): RewriteFrameBinding {
        if (command == RewriteShellCommand.TUTORIAL_FIRST_ATTACK) {
            return createTutorialWindowBinding(
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
        }
        return RewriteFrameBinding(
            frame = createShellWindow(command, preferredPort),
        )
    }

    private fun closeFilePropertiesWindows() {
        val windows = filePropertiesWindowsByPath.values.toList()
        filePropertiesWindowsByPath.clear()
        windows.forEach { frame ->
            runCatching { frame.dispose() }
        }
    }

    private fun closeZombieAttackWindows() {
        val windows = zombieAttackWindowsByKey.values.toList()
        zombieAttackWindowsByKey.clear()
        windows.forEach { frame ->
            runCatching { frame.dispose() }
        }
    }

    private fun zombieAttackWindowKey(
        zombieIp: String,
        zombiePort: Int,
    ): String = "$zombieIp:$zombiePort"

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

        RewriteShellCommand.ZOMBIE_ATTACK -> RewriteZombieAttackDialog(
            owner = ownerWindow,
            controller = this,
        )

        else -> error("No dialog registered for ${command.name}")
    }

    private fun createShellDialogBinding(
        command: RewriteShellCommand,
        ownerWindow: Window?,
    ): RewriteDialogBinding {
        return RewriteDialogBinding(
            dialog = createShellDialog(command, ownerWindow),
        )
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
