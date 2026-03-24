package com.hackwars.rewrite.protocol

import hackwars.rewrite.v1.FrameEnvelope
import hackwars.rewrite.v1.ServiceKind
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class RewriteService {
    GAME,
    CHAT,
    ;

    fun toProto(): ServiceKind = when (this) {
        GAME -> ServiceKind.SERVICE_KIND_GAME
        CHAT -> ServiceKind.SERVICE_KIND_CHAT
    }

    companion object {
        fun fromProto(service: ServiceKind): RewriteService = when (service) {
            ServiceKind.SERVICE_KIND_GAME -> GAME
            ServiceKind.SERVICE_KIND_CHAT -> CHAT
            else -> throw IllegalArgumentException("Unsupported service kind: $service")
        }
    }
}

enum class ConnectionLifecycleState {
    CONNECTED,
    AUTHENTICATED,
    ACTIVE,
    CLOSED,
}

enum class DisconnectReason {
    MISSING_AUTH,
    INVALID_AUTH,
    MALFORMED_FRAME,
    OVERSIZED_FRAME,
    OVERSIZED_AUTH_PAYLOAD,
    AUTH_TIMEOUT,
    IDLE_TIMEOUT,
    CLIENT_CLOSED,
    UNSUPPORTED_FRAME,
    INTERNAL_ERROR,
}

enum class FramePayloadType {
    AUTH_REQUEST,
    AUTH_RESPONSE,
    COMMAND,
    COMMAND_RESPONSE,
    SNAPSHOT,
    DELTA,
    PROGRAM_UPDATE,
    CHAT_EVENT,
    PING,
    ERROR,
    EMPTY,
}

data class ProtocolTimeoutPolicy(
    val authTimeout: Duration = 5.seconds,
    val idleTimeout: Duration = 45.seconds,
    val heartbeatInterval: Duration = 15.seconds,
    val maxAuthPayloadBytes: Int = 2_048,
    val maxFrameBytes: Int = 256 * 1024,
)

data class SessionTicketAuthRequest(
    val service: RewriteService,
    val playFabIdHint: String?,
    val sessionTicket: String,
    val requestedIp: String?,
    val clientBuild: String,
)

data class VerifiedSession(
    val playFabId: String,
    val playerIp: String,
    val sessionTicket: String,
    val heartbeatInterval: Duration,
    val sessionStartedAt: Instant,
)

interface SessionTicketVerifier {
    suspend fun verify(request: SessionTicketAuthRequest): VerifiedSession?
}

class ConnectionStateMachine(
    private val connectedAt: Instant,
    private val timeoutPolicy: ProtocolTimeoutPolicy = ProtocolTimeoutPolicy(),
) {
    var state: ConnectionLifecycleState = ConnectionLifecycleState.CONNECTED
        private set

    var disconnectReason: DisconnectReason? = null
        private set

    private var lastActivityAt: Instant = connectedAt

    fun noteInbound(now: Instant) {
        if (state != ConnectionLifecycleState.CLOSED) {
            lastActivityAt = now
        }
    }

    fun requireAuth(payloadType: FramePayloadType): DisconnectReason? {
        if (state == ConnectionLifecycleState.CONNECTED && payloadType != FramePayloadType.AUTH_REQUEST) {
            return close(DisconnectReason.MISSING_AUTH)
        }
        return null
    }

    fun authenticate(now: Instant) {
        state = ConnectionLifecycleState.AUTHENTICATED
        lastActivityAt = now
    }

    fun activate(now: Instant) {
        state = ConnectionLifecycleState.ACTIVE
        lastActivityAt = now
    }

    fun close(reason: DisconnectReason): DisconnectReason {
        state = ConnectionLifecycleState.CLOSED
        disconnectReason = reason
        return reason
    }

    fun timeoutReason(now: Instant): DisconnectReason? = when (state) {
        ConnectionLifecycleState.CONNECTED -> {
            if (connectedAt.elapsedUntil(now) >= timeoutPolicy.authTimeout) {
                DisconnectReason.AUTH_TIMEOUT
            } else {
                null
            }
        }

        ConnectionLifecycleState.AUTHENTICATED, ConnectionLifecycleState.ACTIVE -> {
            if (lastActivityAt.elapsedUntil(now) >= timeoutPolicy.idleTimeout) {
                DisconnectReason.IDLE_TIMEOUT
            } else {
                null
            }
        }

        ConnectionLifecycleState.CLOSED -> disconnectReason
    }

    private fun Instant.elapsedUntil(now: Instant): Duration {
        return (now.toEpochMilli() - toEpochMilli()).milliseconds
    }
}

fun FrameEnvelope.payloadType(): FramePayloadType = when {
    auth_request != null -> FramePayloadType.AUTH_REQUEST
    auth_response != null -> FramePayloadType.AUTH_RESPONSE
    command != null -> FramePayloadType.COMMAND
    command_response != null -> FramePayloadType.COMMAND_RESPONSE
    snapshot != null -> FramePayloadType.SNAPSHOT
    delta != null -> FramePayloadType.DELTA
    program_update != null -> FramePayloadType.PROGRAM_UPDATE
    chat_event != null -> FramePayloadType.CHAT_EVENT
    ping != null -> FramePayloadType.PING
    error != null -> FramePayloadType.ERROR
    else -> FramePayloadType.EMPTY
}
