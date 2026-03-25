package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.StringHookValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttackCommandsTest {
    @Test
    fun requestAttackDebitsCashReservesCpuPersistsSessionAndSchedulesProgram() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(attackerId),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(
                    secondaryPorts = listOf(7, 8),
                    maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
                    extraInfo = listOf(StringHookValue("alpha")),
                ),
                windowHandle = 9,
                attackProgramRegistry = registry,
                clock = { 1_000L },
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-start"),
            publisher = publisher,
        )
        val updated = repository.load(attackerId)

        requireNotNull(updated)
        assertTrue(response.accepted)
        assertEquals(10.0, response.chargedAmount)
        assertEquals(90.0, response.pettyCashAfter)
        assertEquals(8.0, response.currentCpuLoadAfter)
        assertEquals(setOf("economy", "ports", "combat", "runtime"), publisher.deltas.single().second.deltaKeys)
        assertTrue(updated.port(12)?.attacking == true)
        assertEquals(90.0, updated.economy.pettyCash)
        assertEquals(8.0, updated.runtime.currentCpuLoad)
        assertEquals(1, updated.combat.activeAttacksBySourcePort.size)
        assertEquals(listOf(7, 8), updated.combat.activeAttacksBySourcePort.getValue(12).secondaryPorts)
        assertEquals("worm.bin", updated.combat.activeAttacksBySourcePort.getValue(12).maliciousScripts.single()?.name)
        assertEquals("alpha", (updated.combat.activeAttacksBySourcePort.getValue(12).extraInfo.single() as StringHookValue).value)

        runCurrent()

        assertEquals(
            listOf(ProgramLifecycleStatus.RUNNING),
            publisher.programUpdates.map { it.second.status },
        )
        assertTrue(publisher.programUpdates.all { it.first == setOf("attacker-conn") })
    }

    @Test
    fun requestAttackRejectsMismatchedSourcePortAndResourceFailuresWithoutMutation() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")

        val sourceMismatch = requestAttack(
            attackerState = attackerState(attackerId),
            targetState = targetState(targetId),
            sourceIp = "OTHER-IP",
        )
        val invalidSourcePort = requestAttack(
            attackerState = attackerState(attackerId).copy(
                ports = attackerState(attackerId).ports.map { port ->
                    if (port.number == 12) {
                        port.copy(installedApplication = port.installedApplication?.copy(kind = ApplicationKind.HTTP))
                    } else {
                        port
                    }
                },
            ),
            targetState = targetState(targetId),
        )
        val alreadyAttacking = requestAttack(
            attackerState = attackerState(attackerId).copy(
                ports = attackerState(attackerId).ports.markAttacking(12, true),
                combat = CombatState(
                    activeAttacksBySourcePort = mapOf(
                        12 to AttackSessionState(
                            programId = "attack-live",
                            sourcePort = 12,
                            targetStateId = targetId,
                            targetPort = 25,
                        ),
                    ),
                ),
                runtime = RuntimeState(currentCpuLoad = 8.0),
            ),
            targetState = targetState(targetId),
        )
        val missingBank = requestAttack(
            attackerState = attackerState(attackerId).copy(
                economy = attackerState(attackerId).economy.copy(defaultBankPort = null),
            ),
            targetState = targetState(targetId),
        )
        val lowCash = requestAttack(
            attackerState = attackerState(attackerId).copy(
                economy = attackerState(attackerId).economy.copy(pettyCash = 5.0),
            ),
            targetState = targetState(targetId),
        )
        val overheated = requestAttack(
            attackerState = attackerState(attackerId).copy(
                runtime = RuntimeState(currentCpuLoad = 101.0),
            ),
            targetState = targetState(targetId),
        )
        val cpuLimited = requestAttack(
            attackerState = attackerState(attackerId).copy(
                runtime = RuntimeState(currentCpuLoad = 95.0),
            ),
            targetState = targetState(targetId),
        )
        val selfTarget = requestAttack(
            attackerState = attackerState(attackerId),
            targetState = attackerState(attackerId),
            targetStateId = attackerId,
        )
        val invalidTargetPort = requestAttack(
            attackerState = attackerState(attackerId),
            targetState = targetState(targetId).copy(
                ports = listOf(
                    PortState(
                        number = 25,
                        type = "http",
                        enabled = false,
                    ),
                ),
            ),
        )

        assertEquals(AttackStartFailureCode.SOURCE_IP_MISMATCH, sourceMismatch.response.failureCode)
        assertEquals(AttackStartFailureCode.INVALID_SOURCE_PORT, invalidSourcePort.response.failureCode)
        assertEquals(AttackStartFailureCode.SOURCE_ALREADY_ATTACKING, alreadyAttacking.response.failureCode)
        assertEquals(AttackStartFailureCode.ACTIVE_BANK_REQUIRED, missingBank.response.failureCode)
        assertEquals(AttackStartFailureCode.INSUFFICIENT_PETTY_CASH, lowCash.response.failureCode)
        assertEquals(AttackStartFailureCode.OVERHEATED, overheated.response.failureCode)
        assertEquals(AttackStartFailureCode.CPU_HEADROOM_EXCEEDED, cpuLimited.response.failureCode)
        assertEquals(AttackStartFailureCode.SELF_TARGET, selfTarget.response.failureCode)
        assertEquals(AttackStartFailureCode.INVALID_TARGET_PORT, invalidTargetPort.response.failureCode)
        assertFalse(sourceMismatch.response.accepted)
        assertTrue(sourceMismatch.publisher.deltas.isEmpty())
        assertTrue(cpuLimited.publisher.deltas.isEmpty())
        assertNull(cpuLimited.repository.load(attackerId)?.combat?.activeAttacksBySourcePort?.get(12))
    }

    @Test
    fun attackTicksIncrementIterationCountWithoutMutatingTheTarget() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(attackerId),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        dispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-tick"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = repository.load(attackerId)
        val updatedTarget = repository.load(targetId)

        requireNotNull(updatedAttacker)
        requireNotNull(updatedTarget)
        assertEquals(1, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).iterationCount)
        assertEquals(setOf("combat"), publisher.deltas.single().second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
        assertEquals(0, updatedTarget.version)
    }

    @Test
    fun requestCancelAttackIsNoOpWithoutSessionAndCleansUpWhenOneExists() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(attackerId),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        val noOp = dispatcher.request(
            command = RequestCancelAttackCommand(
                attackerStateId = attackerId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "cancel-noop"),
            publisher = publisher,
        )

        assertTrue(noOp.accepted)
        assertFalse(noOp.hadActiveSession)
        assertTrue(publisher.deltas.isEmpty())

        dispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-cancel"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        val cancelled = dispatcher.request(
            command = RequestCancelAttackCommand(
                attackerStateId = attackerId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "cancel-live"),
            publisher = publisher,
        )
        runCurrent()
        val updated = repository.load(attackerId)

        requireNotNull(updated)
        assertTrue(cancelled.accepted)
        assertTrue(cancelled.hadActiveSession)
        assertEquals(setOf("ports", "combat", "runtime"), publisher.deltas.single().second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
        assertTrue(updated.combat.activeAttacksBySourcePort.isEmpty())
        assertFalse(updated.port(12)?.attacking == true)
        assertEquals(0.0, updated.runtime.currentCpuLoad)
    }

    @Test
    fun attackTimeoutCleanupMatchesCancelCleanup() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(attackerId),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        dispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-timeout"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(450_100)
        runCurrent()

        val updated = repository.load(attackerId)

        requireNotNull(updated)
        assertTrue(updated.combat.activeAttacksBySourcePort.isEmpty())
        assertFalse(updated.port(12)?.attacking == true)
        assertEquals(0.0, updated.runtime.currentCpuLoad)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.last().second.status)
    }

    @Test
    fun requestAttackDefaultResolvesBankAndAttackSourcePorts() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")

        val bankFixture = requestAttackDefault(
            attackerState = attackerState(attackerId, bankIsAlsoAttack = true),
            targetState = targetState(targetId),
            target = "Bank",
        )
        val attackFixture = requestAttackDefault(
            attackerState = attackerState(attackerId, bankIsAlsoAttack = true),
            targetState = targetState(targetId),
            target = "Attack",
        )

        assertTrue(bankFixture.response.accepted)
        assertEquals(6, bankFixture.response.session?.sourcePort)
        assertTrue(attackFixture.response.accepted)
        assertEquals(12, attackFixture.response.session?.sourcePort)
    }

    @Test
    fun bootstrapRefreshClearsStalePersistedSessionsAndAttackingFlags() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val staleState = attackerState(attackerId).copy(
            ports = attackerState(attackerId).ports.markAttacking(12, true),
            combat = CombatState(
                activeAttacksBySourcePort = mapOf(
                    12 to AttackSessionState(
                        programId = "stale-program",
                        sourcePort = 12,
                        targetStateId = GameStateId("TARGET-IP"),
                        targetPort = 25,
                        iterationCount = 2,
                    ),
                ),
            ),
            runtime = RuntimeState(currentCpuLoad = 8.0),
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(attackerId to staleState),
        )
        val interests = InMemoryInterestRegistry()
        val dispatcher = dispatcher(repository, interests)

        val result = dispatcher.request(
            command = GameSessionBootstrapCommand(
                stateId = attackerId,
                playFabId = "PF-ATTACKER",
                interestRegistry = interests,
                attackProgramRegistry = InMemoryAttackProgramRegistry(),
                clock = { 10_000L },
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn"),
            publisher = NoOpGameStatePublisher,
        )

        assertTrue(result.state.combat.activeAttacksBySourcePort.isEmpty())
        assertFalse(result.state.port(12)?.attacking == true)
        assertEquals(0.0, result.state.runtime.currentCpuLoad)
        assertTrue(repository.load(attackerId)?.combat?.activeAttacksBySourcePort?.isEmpty() == true)
    }

    private suspend fun TestScope.requestAttack(
        attackerState: ComputerState,
        targetState: ComputerState,
        sourceIp: String = attackerState.id.value,
        targetStateId: GameStateId = targetState.id,
    ): AttackFixtureResult {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerState.id to attackerState,
                targetState.id to targetState,
            ),
        )
        val interests = InMemoryInterestRegistry()
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerState.id,
                targetStateId = targetStateId,
                sourceIp = sourceIp,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            publisher = publisher,
        )

        return AttackFixtureResult(response, repository, publisher)
    }

    private suspend fun TestScope.requestAttackDefault(
        attackerState: ComputerState,
        targetState: ComputerState,
        target: String,
    ): AttackFixtureResult {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerState.id to attackerState,
                targetState.id to targetState,
            ),
        )
        val interests = InMemoryInterestRegistry()
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestAttackDefaultCommand(
                attackerStateId = attackerState.id,
                targetStateId = targetState.id,
                targetPort = 25,
                target = target,
                attackProgramRegistry = registry,
            ),
            publisher = publisher,
        )

        return AttackFixtureResult(response, repository, publisher)
    }

    private fun TestScope.dispatcher(
        repository: InMemoryComputerStateRepository,
        interests: InMemoryInterestRegistry,
    ): DefaultCommandDispatcher {
        return DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = interests,
            programScheduler = CoroutineProgramScheduler(
                dispatcher = null,
                interestRegistry = interests,
                coroutineScope = backgroundScope,
            ),
        )
    }

    private data class AttackFixtureResult(
        val response: AttackStartResponse,
        val repository: InMemoryComputerStateRepository,
        val publisher: RecordingGameStatePublisher,
    )

    private fun attackerState(
        stateId: GameStateId,
        bankIsAlsoAttack: Boolean = false,
    ): ComputerState {
        val bankApplication = InstalledApplication(
            name = "bank.bin",
            kind = if (bankIsAlsoAttack) ApplicationKind.ATTACK else ApplicationKind.BANKING,
            banking = true,
            cpuCost = if (bankIsAlsoAttack) 4.0 else 2.0,
        )
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            economy = EconomyState(
                pettyCash = 100.0,
                bankMoney = 50.0,
                defaultBankPort = 6,
            ),
            hardware = HardwareState(
                cpuMax = 100.0,
                hdMaximum = 100,
            ),
            ports = listOf(
                PortState(
                    number = 6,
                    type = "bank",
                    enabled = true,
                    defaultPort = false,
                    installedApplication = bankApplication,
                ),
                PortState(
                    number = 12,
                    type = "attack",
                    enabled = true,
                    defaultPort = true,
                    installedApplication = InstalledApplication(
                        name = "attack.bin",
                        kind = ApplicationKind.ATTACK,
                        cpuCost = 8.0,
                    ),
                ),
            ),
        )
    }

    private fun targetState(stateId: GameStateId): ComputerState {
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            ports = listOf(
                PortState(
                    number = 25,
                    type = "http",
                    enabled = true,
                ),
            ),
        )
    }
}
