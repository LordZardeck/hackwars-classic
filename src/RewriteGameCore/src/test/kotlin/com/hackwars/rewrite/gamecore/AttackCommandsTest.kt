package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.StringHookValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttackCommandsTest {
    @Test
    fun requestAttackDebitsCashReservesCpuPersistsBilateralCombatStateAndSchedulesProgram() = runTest {
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
        assertEquals(2, publisher.deltas.size)
        assertEquals(setOf("attacker-conn"), publisher.deltas[0].first)
        assertEquals(setOf("economy", "ports", "combat", "runtime"), publisher.deltas[0].second.deltaKeys)
        assertEquals(setOf("target-conn"), publisher.deltas[1].first)
        assertEquals(setOf("combat"), publisher.deltas[1].second.deltaKeys)
        assertTrue(updated.port(12)?.attacking == true)
        assertEquals(90.0, updated.economy.pettyCash)
        assertEquals(8.0, updated.runtime.currentCpuLoad)
        assertEquals(1, updated.combat.activeAttacksBySourcePort.size)
        assertEquals(listOf(7, 8), updated.combat.activeAttacksBySourcePort.getValue(12).secondaryPorts)
        assertEquals("worm.bin", updated.combat.activeAttacksBySourcePort.getValue(12).maliciousScripts.single()?.name)
        assertEquals("alpha", (updated.combat.activeAttacksBySourcePort.getValue(12).extraInfo.single() as StringHookValue).value)
        assertEquals(listOf(25, 7, 8), updated.combat.activeAttacksBySourcePort.getValue(12).targetCyclePorts)
        assertEquals(0, updated.combat.activeAttacksBySourcePort.getValue(12).targetCycleCursor)
        assertEquals(targetId, updated.combat.activeAttacksBySourcePort.getValue(12).targetView.targetStateId)
        assertEquals(25, updated.combat.activeAttacksBySourcePort.getValue(12).targetView.targetPort)
        assertEquals(100.0, updated.combat.activeAttacksBySourcePort.getValue(12).targetView.health)
        assertEquals(0.0, updated.combat.activeAttacksBySourcePort.getValue(12).targetView.lastAppliedDamage)
        assertFalse(updated.combat.activeAttacksBySourcePort.getValue(12).targetView.completed)

        val updatedTarget = repository.load(targetId)
        requireNotNull(updatedTarget)
        assertEquals(attackerId, updatedTarget.combat.incomingAttacksByTargetPort.getValue(25).attackerStateId)
        assertEquals(12, updatedTarget.combat.incomingAttacksByTargetPort.getValue(25).attackerSourcePort)
        assertEquals(9, updatedTarget.combat.incomingAttacksByTargetPort.getValue(25).windowHandle)
        assertEquals(targetId, response.session?.targetView?.targetStateId)
        assertEquals(25, response.session?.targetView?.targetPort)
        assertEquals(100.0, response.session?.targetView?.health)
        assertEquals(listOf(25, 7, 8), response.session?.targetCyclePorts)
        assertEquals(0, response.session?.targetCycleCursor)

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
        val targetAlreadyUnderAttack = requestAttack(
            attackerState = attackerState(attackerId),
            targetState = targetState(targetId).copy(
                combat = CombatState(
                    incomingAttacksByTargetPort = mapOf(
                        25 to IncomingAttackState(
                            attackerStateId = GameStateId("OTHER-IP"),
                            attackerSourcePort = 44,
                            targetPort = 25,
                            startedAtEpochMillis = 1_000L,
                            windowHandle = 2,
                        ),
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
        assertEquals(AttackStartFailureCode.TARGET_ALREADY_UNDER_ATTACK, targetAlreadyUnderAttack.response.failureCode)
        assertFalse(sourceMismatch.response.accepted)
        assertTrue(sourceMismatch.publisher.deltas.isEmpty())
        assertTrue(cpuLimited.publisher.deltas.isEmpty())
        assertNull(cpuLimited.repository.load(attackerId)?.combat?.activeAttacksBySourcePort?.get(12))
    }

    @Test
    fun attackTicksDamageTargetAwardXpAndEmitHealthTriggerWithoutRunningHealthWatches() = runTest {
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
        val passiveWatchSink = RecordingPassiveWatchTriggerSink()
        val dispatcher = dispatcher(repository, interests, passiveWatchSink = passiveWatchSink)

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
        passiveWatchSink.triggers.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = repository.load(attackerId)
        val updatedTarget = repository.load(targetId)

        requireNotNull(updatedAttacker)
        requireNotNull(updatedTarget)
        assertEquals(1, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).iterationCount)
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(2.2, updatedAttacker.stats.skillExperience(ScriptFamily.ATTACK))
        assertEquals(97.8, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetView.health)
        assertEquals(2.2, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetView.lastAppliedDamage)
        assertFalse(updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetView.completed)
        assertEquals(2, publisher.deltas.size)
        assertEquals(setOf("target-conn"), publisher.deltas[0].first)
        assertEquals(setOf("ports"), publisher.deltas[0].second.deltaKeys)
        assertEquals(setOf("attacker-conn"), publisher.deltas[1].first)
        assertEquals(setOf("combat", "stats"), publisher.deltas[1].second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
        assertEquals(
            listOf<PassiveWatchTrigger>(
                PassiveWatchTrigger.HealthChanged(
                    targetStateId = targetId,
                    sourceIp = attackerId.value,
                    external = true,
                    portNumber = 25,
                    sourcePort = 12,
                    previousHealth = 100.0,
                    newHealth = 97.8,
                ),
            ),
            passiveWatchSink.triggers,
        )
    }

    @Test
    fun requestAttackRunsInitializeScriptAndFlushesAttackerLogsBeforeResponse() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(initialize = """int main() { logMessage("init"); return 0; }"""),
                ),
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
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-init"),
            publisher = publisher,
        )

        val updatedAttacker = repository.load(attackerId)

        requireNotNull(updatedAttacker)
        assertTrue(response.accepted)
        assertEquals(1, updatedAttacker.logs.entries.size)
        assertContains(updatedAttacker.logs.entries.single().renderedLine, "init")
        assertEquals(2, publisher.deltas.size)
        assertEquals(setOf("economy", "ports", "combat", "runtime", "logs"), publisher.deltas[0].second.deltaKeys)
        assertEquals(setOf("combat"), publisher.deltas[1].second.deltaKeys)
    }

    @Test
    fun attackTicksRunContinueScriptBeforeDamageStateFlush() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(continueScript = """int main() { logMessage("tick"); return 0; }"""),
                ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-continue"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = repository.load(attackerId)

        requireNotNull(updatedAttacker)
        assertEquals(1, updatedAttacker.logs.entries.size)
        assertContains(updatedAttacker.logs.entries.single().renderedLine, "tick")
        val targetDelta = publisher.deltas.single { it.first == setOf("target-conn") }.second
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("ports"), targetDelta.deltaKeys)
        assertEquals(setOf("combat", "stats", "logs"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueDeleteLogsRemovesMatchingTargetLogsStillDamagesAndCancelsAttack() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { deleteLogs("REMOTE-IP"); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    logs = listOf(
                        ComputerLogEntry(1L, "first", "REMOTE-IP"),
                        ComputerLogEntry(2L, "second", "OTHER-IP"),
                    ),
                ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-delete-logs"),
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
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(listOf("OTHER-IP"), updatedTarget.logs.entries.map { it.sourceIp })
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(2.2, updatedAttacker.stats.skillExperience(ScriptFamily.ATTACK))
        assertEquals(2, publisher.deltas.size)
        val targetDelta = publisher.deltas.single { it.first == setOf("target-conn") }.second
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("logs", "ports", "combat"), targetDelta.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueEditLogsMutatesTargetRenderedLinesStillDamagesAndCancelsAttack() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { editLogs("REMOTE", "LOCAL"); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    logs = listOf(
                        ComputerLogEntry(1L, "REMOTE touched this host", "REMOTE-IP"),
                    ),
                ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-edit-logs"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = repository.load(targetId)
        requireNotNull(updatedTarget)
        assertEquals("LOCAL touched this host", updatedTarget.logs.entries.single().renderedLine)
        assertEquals(97.8, updatedTarget.port(25)?.health)
        val targetDelta = publisher.deltas.single { it.first == setOf("target-conn") }.second
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("logs", "ports", "combat"), targetDelta.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueCancelAttackStillDamagesAndCancelsWithoutTargetLogMutation() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { cancelAttack(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    logs = listOf(ComputerLogEntry(1L, "unchanged", "REMOTE-IP")),
                ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-cancel-helper"),
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
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(listOf("unchanged"), updatedTarget.logs.entries.map { it.renderedLine })
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        val targetDelta = publisher.deltas.single { it.first == setOf("target-conn") }.second
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("ports", "combat"), targetDelta.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun attackCompletionRunsFinalizeScriptBeforeCleanupFlush() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(finalize = """int main() { logMessage("finalize"); return 0; }"""),
                ),
                targetId to targetState(targetId).copy(
                    ports = listOf(
                        PortState(
                            number = 25,
                            type = "http",
                            enabled = true,
                            health = 1.5,
                        ),
                    ),
                ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-finalize"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = repository.load(attackerId)

        requireNotNull(updatedAttacker)
        assertEquals(1, updatedAttacker.logs.entries.size)
        assertContains(updatedAttacker.logs.entries.single().renderedLine, "finalize")
        val targetDelta = publisher.deltas.single { it.first == setOf("target-conn") }.second
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("ports", "combat"), targetDelta.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats", "logs"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun finalizeDeleteLogsMutatesTargetLogsBeforeCompletionCleanup() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        finalize = """int main() { deleteLogs("REMOTE-IP"); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    health = 1.5,
                    logs = listOf(
                        ComputerLogEntry(1L, "remove", "REMOTE-IP"),
                        ComputerLogEntry(2L, "keep", "OTHER-IP"),
                    ),
                ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-finalize-delete-logs"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = repository.load(targetId)
        requireNotNull(updatedTarget)
        assertEquals(listOf("OTHER-IP"), updatedTarget.logs.entries.map { it.sourceIp })
        assertEquals(0.0, updatedTarget.port(25)?.health)
        val targetDelta = publisher.deltas.single { it.first == setOf("target-conn") }.second
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("logs", "ports", "combat"), targetDelta.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun initializeDeleteLogsIsDiagnosticNoOpAndAttackRemainsActive() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        initialize = """int main() { deleteLogs("REMOTE-IP"); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    logs = listOf(ComputerLogEntry(1L, "keep", "REMOTE-IP")),
                ),
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
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-init-delete-logs"),
            publisher = publisher,
        )
        runCurrent()

        val updatedAttacker = repository.load(attackerId)
        val updatedTarget = repository.load(targetId)

        requireNotNull(updatedAttacker)
        requireNotNull(updatedTarget)
        assertTrue(response.accepted)
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.containsKey(12))
        assertEquals(listOf("REMOTE-IP"), updatedTarget.logs.entries.map { it.sourceIp })
        assertEquals(2, publisher.deltas.size)
        assertEquals(setOf("economy", "ports", "combat", "runtime"), publisher.deltas[0].second.deltaKeys)
        assertEquals(setOf("combat"), publisher.deltas[1].second.deltaKeys)
    }

    @Test
    fun attackScriptFailureDoesNotCancelAttackOrSuppressDeterministicDamage() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(continueScript = """int main() { emptyPettyCash(); return 0; }"""),
                ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-failure"),
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
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(1, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).iterationCount)
        assertTrue(updatedAttacker.logs.entries.isEmpty())
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
    }

    @Test
    fun berserkAddsExtraDamageSelfDamageAndExtraXpBeforeTheNormalPass() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(continueScript = """int main() { berserk(); return 0; }"""),
                ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-berserk"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(95.6, updatedTarget.port(25)?.health)
        assertEquals(98.9, updatedAttacker.port(12)?.health)
        assertEquals(4.4, updatedAttacker.stats.skillExperience(ScriptFamily.ATTACK))
        assertEquals(95.6, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetView.health)
        assertEquals(2.2, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetView.lastAppliedDamage)
        val targetDelta = publisher.deltas.single { it.first == setOf("target-conn") }.second
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("ports"), targetDelta.deltaKeys)
        assertEquals(setOf("combat", "stats", "ports"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
    }

    @Test
    fun berserkCanCompleteTheTargetBeforeTheNormalPass() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(continueScript = """int main() { berserk(); return 0; }"""),
                ),
                targetId to targetState(targetId, health = 1.5),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-berserk-complete"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(0.0, updatedTarget.port(25)?.health)
        assertEquals(98.9, updatedAttacker.port(12)?.health)
        assertEquals(2.2, updatedAttacker.stats.skillExperience(ScriptFamily.ATTACK))
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun switchAttackRetargetsToTheNextValidRingMemberAndDamagesThatPort() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(continueScript = """int main() { switchAttack(); return 0; }"""),
                ),
                targetId to targetState(
                    targetId,
                    additionalPorts = listOf(
                        PortState(number = 26, type = "ftp", enabled = false, health = 100.0),
                        PortState(number = 27, type = "bank", enabled = true, health = 100.0),
                    ),
                ),
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
                loadout = AttackLoadout(secondaryPorts = listOf(26, 27)),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-switch"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertEquals(97.8, updatedTarget.port(27)?.health)
        assertNull(updatedTarget.combat.incomingAttacksByTargetPort[25])
        assertEquals(12, updatedTarget.combat.incomingAttacksByTargetPort.getValue(27).attackerSourcePort)
        assertEquals(27, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetPort)
        assertEquals(2, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetCycleCursor)
        assertEquals(27, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetView.targetPort)
        val targetDelta = publisher.deltas.single { it.first == setOf("target-conn") }.second
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("ports", "combat"), targetDelta.deltaKeys)
        assertEquals(setOf("combat", "stats"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
    }

    @Test
    fun switchAttackThenFreezePreservesScriptOrderOnTheRetargetedPort() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { switchAttack(); freeze(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    additionalPorts = listOf(
                        PortState(number = 26, type = "ftp", enabled = true, health = 100.0),
                    ),
                ),
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
                loadout = AttackLoadout(secondaryPorts = listOf(26)),
                attackProgramRegistry = registry,
                clock = { 5_000L },
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-switch-freeze"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertEquals(100.0, updatedTarget.port(26)?.health)
        assertNull(updatedTarget.port(25)?.freezeExpiresAtEpochMillis)
        assertEquals(15_000L, updatedTarget.port(26)?.freezeExpiresAtEpochMillis)
        assertEquals(26, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetPort)
        assertEquals(0.0, updatedAttacker.stats.skillExperience(ScriptFamily.ATTACK))
        val targetDelta = publisher.deltas.single { it.first == setOf("target-conn") }.second
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("ports", "combat"), targetDelta.deltaKeys)
        assertEquals(setOf("combat"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
    }

    @Test
    fun freezeSuppressesTickDamageAndMarksTheTargetPortFrozen() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(continueScript = """int main() { freeze(); return 0; }"""),
                ),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val passiveWatchSink = RecordingPassiveWatchTriggerSink()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests, passiveWatchSink = passiveWatchSink)

        dispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
                clock = { 5_000L },
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-freeze"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()
        passiveWatchSink.triggers.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertEquals(15_000L, updatedTarget.port(25)?.freezeExpiresAtEpochMillis)
        assertEquals(0.0, updatedAttacker.stats.skillExperience(ScriptFamily.ATTACK))
        assertEquals(1, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).iterationCount)
        assertEquals(0.0, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetView.lastAppliedDamage)
        assertEquals(2, publisher.deltas.size)
        assertEquals(setOf("ports"), publisher.deltas[0].second.deltaKeys)
        assertEquals(setOf("combat"), publisher.deltas[1].second.deltaKeys)
        assertTrue(passiveWatchSink.triggers.isEmpty())
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
    }

    @Test
    fun freezeStillSuppressesDamageAgainstFreezeImmuneTargets() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(continueScript = """int main() { freeze(); return 0; }"""),
                ),
                targetId to targetState(
                    targetId,
                    freezeImmune = true,
                ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-freeze-immune"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertNull(updatedTarget.port(25)?.freezeExpiresAtEpochMillis)
        assertEquals(0.0, updatedAttacker.stats.skillExperience(ScriptFamily.ATTACK))
        assertEquals(setOf("combat"), publisher.deltas.single().second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
    }

    @Test
    fun firewallModifiedDamageAndAttackBackDamageApplyInTheSameTick() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(attackerId),
                targetId to targetState(
                    targetId,
                    firewallCombatProfile = FirewallCombatProfile(
                        httpDamageModifier = 0.5,
                        attackBackDamage = 3.0,
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val passiveWatchSink = RecordingPassiveWatchTriggerSink()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests, passiveWatchSink = passiveWatchSink)

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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-firewall"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()
        passiveWatchSink.triggers.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(98.9, updatedTarget.port(25)?.health)
        assertEquals(97.0, updatedAttacker.port(12)?.health)
        assertEquals(2.2, updatedAttacker.stats.skillExperience(ScriptFamily.ATTACK))
        assertEquals(1.1, updatedAttacker.combat.activeAttacksBySourcePort.getValue(12).targetView.lastAppliedDamage)
        assertEquals(2, publisher.deltas.size)
        assertEquals(setOf("ports"), publisher.deltas[0].second.deltaKeys)
        assertEquals(setOf("combat", "stats", "ports"), publisher.deltas[1].second.deltaKeys)
        assertEquals(
            listOf<PassiveWatchTrigger>(
                PassiveWatchTrigger.HealthChanged(
                    targetStateId = targetId,
                    sourceIp = attackerId.value,
                    external = true,
                    portNumber = 25,
                    sourcePort = 12,
                    previousHealth = 100.0,
                    newHealth = 98.9,
                ),
                PassiveWatchTrigger.HealthChanged(
                    targetStateId = attackerId,
                    sourceIp = targetId.value,
                    external = true,
                    portNumber = 12,
                    sourcePort = 25,
                    previousHealth = 100.0,
                    newHealth = 97.0,
                ),
            ),
            passiveWatchSink.triggers,
        )
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
    }

    @Test
    fun requestAttackRejectsFrozenTargetsButAllowsExpiredFreezeState() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")

        val frozen = requestAttack(
            attackerState = attackerState(attackerId),
            targetState = targetState(
                targetId,
                freezeExpiresAtEpochMillis = Long.MAX_VALUE,
            ),
        )
        val expired = requestAttack(
            attackerState = attackerState(attackerId),
            targetState = targetState(
                targetId,
                freezeExpiresAtEpochMillis = 1L,
            ),
        )

        assertEquals(AttackStartFailureCode.INVALID_TARGET_PORT, frozen.response.failureCode)
        assertFalse(frozen.response.accepted)
        assertTrue(expired.response.accepted)
    }

    @Test
    fun attackCompletionClearsBilateralCombatStateFreesCpuAndPublishesCompleted() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(attackerId),
                targetId to targetState(targetId).copy(
                    ports = listOf(
                        PortState(
                            number = 25,
                            type = "http",
                            enabled = true,
                            health = 1.5,
                        ),
                    ),
                ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-complete"),
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
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertFalse(updatedAttacker.port(12)?.attacking == true)
        assertEquals(0.0, updatedAttacker.runtime.currentCpuLoad)
        assertEquals(0.0, updatedTarget.port(25)?.health)
        assertEquals(2.2, updatedAttacker.stats.skillExperience(ScriptFamily.ATTACK))
        assertEquals(2, publisher.deltas.size)
        assertEquals(setOf("target-conn"), publisher.deltas[0].first)
        assertEquals(setOf("ports", "combat"), publisher.deltas[0].second.deltaKeys)
        assertEquals(setOf("attacker-conn"), publisher.deltas[1].first)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), publisher.deltas[1].second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun requestCancelAttackIsNoOpWithoutSessionAndCleansUpBilateralCombatStateWhenOneExists() = runTest {
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
        val updatedTarget = repository.load(targetId)

        requireNotNull(updated)
        requireNotNull(updatedTarget)
        assertTrue(cancelled.accepted)
        assertTrue(cancelled.hadActiveSession)
        assertEquals(2, publisher.deltas.size)
        assertEquals(setOf("attacker-conn"), publisher.deltas[0].first)
        assertEquals(setOf("ports", "combat", "runtime"), publisher.deltas[0].second.deltaKeys)
        assertEquals(setOf("target-conn"), publisher.deltas[1].first)
        assertEquals(setOf("combat"), publisher.deltas[1].second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
        assertTrue(updated.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
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
        val updatedTarget = repository.load(targetId)

        requireNotNull(updated)
        requireNotNull(updatedTarget)
        assertTrue(updated.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertFalse(updated.port(12)?.attacking == true)
        assertEquals(0.0, updated.runtime.currentCpuLoad)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.last().second.status)
    }

    @Test
    fun cancelAndTimeoutCleanupDoNotRunFinalizeScripts() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val finalizer = attackScriptBundle(finalize = """int main() { logMessage("should-not-run"); return 0; }""")

        val cancelRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(attackerId, attackScriptBundle = finalizer),
                targetId to targetState(targetId),
            ),
        )
        val cancelInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val cancelRegistry = InMemoryAttackProgramRegistry()
        val cancelDispatcher = dispatcher(cancelRepository, cancelInterests)

        cancelDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = cancelRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-cancel-no-finalize"),
            publisher = RecordingGameStatePublisher(),
        )
        runCurrent()
        cancelDispatcher.request(
            command = RequestCancelAttackCommand(
                attackerStateId = attackerId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                attackProgramRegistry = cancelRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "cancel-no-finalize"),
            publisher = RecordingGameStatePublisher(),
        )
        runCurrent()

        assertTrue(cancelRepository.load(attackerId)?.logs?.entries?.isEmpty() == true)

        val timeoutRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(attackerId, attackScriptBundle = finalizer),
                targetId to targetState(targetId),
            ),
        )
        val timeoutInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
        }
        val timeoutRegistry = InMemoryAttackProgramRegistry()
        val timeoutDispatcher = dispatcher(timeoutRepository, timeoutInterests)

        timeoutDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = timeoutRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-timeout-no-finalize"),
            publisher = RecordingGameStatePublisher(),
        )
        runCurrent()
        advanceTimeBy(450_100)
        runCurrent()

        assertTrue(timeoutRepository.load(attackerId)?.logs?.entries?.isEmpty() == true)
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
    fun bootstrapRefreshClearsStalePersistedAttackerAndTargetCombatRuntimeState() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
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
        val staleTargetState = targetState(targetId).copy(
            combat = CombatState(
                incomingAttacksByTargetPort = mapOf(
                    25 to IncomingAttackState(
                        attackerStateId = attackerId,
                        attackerSourcePort = 12,
                        targetPort = 25,
                        startedAtEpochMillis = 1_000L,
                        windowHandle = 0,
                    ),
                ),
            ),
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to staleState,
                targetId to staleTargetState,
            ),
        )
        val interests = InMemoryInterestRegistry()
        val dispatcher = dispatcher(repository, interests)

        val attackerResult = dispatcher.request(
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
        val targetResult = dispatcher.request(
            command = GameSessionBootstrapCommand(
                stateId = targetId,
                playFabId = "PF-TARGET",
                interestRegistry = interests,
                attackProgramRegistry = InMemoryAttackProgramRegistry(),
                clock = { 10_000L },
            ),
            metadata = CommandMetadata(connectionId = "target-conn"),
            publisher = NoOpGameStatePublisher,
        )

        assertTrue(attackerResult.state.combat.activeAttacksBySourcePort.isEmpty())
        assertFalse(attackerResult.state.port(12)?.attacking == true)
        assertEquals(0.0, attackerResult.state.runtime.currentCpuLoad)
        assertTrue(targetResult.state.combat.incomingAttacksByTargetPort.isEmpty())
        assertTrue(repository.load(attackerId)?.combat?.activeAttacksBySourcePort?.isEmpty() == true)
        assertTrue(repository.load(targetId)?.combat?.incomingAttacksByTargetPort?.isEmpty() == true)
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
        passiveWatchSink: PassiveWatchTriggerSink = NoOpPassiveWatchTriggerSink,
    ): DefaultCommandDispatcher {
        return DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = interests,
            passiveWatchTriggerSink = passiveWatchSink,
            programScheduler = CoroutineProgramScheduler(
                dispatcher = null,
                interestRegistry = interests,
                coroutineScope = backgroundScope,
            ),
        )
    }

    private class RecordingPassiveWatchTriggerSink : PassiveWatchTriggerSink {
        val triggers = mutableListOf<PassiveWatchTrigger>()

        override suspend fun emit(context: CommandContext, trigger: PassiveWatchTrigger) {
            triggers += trigger
        }
    }

    private data class AttackFixtureResult(
        val response: AttackStartResponse,
        val repository: InMemoryComputerStateRepository,
        val publisher: RecordingGameStatePublisher,
    )

    private fun attackerState(
        stateId: GameStateId,
        bankIsAlsoAttack: Boolean = false,
        attackScriptBundle: ProgramScriptBundle? = null,
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
                        scriptBundle = attackScriptBundle,
                    ),
                ),
            ),
        )
    }

    private fun targetState(
        stateId: GameStateId,
        health: Double = 100.0,
        logs: List<ComputerLogEntry> = emptyList(),
        freezeExpiresAtEpochMillis: Long? = null,
        freezeImmune: Boolean = false,
        firewallCombatProfile: FirewallCombatProfile = FirewallCombatProfile(),
        additionalPorts: List<PortState> = emptyList(),
    ): ComputerState {
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            logs = LogState(logs),
            hardware = HardwareState(
                equipmentSlots = if (freezeImmune) {
                    mapOf(
                        EquipmentSlot.PCI to InstalledEquipment(
                            slot = EquipmentSlot.PCI,
                            name = "freeze-shield.bin",
                            freezeImmune = true,
                        ),
                    )
                } else {
                    emptyMap()
                },
            ),
            ports = buildList {
                add(
                    PortState(
                        number = 25,
                        type = "http",
                        enabled = true,
                        health = health,
                        freezeExpiresAtEpochMillis = freezeExpiresAtEpochMillis,
                        installedFirewall = InstalledFirewall(
                            name = "target-wall.bin",
                            combatProfile = firewallCombatProfile,
                        ),
                    ),
                )
                addAll(additionalPorts)
            },
        )
    }

    private fun attackScriptBundle(
        initialize: String? = null,
        continueScript: String? = null,
        finalize: String? = null,
    ): ProgramScriptBundle {
        return ProgramScriptBundle(
            family = ScriptFamily.ATTACK,
            scriptsBySlot = buildMap {
                initialize?.let { put(ProgramScriptSlot.INITIALIZE, it) }
                continueScript?.let { put(ProgramScriptSlot.CONTINUE, it) }
                finalize?.let { put(ProgramScriptSlot.FINALIZE, it) }
            },
        )
    }
}
