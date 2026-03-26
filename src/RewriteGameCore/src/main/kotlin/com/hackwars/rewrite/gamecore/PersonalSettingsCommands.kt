package com.hackwars.rewrite.gamecore

import kotlinx.serialization.Serializable

@Serializable
data class RequestPersonalSettingsPayload(
    val ip: String? = null,
)

@Serializable
data class SavePersonalSettingsPayload(
    val ip: String,
    val imagePath: String? = null,
    val description: String? = null,
    val location: String? = null,
)

@Serializable
data class PersonalSettingsResponse(
    val stateId: GameStateId,
    val profile: PersonalSettingsProfile,
)

class RequestPersonalSettingsCommand(
    private val stateId: GameStateId,
    private val profileRepository: PersonalSettingsProfileRepository = NoOpPersonalSettingsProfileRepository,
) : RequestCommand<PersonalSettingsResponse> {
    override val name: String = "requestpersonalsettings"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): PersonalSettingsResponse {
        val state = context.requireExistingState(stateId)
        return PersonalSettingsResponse(
            stateId = stateId,
            profile = profileRepository.load(stateId) ?: defaultProfileFor(state),
        )
    }
}

class SavePersonalSettingsCommand(
    private val stateId: GameStateId,
    private val imagePath: String? = null,
    private val description: String? = null,
    private val location: String? = null,
    private val profileRepository: PersonalSettingsProfileRepository = NoOpPersonalSettingsProfileRepository,
) : RequestCommand<PersonalSettingsResponse> {
    override val name: String = "setpersonalsettings"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): PersonalSettingsResponse {
        val state = context.requireExistingState(stateId)
        val current = profileRepository.load(stateId) ?: defaultProfileFor(state)
        val updated = current.copy(
            imagePath = normalizeImagePath(imagePath, current.imagePath),
            description = normalizeFreeText(description, current.description),
            location = normalizeFreeText(location, current.location),
        )
        profileRepository.save(stateId, updated)
        return PersonalSettingsResponse(
            stateId = stateId,
            profile = updated,
        )
    }
}

private fun defaultProfileFor(state: ComputerState): PersonalSettingsProfile {
    return PersonalSettingsProfile(
        displayName = state.identity.displayName.ifBlank {
            state.identity.playFabId.ifBlank { state.id.value }
        },
        imagePath = DEFAULT_PERSONAL_SETTINGS_IMAGE_PATH,
        description = "",
        location = state.network.currentNetworkName,
    )
}

private fun normalizeImagePath(
    candidate: String?,
    fallback: String,
): String {
    return candidate
        ?.trim()
        ?.takeUnless(String::isEmpty)
        ?: fallback
}

private fun normalizeFreeText(
    candidate: String?,
    fallback: String,
): String {
    return candidate?.trim() ?: fallback
}
