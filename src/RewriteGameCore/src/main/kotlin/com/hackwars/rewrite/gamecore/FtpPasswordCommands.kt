package com.hackwars.rewrite.gamecore

import kotlinx.serialization.Serializable

@Serializable
data class SetFtpPasswordPayload(
    val ip: String,
    val password: String? = null,
)

@Serializable
data class SetFtpPasswordResponse(
    val stateId: GameStateId,
    val passwordSet: Boolean,
)

class SetFtpPasswordCommand(
    private val stateId: GameStateId,
    private val password: String?,
    private val ftpPasswordRepository: FtpPasswordRepository,
) : RequestCommand<SetFtpPasswordResponse> {
    override val name: String = "setftppassword"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): SetFtpPasswordResponse {
        val normalizedPassword = password?.takeUnless { it.isEmpty() }
        ftpPasswordRepository.save(stateId, normalizedPassword)
        return SetFtpPasswordResponse(
            stateId = stateId,
            passwordSet = !normalizedPassword.isNullOrEmpty(),
        )
    }
}
