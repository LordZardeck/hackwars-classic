package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonalSettingsCommandsTest {
    @Test
    fun requestPersonalSettingsReturnsStoredProfileForLocalView() = runTest {
        val stateId = GameStateId("192.0.2.10")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to ComputerState.empty(
                    id = stateId,
                    playFabId = "PF-LOCAL",
                    playerIp = stateId.value,
                    displayName = "localuser",
                ),
            ),
        )
        val profileRepository = InMemoryPersonalSettingsProfileRepository(
            seededProfiles = mapOf(
                stateId to PersonalSettingsProfile(
                    displayName = "localuser",
                    imagePath = "images/Gunner001.png",
                    description = "Local operator profile",
                    location = "UGOPNet",
                ),
            ),
        )
        val dispatcher = DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = InMemoryInterestRegistry(),
        )

        val response = dispatcher.request(
            command = RequestPersonalSettingsCommand(
                stateId = stateId,
                profileRepository = profileRepository,
            ),
            metadata = CommandMetadata(authenticatedStateId = stateId),
            publisher = NoOpGameStatePublisher,
        )

        assertEquals(stateId, response.stateId)
        assertEquals("localuser", response.profile.displayName)
        assertEquals("images/Gunner001.png", response.profile.imagePath)
        assertEquals("Local operator profile", response.profile.description)
        assertEquals("UGOPNet", response.profile.location)
    }

    @Test
    fun savePersonalSettingsPersistsBlankLocationAndDefaultImageFallback() = runTest {
        val stateId = GameStateId("192.0.2.10")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to ComputerState.empty(
                    id = stateId,
                    playFabId = "PF-LOCAL",
                    playerIp = stateId.value,
                    displayName = "localuser",
                ),
            ),
        )
        val profileRepository = InMemoryPersonalSettingsProfileRepository(
            seededProfiles = mapOf(
                stateId to PersonalSettingsProfile(
                    displayName = "localuser",
                    imagePath = DEFAULT_PERSONAL_SETTINGS_IMAGE_PATH,
                    description = "Before",
                    location = "BeforeNet",
                ),
            ),
        )
        val dispatcher = DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = InMemoryInterestRegistry(),
        )

        val response = dispatcher.request(
            command = SavePersonalSettingsCommand(
                stateId = stateId,
                imagePath = "",
                description = "After",
                location = "",
                profileRepository = profileRepository,
            ),
            metadata = CommandMetadata(authenticatedStateId = stateId),
            publisher = NoOpGameStatePublisher,
        )

        val savedProfile = profileRepository.load(stateId)
        assertEquals(DEFAULT_PERSONAL_SETTINGS_IMAGE_PATH, response.profile.imagePath)
        assertEquals("After", response.profile.description)
        assertEquals("", response.profile.location)
        assertEquals(
            PersonalSettingsProfile(
                displayName = "localuser",
                imagePath = DEFAULT_PERSONAL_SETTINGS_IMAGE_PATH,
                description = "After",
                location = "",
            ),
            savedProfile,
        )
    }
}
