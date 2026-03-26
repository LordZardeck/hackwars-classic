package com.hackwars.rewrite.client

import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.FrameEnvelope
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal sealed interface RewriteGameCommandResult<out T> {
    data class Success<T>(
        val value: T,
    ) : RewriteGameCommandResult<T>

    data class Failure(
        val message: String,
        val code: String? = null,
    ) : RewriteGameCommandResult<Nothing>
}

internal class RewriteGameCommandBroker(
    private val requestTimeout: Duration = 5.seconds,
    private val sendFrame: suspend (FrameEnvelope) -> Unit,
) {
    private val pendingByCommandId = linkedMapOf<String, CompletableDeferred<PendingCommandResult>>()
    private val lock = Any()

    suspend fun <Request : Any, Response : Any> request(
        commandName: String,
        payloadSerializer: KSerializer<Request>,
        payload: Request,
        responseSerializer: KSerializer<Response>,
        targetStateIds: List<String> = emptyList(),
    ): RewriteGameCommandResult<Response> {
        val commandId = UUID.randomUUID().toString()
        val completion = CompletableDeferred<PendingCommandResult>()
        synchronized(lock) {
            pendingByCommandId[commandId] = completion
        }

        try {
            sendFrame(
                RewriteFrames.command(
                    commandId = commandId,
                    commandName = commandName,
                    targetGameStateIds = targetStateIds,
                    payload = RewriteClientJson.encode(payloadSerializer, payload),
                    expectsResponse = true,
                ),
            )
        } catch (throwable: Throwable) {
            synchronized(lock) {
                pendingByCommandId.remove(commandId)
            }
            return RewriteGameCommandResult.Failure(
                message = "The rewrite game connection failed.",
                code = "CLIENT_IO_ERROR",
            )
        }

        val result = withTimeoutOrNull(requestTimeout) {
            completion.await()
        } ?: run {
            synchronized(lock) {
                pendingByCommandId.remove(commandId)
            }
            return RewriteGameCommandResult.Failure(
                message = "The rewrite game server did not respond in time.",
                code = "COMMAND_TIMEOUT",
            )
        }

        return when (result) {
            is PendingCommandResult.Success -> {
                try {
                    RewriteGameCommandResult.Success(
                        RewriteClientJson.decode(responseSerializer, result.payload),
                    )
                } catch (exception: SerializationException) {
                    RewriteGameCommandResult.Failure(
                        message = "The rewrite game server sent an invalid response.",
                        code = "MALFORMED_FRAME",
                    )
                } catch (exception: IllegalArgumentException) {
                    RewriteGameCommandResult.Failure(
                        message = "The rewrite game server sent an invalid response.",
                        code = "MALFORMED_FRAME",
                    )
                }
            }

            is PendingCommandResult.Failure -> RewriteGameCommandResult.Failure(
                message = result.message,
                code = result.code,
            )
        }
    }

    fun accept(frame: FrameEnvelope) {
        when {
            frame.command_response != null -> acceptCommandResponse(frame)
            frame.error != null -> {
                failAll(
                    message = sanitizeGameTransportFailure(
                        code = frame.error!!.code,
                        message = frame.error!!.message,
                    ),
                    code = frame.error!!.code,
                )
            }
        }
    }

    fun failAll(
        message: String,
        code: String? = null,
    ) {
        val pending = synchronized(lock) {
            val values = pendingByCommandId.values.toList()
            pendingByCommandId.clear()
            values
        }
        pending.forEach { completion ->
            if (!completion.isCompleted) {
                completion.complete(PendingCommandResult.Failure(message, code))
            }
        }
    }

    private fun acceptCommandResponse(frame: FrameEnvelope) {
        val commandResponse = frame.command_response ?: return
        val completion = synchronized(lock) {
            pendingByCommandId.remove(commandResponse.command_id)
        } ?: return

        if (completion.isCompleted) {
            return
        }
        when (commandResponse.status) {
            CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK -> {
                completion.complete(
                    PendingCommandResult.Success(commandResponse.payload.toByteArray()),
                )
            }

            CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
            CommandResponseStatus.COMMAND_RESPONSE_STATUS_UNSPECIFIED,
            CommandResponseStatus.COMMAND_RESPONSE_STATUS_TIMEOUT -> {
                val error = commandResponse.error
                completion.complete(
                    PendingCommandResult.Failure(
                        message = error?.message?.ifBlank { "The rewrite game request failed." }
                            ?: "The rewrite game request failed.",
                        code = error?.code,
                    ),
                )
            }
        }
    }

    private sealed interface PendingCommandResult {
        data class Success(
            val payload: ByteArray,
        ) : PendingCommandResult

        data class Failure(
            val message: String,
            val code: String? = null,
        ) : PendingCommandResult
    }
}

internal fun sanitizeGameTransportFailure(
    code: String,
    message: String,
): String = when (code) {
    "MALFORMED_FRAME" -> "The rewrite game server sent an invalid response."
    "SERVER_DISCONNECTED" -> "The rewrite game server closed the connection."
    "CLIENT_IO_ERROR" -> "The rewrite game connection failed."
    else -> message.ifBlank { "The rewrite game connection failed." }
}
