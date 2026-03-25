package com.hackwars.rewrite.testkit

import com.hackwars.rewrite.protocol.ConnectionLifecycleState
import com.hackwars.rewrite.protocol.ConnectionStateMachine
import com.hackwars.rewrite.protocol.DisconnectReason
import com.hackwars.rewrite.protocol.FrameCodec
import com.hackwars.rewrite.protocol.FramePayloadType
import com.hackwars.rewrite.protocol.MalformedFrameException
import com.hackwars.rewrite.protocol.OversizedFrameException
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.protocol.SessionTicketAuthRequest
import com.hackwars.rewrite.protocol.SessionTicketVerifier
import com.hackwars.rewrite.protocol.VerifiedSession
import com.hackwars.rewrite.protocol.payloadType
import hackwars.rewrite.v1.AuthRequest
import hackwars.rewrite.v1.CommandEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import hackwars.rewrite.v1.PingEnvelope
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class InMemoryAuthenticatedSession(
    val connectionId: String,
    val service: RewriteService,
    val verifiedSession: VerifiedSession,
)

data class ConnectionLogEntry(
    val connectionId: String,
    val service: RewriteService,
    val reason: DisconnectReason,
    val detail: String,
    val occurredAt: Instant,
)

class InMemoryConnectionLog {
    private val entries = mutableListOf<ConnectionLogEntry>()

    fun record(entry: ConnectionLogEntry) {
        entries += entry
    }

    fun snapshot(): List<ConnectionLogEntry> = entries.toList()
}

interface RewriteServiceAdapter {
    val service: RewriteService

    suspend fun onSessionStarted(session: InMemoryAuthenticatedSession): List<FrameEnvelope> = emptyList()

    suspend fun onSessionEnded(session: InMemoryAuthenticatedSession) = Unit

    suspend fun onCommand(
        session: InMemoryAuthenticatedSession,
        command: CommandEnvelope,
    ): List<FrameEnvelope> = emptyList()

    suspend fun onPing(
        session: InMemoryAuthenticatedSession,
        ping: PingEnvelope,
        now: Instant,
    ): List<FrameEnvelope> = listOf(
        RewriteFrames.ping(
            connectionId = session.connectionId,
            sentAtEpochMillis = ping.sent_at_epoch_millis,
            acknowledgedAtEpochMillis = now.toEpochMilli(),
        ),
    )
}

class InMemoryClientConnection internal constructor(
    val connectionId: String,
    private val harness: InMemoryRewriteServiceHarness,
    private val outbound: ReceiveChannel<FrameEnvelope>,
    private val stateMachine: ConnectionStateMachine,
) {
    suspend fun send(frame: FrameEnvelope) {
        harness.receiveEncoded(this, FrameCodec.encode(frame))
    }

    suspend fun sendEncoded(encodedFrame: ByteArray) {
        harness.receiveEncoded(this, encodedFrame)
    }

    suspend fun awaitFrame(timeout: Duration = 1.seconds): FrameEnvelope {
        return withTimeout(timeout) {
            outbound.receive()
        }
    }

    fun drainFrames(): List<FrameEnvelope> {
        val drained = mutableListOf<FrameEnvelope>()
        while (true) {
            val next = outbound.tryReceive().getOrNull() ?: break
            drained += next
        }
        return drained
    }

    fun state(): ConnectionLifecycleState = stateMachine.state

    fun disconnectReason(): DisconnectReason? = stateMachine.disconnectReason

    fun close() {
        harness.disconnect(this, DisconnectReason.CLIENT_CLOSED, "Client closed test connection.")
    }
}

class InMemoryRewriteServiceHarness(
    private val adapter: RewriteServiceAdapter,
    private val verifier: SessionTicketVerifier,
    private val scope: CoroutineScope,
    private val timeoutPolicy: ProtocolTimeoutPolicy = ProtocolTimeoutPolicy(),
    private val clock: () -> Instant = { Instant.now() },
    private val connectionLog: InMemoryConnectionLog = InMemoryConnectionLog(),
) {
    private val nextConnectionNumber = AtomicInteger(1)
    private val connections = linkedMapOf<String, ConnectionContext>()

    fun connect(connectionPrefix: String = adapter.service.name.lowercase()): InMemoryClientConnection {
        val connectionId = "$connectionPrefix-${nextConnectionNumber.getAndIncrement()}"
        val outbound = Channel<FrameEnvelope>(capacity = Channel.UNLIMITED)
        val stateMachine = ConnectionStateMachine(
            connectedAt = clock(),
            timeoutPolicy = timeoutPolicy,
        )
        val context = ConnectionContext(
            connectionId = connectionId,
            outbound = outbound,
            stateMachine = stateMachine,
        )
        connections[connectionId] = context
        startTimeoutMonitor(context)
        return InMemoryClientConnection(
            connectionId = connectionId,
            harness = this,
            outbound = outbound,
            stateMachine = stateMachine,
        )
    }

    suspend fun push(
        connectionId: String,
        frame: FrameEnvelope,
    ) {
        connections[connectionId]?.outbound?.send(frame)
    }

    internal suspend fun receiveEncoded(
        connection: InMemoryClientConnection,
        encodedFrame: ByteArray,
    ) {
        val context = connections[connection.connectionId] ?: return
        context.mutex.withLock {
            processIncomingFrame(context, encodedFrame)
        }
    }

    internal fun disconnect(
        connection: InMemoryClientConnection,
        reason: DisconnectReason,
        detail: String,
    ) {
        val context = connections[connection.connectionId] ?: return
        closeContext(context, reason, detail)
    }

    fun log(): InMemoryConnectionLog = connectionLog

    fun sweepTimeouts() {
        connections.values.toList().forEach { context ->
            val reason = context.stateMachine.timeoutReason(clock()) ?: return@forEach
            closeContext(context, reason, "Connection timed out while in ${context.stateMachine.state}.")
        }
    }

    private suspend fun processIncomingFrame(
        context: ConnectionContext,
        encodedFrame: ByteArray,
    ) {
        val frame = try {
            FrameCodec.decode(encodedFrame, timeoutPolicy.maxFrameBytes)
        } catch (exception: OversizedFrameException) {
            closeContext(context, DisconnectReason.OVERSIZED_FRAME, exception.message ?: "Oversized frame.")
            return
        } catch (exception: MalformedFrameException) {
            closeContext(context, DisconnectReason.MALFORMED_FRAME, exception.message ?: "Malformed frame.")
            return
        }

        val now = clock()
        context.stateMachine.noteInbound(now)
        val payloadType = frame.payloadType()
        val authRequirement = context.stateMachine.requireAuth(payloadType)
        if (authRequirement != null) {
            closeContext(context, authRequirement, "Received $payloadType before auth.")
            return
        }

        when (payloadType) {
            FramePayloadType.AUTH_REQUEST -> handleAuth(context, frame.auth_request ?: return)
            FramePayloadType.COMMAND -> handleCommand(context, frame.command ?: return)
            FramePayloadType.PING -> handlePing(context, frame.ping ?: return)
            else -> closeContext(context, DisconnectReason.UNSUPPORTED_FRAME, "Unsupported inbound frame: $payloadType")
        }
    }

    private suspend fun handleAuth(
        context: ConnectionContext,
        authRequest: AuthRequest,
    ) {
        val service = runCatching { RewriteService.fromProto(authRequest.service) }.getOrElse {
            context.outbound.send(RewriteFrames.authRejected("UNSUPPORTED_SERVICE", it.message ?: "Unknown service."))
            closeContext(context, DisconnectReason.INVALID_AUTH, "Unsupported service kind ${authRequest.service}.")
            return
        }

        if (service != adapter.service) {
            context.outbound.send(
                RewriteFrames.authRejected(
                    reasonCode = "WRONG_SERVICE",
                    reasonText = "Expected ${adapter.service} but received $service.",
                ),
            )
            closeContext(context, DisconnectReason.INVALID_AUTH, "Service mismatch during auth.")
            return
        }

        val encodedAuthSize = AuthRequest.ADAPTER.encode(authRequest).size
        if (encodedAuthSize > timeoutPolicy.maxAuthPayloadBytes) {
            closeContext(
                context,
                DisconnectReason.OVERSIZED_AUTH_PAYLOAD,
                "Auth payload exceeded ${timeoutPolicy.maxAuthPayloadBytes} bytes.",
            )
            return
        }

        if (authRequest.session_ticket.isBlank()) {
            context.outbound.send(RewriteFrames.authRejected("MISSING_SESSION", "Session ticket is required."))
            closeContext(context, DisconnectReason.INVALID_AUTH, "Missing session ticket.")
            return
        }

        val verifiedSession = verifier.verify(
            SessionTicketAuthRequest(
                service = service,
                playFabIdHint = authRequest.playfab_id_hint.ifBlank { null },
                sessionTicket = authRequest.session_ticket,
                requestedIp = authRequest.requested_ip.ifBlank { null },
                clientBuild = authRequest.client_build,
            ),
        )

        if (verifiedSession == null) {
            context.outbound.send(RewriteFrames.authRejected("INVALID_SESSION", "Session ticket could not be verified."))
            closeContext(context, DisconnectReason.INVALID_AUTH, "Session verification failed.")
            return
        }

        context.verifiedSession = InMemoryAuthenticatedSession(
            connectionId = context.connectionId,
            service = service,
            verifiedSession = verifiedSession,
        )
        context.stateMachine.authenticate(clock())
        context.outbound.send(
            RewriteFrames.authAccepted(
                connectionId = context.connectionId,
                playFabId = verifiedSession.playFabId,
                playerIp = verifiedSession.playerIp,
                heartbeatInterval = verifiedSession.heartbeatInterval,
                sessionStartedAt = verifiedSession.sessionStartedAt,
            ),
        )
        context.stateMachine.activate(clock())
        adapter.onSessionStarted(context.verifiedSession!!).forEach { context.outbound.send(it) }
    }

    private suspend fun handleCommand(
        context: ConnectionContext,
        command: CommandEnvelope,
    ) {
        val session = context.verifiedSession
            ?: run {
                closeContext(context, DisconnectReason.UNSUPPORTED_FRAME, "Received command without authenticated session.")
                return
            }
        adapter.onCommand(session, command).forEach { context.outbound.send(it) }
    }

    private suspend fun handlePing(
        context: ConnectionContext,
        ping: PingEnvelope,
    ) {
        val session = context.verifiedSession
            ?: run {
                closeContext(context, DisconnectReason.UNSUPPORTED_FRAME, "Received ping without authenticated session.")
                return
            }
        adapter.onPing(session, ping, clock()).forEach { context.outbound.send(it) }
    }

    private fun startTimeoutMonitor(context: ConnectionContext) {
        scope.launch {
            while (context.stateMachine.state != ConnectionLifecycleState.CLOSED) {
                delay(250)
                val reason = context.stateMachine.timeoutReason(clock()) ?: continue
                closeContext(context, reason, "Connection timed out while in ${context.stateMachine.state}.")
                break
            }
        }
    }

    private fun closeContext(
        context: ConnectionContext,
        reason: DisconnectReason,
        detail: String,
    ) {
        if (context.stateMachine.state == ConnectionLifecycleState.CLOSED) {
            return
        }

        context.stateMachine.close(reason)
        connectionLog.record(
            ConnectionLogEntry(
                connectionId = context.connectionId,
                service = adapter.service,
                reason = reason,
                detail = detail,
                occurredAt = clock(),
            ),
        )
        context.outbound.close()
        connections.remove(context.connectionId)
        context.verifiedSession?.let { session ->
            runBlocking {
                adapter.onSessionEnded(session)
            }
        }
    }

    private data class ConnectionContext(
        val connectionId: String,
        val outbound: Channel<FrameEnvelope>,
        val stateMachine: ConnectionStateMachine,
        val mutex: Mutex = Mutex(),
        var verifiedSession: InMemoryAuthenticatedSession? = null,
    )
}

class FakeSessionCatalog(
    accounts: List<FakePlayerAccount>,
) {
    private val bySessionTicket = accounts.associateBy { it.sessionTicket }

    fun lookup(sessionTicket: String): FakePlayerAccount? = bySessionTicket[sessionTicket]

    companion object {
        fun defaults(): FakeSessionCatalog = FakeSessionCatalog(
            accounts = listOf(
                FakePlayerAccount(
                    playFabId = "PF-LOCALUSER",
                    playerIp = "LOCAL-IP",
                    sessionTicket = "SESSION-LOCALUSER",
                ),
            ),
        )
    }
}

data class FakePlayerAccount(
    val playFabId: String,
    val playerIp: String,
    val sessionTicket: String,
)

class FakeSessionTicketVerifier(
    private val catalog: FakeSessionCatalog = FakeSessionCatalog.defaults(),
    private val heartbeatInterval: Duration = ProtocolTimeoutPolicy().heartbeatInterval,
    private val clock: () -> Instant = { Instant.now() },
) : SessionTicketVerifier {
    override suspend fun verify(request: SessionTicketAuthRequest): VerifiedSession? {
        val account = catalog.lookup(request.sessionTicket) ?: return null
        if (!request.playFabIdHint.isNullOrBlank() && !account.playFabId.equals(request.playFabIdHint, ignoreCase = true)) {
            return null
        }
        if (!request.requestedIp.isNullOrBlank() && request.requestedIp != account.playerIp) {
            return null
        }
        return VerifiedSession(
            playFabId = account.playFabId,
            playerIp = account.playerIp,
            sessionTicket = account.sessionTicket,
            heartbeatInterval = heartbeatInterval,
            sessionStartedAt = clock(),
        )
    }
}

object StubRewriteAdapters {
    fun game(
        bootstrapStateId: String = "LOCAL-IP",
        bootstrapPayload: ByteArray = """{"bootstrap":"game-state"}""".toByteArray(),
    ): RewriteServiceAdapter = object : RewriteServiceAdapter {
        override val service: RewriteService = RewriteService.GAME

        override suspend fun onSessionStarted(session: InMemoryAuthenticatedSession): List<FrameEnvelope> {
            return listOf(
                RewriteFrames.snapshot(
                    gameStateId = bootstrapStateId,
                    sequence = 1,
                    payload = bootstrapPayload,
                ),
            )
        }

        override suspend fun onCommand(
            session: InMemoryAuthenticatedSession,
            command: CommandEnvelope,
        ): List<FrameEnvelope> {
            return when (command.command_name) {
                "requestscan" -> listOf(
                    RewriteFrames.commandResponse(
                        commandId = command.command_id,
                        payload = """{"ports":[80,443]}""".toByteArray(),
                    ),
                )

                "mutate-state" -> listOf(
                    RewriteFrames.commandResponse(
                        commandId = command.command_id,
                        payload = """{"accepted":true}""".toByteArray(),
                    ),
                    RewriteFrames.delta(
                        gameStateId = session.verifiedSession.playerIp,
                        sequence = 2,
                        changedPaths = listOf("computer.cash"),
                        deltaKeys = listOf("petty_cash"),
                        payload = """{"pettyCash":25}""".toByteArray(),
                    ),
                )

                else -> emptyList()
            }
        }
    }

    fun chat(): RewriteServiceAdapter = object : RewriteServiceAdapter {
        override val service: RewriteService = RewriteService.CHAT
    }
}
