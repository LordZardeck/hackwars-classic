package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineProgramSchedulerTest {
    @Test
    fun programSchedulerPublishesScopedRunningAndCompletionUpdates() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val dispatcher = DefaultCommandDispatcher(
            repository = InMemoryComputerStateRepository(
                seededStates = mapOf(stateId to ComputerState.empty(id = stateId)),
            ),
            interestRegistry = interests,
            programScheduler = CoroutineProgramScheduler(
                dispatcher = null,
                interestRegistry = interests,
                coroutineScope = backgroundScope,
            ),
        )
        val publisher = RecordingGameStatePublisher()

        dispatcher.schedule(
            command = CountingProgramCommand(stateId),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        runCurrent()
        advanceTimeBy(2_100)
        runCurrent()

        assertTrue(publisher.snapshots.isEmpty())
        assertEquals(
            listOf(
                ProgramLifecycleStatus.RUNNING,
                ProgramLifecycleStatus.RUNNING,
                ProgramLifecycleStatus.COMPLETED,
            ),
            publisher.programUpdates.map { (_, update) -> update.status },
        )
        assertTrue(publisher.programUpdates.all { (connections, _) -> connections == setOf("conn-1") })
    }

    @Test
    fun programSchedulerSupportsExplicitCancellationAndLifetimeExpiry() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val dispatcher = DefaultCommandDispatcher(
            repository = InMemoryComputerStateRepository(
                seededStates = mapOf(stateId to ComputerState.empty(id = stateId)),
            ),
            interestRegistry = interests,
            programScheduler = CoroutineProgramScheduler(
                dispatcher = null,
                interestRegistry = interests,
                coroutineScope = backgroundScope,
            ),
        )
        val cancelledPublisher = RecordingGameStatePublisher()
        val handle = dispatcher.schedule(
            command = EndlessProgramCommand(
                stateId = stateId,
                lifetime = CommandLifetime(20.seconds),
                programId = "cancel-me",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = cancelledPublisher,
        )

        runCurrent()
        handle.cancel("user requested stop")
        runCurrent()

        assertEquals(
            ProgramLifecycleStatus.CANCELLED,
            cancelledPublisher.programUpdates.last().second.status,
        )

        val expiryPublisher = RecordingGameStatePublisher()
        dispatcher.schedule(
            command = EndlessProgramCommand(
                stateId = stateId,
                lifetime = CommandLifetime(2.seconds),
                programId = "expire-me",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = expiryPublisher,
        )

        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(
            ProgramLifecycleStatus.CANCELLED,
            expiryPublisher.programUpdates.last().second.status,
        )
        assertTrue(
            expiryPublisher.programUpdates.last().second.progress.message.contains("lifetime", ignoreCase = true),
        )
    }

    private class CountingProgramCommand(
        private val stateId: GameStateId,
    ) : ProgramCommand {
        private var ticks: Int = 0

        override val name: String = "counting-program"
        override val lifetime: CommandLifetime = CommandLifetime(10.seconds)
        override val targetStateIds: Set<GameStateId> = setOf(stateId)
        override val programId: String = "program-counting"
        override val programType: String = "attack"
        override val tickInterval = 1.seconds

        override suspend fun onStart(context: CommandContext): ProgramExecutionStep {
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.RUNNING,
                progress = ProgramProgress(message = "started", completedSteps = 0, totalSteps = 2),
                relatedStateIds = targetStateIds,
            )
        }

        override suspend fun onTick(context: CommandContext): ProgramExecutionStep {
            ticks += 1
            return if (ticks >= 2) {
                ProgramExecutionStep(
                    status = ProgramLifecycleStatus.COMPLETED,
                    progress = ProgramProgress(message = "done", completedSteps = 2, totalSteps = 2),
                    relatedStateIds = targetStateIds,
                )
            } else {
                ProgramExecutionStep(
                    status = ProgramLifecycleStatus.RUNNING,
                    progress = ProgramProgress(message = "tick", completedSteps = ticks, totalSteps = 2),
                    relatedStateIds = targetStateIds,
                )
            }
        }
    }

    private class EndlessProgramCommand(
        private val stateId: GameStateId,
        override val lifetime: CommandLifetime,
        override val programId: String,
    ) : ProgramCommand {
        override val name: String = "endless-program"
        override val targetStateIds: Set<GameStateId> = setOf(stateId)
        override val programType: String = "attack"
        override val tickInterval = 1.seconds

        override suspend fun onTick(context: CommandContext): ProgramExecutionStep {
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.RUNNING,
                progress = ProgramProgress(message = "still running", completedSteps = 0, totalSteps = 0),
                relatedStateIds = targetStateIds,
            )
        }
    }
}
