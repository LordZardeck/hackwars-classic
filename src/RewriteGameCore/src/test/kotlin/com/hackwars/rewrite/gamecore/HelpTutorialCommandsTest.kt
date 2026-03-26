package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpTutorialCommandsTest {
    @Test
    fun requestHelpTopicListReturnsRequestedLegacyGroupBackedByRealIpv4Targets() = runTest {
        val stateId = GameStateId("192.0.2.10")
        val dispatcher = DefaultCommandDispatcher(
            repository = InMemoryComputerStateRepository(
                seededStates = mapOf(
                    stateId to ComputerState.empty(
                        id = stateId,
                        playFabId = "PF-LOCALUSER",
                        playerIp = stateId.value,
                    ),
                ),
            ),
            interestRegistry = InMemoryInterestRegistry(),
        )

        val response = dispatcher.request(
            command = RequestHelpTopicListCommand(
                requesterStateId = stateId,
                topicGroup = "Banking",
                repository = DefaultRetainedHelpTutorialRepository(),
            ),
            metadata = CommandMetadata(authenticatedStateId = stateId),
            publisher = NoOpGameStatePublisher,
        )

        assertEquals("Banking", response.topicGroup)
        assertEquals(
            listOf("Deposit Money", "Withdraw Money", "Transfer Money"),
            response.topics.map { it.name },
        )
        assertTrue(response.topics.all { it.targetUrl.matches(Regex("http://\\d+\\.\\d+\\.\\d+\\.\\d+/?")) })
    }

    @Test
    fun requestTutorialReturnsRetainedOpeningMessage() = runTest {
        val stateId = GameStateId("192.0.2.10")
        val dispatcher = DefaultCommandDispatcher(
            repository = InMemoryComputerStateRepository(
                seededStates = mapOf(
                    stateId to ComputerState.empty(
                        id = stateId,
                        playFabId = "PF-LOCALUSER",
                        playerIp = stateId.value,
                    ),
                ),
            ),
            interestRegistry = InMemoryInterestRegistry(),
        )

        val response = dispatcher.request(
            command = RequestTutorialCommand(
                requesterStateId = stateId,
                tutorialId = RETAINED_FIRST_ATTACK_TUTORIAL_ID,
                repository = DefaultRetainedHelpTutorialRepository(),
            ),
            metadata = CommandMetadata(authenticatedStateId = stateId),
            publisher = NoOpGameStatePublisher,
        )

        assertEquals("First Attack", response.title)
        assertTrue(response.body.contains("Welcome to Hack Wars!"))
        assertTrue(response.body.contains("Store"))
    }

    @Test
    fun requestWebpageRendersRetainedHelpPagesWithoutComputerState() = runTest {
        val sourceStateId = GameStateId("192.0.2.10")
        val dispatcher = DefaultCommandDispatcher(
            repository = InMemoryComputerStateRepository(
                seededStates = mapOf(
                    sourceStateId to ComputerState.empty(
                        id = sourceStateId,
                        playFabId = "PF-LOCALUSER",
                        playerIp = sourceStateId.value,
                    ),
                ),
            ),
            interestRegistry = InMemoryInterestRegistry(),
        )

        val response = dispatcher.request(
            command = RequestWebpageCommand(
                sourceStateId = sourceStateId,
                targetStateId = GameStateId("203.0.113.210"),
                parameters = emptyMap(),
                retainedHelpTutorialRepository = DefaultRetainedHelpTutorialRepository(),
            ),
            metadata = CommandMetadata(authenticatedStateId = sourceStateId),
            publisher = NoOpGameStatePublisher,
        )

        assertEquals(GameStateId("203.0.113.210"), response.resolvedTargetStateId)
        assertEquals("First Attack", response.title)
        assertTrue(response.body.contains("Port Management"))
        assertFalse(response.fallback)
        assertEquals(0L, response.version)
    }
}
