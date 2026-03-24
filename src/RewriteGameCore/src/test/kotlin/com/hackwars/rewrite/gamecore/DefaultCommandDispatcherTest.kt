package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DefaultCommandDispatcherTest {
    @Test
    fun requestReturnsOnlyAfterDeltaPublicationCompletes() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER"),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val dispatcher = DefaultCommandDispatcher(repository = repository, interestRegistry = interests)
        val publicationOrder = mutableListOf<String>()
        val publisher = object : GameStatePublisher {
            override suspend fun publishSnapshot(connectionIds: Set<String>, snapshot: ComputerState) = Unit

            override suspend fun publishDelta(connectionIds: Set<String>, delta: ComputerDelta) {
                publicationOrder += "delta-start"
                delay(25)
                publicationOrder += "delta-end"
            }

            override suspend fun publishProgramUpdate(connectionIds: Set<String>, update: ProgramUpdate) = Unit

            override suspend fun publishUiEvent(connectionIds: Set<String>, event: GameUiEvent) = Unit
        }

        val response = dispatcher.request(
            command = SetPreferenceCommand(
                stateId = stateId,
                key = "show_clock",
                value = "true",
            ),
            metadata = CommandMetadata(connectionId = "conn-1", requestId = "pref-1"),
            publisher = publisher,
        )
        publicationOrder += "response-returned"

        assertEquals(
            listOf("delta-start", "delta-end", "response-returned"),
            publicationOrder,
        )
        assertEquals(1, response.version)
        assertEquals("true", repository.load(stateId)?.preferences?.values?.get("show_clock"))
    }

    @Test
    fun concurrentMultiStateRequestsUseStableLockOrderingWithoutDeadlock() = runTest {
        val first = GameStateId("A-IP")
        val second = GameStateId("B-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                first to ComputerState.empty(id = first),
                second to ComputerState.empty(id = second),
            ),
        )
        val dispatcher = DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = InMemoryInterestRegistry(),
        )

        val left = async {
            dispatcher.request(TwoStateInspectionCommand(setOf(second, first)))
        }
        val right = async {
            dispatcher.request(TwoStateInspectionCommand(setOf(first, second)))
        }

        val results = withTimeout(1.seconds) {
            listOf(left.await(), right.await())
        }

        assertEquals(listOf("A-IP|B-IP", "A-IP|B-IP"), results)
    }

    private class TwoStateInspectionCommand(
        override val targetStateIds: Set<GameStateId>,
    ) : RequestCommand<String> {
        override val name: String = "two-state-inspection"
        override val lifetime: CommandLifetime = CommandLifetime.defaultRequest

        override suspend fun execute(context: CommandContext): String {
            val loaded = context.loadStates(targetStateIds)
            return loaded.keys.map { it.value }.sorted().joinToString("|")
        }
    }
}
