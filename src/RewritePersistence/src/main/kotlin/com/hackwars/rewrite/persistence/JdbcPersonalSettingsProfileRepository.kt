package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.DEFAULT_PERSONAL_SETTINGS_IMAGE_PATH
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.PersonalSettingsProfile
import com.hackwars.rewrite.gamecore.PersonalSettingsProfileRepository
import java.sql.Connection

class JdbcPersonalSettingsProfileRepository(
    private val connectionFactory: () -> Connection,
) : PersonalSettingsProfileRepository {
    private val projections = JdbcPlayerComputerProjectionRepository(connectionFactory)

    override suspend fun load(stateId: GameStateId): PersonalSettingsProfile? {
        val projection = projections.findComputerProjection(stateId.value) ?: return null
        val profile = projections.findPlayerProfile(projection.playerId) ?: return null
        return PersonalSettingsProfile(
            displayName = profile.displayName,
            imagePath = profile.profileImagePath.ifBlank { DEFAULT_PERSONAL_SETTINGS_IMAGE_PATH },
            description = profile.profileDescription,
            location = profile.profileLocation,
        )
    }

    override suspend fun save(stateId: GameStateId, profile: PersonalSettingsProfile) {
        val projection = checkNotNull(projections.findComputerProjection(stateId.value)) {
            "No computer projection exists for ${stateId.value}."
        }
        val existingProfile = projections.findPlayerProfile(projection.playerId)
        projections.upsertPlayerProfile(
            PersistedPlayerProfile(
                playerId = projection.playerId,
                displayName = profile.displayName.ifBlank {
                    existingProfile?.displayName?.takeUnless { it.isBlank() } ?: projection.playerId
                },
                profileImagePath = profile.imagePath.ifBlank { DEFAULT_PERSONAL_SETTINGS_IMAGE_PATH },
                profileDescription = profile.description,
                profileLocation = profile.location,
                profilePayload = existingProfile?.profilePayload ?: "{}",
            ),
        )
    }
}
