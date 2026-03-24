package com.hackwars.rewrite.protocol

import hackwars.rewrite.v1.AuthAccepted
import hackwars.rewrite.v1.AuthRejected
import hackwars.rewrite.v1.AuthRequest
import hackwars.rewrite.v1.AuthResponse
import hackwars.rewrite.v1.ChatEventEnvelope
import hackwars.rewrite.v1.CommandEnvelope
import hackwars.rewrite.v1.CommandResponseEnvelope
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.ErrorEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import hackwars.rewrite.v1.GameUiEventEnvelope
import hackwars.rewrite.v1.GameStateDeltaEnvelope
import hackwars.rewrite.v1.GameStateSnapshotEnvelope
import hackwars.rewrite.v1.PingEnvelope
import hackwars.rewrite.v1.ProgramStatus
import hackwars.rewrite.v1.ProgramUpdateEnvelope
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import okio.ByteString
import okio.ByteString.Companion.toByteString

object RewriteFrames {
    fun authRequest(
        service: RewriteService,
        sessionTicket: String,
        clientBuild: String,
        playFabIdHint: String? = null,
        requestedIp: String? = null,
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        auth_request = AuthRequest(
            service = service.toProto(),
            client_build = clientBuild,
            playfab_id_hint = playFabIdHint.orEmpty(),
            session_ticket = sessionTicket,
            requested_ip = requestedIp.orEmpty(),
        ),
    )

    fun authAccepted(
        connectionId: String,
        playFabId: String,
        playerIp: String,
        heartbeatInterval: Duration,
        sessionStartedAt: Instant,
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        auth_response = AuthResponse(
            accepted = AuthAccepted(
                connection_id = connectionId,
                playfab_id = playFabId,
                player_ip = playerIp,
                heartbeat_interval_ms = heartbeatInterval.inWholeMilliseconds.toInt(),
                session_started_at = sessionStartedAt.toEpochMilli(),
            ),
        ),
    )

    fun authRejected(
        reasonCode: String,
        reasonText: String,
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        auth_response = AuthResponse(
            rejected = AuthRejected(
                reason_code = reasonCode,
                reason_text = reasonText,
            ),
        ),
    )

    fun command(
        commandId: String,
        commandName: String,
        targetGameStateIds: List<String> = emptyList(),
        payload: ByteArray = ByteArray(0),
        expectsResponse: Boolean = false,
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        command = CommandEnvelope(
            command_id = commandId,
            command_name = commandName,
            target_game_state_ids = targetGameStateIds,
            payload = payload.toByteString(),
            expects_response = expectsResponse,
        ),
    )

    fun commandResponse(
        commandId: String,
        status: CommandResponseStatus = CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK,
        payload: ByteArray = ByteArray(0),
        error: ErrorEnvelope? = null,
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        command_response = CommandResponseEnvelope(
            command_id = commandId,
            status = status,
            payload = payload.toByteString(),
            error = error,
        ),
    )

    fun snapshot(
        gameStateId: String,
        sequence: Long,
        payload: ByteArray = ByteArray(0),
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        snapshot = GameStateSnapshotEnvelope(
            game_state_id = gameStateId,
            sequence = sequence,
            payload = payload.toByteString(),
        ),
    )

    fun delta(
        gameStateId: String,
        sequence: Long,
        changedPaths: List<String> = emptyList(),
        deltaKeys: List<String> = emptyList(),
        payload: ByteArray = ByteArray(0),
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        delta = GameStateDeltaEnvelope(
            game_state_id = gameStateId,
            sequence = sequence,
            changed_paths = changedPaths,
            delta_keys = deltaKeys,
            payload = payload.toByteString(),
        ),
    )

    fun programUpdate(
        programId: String,
        programType: String,
        status: ProgramStatus,
        relatedGameStateIds: List<String> = emptyList(),
        payload: ByteArray = ByteArray(0),
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        program_update = ProgramUpdateEnvelope(
            program_id = programId,
            program_type = programType,
            status = status,
            related_game_state_ids = relatedGameStateIds,
            payload = payload.toByteString(),
        ),
    )

    fun chatEvent(
        eventId: String,
        eventType: String,
        channelName: String = "",
        payload: ByteArray = ByteArray(0),
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        chat_event = ChatEventEnvelope(
            event_id = eventId,
            event_type = eventType,
            channel_name = channelName,
            payload = payload.toByteString(),
        ),
    )

    fun gameUiEvent(
        eventId: String,
        eventType: String,
        payload: ByteArray = ByteArray(0),
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        game_ui_event = GameUiEventEnvelope(
            event_id = eventId,
            event_type = eventType,
            payload = payload.toByteString(),
        ),
    )

    fun ping(
        connectionId: String,
        sentAtEpochMillis: Long,
        acknowledgedAtEpochMillis: Long,
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        ping = PingEnvelope(
            connection_id = connectionId,
            sent_at_epoch_millis = sentAtEpochMillis,
            acknowledged_at_epoch_millis = acknowledgedAtEpochMillis,
        ),
    )

    fun error(
        code: String,
        message: String,
        retryable: Boolean,
        envelopeId: String = UUID.randomUUID().toString(),
    ): FrameEnvelope = FrameEnvelope(
        envelope_id = envelopeId,
        error = ErrorEnvelope(
            code = code,
            message = message,
            retryable = retryable,
        ),
    )
}

fun ByteArray.toProtocolBytes(): ByteString = toByteString()
