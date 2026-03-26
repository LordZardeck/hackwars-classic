package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.economy.RewriteCreateBountyDialog
import com.hackwars.rewrite.client.economy.RewriteDepositWindow
import com.hackwars.rewrite.client.economy.RewriteTransferWindow
import com.hackwars.rewrite.client.economy.RewriteWithdrawWindow
import com.hackwars.rewrite.client.files.RewriteHomeWindow
import com.hackwars.rewrite.client.shell.RewritePlaceholderInternalFrame
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.shell.RewriteShellDialogCoordinator
import com.hackwars.rewrite.client.shell.RewriteShellDialogHost
import com.hackwars.rewrite.client.shell.RewriteShellWindowCoordinator
import com.hackwars.rewrite.client.shell.RewriteShellWindowHost
import com.hackwars.rewrite.clientmodel.RewriteClientBootstrapState
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.clientmodel.RewriteClientState
import com.hackwars.rewrite.clientmodel.RewriteClientStore
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameState
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameUiNotice
import com.hackwars.rewrite.clientmodel.RewriteServiceState
import com.hackwars.rewrite.protocol.ClientBankTransactionResponse
import com.hackwars.rewrite.protocol.ClientBountyCreatedResponse
import com.hackwars.rewrite.protocol.ClientDepositPayload
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientFilesystemState
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientMakeBountyPayload
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.ClientRequestDirectoryPayload
import com.hackwars.rewrite.protocol.ClientTransferPayload
import com.hackwars.rewrite.protocol.ClientTransferResponse
import com.hackwars.rewrite.protocol.ClientWithdrawPayload
import com.hackwars.rewrite.protocol.RewriteFrames
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

        else -> RewritePlaceholderInternalFrame(command)
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
