package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.shell.RewriteShellWindowCoordinator
import com.hackwars.rewrite.client.shell.RewriteShellWindowHost
import com.hackwars.rewrite.clientmodel.RewriteClientBootstrapState
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.clientmodel.RewriteClientState
import com.hackwars.rewrite.clientmodel.RewriteClientStore
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameState
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameUiNotice
import com.hackwars.rewrite.clientmodel.RewriteServiceState
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
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
    private val shellWindows = RewriteShellWindowCoordinator()
    private val bootstrapLock = Any()
    private var loginAttemptId: Long = 0
    private var activeLoginJob: Job? = null
    private var pendingGameBootstrap: PendingGameBootstrap? = null

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
        shellWindows.attachHost(host)
    }

    fun launchShellCommand(command: RewriteShellCommand) {
        shellWindows.open(command)
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
            handleGameBootstrapFrame(frame)
        }
    }

    fun shutdown() {
        activeLoginJob?.cancel()
        activeLoginJob = null
        clearPendingBootstrap()
        shellWindows.closeAll()
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
                        sanitizeTransportFailure(
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
        store.noteClosed(service)
    }

    private fun isCurrentAttempt(attemptId: Long): Boolean = synchronized(bootstrapLock) {
        isCurrentAttemptLocked(attemptId)
    }

    private fun isCurrentAttemptLocked(attemptId: Long): Boolean {
        return attemptId == loginAttemptId
    }

    private fun sanitizeTransportFailure(code: String, message: String): String = when (code) {
        "MALFORMED_FRAME" -> "The rewrite game server sent an invalid response."
        "SERVER_DISCONNECTED" -> "The rewrite game server closed the connection."
        "CLIENT_IO_ERROR" -> "The rewrite game connection failed."
        else -> message.ifBlank { "The rewrite game connection failed." }
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
