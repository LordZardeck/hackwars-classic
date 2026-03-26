package com.hackwars.rewrite.clientmodel

import com.hackwars.rewrite.protocol.ClientGameDeltaProjection
import com.hackwars.rewrite.protocol.ClientGameSectionsProjection
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientGameStateSummaryProjection
import com.hackwars.rewrite.protocol.ClientGameUiEvent
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.ConnectionLifecycleState
import com.hackwars.rewrite.protocol.FrameCodec
import com.hackwars.rewrite.protocol.FramePayloadType
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.protocol.payloadType
import hackwars.rewrite.v1.AuthAccepted
import hackwars.rewrite.v1.AuthRejected
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.flow.Flow

class RewriteClientStore(initialState: RewriteClientState = RewriteClientState()) {
    private val store = SelectorStore(initialState)

    fun snapshot(): RewriteClientState = store.snapshot()

    fun update(reducer: (RewriteClientState) -> RewriteClientState) {
        store.update(reducer)
    }

    fun <Selected> selector(project: (RewriteClientState) -> Selected): Flow<Selected> {
        return store.selector(project)
    }

    fun bootstrapSelector(): Flow<RewriteClientBootstrapState> {
        return selector { it.bootstrap }
    }

    fun routeSelector(): Flow<RewriteClientRoute> {
        return selector { it.bootstrap.route }
    }

    fun loginErrorSelector(): Flow<String?> {
        return selector { it.bootstrap.loginError }
    }

    fun serviceSelector(service: RewriteService): Flow<RewriteServiceState> {
        return selector { it.service(service) }
    }

    fun serviceConnectionSelector(service: RewriteService): Flow<ConnectionLifecycleState> {
        return selector { it.service(service).connectionState }
    }

    fun serviceAuthSelector(service: RewriteService): Flow<RewriteServiceAuthState> {
        return selector { it.service(service).authState }
    }

    fun serviceInboxSelector(service: RewriteService): Flow<RewriteServiceFrameInbox> {
        return selector { it.service(service).inbox }
    }

    fun serviceDecodedGameSelector(service: RewriteService): Flow<RewriteDecodedGameState> {
        return selector { it.service(service).decodedGame }
    }

    fun serviceShellStateSelector(service: RewriteService): Flow<ClientGameSnapshot?> {
        return selector { it.service(service).decodedGame.shellState }
    }

    fun serviceProgramUpdatesSelector(service: RewriteService): Flow<Map<String, ClientProgramUpdate>> {
        return selector { it.service(service).decodedGame.programUpdatesById }
    }

    fun serviceGameUiNoticesSelector(service: RewriteService): Flow<List<RewriteDecodedGameUiNotice>> {
        return selector { it.service(service).decodedGame.uiNotices }
    }

    fun serviceDecodeErrorsSelector(service: RewriteService): Flow<List<RewriteDecodeErrorRecord>> {
        return selector { it.service(service).decodedGame.decodeErrors }
    }

    fun noteConnected(service: RewriteService) {
        update { state ->
            val current = state.service(service)
            state.withService(
                service = service,
                next = current.copy(
                    connectionState = current.connectionState.onConnected(),
                    authState = RewriteServiceAuthState.Awaiting,
                ),
            )
        }
    }

    fun noteClosed(service: RewriteService) {
        update { state ->
            state.withService(
                service = service,
                next = state.service(service).copy(connectionState = ConnectionLifecycleState.CLOSED),
            )
        }
    }

    fun resetService(service: RewriteService) {
        update { state ->
            state.withService(
                service = service,
                next = RewriteServiceState(service = service),
            )
        }
    }

    fun showLoginScreen(error: String? = null) {
        update { state ->
            state.copy(
                bootstrap = RewriteClientBootstrapState(
                    route = RewriteClientRoute.LOGIN,
                    loginError = error,
                ),
            )
        }
    }

    fun beginGameBootstrap() {
        update { state ->
            state.copy(
                bootstrap = RewriteClientBootstrapState(
                    route = RewriteClientRoute.BOOTSTRAPPING_GAME,
                    loginError = null,
                ),
            )
        }
    }

    fun showDesktop() {
        update { state ->
            state.copy(
                bootstrap = RewriteClientBootstrapState(
                    route = RewriteClientRoute.DESKTOP,
                    loginError = null,
                ),
            )
        }
    }

    fun recordInboundFrame(service: RewriteService, frame: FrameEnvelope, receivedAt: Instant = Instant.now()) {
        update { state -> state.recordInboundFrame(service, frame, receivedAt) }
    }
}

data class RewriteClientState(
    val bootstrap: RewriteClientBootstrapState = RewriteClientBootstrapState(),
    val game: RewriteServiceState = RewriteServiceState(service = RewriteService.GAME),
    val chat: RewriteServiceState = RewriteServiceState(service = RewriteService.CHAT),
) {
    val services: Map<RewriteService, RewriteServiceState>
        get() = mapOf(
            RewriteService.GAME to game,
            RewriteService.CHAT to chat,
        )

    fun service(service: RewriteService): RewriteServiceState = when (service) {
        RewriteService.GAME -> game
        RewriteService.CHAT -> chat
    }

    fun withService(service: RewriteService, next: RewriteServiceState): RewriteClientState = when (service) {
        RewriteService.GAME -> copy(game = next)
        RewriteService.CHAT -> copy(chat = next)
    }

    fun recordInboundFrame(
        service: RewriteService,
        frame: FrameEnvelope,
        receivedAt: Instant = Instant.now(),
    ): RewriteClientState {
        val current = service(service)
        val record = RewriteRawFrameRecord.fromFrame(
            service = service,
            frame = frame,
            receivedAt = receivedAt,
        )
        return withService(service, current.record(record, frame))
    }
}

data class RewriteClientBootstrapState(
    val route: RewriteClientRoute = RewriteClientRoute.LOGIN,
    val loginError: String? = null,
)

enum class RewriteClientRoute {
    LOGIN,
    BOOTSTRAPPING_GAME,
    DESKTOP,
}

data class RewriteServiceState(
    val service: RewriteService,
    val connectionState: ConnectionLifecycleState = ConnectionLifecycleState.CLOSED,
    val authState: RewriteServiceAuthState = RewriteServiceAuthState.Awaiting,
    val latestAcceptedSession: RewriteAcceptedSessionMetadata? = null,
    val inbox: RewriteServiceFrameInbox = RewriteServiceFrameInbox(),
    val decodedGame: RewriteDecodedGameState = RewriteDecodedGameState(),
) {
    fun record(
        record: RewriteRawFrameRecord,
        frame: FrameEnvelope,
    ): RewriteServiceState {
        val accepted = frame.auth_response?.accepted
        if (accepted != null) {
            return recordAuthAccepted(accepted, record)
        }

        val rejected = frame.auth_response?.rejected
        if (rejected != null) {
            return recordAuthRejected(rejected, record)
        }

        val next = copy(
            connectionState = when (connectionState) {
                ConnectionLifecycleState.CONNECTED -> ConnectionLifecycleState.CONNECTED
                ConnectionLifecycleState.AUTHENTICATED -> ConnectionLifecycleState.ACTIVE
                ConnectionLifecycleState.ACTIVE -> ConnectionLifecycleState.ACTIVE
                ConnectionLifecycleState.CLOSED -> ConnectionLifecycleState.CONNECTED
            },
            inbox = inbox.record(record),
        )
        return if (service == RewriteService.GAME) {
            next.recordDecodedGamePayload(record, frame)
        } else {
            next
        }
    }

    private fun recordAuthAccepted(
        accepted: AuthAccepted,
        record: RewriteRawFrameRecord,
    ): RewriteServiceState {
        val session = RewriteAcceptedSessionMetadata(
            connectionId = accepted.connection_id,
            playFabId = accepted.playfab_id,
            playerIp = accepted.player_ip,
            heartbeatIntervalMillis = accepted.heartbeat_interval_ms.toLong(),
            sessionStartedAtEpochMillis = accepted.session_started_at,
        )
        return copy(
            connectionState = ConnectionLifecycleState.AUTHENTICATED,
            authState = RewriteServiceAuthState.Accepted(session = session),
            latestAcceptedSession = session,
            inbox = inbox.record(record),
        )
    }

    private fun recordAuthRejected(
        rejected: AuthRejected,
        record: RewriteRawFrameRecord,
    ): RewriteServiceState {
        return copy(
            connectionState = ConnectionLifecycleState.CLOSED,
            authState = RewriteServiceAuthState.Rejected(
                reasonCode = rejected.reason_code,
                reasonText = rejected.reason_text,
            ),
            inbox = inbox.record(record),
        )
    }
}

data class RewriteDecodedGameState(
    val latestSnapshot: ClientGameSnapshot? = null,
    val shellState: ClientGameSnapshot? = null,
    val lastDelta: RewriteDecodedGameDeltaRecord? = null,
    val programUpdatesById: Map<String, ClientProgramUpdate> = emptyMap(),
    val uiNotices: List<RewriteDecodedGameUiNotice> = emptyList(),
    val decodeErrors: List<RewriteDecodeErrorRecord> = emptyList(),
)

data class RewriteDecodedGameDeltaRecord(
    val projection: ClientGameDeltaProjection,
    val metadata: RewriteRawFrameMetadata,
)

data class RewriteDecodedGameUiNotice(
    val event: ClientGameUiEvent,
    val metadata: RewriteRawFrameMetadata,
)

data class RewriteDecodeErrorRecord(
    val payloadType: FramePayloadType,
    val envelopeId: String?,
    val metadata: RewriteRawFrameMetadata,
    val message: String,
)

sealed interface RewriteServiceAuthState {
    data object Awaiting : RewriteServiceAuthState

    data class Accepted(
        val session: RewriteAcceptedSessionMetadata,
    ) : RewriteServiceAuthState

    data class Rejected(
        val reasonCode: String,
        val reasonText: String,
    ) : RewriteServiceAuthState
}

data class RewriteAcceptedSessionMetadata(
    val connectionId: String,
    val playFabId: String,
    val playerIp: String,
    val heartbeatIntervalMillis: Long,
    val sessionStartedAtEpochMillis: Long,
)

data class RewriteServiceFrameInbox(
    val lastAuthResponse: RewriteRawFrameRecord? = null,
    val lastCommand: RewriteRawFrameRecord? = null,
    val lastCommandResponse: RewriteRawFrameRecord? = null,
    val lastSnapshot: RewriteRawFrameRecord? = null,
    val lastDelta: RewriteRawFrameRecord? = null,
    val lastProgramUpdate: RewriteRawFrameRecord? = null,
    val lastChatEvent: RewriteRawFrameRecord? = null,
    val lastGameUiEvent: RewriteRawFrameRecord? = null,
    val lastPing: RewriteRawFrameRecord? = null,
    val lastError: RewriteRawFrameRecord? = null,
    val history: List<RewriteRawFrameRecord> = emptyList(),
) {
    fun record(record: RewriteRawFrameRecord): RewriteServiceFrameInbox {
        val nextHistory = history + record
        return when (record.payloadType) {
            FramePayloadType.AUTH_REQUEST,
            FramePayloadType.AUTH_RESPONSE -> copy(
                lastAuthResponse = record,
                history = nextHistory,
            )

            FramePayloadType.COMMAND -> copy(
                lastCommand = record,
                history = nextHistory,
            )

            FramePayloadType.COMMAND_RESPONSE -> copy(
                lastCommandResponse = record,
                history = nextHistory,
            )

            FramePayloadType.SNAPSHOT -> copy(
                lastSnapshot = record,
                history = nextHistory,
            )

            FramePayloadType.DELTA -> copy(
                lastDelta = record,
                history = nextHistory,
            )

            FramePayloadType.PROGRAM_UPDATE -> copy(
                lastProgramUpdate = record,
                history = nextHistory,
            )

            FramePayloadType.CHAT_EVENT -> copy(
                lastChatEvent = record,
                history = nextHistory,
            )

            FramePayloadType.GAME_UI_EVENT -> copy(
                lastGameUiEvent = record,
                history = nextHistory,
            )

            FramePayloadType.PING -> copy(
                lastPing = record,
                history = nextHistory,
            )

            FramePayloadType.ERROR -> copy(
                lastError = record,
                history = nextHistory,
            )

            FramePayloadType.EMPTY -> copy(history = nextHistory)
        }
    }
}

data class RewriteRawFrameRecord(
    val service: RewriteService,
    val envelopeId: String?,
    val payloadType: FramePayloadType,
    val encodedFrameBytes: ByteArray,
    val metadata: RewriteRawFrameMetadata = RewriteRawFrameMetadata(),
) {
    companion object {
        fun fromFrame(
            service: RewriteService,
            frame: FrameEnvelope,
            receivedAt: Instant,
        ): RewriteRawFrameRecord {
            return RewriteRawFrameRecord(
                service = service,
                envelopeId = frame.envelope_id,
                payloadType = frame.payloadType(),
                encodedFrameBytes = FrameCodec.encode(frame),
                metadata = RewriteRawFrameMetadata.fromFrame(frame, receivedAt),
            )
        }
    }
}

private fun ConnectionLifecycleState.onConnected(): ConnectionLifecycleState = when (this) {
    ConnectionLifecycleState.CLOSED -> ConnectionLifecycleState.CONNECTED
    ConnectionLifecycleState.CONNECTED -> ConnectionLifecycleState.CONNECTED
    ConnectionLifecycleState.AUTHENTICATED -> ConnectionLifecycleState.AUTHENTICATED
    ConnectionLifecycleState.ACTIVE -> ConnectionLifecycleState.ACTIVE
}

data class RewriteRawFrameMetadata(
    val commandId: String? = null,
    val commandName: String? = null,
    val gameStateId: String? = null,
    val programId: String? = null,
    val programType: String? = null,
    val eventId: String? = null,
    val eventType: String? = null,
    val channelName: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val relatedGameStateIds: List<String> = emptyList(),
    val changedPaths: List<String> = emptyList(),
    val deltaKeys: List<String> = emptyList(),
    val receivedAtEpochMillis: Long? = null,
) {
    companion object {
        fun fromFrame(frame: FrameEnvelope, receivedAt: Instant): RewriteRawFrameMetadata {
            return RewriteRawFrameMetadata(
                commandId = frame.command?.command_id ?: frame.command_response?.command_id,
                commandName = frame.command?.command_name,
                gameStateId = frame.snapshot?.game_state_id ?: frame.delta?.game_state_id,
                programId = frame.program_update?.program_id,
                programType = frame.program_update?.program_type,
                eventId = frame.chat_event?.event_id ?: frame.game_ui_event?.event_id,
                eventType = frame.chat_event?.event_type ?: frame.game_ui_event?.event_type,
                channelName = frame.chat_event?.channel_name,
                errorCode = frame.error?.code,
                errorMessage = frame.error?.message,
                relatedGameStateIds = frame.program_update?.related_game_state_ids.orEmpty(),
                changedPaths = frame.delta?.changed_paths.orEmpty(),
                deltaKeys = frame.delta?.delta_keys.orEmpty(),
                receivedAtEpochMillis = receivedAt.toEpochMilli(),
            )
        }
    }
}

private const val MAX_DECODED_GAME_UI_NOTICES: Int = 25
private const val MAX_DECODE_ERRORS: Int = 10

private fun RewriteServiceState.recordDecodedGamePayload(
    record: RewriteRawFrameRecord,
    frame: FrameEnvelope,
): RewriteServiceState {
    return copy(decodedGame = decodedGame.record(record, frame))
}

private fun RewriteDecodedGameState.record(
    record: RewriteRawFrameRecord,
    frame: FrameEnvelope,
): RewriteDecodedGameState = when (record.payloadType) {
    FramePayloadType.SNAPSHOT -> recordSnapshot(record, frame)
    FramePayloadType.DELTA -> recordDelta(record, frame)
    FramePayloadType.PROGRAM_UPDATE -> recordProgramUpdate(record, frame)
    FramePayloadType.GAME_UI_EVENT -> recordGameUiEvent(record, frame)
    else -> this
}

private fun RewriteDecodedGameState.recordSnapshot(
    record: RewriteRawFrameRecord,
    frame: FrameEnvelope,
): RewriteDecodedGameState {
    return decodePayload(
        record = record,
        decode = {
            RewriteClientJson.decode(ClientGameSnapshot.serializer(), frame.snapshotPayloadBytes())
        },
        onSuccess = { snapshot ->
        copy(
            latestSnapshot = snapshot,
            shellState = snapshot,
        )
        },
    )
}

private fun RewriteDecodedGameState.recordDelta(
    record: RewriteRawFrameRecord,
    frame: FrameEnvelope,
): RewriteDecodedGameState {
    return decodePayload(
        record = record,
        decode = {
            RewriteClientJson.decode(ClientGameDeltaProjection.serializer(), frame.deltaPayloadBytes())
        },
        onSuccess = { projection ->
        val nextShellState = when (projection) {
            is ClientGameSectionsProjection -> {
                (shellState ?: latestSnapshot)?.applySections(projection) ?: shellState
            }

            is ClientGameStateSummaryProjection -> {
                shellState?.applySummary(projection) ?: shellState
            }
        }
        copy(
            shellState = nextShellState,
            lastDelta = RewriteDecodedGameDeltaRecord(
                projection = projection,
                metadata = record.metadata,
            ),
        )
        },
    )
}

private fun RewriteDecodedGameState.recordProgramUpdate(
    record: RewriteRawFrameRecord,
    frame: FrameEnvelope,
): RewriteDecodedGameState {
    return decodePayload(
        record = record,
        decode = {
            RewriteClientJson.decode(ClientProgramUpdate.serializer(), frame.programUpdatePayloadBytes())
        },
        onSuccess = { programUpdate ->
        copy(programUpdatesById = programUpdatesById + (programUpdate.programId to programUpdate))
        },
    )
}

private fun RewriteDecodedGameState.recordGameUiEvent(
    record: RewriteRawFrameRecord,
    frame: FrameEnvelope,
): RewriteDecodedGameState {
    return decodePayload(
        record = record,
        decode = {
            RewriteClientJson.decode(ClientGameUiEvent.serializer(), frame.gameUiPayloadBytes())
        },
        onSuccess = { event ->
        copy(
            uiNotices = (uiNotices + RewriteDecodedGameUiNotice(event, record.metadata))
                .takeLast(MAX_DECODED_GAME_UI_NOTICES),
        )
        },
    )
}

private inline fun <T> RewriteDecodedGameState.decodePayload(
    record: RewriteRawFrameRecord,
    decode: () -> T,
    onSuccess: RewriteDecodedGameState.(T) -> RewriteDecodedGameState,
): RewriteDecodedGameState {
    return try {
        onSuccess(decode())
    } catch (exception: Exception) {
        appendDecodeError(record, exception)
    }
}

private fun RewriteDecodedGameState.appendDecodeError(
    record: RewriteRawFrameRecord,
    exception: Exception,
): RewriteDecodedGameState {
    return copy(
        decodeErrors = (decodeErrors + RewriteDecodeErrorRecord(
            payloadType = record.payloadType,
            envelopeId = record.envelopeId,
            metadata = record.metadata,
            message = exception.message ?: exception.javaClass.simpleName,
        )).takeLast(MAX_DECODE_ERRORS),
    )
}

private fun ClientGameSnapshot.applySections(projection: ClientGameSectionsProjection): ClientGameSnapshot {
    return copy(
        identity = projection.identity ?: identity,
        economy = projection.economy ?: economy,
        hardware = projection.hardware ?: hardware,
        ports = projection.ports ?: ports,
        website = projection.website ?: website,
        preferences = projection.preferences ?: preferences,
        stats = projection.stats ?: stats,
        logs = projection.logs ?: logs,
        runtime = projection.runtime ?: runtime,
    )
}

private fun ClientGameSnapshot.applySummary(projection: ClientGameStateSummaryProjection): ClientGameSnapshot {
    return copy(
        version = projection.version,
        identity = identity.copy(
            playerIp = projection.playerIp.ifBlank { identity.playerIp },
        ),
    )
}

private fun FrameEnvelope.snapshotPayloadBytes(): ByteArray = snapshot?.payload?.toByteArray() ?: ByteArray(0)

private fun FrameEnvelope.deltaPayloadBytes(): ByteArray = delta?.payload?.toByteArray() ?: ByteArray(0)

private fun FrameEnvelope.programUpdatePayloadBytes(): ByteArray = program_update?.payload?.toByteArray() ?: ByteArray(0)

private fun FrameEnvelope.gameUiPayloadBytes(): ByteArray = game_ui_event?.payload?.toByteArray() ?: ByteArray(0)
