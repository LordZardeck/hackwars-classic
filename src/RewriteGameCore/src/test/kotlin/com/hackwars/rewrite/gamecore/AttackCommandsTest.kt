package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.IntHookValue
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
    fun requestAttackInitializeMessagePublishesSelfTargetedUiEventAndStillStartsNormally() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        initialize = """int main() { message("ATTACKER-IP", "init-message"); return 0; }""",
                    ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-init-message"),
            publisher = publisher,
        )

        assertTrue(response.accepted)
        assertEquals(1, publisher.uiEvents.size)
        assertEquals(setOf("attacker-conn"), publisher.uiEvents.single().first)
        assertEquals("init-message", assertIs<TextMessageUiEvent>(publisher.uiEvents.single().second).message)
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
    fun continueMessagePublishesTargetedUiEventAndStillAppliesNormalDeterministicTick() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { message("TARGET-IP", "tick-message"); return 0; }""",
                    ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-continue-message"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()
        publisher.uiEvents.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = requireNotNull(repository.load(targetId))
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(1, publisher.uiEvents.size)
        assertEquals(setOf("target-conn"), publisher.uiEvents.single().first)
        assertEquals("tick-message", assertIs<TextMessageUiEvent>(publisher.uiEvents.single().second).message)
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
    fun continueDestroyWatchesRemovesOnlyEnabledNonScanWatchesOnTheCurrentTargetPort() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { destroyWatches(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    currentCpuLoad = 10.0,
                    additionalPorts = listOf(PortState(number = 26, type = "ftp", enabled = true, health = 100.0)),
                    watches = WatchManagerState(
                        watches = listOf(
                            targetWatch(note = "remove-health", kind = WatchKind.HEALTH, installPort = 25, cpuCost = 3.0),
                            targetWatch(note = "remove-cash", kind = WatchKind.PETTY_CASH, installPort = 25, cpuCost = 2.0),
                            targetWatch(note = "keep-scan", kind = WatchKind.SCAN, installPort = 25, cpuCost = 4.0),
                            targetWatch(note = "keep-disabled", kind = WatchKind.HEALTH, installPort = 25, cpuCost = 6.0, enabled = false),
                            targetWatch(note = "keep-other-port", kind = WatchKind.HEALTH, installPort = 26, cpuCost = 1.0),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-destroy-watches"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(listOf("keep-scan", "keep-disabled", "keep-other-port"), updatedTarget.watches.watches.map { it.note })
        assertEquals(5.0, updatedTarget.runtime.currentCpuLoad)
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(setOf("watches", "runtime", "ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun destroyedHealthWatchesDoNotAutoFireLaterInTheSameTick() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { destroyWatches(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    currentCpuLoad = 5.0,
                    watches = WatchManagerState(
                        watches = listOf(
                            targetWatch(
                                note = "health-watch",
                                kind = WatchKind.HEALTH,
                                installPort = 25,
                                cpuCost = 5.0,
                                quantityThreshold = 99.0,
                                baselineQuantity = 100.0,
                                fireScript = """int main() { logMessage("health-fired"); return 0; }""",
                            ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-destroy-health-watch"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = requireNotNull(repository.load(targetId))
        assertTrue(updatedTarget.watches.watches.isEmpty())
        assertTrue(updatedTarget.logs.entries.isEmpty())
        assertEquals(0.0, updatedTarget.stats.skillExperience(ScriptFamily.WATCH))
        assertEquals(97.8, updatedTarget.port(25)?.health)
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
    fun continueMessageThenCancelAttackStillDeliversMessageBeforeCancellation() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { message("TARGET-IP", "warn"); cancelAttack(); return 0; }""",
                    ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-message-cancel"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()
        publisher.uiEvents.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(1, publisher.uiEvents.size)
        assertEquals(setOf("target-conn"), publisher.uiEvents.single().first)
        assertEquals("warn", assertIs<TextMessageUiEvent>(publisher.uiEvents.single().second).message)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun invalidMessageCasesProduceNoUiEventAndDoNotSuppressDamage() = runTest {
        suspend fun runCase(script: String, requestId: String): Pair<RecordingGameStatePublisher, ComputerState> {
            val attackerId = GameStateId("ATTACKER-IP")
            val targetId = GameStateId("TARGET-IP")
            val repository = InMemoryComputerStateRepository(
                seededStates = mapOf(
                    attackerId to attackerState(
                        attackerId,
                        attackScriptBundle = attackScriptBundle(continueScript = script),
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
                metadata = CommandMetadata(connectionId = "attacker-conn", requestId = requestId),
                publisher = publisher,
            )
            runCurrent()
            publisher.deltas.clear()
            publisher.programUpdates.clear()
            publisher.uiEvents.clear()

            advanceTimeBy(180_100)
            runCurrent()

            return publisher to requireNotNull(repository.load(targetId))
        }

        val (invalidPublisher, invalidTarget) = runCase(
            script = """int main() { message("OTHER-IP", "bad"); cancelAttack(); return 0; }""",
            requestId = "attack-invalid-message-target",
        )
        val (tooLongPublisher, tooLongTarget) = runCase(
            script = """int main() { message("TARGET-IP", "${"x".repeat(256)}"); cancelAttack(); return 0; }""",
            requestId = "attack-invalid-message-length",
        )

        assertTrue(invalidPublisher.uiEvents.isEmpty())
        assertEquals(97.8, invalidTarget.port(25)?.health)
        assertEquals(ProgramLifecycleStatus.CANCELLED, invalidPublisher.programUpdates.single().second.status)
        assertTrue(tooLongPublisher.uiEvents.isEmpty())
        assertEquals(97.8, tooLongTarget.port(25)?.health)
        assertEquals(ProgramLifecycleStatus.CANCELLED, tooLongPublisher.programUpdates.single().second.status)
    }

    @Test
    fun continueEmptyPettyCashTransfersAdjustedAmountTriggersPassiveWatchesStillDamagesAndCancelsAttack() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { emptyPettyCash(); return 0; }""",
                    ),
                ).copy(
                    watches = WatchManagerState(
                        watches = listOf(
                            targetWatch(
                                note = "attacker-cash",
                                kind = WatchKind.PETTY_CASH,
                                installPort = 6,
                                cpuCost = 1.0,
                                quantityThreshold = 120.0,
                                baselineQuantity = 100.0,
                                fireScript = """int main() { logMessage("cash-fired"); return 0; }""",
                            ),
                        ),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    pettyCash = 80.0,
                    portType = "bank",
                    installedApplication = InstalledApplication(
                        name = "target-bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
                    firewallActionProfile = FirewallActionProfile(
                        emptyPettyCashReductionMultiplier = 0.5,
                    ),
                    watches = WatchManagerState(
                        watches = listOf(
                            targetWatch(
                                note = "target-cash",
                                kind = WatchKind.PETTY_CASH,
                                installPort = 25,
                                cpuCost = 1.0,
                                quantityThreshold = 100.0,
                                baselineQuantity = 80.0,
                                fireScript = """int main() { logMessage("should-not-run"); return 0; }""",
                            ),
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
        val dispatcher = dispatcher(repository, interests, passiveWatchSink = DefaultPassiveWatchCoordinator)

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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-empty-petty-cash"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(130.0, updatedAttacker.economy.pettyCash)
        assertEquals(40.0, updatedTarget.economy.pettyCash)
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertContains(updatedAttacker.logs.entries.single().renderedLine, "cash-fired")
        assertTrue(updatedTarget.logs.entries.isEmpty())
        assertEquals(130.0, updatedAttacker.watches.watches.single().baselineQuantity)
        assertEquals(40.0, updatedTarget.watches.watches.single().baselineQuantity)
        assertEquals(0.8, updatedAttacker.stats.skillExperience(ScriptFamily.WATCH))
        assertEquals(2.2, updatedAttacker.stats.skillExperience(ScriptFamily.ATTACK))
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        val targetDelta = publisher.deltas.single { it.first == setOf("target-conn") }.second
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("economy", "watches", "ports", "combat"), targetDelta.deltaKeys)
        assertEquals(setOf("economy", "logs", "watches", "stats", "combat", "ports", "runtime"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueEmptyPettyCashWithFirewallFailResolvesToZeroAmountStillDamagesAndCancelsAttack() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { emptyPettyCash(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    pettyCash = 80.0,
                    portType = "bank",
                    installedApplication = InstalledApplication(
                        name = "target-bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
                    firewallActionProfile = FirewallActionProfile(
                        emptyPettyCashFailChance = 0.4,
                        emptyPettyCashReductionMultiplier = 0.5,
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-empty-petty-cash-fail"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(90.0, updatedAttacker.economy.pettyCash)
        assertEquals(80.0, updatedTarget.economy.pettyCash)
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(setOf("ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), publisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueEmptyPettyCashRequiresBankingTargetAndAttackerDefaultBank() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")

        val wrongTargetRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { emptyPettyCash(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    pettyCash = 75.0,
                    portType = "http",
                ),
            ),
        )
        val wrongTargetInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val wrongTargetPublisher = RecordingGameStatePublisher()
        val wrongTargetRegistry = InMemoryAttackProgramRegistry()
        val wrongTargetDispatcher = dispatcher(wrongTargetRepository, wrongTargetInterests)

        wrongTargetDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = wrongTargetRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-empty-petty-cash-wrong-target"),
            publisher = wrongTargetPublisher,
        )
        runCurrent()
        wrongTargetPublisher.deltas.clear()
        wrongTargetPublisher.programUpdates.clear()
        advanceTimeBy(180_100)
        runCurrent()

        val disabledBankRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { emptyPettyCash(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    pettyCash = 75.0,
                    portType = "bank",
                    installedApplication = InstalledApplication(
                        name = "target-bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
                ),
            ),
        )
        val disabledBankInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val disabledBankPublisher = RecordingGameStatePublisher()
        val disabledBankRegistry = InMemoryAttackProgramRegistry()
        val disabledBankDispatcher = dispatcher(disabledBankRepository, disabledBankInterests)

        disabledBankDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = disabledBankRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-empty-petty-cash-missing-bank"),
            publisher = disabledBankPublisher,
        )
        runCurrent()
        disabledBankPublisher.deltas.clear()
        disabledBankPublisher.programUpdates.clear()
        val disabledBankAttacker = requireNotNull(disabledBankRepository.load(attackerId))
        disabledBankRepository.appendEvents(
            attackerId,
            listOf(
                CombatStateUpdatedEvent(
                    changedPathList = setOf("ports.6.enabled"),
                    deltaKeyList = setOf("ports"),
                    combat = disabledBankAttacker.combat,
                    ports = disabledBankAttacker.ports.map { port ->
                        if (port.number == 6) {
                            port.copy(enabled = false)
                        } else {
                            port
                        }
                    },
                    currentCpuLoad = disabledBankAttacker.runtime.currentCpuLoad,
                    includePorts = true,
                ),
            ),
        )
        advanceTimeBy(180_100)
        runCurrent()

        assertEquals(90.0, requireNotNull(wrongTargetRepository.load(attackerId)).economy.pettyCash)
        assertEquals(75.0, requireNotNull(wrongTargetRepository.load(targetId)).economy.pettyCash)
        assertEquals(97.8, wrongTargetRepository.load(targetId)?.port(25)?.health)
        assertEquals(setOf("ports", "combat"), wrongTargetPublisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), wrongTargetPublisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, wrongTargetPublisher.programUpdates.single().second.status)

        assertEquals(90.0, requireNotNull(disabledBankRepository.load(attackerId)).economy.pettyCash)
        assertEquals(75.0, requireNotNull(disabledBankRepository.load(targetId)).economy.pettyCash)
        assertEquals(97.8, disabledBankRepository.load(targetId)?.port(25)?.health)
        assertEquals(setOf("ports", "combat"), disabledBankPublisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), disabledBankPublisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, disabledBankPublisher.programUpdates.single().second.status)
    }

    @Test
    fun finalizeEmptyPettyCashMutatesEconomyBeforeCompletionCleanup() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        finalize = """int main() { emptyPettyCash(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    health = 1.5,
                    pettyCash = 50.0,
                    portType = "bank",
                    installedApplication = InstalledApplication(
                        name = "target-bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-finalize-empty-petty-cash"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(140.0, updatedAttacker.economy.pettyCash)
        assertEquals(0.0, updatedTarget.economy.pettyCash)
        assertEquals(0.0, updatedTarget.port(25)?.health)
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(setOf("economy", "ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("economy", "combat", "ports", "runtime", "stats"), publisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun switchAttackThenEmptyPettyCashActsOnTheRetargetedBankingPort() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { switchAttack(); emptyPettyCash(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    pettyCash = 80.0,
                    additionalPorts = listOf(
                        PortState(
                            number = 26,
                            type = "bank",
                            enabled = true,
                            health = 100.0,
                            installedApplication = InstalledApplication(
                                name = "bank.bin",
                                kind = ApplicationKind.BANKING,
                                banking = true,
                                cpuCost = 2.0,
                            ),
                            installedFirewall = InstalledFirewall(
                                name = "target-bank-wall.bin",
                                actionProfile = FirewallActionProfile(
                                    emptyPettyCashReductionMultiplier = 0.25,
                                ),
                            ),
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
                loadout = AttackLoadout(secondaryPorts = listOf(26)),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-switch-empty-petty-cash"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(110.0, updatedAttacker.economy.pettyCash)
        assertEquals(60.0, updatedTarget.economy.pettyCash)
        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertEquals(97.8, updatedTarget.port(26)?.health)
        assertEquals(setOf("economy", "ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("economy", "combat", "ports", "runtime", "stats"), publisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueStealFileTransfersFirstDeterministicPublicFileStillDamagesAndCancelsAttack() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val stolenBundle = ProgramScriptBundle(
            family = ScriptFamily.HTTP,
            scriptsBySlot = linkedMapOf(ProgramScriptSlot.ENTER to "enter"),
        )
        val stolenBinary = CompiledBinaryMetadata(
            scriptFamily = ScriptFamily.HTTP,
            applicationKind = ApplicationKind.HTTP,
            outputName = "alpha.bin",
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { stealFile(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    portType = "ftp",
                    installedApplication = InstalledApplication(
                        name = "target-ftp.bin",
                        kind = ApplicationKind.FTP,
                        cpuCost = 2.0,
                    ),
                    filesystemFiles = listOf(
                        storedFile("/Public", "zeta.txt", contents = "zeta"),
                        storedFile(
                            "/Public",
                            "alpha.txt",
                            contents = "alpha",
                            description = "loot",
                            maker = "npc",
                            compileCost = 7.0,
                            cpuCost = 8.0,
                            price = 9.0,
                            compiledBinary = stolenBinary,
                            scriptBundle = stolenBundle,
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-steal-file"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))
        val receivedFile = requireNotNull(updatedAttacker.filesystem.resolveFile("/", "alpha.txt"))

        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals("alpha", receivedFile.contents)
        assertEquals("loot", receivedFile.description)
        assertEquals("npc", receivedFile.maker)
        assertEquals(7.0, receivedFile.compileCost)
        assertEquals(8.0, receivedFile.cpuCost)
        assertEquals(9.0, receivedFile.price)
        assertEquals(stolenBinary, receivedFile.compiledBinary)
        assertEquals(stolenBundle, receivedFile.scriptBundle)
        assertNull(updatedTarget.filesystem.resolveFile("/Public", "alpha.txt"))
        assertNotNull(updatedTarget.filesystem.resolveFile("/Public", "zeta.txt"))
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(setOf("filesystem", "ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), publisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueStealFileDecrementsStackedTargetFileAndIncrementsExistingAttackerCopy() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { stealFile(); return 0; }""",
                    ),
                    filesystemFiles = listOf(
                        storedFile("/", "loot.txt", quantity = 2, contents = "owned", description = "keep-me"),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    portType = "ftp",
                    installedApplication = InstalledApplication(
                        name = "target-ftp.bin",
                        kind = ApplicationKind.FTP,
                        cpuCost = 2.0,
                    ),
                    filesystemFiles = listOf(
                        storedFile("/Public", "loot.txt", quantity = 3, contents = "target", description = "target-copy"),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-steal-file-stack"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))
        val attackerCopy = requireNotNull(updatedAttacker.filesystem.resolveFile("/", "loot.txt"))
        val targetRemaining = requireNotNull(updatedTarget.filesystem.resolveFile("/Public", "loot.txt"))

        assertEquals(3, attackerCopy.quantity)
        assertEquals("owned", attackerCopy.contents)
        assertEquals("keep-me", attackerCopy.description)
        assertEquals(2, targetRemaining.quantity)
        assertEquals("target", targetRemaining.contents)
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueStealFileFirewallFailProducesNoFilesystemMutationStillDamagesAndCancelsAttack() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { stealFile(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    portType = "ftp",
                    installedApplication = InstalledApplication(
                        name = "target-ftp.bin",
                        kind = ApplicationKind.FTP,
                        cpuCost = 2.0,
                    ),
                    firewallActionProfile = FirewallActionProfile(
                        stealFileFailChance = 0.3,
                    ),
                    filesystemFiles = listOf(
                        storedFile("/Public", "loot.txt"),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-steal-file-fail"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertNull(updatedAttacker.filesystem.resolveFile("/", "loot.txt"))
        assertNotNull(updatedTarget.filesystem.resolveFile("/Public", "loot.txt"))
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(setOf("ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), publisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueStealFileRequiresFtpTargetAndAvailablePublicFile() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")

        val wrongTargetRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { stealFile(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    portType = "http",
                    filesystemFiles = listOf(
                        storedFile("/Public", "loot.txt"),
                    ),
                ),
            ),
        )
        val wrongTargetInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val wrongTargetPublisher = RecordingGameStatePublisher()
        val wrongTargetRegistry = InMemoryAttackProgramRegistry()
        val wrongTargetDispatcher = dispatcher(wrongTargetRepository, wrongTargetInterests)

        wrongTargetDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = wrongTargetRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-steal-file-wrong-target"),
            publisher = wrongTargetPublisher,
        )
        runCurrent()
        wrongTargetPublisher.deltas.clear()
        wrongTargetPublisher.programUpdates.clear()
        advanceTimeBy(180_100)
        runCurrent()

        val noFileRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { stealFile(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    portType = "ftp",
                    installedApplication = InstalledApplication(
                        name = "target-ftp.bin",
                        kind = ApplicationKind.FTP,
                        cpuCost = 2.0,
                    ),
                    filesystemFiles = listOf(
                        storedFile("/Private", "secret.txt"),
                    ),
                ),
            ),
        )
        val noFileInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val noFilePublisher = RecordingGameStatePublisher()
        val noFileRegistry = InMemoryAttackProgramRegistry()
        val noFileDispatcher = dispatcher(noFileRepository, noFileInterests)

        noFileDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = noFileRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-steal-file-no-public"),
            publisher = noFilePublisher,
        )
        runCurrent()
        noFilePublisher.deltas.clear()
        noFilePublisher.programUpdates.clear()
        advanceTimeBy(180_100)
        runCurrent()

        assertNull(requireNotNull(wrongTargetRepository.load(attackerId)).filesystem.resolveFile("/", "loot.txt"))
        assertNotNull(requireNotNull(wrongTargetRepository.load(targetId)).filesystem.resolveFile("/Public", "loot.txt"))
        assertEquals(97.8, wrongTargetRepository.load(targetId)?.port(25)?.health)
        assertEquals(setOf("ports", "combat"), wrongTargetPublisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), wrongTargetPublisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, wrongTargetPublisher.programUpdates.single().second.status)

        assertNull(requireNotNull(noFileRepository.load(attackerId)).filesystem.resolveFile("/", "secret.txt"))
        assertNotNull(requireNotNull(noFileRepository.load(targetId)).filesystem.resolveFile("/Private", "secret.txt"))
        assertEquals(97.8, noFileRepository.load(targetId)?.port(25)?.health)
        assertEquals(setOf("ports", "combat"), noFilePublisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), noFilePublisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, noFilePublisher.programUpdates.single().second.status)
    }

    @Test
    fun finalizeStealFileMutatesFilesystemBeforeCompletionCleanup() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        finalize = """int main() { stealFile(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    health = 1.5,
                    portType = "ftp",
                    installedApplication = InstalledApplication(
                        name = "target-ftp.bin",
                        kind = ApplicationKind.FTP,
                        cpuCost = 2.0,
                    ),
                    filesystemFiles = listOf(
                        storedFile("/Public", "loot.txt"),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-finalize-steal-file"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertNotNull(updatedAttacker.filesystem.resolveFile("/", "loot.txt"))
        assertNull(updatedTarget.filesystem.resolveFile("/Public", "loot.txt"))
        assertEquals(0.0, updatedTarget.port(25)?.health)
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(setOf("filesystem", "ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), publisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun switchAttackThenStealFileActsOnTheRetargetedFtpPort() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { switchAttack(); stealFile(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    filesystemFiles = listOf(
                        storedFile("/Public", "loot.txt"),
                    ),
                    additionalPorts = listOf(
                        PortState(
                            number = 26,
                            type = "ftp",
                            enabled = true,
                            health = 100.0,
                            installedApplication = InstalledApplication(
                                name = "ftp.bin",
                                kind = ApplicationKind.FTP,
                                cpuCost = 2.0,
                            ),
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
                loadout = AttackLoadout(secondaryPorts = listOf(26)),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-switch-steal-file"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertNotNull(updatedAttacker.filesystem.resolveFile("/", "loot.txt"))
        assertNull(updatedTarget.filesystem.resolveFile("/Public", "loot.txt"))
        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertEquals(97.8, updatedTarget.port(26)?.health)
        assertEquals(setOf("filesystem", "ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), publisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueInstallScriptConsumesCompatibleSourceButRequiresWeakenedTargetAndPreservesApplicationIdentity() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val cleanBundle = ProgramScriptBundle(
            family = ScriptFamily.BANKING,
            scriptsBySlot = linkedMapOf(ProgramScriptSlot.DEPOSIT to "clean"),
        )
        val maliciousBundle = ProgramScriptBundle(
            family = ScriptFamily.BANKING,
            scriptsBySlot = linkedMapOf(ProgramScriptSlot.TRANSFER to "infected"),
        )
        val targetApplication = InstalledApplication(
            name = "target-bank.bin",
            kind = ApplicationKind.BANKING,
            banking = true,
            binaryPath = "/system/target-bank.bin",
            cpuCost = 2.0,
            scriptBundle = cleanBundle,
            maliciousConfig = MaliciousProgramConfig(
                targetIp = "OLD-IP",
                pettyCashTarget = 5.0,
            ),
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { installScript(); return 0; }""",
                    ),
                    filesystemFiles = listOf(
                        storedFile(
                            "/Public",
                            "worm.bin",
                            quantity = 2,
                            kind = StoredFileKind.APPLICATION_BINARY,
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = ScriptFamily.BANKING,
                                applicationKind = ApplicationKind.BANKING,
                                outputName = "worm.bin",
                            ),
                            scriptBundle = maliciousBundle,
                        ),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    pettyCash = 25.0,
                    portType = "bank",
                    installedApplication = targetApplication,
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
                loadout = AttackLoadout(
                    maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
                    extraInfo = listOf(StringHookValue("MAL-IP"), IntHookValue(42)),
                ),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-install-script-continue"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))
        val remainingSource = requireNotNull(updatedAttacker.filesystem.resolveFile("/Public", "worm.bin"))
        val updatedApplication = requireNotNull(updatedTarget.port(25)?.installedApplication)

        assertEquals(1, remainingSource.quantity)
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(targetApplication.name, updatedApplication.name)
        assertEquals(targetApplication.kind, updatedApplication.kind)
        assertEquals(targetApplication.binaryPath, updatedApplication.binaryPath)
        assertEquals(targetApplication.cpuCost, updatedApplication.cpuCost)
        assertEquals(cleanBundle, updatedApplication.scriptBundle)
        assertEquals(targetApplication.maliciousConfig, updatedApplication.maliciousConfig)
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(setOf("ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), publisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun finalizeInstallScriptOnWeakenedTargetMutatesScriptsAndMaliciousConfigBeforeCompletionCleanup() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val cleanBundle = ProgramScriptBundle(
            family = ScriptFamily.BANKING,
            scriptsBySlot = linkedMapOf(ProgramScriptSlot.DEPOSIT to "clean"),
        )
        val maliciousBundle = ProgramScriptBundle(
            family = ScriptFamily.BANKING,
            scriptsBySlot = linkedMapOf(
                ProgramScriptSlot.DEPOSIT to "infected-deposit",
                ProgramScriptSlot.TRANSFER to "infected-transfer",
            ),
        )
        val targetApplication = InstalledApplication(
            name = "target-bank.bin",
            kind = ApplicationKind.BANKING,
            banking = true,
            binaryPath = "/system/target-bank.bin",
            cpuCost = 2.0,
            scriptBundle = cleanBundle,
            maliciousConfig = MaliciousProgramConfig(
                targetIp = "OLD-IP",
                pettyCashTarget = 1.0,
            ),
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        finalize = """int main() { installScript(); return 0; }""",
                    ),
                    filesystemFiles = listOf(
                        storedFile(
                            "/Public",
                            "worm.bin",
                            kind = StoredFileKind.APPLICATION_BINARY,
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = ScriptFamily.BANKING,
                                applicationKind = ApplicationKind.BANKING,
                                outputName = "worm.bin",
                            ),
                            scriptBundle = maliciousBundle,
                        ),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    health = 1.5,
                    portType = "bank",
                    installedApplication = targetApplication,
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
                loadout = AttackLoadout(
                    maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
                    extraInfo = listOf(StringHookValue("MAL-IP"), IntHookValue(77)),
                ),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-install-script-finalize"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))
        val updatedApplication = requireNotNull(updatedTarget.port(25)?.installedApplication)

        assertNull(updatedAttacker.filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals(0.0, updatedTarget.port(25)?.health)
        assertEquals(targetApplication.name, updatedApplication.name)
        assertEquals(targetApplication.kind, updatedApplication.kind)
        assertEquals(targetApplication.binaryPath, updatedApplication.binaryPath)
        assertEquals(targetApplication.cpuCost, updatedApplication.cpuCost)
        assertEquals(maliciousBundle, updatedApplication.scriptBundle)
        assertEquals(
            MaliciousProgramConfig(
                targetIp = "MAL-IP",
                pettyCashTarget = 77.0,
            ),
            updatedApplication.maliciousConfig,
        )
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(setOf("ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), publisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun continueInstallScriptRequiresCompatibleSourceAndExistingTargetApplication() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")

        val missingFileRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { installScript(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    portType = "bank",
                    installedApplication = InstalledApplication(
                        name = "target-bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
                ),
            ),
        )
        val missingFileInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val missingFilePublisher = RecordingGameStatePublisher()
        val missingFileRegistry = InMemoryAttackProgramRegistry()
        val missingFileDispatcher = dispatcher(missingFileRepository, missingFileInterests)

        missingFileDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(
                    maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
                ),
                attackProgramRegistry = missingFileRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-install-script-missing-file"),
            publisher = missingFilePublisher,
        )
        runCurrent()
        missingFilePublisher.deltas.clear()
        missingFilePublisher.programUpdates.clear()
        advanceTimeBy(180_100)
        runCurrent()

        val wrongKindRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { installScript(); return 0; }""",
                    ),
                    filesystemFiles = listOf(
                        storedFile(
                            "/Public",
                            "worm.bin",
                            kind = StoredFileKind.TEXT,
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = ScriptFamily.BANKING,
                                applicationKind = ApplicationKind.BANKING,
                                outputName = "worm.bin",
                            ),
                        ),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    portType = "bank",
                    installedApplication = InstalledApplication(
                        name = "target-bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
                ),
            ),
        )
        val wrongKindInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val wrongKindPublisher = RecordingGameStatePublisher()
        val wrongKindRegistry = InMemoryAttackProgramRegistry()
        val wrongKindDispatcher = dispatcher(wrongKindRepository, wrongKindInterests)

        wrongKindDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(
                    maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
                ),
                attackProgramRegistry = wrongKindRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-install-script-wrong-kind"),
            publisher = wrongKindPublisher,
        )
        runCurrent()
        wrongKindPublisher.deltas.clear()
        wrongKindPublisher.programUpdates.clear()
        advanceTimeBy(180_100)
        runCurrent()

        val wrongAppKindRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { installScript(); return 0; }""",
                    ),
                    filesystemFiles = listOf(
                        storedFile(
                            "/Public",
                            "worm.bin",
                            kind = StoredFileKind.APPLICATION_BINARY,
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = ScriptFamily.GENERAL,
                                applicationKind = ApplicationKind.FTP,
                                outputName = "worm.bin",
                            ),
                            scriptBundle = ProgramScriptBundle(
                                family = ScriptFamily.GENERAL,
                                scriptsBySlot = linkedMapOf(ProgramScriptSlot.GET to "ftp"),
                            ),
                        ),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    portType = "bank",
                    installedApplication = InstalledApplication(
                        name = "target-bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
                ),
            ),
        )
        val wrongAppKindInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val wrongAppKindPublisher = RecordingGameStatePublisher()
        val wrongAppKindRegistry = InMemoryAttackProgramRegistry()
        val wrongAppKindDispatcher = dispatcher(wrongAppKindRepository, wrongAppKindInterests)

        wrongAppKindDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(
                    maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
                ),
                attackProgramRegistry = wrongAppKindRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-install-script-wrong-app-kind"),
            publisher = wrongAppKindPublisher,
        )
        runCurrent()
        wrongAppKindPublisher.deltas.clear()
        wrongAppKindPublisher.programUpdates.clear()
        advanceTimeBy(180_100)
        runCurrent()

        val missingInstalledAppRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { installScript(); return 0; }""",
                    ),
                    filesystemFiles = listOf(
                        storedFile(
                            "/Public",
                            "worm.bin",
                            kind = StoredFileKind.APPLICATION_BINARY,
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = ScriptFamily.BANKING,
                                applicationKind = ApplicationKind.BANKING,
                                outputName = "worm.bin",
                            ),
                        ),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    portType = "bank",
                    installedApplication = null,
                ),
            ),
        )
        val missingInstalledAppInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val missingInstalledAppPublisher = RecordingGameStatePublisher()
        val missingInstalledAppRegistry = InMemoryAttackProgramRegistry()
        val missingInstalledAppDispatcher = dispatcher(missingInstalledAppRepository, missingInstalledAppInterests)

        missingInstalledAppDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(
                    maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
                ),
                attackProgramRegistry = missingInstalledAppRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-install-script-missing-app"),
            publisher = missingInstalledAppPublisher,
        )
        runCurrent()
        missingInstalledAppPublisher.deltas.clear()
        missingInstalledAppPublisher.programUpdates.clear()
        advanceTimeBy(180_100)
        runCurrent()

        assertNull(requireNotNull(missingFileRepository.load(attackerId)).filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals(97.8, missingFileRepository.load(targetId)?.port(25)?.health)
        assertEquals(setOf("ports", "combat"), missingFilePublisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), missingFilePublisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)

        assertNotNull(requireNotNull(wrongKindRepository.load(attackerId)).filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals(97.8, wrongKindRepository.load(targetId)?.port(25)?.health)
        assertEquals(setOf("ports", "combat"), wrongKindPublisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), wrongKindPublisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)

        assertNotNull(requireNotNull(wrongAppKindRepository.load(attackerId)).filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals(97.8, wrongAppKindRepository.load(targetId)?.port(25)?.health)
        assertEquals(setOf("ports", "combat"), wrongAppKindPublisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), wrongAppKindPublisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)

        assertNotNull(requireNotNull(missingInstalledAppRepository.load(attackerId)).filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals(97.8, missingInstalledAppRepository.load(targetId)?.port(25)?.health)
        assertEquals(setOf("ports", "combat"), missingInstalledAppPublisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("combat", "ports", "runtime", "stats"), missingInstalledAppPublisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
    }

    @Test
    fun finalizeInstallScriptNpcAndFirewallFailStillConsumeCompatibleSourceWithoutInstalling() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val maliciousBundle = ProgramScriptBundle(
            family = ScriptFamily.BANKING,
            scriptsBySlot = linkedMapOf(ProgramScriptSlot.DEPOSIT to "infected"),
        )

        val npcRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        finalize = """int main() { installScript(); return 0; }""",
                    ),
                    filesystemFiles = listOf(
                        storedFile(
                            "/Public",
                            "worm.bin",
                            kind = StoredFileKind.APPLICATION_BINARY,
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = ScriptFamily.BANKING,
                                applicationKind = ApplicationKind.BANKING,
                                outputName = "worm.bin",
                            ),
                            scriptBundle = maliciousBundle,
                        ),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    health = 1.5,
                    portType = "bank",
                    isNpc = true,
                    installedApplication = InstalledApplication(
                        name = "target-bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
                ),
            ),
        )
        val npcInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val npcPublisher = RecordingGameStatePublisher()
        val npcRegistry = InMemoryAttackProgramRegistry()
        val npcDispatcher = dispatcher(npcRepository, npcInterests)

        npcDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(
                    maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
                ),
                attackProgramRegistry = npcRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-install-script-npc"),
            publisher = npcPublisher,
        )
        runCurrent()
        npcPublisher.deltas.clear()
        npcPublisher.programUpdates.clear()
        advanceTimeBy(180_100)
        runCurrent()

        val firewallRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        finalize = """int main() { installScript(); return 0; }""",
                    ),
                    filesystemFiles = listOf(
                        storedFile(
                            "/Public",
                            "worm.bin",
                            kind = StoredFileKind.APPLICATION_BINARY,
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = ScriptFamily.BANKING,
                                applicationKind = ApplicationKind.BANKING,
                                outputName = "worm.bin",
                            ),
                            scriptBundle = maliciousBundle,
                        ),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    health = 1.5,
                    portType = "bank",
                    firewallActionProfile = FirewallActionProfile(
                        installScriptFailChance = 0.5,
                    ),
                    installedApplication = InstalledApplication(
                        name = "target-bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
                ),
            ),
        )
        val firewallInterests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
            register("target-conn", targetId)
        }
        val firewallPublisher = RecordingGameStatePublisher()
        val firewallRegistry = InMemoryAttackProgramRegistry()
        val firewallDispatcher = dispatcher(firewallRepository, firewallInterests)

        firewallDispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(
                    maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
                ),
                attackProgramRegistry = firewallRegistry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-install-script-firewall"),
            publisher = firewallPublisher,
        )
        runCurrent()
        firewallPublisher.deltas.clear()
        firewallPublisher.programUpdates.clear()
        advanceTimeBy(180_100)
        runCurrent()

        assertNull(requireNotNull(npcRepository.load(attackerId)).filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals(0.0, npcRepository.load(targetId)?.port(25)?.health)
        assertNull(npcRepository.load(targetId)?.port(25)?.installedApplication?.scriptBundle)
        assertEquals(ProgramLifecycleStatus.COMPLETED, npcPublisher.programUpdates.single().second.status)

        assertNull(requireNotNull(firewallRepository.load(attackerId)).filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals(0.0, firewallRepository.load(targetId)?.port(25)?.health)
        assertNull(firewallRepository.load(targetId)?.port(25)?.installedApplication?.scriptBundle)
        assertEquals(ProgramLifecycleStatus.COMPLETED, firewallPublisher.programUpdates.single().second.status)
    }

    @Test
    fun switchAttackThenInstallScriptActsOnTheRetargetedCompatiblePort() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { switchAttack(); installScript(); return 0; }""",
                    ),
                    filesystemFiles = listOf(
                        storedFile(
                            "/Public",
                            "worm.bin",
                            kind = StoredFileKind.APPLICATION_BINARY,
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = ScriptFamily.GENERAL,
                                applicationKind = ApplicationKind.FTP,
                                outputName = "worm.bin",
                            ),
                        ),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    installedApplication = InstalledApplication(
                        name = "http.bin",
                        kind = ApplicationKind.HTTP,
                        cpuCost = 2.0,
                    ),
                    additionalPorts = listOf(
                        PortState(
                            number = 26,
                            type = "ftp",
                            enabled = true,
                            health = 100.0,
                            installedApplication = InstalledApplication(
                                name = "ftp.bin",
                                kind = ApplicationKind.FTP,
                                cpuCost = 2.0,
                                scriptBundle = ProgramScriptBundle(
                                    family = ScriptFamily.GENERAL,
                                    scriptsBySlot = linkedMapOf(ProgramScriptSlot.GET to "clean-ftp"),
                                ),
                            ),
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
                loadout = AttackLoadout(
                    secondaryPorts = listOf(26),
                    maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
                ),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-switch-install-script"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertNull(updatedAttacker.filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertEquals(97.8, updatedTarget.port(26)?.health)
        assertEquals(setOf("ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), publisher.deltas.single { it.first == setOf("attacker-conn") }.second.deltaKeys)
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
    fun finalizeMessagePublishesTargetedUiEventBeforeCompletionCleanup() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        finalize = """int main() { message("TARGET-IP", "finish-message"); return 0; }""",
                    ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-finalize-message"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()
        publisher.uiEvents.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = requireNotNull(repository.load(targetId))
        assertEquals(0.0, updatedTarget.port(25)?.health)
        assertEquals(1, publisher.uiEvents.size)
        assertEquals(setOf("target-conn"), publisher.uiEvents.single().first)
        assertEquals("finish-message", assertIs<TextMessageUiEvent>(publisher.uiEvents.single().second).message)
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
    fun finalizeDestroyWatchesMutatesTargetWatchesBeforeCompletionCleanup() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        finalize = """int main() { destroyWatches(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    health = 1.5,
                    currentCpuLoad = 9.0,
                    watches = WatchManagerState(
                        watches = listOf(
                            targetWatch(note = "remove-health", kind = WatchKind.HEALTH, installPort = 25, cpuCost = 4.0),
                            targetWatch(note = "remove-cash", kind = WatchKind.PETTY_CASH, installPort = 25, cpuCost = 2.0),
                            targetWatch(note = "keep-scan", kind = WatchKind.SCAN, installPort = 25, cpuCost = 3.0),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-finalize-destroy-watches"),
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
        assertEquals(listOf("keep-scan"), updatedTarget.watches.watches.map { it.note })
        assertEquals(3.0, updatedTarget.runtime.currentCpuLoad)
        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(setOf("watches", "runtime", "ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        val attackerDelta = publisher.deltas.single { it.first == setOf("attacker-conn") }.second
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.deltaKeys)
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun destroyWatchesNoOpsForImmuneTargetsButStillFinalizesNormally() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { destroyWatches(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    currentCpuLoad = 5.0,
                    destroyWatchesImmune = true,
                    watches = WatchManagerState(
                        watches = listOf(
                            targetWatch(note = "immune-health", kind = WatchKind.HEALTH, installPort = 25, cpuCost = 5.0),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-destroy-watches-immune"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = requireNotNull(repository.load(targetId))
        assertEquals(listOf("immune-health"), updatedTarget.watches.watches.map { it.note })
        assertEquals(5.0, updatedTarget.runtime.currentCpuLoad)
        assertEquals(97.8, updatedTarget.port(25)?.health)
        assertEquals(setOf("ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
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
                    attackScriptBundle = attackScriptBundle(continueScript = """int main() { totallyUnsupported(); return 0; }"""),
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
    fun switchAttackThenDestroyWatchesActsOnTheRetargetedPort() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { switchAttack(); destroyWatches(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    currentCpuLoad = 5.0,
                    watches = WatchManagerState(
                        watches = listOf(
                            targetWatch(note = "original-port", kind = WatchKind.HEALTH, installPort = 25, cpuCost = 2.0),
                            targetWatch(note = "retargeted-port", kind = WatchKind.PETTY_CASH, installPort = 26, cpuCost = 3.0),
                        ),
                    ),
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-switch-destroy"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = requireNotNull(repository.load(targetId))
        assertEquals(listOf("original-port"), updatedTarget.watches.watches.map { it.note })
        assertEquals(2.0, updatedTarget.runtime.currentCpuLoad)
        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertEquals(97.8, updatedTarget.port(26)?.health)
        assertEquals(setOf("watches", "runtime", "ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun switchAttackThenChangeDailyPayActsOnTheRetargetedHttpPort() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { switchAttack(); changeDailyPay("REV-IP"); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    installedApplication = InstalledApplication(
                        name = "bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                    ),
                    portType = "bank",
                    additionalPorts = listOf(
                        PortState(
                            number = 26,
                            type = "http",
                            enabled = true,
                            health = 100.0,
                            installedApplication = InstalledApplication(
                                name = "site.bin",
                                kind = ApplicationKind.HTTP,
                            ),
                            installedFirewall = InstalledFirewall(
                                name = "site-fw.bin",
                                actionProfile = FirewallActionProfile(
                                    changeDailyPayReductionMultiplier = 0.3,
                                ),
                            ),
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
                loadout = AttackLoadout(secondaryPorts = listOf(26)),
                attackProgramRegistry = registry,
                clock = { 5_000L },
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-switch-change-pay"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(GameStateId("REV-IP"), updatedTarget.dailyPay.revenueTargetStateId)
        assertEquals(0.3, updatedTarget.dailyPay.reductionMultiplier)
        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertEquals(97.8, updatedTarget.port(26)?.health)
        assertEquals(setOf("dailyPay", "ports", "combat"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
        assertEquals(listOf(setOf("attacker-conn"), setOf("attacker-conn")), publisher.uiEvents.map { it.first })
        val retargetMessage = assertIs<AttackMessageUiEvent>(publisher.uiEvents[0].second)
        assertEquals("Daily pay successfully changed.", retargetMessage.message)
        assertEquals(26, retargetMessage.port)
        assertEquals(targetId.value, retargetMessage.ip)
        assertEquals(
            "Daily pay successfully changed.",
            assertIs<TextMessageUiEvent>(publisher.uiEvents[1].second).message,
        )
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun finalizeChangeDailyPayPublishesDailyPayUiEventsBeforeCompletionCleanup() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        finalize = """int main() { changeDailyPay("REV-IP"); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    health = 1.5,
                    installedApplication = InstalledApplication(
                        name = "site.bin",
                        kind = ApplicationKind.HTTP,
                    ),
                    portType = "http",
                    firewallActionProfile = FirewallActionProfile(
                        changeDailyPayReductionMultiplier = 0.3,
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-finalize-change-pay"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()
        publisher.uiEvents.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(0.0, updatedTarget.port(25)?.health)
        assertEquals(GameStateId("REV-IP"), updatedTarget.dailyPay.revenueTargetStateId)
        assertEquals(0.3, updatedTarget.dailyPay.reductionMultiplier)
        assertEquals(listOf(setOf("attacker-conn"), setOf("attacker-conn")), publisher.uiEvents.map { it.first })
        val completionMessage = assertIs<AttackMessageUiEvent>(publisher.uiEvents[0].second)
        assertEquals("Daily pay successfully changed.", completionMessage.message)
        assertEquals(25, completionMessage.port)
        assertEquals(targetId.value, completionMessage.ip)
        assertEquals(
            "Daily pay successfully changed.",
            assertIs<TextMessageUiEvent>(publisher.uiEvents[1].second).message,
        )
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
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

    @Test
    fun zombieStartRequiresInitializeAuthorizationWithoutMutation() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                controllerId to attackerState(controllerId),
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = attackScriptBundle(initialize = """int main() { return 0; }"""),
                ),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("controller-conn", controllerId)
            register("zombie-conn", zombieId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        val response = dispatcher.request(
            command = StartZombieAttackSessionCommand(
                controllerStateId = controllerId,
                zombieStateId = zombieId,
                targetStateId = targetId,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "zombie-attack-denied"),
            publisher = publisher,
        )

        val updatedController = requireNotNull(repository.load(controllerId))
        val updatedZombie = requireNotNull(repository.load(zombieId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertFalse(response.accepted)
        assertEquals("zombie-not-authorized", response.message)
        assertEquals(100.0, updatedController.economy.pettyCash)
        assertFalse(updatedZombie.port(12)?.attacking == true)
        assertEquals(0.0, updatedZombie.runtime.currentCpuLoad)
        assertTrue(updatedZombie.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertTrue(publisher.deltas.isEmpty())
        assertTrue(publisher.programUpdates.isEmpty())
    }

    @Test
    fun zombieStartChargesControllerScopesProgramUpdatesToControllerAndControllerCanCancel() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                controllerId to attackerState(controllerId),
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle(controllerId.value),
                ),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("controller-conn", controllerId)
            register("zombie-conn", zombieId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        val response = dispatcher.request(
            command = StartZombieAttackSessionCommand(
                controllerStateId = controllerId,
                zombieStateId = zombieId,
                targetStateId = targetId,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(secondaryPorts = listOf(26)),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "zombie-attack-start"),
            publisher = publisher,
        )

        val updatedController = requireNotNull(repository.load(controllerId))
        val updatedZombie = requireNotNull(repository.load(zombieId))
        val updatedTarget = requireNotNull(repository.load(targetId))
        val session = updatedZombie.combat.activeAttacksBySourcePort.getValue(12)

        assertTrue(response.accepted)
        assertEquals(80.0, updatedController.economy.pettyCash)
        assertEquals(100.0, updatedZombie.economy.pettyCash)
        assertTrue(updatedZombie.port(12)?.attacking == true)
        assertEquals(8.0, updatedZombie.runtime.currentCpuLoad)
        assertEquals(AttackMode.ZOMBIE, session.attackMode)
        assertEquals(controllerId, session.controllerStateId)
        assertEquals(controllerId, session.authorizedZombieStateId)
        assertEquals(listOf(25, 26), session.targetCyclePorts)
        assertEquals(controllerId, response.session?.controllerStateId)
        assertEquals(controllerId, response.session?.authorizedZombieStateId)
        assertEquals(AttackMode.ZOMBIE, response.session?.attackMode)
        assertEquals(zombieId, updatedTarget.combat.incomingAttacksByTargetPort.getValue(25).attackerStateId)
        assertTrue(publisher.deltas.any { it.first == setOf("controller-conn") && it.second.deltaKeys == setOf("economy") })
        assertTrue(publisher.deltas.any { it.first == setOf("zombie-conn") && it.second.deltaKeys == setOf("ports", "combat", "runtime") })
        assertTrue(publisher.deltas.any { it.first == setOf("target-conn") && it.second.deltaKeys == setOf("combat") })

        runCurrent()

        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
        assertEquals(setOf("controller-conn"), publisher.programUpdates.single().first)

        publisher.deltas.clear()
        publisher.programUpdates.clear()

        val cancelResponse = dispatcher.request(
            command = CancelZombieAttackSessionCommand(
                controllerStateId = controllerId,
                zombieStateId = zombieId,
                sourcePort = 12,
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "zombie-attack-cancel"),
            publisher = publisher,
        )
        runCurrent()

        val cancelledZombie = requireNotNull(repository.load(zombieId))
        val cancelledTarget = requireNotNull(repository.load(targetId))

        assertTrue(cancelResponse.accepted)
        assertTrue(cancelResponse.hadActiveSession)
        assertTrue(cancelledZombie.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(cancelledTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertFalse(cancelledZombie.port(12)?.attacking == true)
        assertEquals(0.0, cancelledZombie.runtime.currentCpuLoad)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
        assertEquals(setOf("controller-conn"), publisher.programUpdates.single().first)
        assertTrue(publisher.deltas.any { it.first == setOf("zombie-conn") && it.second.deltaKeys == setOf("ports", "combat", "runtime") })
        assertTrue(publisher.deltas.any { it.first == setOf("target-conn") && it.second.deltaKeys == setOf("combat") })
    }

    @Test
    fun requestZombieAttackRoutesThroughTheInternalRuntimeAndReturnsDedicatedResponse() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                controllerId to attackerState(controllerId),
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle(controllerId.value),
                ),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("controller-conn", controllerId)
            register("zombie-conn", zombieId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestZombieAttackCommand(
                controllerStateId = controllerId,
                controllerIp = controllerId.value,
                zombieStateId = zombieId,
                targetStateId = targetId,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(secondaryPorts = listOf(26)),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "request-zombie-attack"),
            publisher = publisher,
        )

        val updatedController = requireNotNull(repository.load(controllerId))
        val updatedZombie = requireNotNull(repository.load(zombieId))

        assertTrue(response.accepted)
        assertNull(response.failureCode)
        assertEquals(controllerId, response.controllerStateId)
        assertEquals(zombieId, response.zombieStateId)
        assertEquals(targetId, response.targetStateId)
        assertEquals(20.0, response.chargedAmount)
        assertEquals(80.0, response.controllerPettyCashAfter)
        assertEquals(8.0, response.zombieCpuLoadAfter)
        assertEquals(updatedController.version, response.controllerVersion)
        assertEquals(updatedZombie.version, response.zombieVersion)
        assertEquals(AttackMode.ZOMBIE, response.session?.attackMode)
        assertEquals(controllerId, response.session?.controllerStateId)
        assertTrue(publisher.uiEvents.isEmpty())
    }

    @Test
    fun requestZombieAttackPublishesLockedUiMappingsForTypedFailures() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")

        suspend fun execute(
            controllerState: ComputerState = attackerState(controllerId),
            zombieState: ComputerState? = attackerState(
                zombieId,
                attackScriptBundle = zombieAuthorizedBundle(controllerId.value),
            ),
            targetState: ComputerState = targetState(targetId),
            controllerIp: String = controllerId.value,
        ): Pair<ZombieAttackStartResponse, RecordingGameStatePublisher> {
            val seededStates = buildMap {
                put(controllerId, controllerState)
                zombieState?.let { put(zombieId, it) }
                put(targetId, targetState)
            }
            val repository = InMemoryComputerStateRepository(seededStates = seededStates)
            val interests = InMemoryInterestRegistry().apply {
                register("controller-conn", controllerId)
                zombieState?.let { register("zombie-conn", zombieId) }
                register("target-conn", targetId)
            }
            val publisher = RecordingGameStatePublisher()
            val dispatcher = dispatcher(repository, interests)

            val response = dispatcher.request(
                command = RequestZombieAttackCommand(
                    controllerStateId = controllerId,
                    controllerIp = controllerIp,
                    zombieStateId = zombieId,
                    targetStateId = targetId,
                    sourcePort = 12,
                    targetPort = 25,
                    loadout = AttackLoadout(),
                ),
                metadata = CommandMetadata(connectionId = "controller-conn"),
                publisher = publisher,
            )
            return response to publisher
        }

        val (mismatchResponse, mismatchPublisher) = execute(controllerIp = "OTHER-IP")
        val (cashResponse, cashPublisher) = execute(
            controllerState = attackerState(controllerId).copy(
                economy = attackerState(controllerId).economy.copy(pettyCash = 10.0),
            ),
        )
        val (busyResponse, busyPublisher) = execute(
            zombieState = attackerState(zombieId).copy(
                ports = attackerState(zombieId).ports.markAttacking(12, true),
            ),
        )
        val (invalidTargetResponse, invalidTargetPublisher) = execute(
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

        assertFalse(mismatchResponse.accepted)
        assertEquals(ZombieAttackStartFailureCode.CONTROLLER_IP_MISMATCH, mismatchResponse.failureCode)
        assertTrue(mismatchPublisher.uiEvents.isEmpty())

        assertFalse(cashResponse.accepted)
        assertEquals(ZombieAttackStartFailureCode.INSUFFICIENT_PETTY_CASH, cashResponse.failureCode)
        val cashPopup = assertIs<PopupUiEvent>(cashPublisher.uiEvents.single().second)
        assertEquals(setOf("controller-conn"), cashPublisher.uiEvents.single().first)
        assertEquals("It costs \$20 to attempt an attack from a zombie port.", cashPopup.message)
        assertEquals(PopupUiStyle.ERROR, cashPopup.style)

        assertFalse(busyResponse.accepted)
        assertEquals(ZombieAttackStartFailureCode.SOURCE_ALREADY_ATTACKING, busyResponse.failureCode)
        val busyUi = assertIs<ZombieAttackUiEvent>(busyPublisher.uiEvents.single().second)
        assertEquals(setOf("controller-conn"), busyPublisher.uiEvents.single().first)
        assertEquals("Port 12 is already performing an attack.", busyUi.message)
        assertEquals(zombieId.value, busyUi.zombieIp)
        assertEquals(12, busyUi.sourcePort)

        assertFalse(invalidTargetResponse.accepted)
        assertEquals(ZombieAttackStartFailureCode.INVALID_TARGET_PORT, invalidTargetResponse.failureCode)
        val invalidTargetUi = assertIs<ZombieAttackUiEvent>(invalidTargetPublisher.uiEvents.single().second)
        assertEquals("You cannot access this port.", invalidTargetUi.message)
        assertEquals(zombieId.value, invalidTargetUi.zombieIp)
        assertEquals(12, invalidTargetUi.sourcePort)
    }

    @Test
    fun requestZombieCancelRoutesThroughTheInternalRuntimeAndPreservesHadActiveSession() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")
        val liveSession = AttackSessionState(
            programId = "zombie-program",
            sourcePort = 12,
            targetStateId = targetId,
            targetPort = 25,
            attackMode = AttackMode.ZOMBIE,
            controllerStateId = controllerId,
            authorizedZombieStateId = controllerId,
            targetView = AttackTargetView(
                targetStateId = targetId,
                targetPort = 25,
                health = 100.0,
            ),
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                controllerId to attackerState(controllerId),
                zombieId to attackerState(
                    zombieId,
                ).copy(
                    ports = attackerState(zombieId).ports.markAttacking(12, true),
                    combat = CombatState(
                        activeAttacksBySourcePort = mapOf(12 to liveSession),
                    ),
                    runtime = RuntimeState(currentCpuLoad = 8.0),
                ),
                targetId to targetState(targetId).copy(
                    combat = CombatState(
                        incomingAttacksByTargetPort = mapOf(
                            25 to IncomingAttackState(
                                attackerStateId = zombieId,
                                attackerSourcePort = 12,
                                targetPort = 25,
                                startedAtEpochMillis = 1_000L,
                                windowHandle = 0,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("controller-conn", controllerId)
            register("zombie-conn", zombieId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = dispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestZombieCancelAttackCommand(
                controllerStateId = controllerId,
                controllerIp = controllerId.value,
                zombieStateId = zombieId,
                sourcePort = 12,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "zombie-cancel"),
            publisher = publisher,
        )

        assertTrue(response.accepted)
        assertTrue(response.hadActiveSession)
        assertNull(response.failureCode)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
        assertEquals(setOf("controller-conn"), publisher.programUpdates.single().first)
        assertTrue(requireNotNull(repository.load(zombieId)).combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(requireNotNull(repository.load(targetId)).combat.incomingAttacksByTargetPort.isEmpty())
    }

    @Test
    fun requestZombieCancelDoesNotAffectDirectAttackSessions() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                controllerId to attackerState(controllerId),
                zombieId to attackerState(zombieId),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("controller-conn", controllerId)
            register("zombie-conn", zombieId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        dispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = zombieId,
                targetStateId = targetId,
                sourceIp = zombieId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "zombie-conn", requestId = "direct-zombie-host-attack"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        val response = dispatcher.request(
            command = RequestZombieCancelAttackCommand(
                controllerStateId = controllerId,
                controllerIp = controllerId.value,
                zombieStateId = zombieId,
                sourcePort = 12,
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "no-op-zombie-cancel"),
            publisher = publisher,
        )

        assertTrue(response.accepted)
        assertFalse(response.hadActiveSession)
        assertNull(response.failureCode)
        assertNotNull(repository.load(zombieId)?.combat?.activeAttacksBySourcePort?.get(12))
        assertTrue(publisher.deltas.isEmpty())
        assertTrue(publisher.programUpdates.isEmpty())
    }

    @Test
    fun zombieBerserkUsesControllerAttackXpAndZombieHostSelfDamageWithControllerAttribution() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                controllerId to attackerState(controllerId),
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle(
                        controllerIp = controllerId.value,
                        continueScript = """int main() { berserk(); return 0; }""",
                    ),
                ),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("controller-conn", controllerId)
            register("zombie-conn", zombieId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val passiveWatchSink = RecordingPassiveWatchTriggerSink()
        val dispatcher = dispatcher(repository, interests, passiveWatchSink = passiveWatchSink)

        dispatcher.request(
            command = StartZombieAttackSessionCommand(
                controllerStateId = controllerId,
                zombieStateId = zombieId,
                targetStateId = targetId,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "zombie-berserk-start"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()
        passiveWatchSink.triggers.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedController = requireNotNull(repository.load(controllerId))
        val updatedZombie = requireNotNull(repository.load(zombieId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(4.4, updatedController.stats.skillExperience(ScriptFamily.ATTACK))
        assertEquals(0.0, updatedZombie.stats.skillExperience(ScriptFamily.ATTACK))
        assertEquals(98.9, updatedZombie.port(12)?.health)
        assertEquals(95.6, updatedTarget.port(25)?.health)
        assertTrue(
            passiveWatchSink.triggers
                .filterIsInstance<PassiveWatchTrigger.HealthChanged>()
                .filter { it.targetStateId == targetId }
                .all { it.sourceIp == controllerId.value },
        )
        assertContains(
            passiveWatchSink.triggers.filterIsInstance<PassiveWatchTrigger.HealthChanged>(),
            PassiveWatchTrigger.HealthChanged(
                targetStateId = zombieId,
                sourceIp = controllerId.value,
                external = true,
                portNumber = 12,
                sourcePort = 12,
                previousHealth = 100.0,
                newHealth = 98.9,
            ),
        )
        assertTrue(publisher.deltas.any { it.first == setOf("controller-conn") && it.second.deltaKeys == setOf("stats") })
        assertTrue(publisher.deltas.any { it.first == setOf("zombie-conn") && it.second.deltaKeys.contains("ports") })
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
        assertEquals(setOf("controller-conn"), publisher.programUpdates.single().first)
    }

    @Test
    fun zombieEmptyPettyCashUsesControllerEconomyNotZombieHost() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                controllerId to attackerState(controllerId),
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle(
                        controllerIp = controllerId.value,
                        continueScript = """int main() { emptyPettyCash(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    pettyCash = 80.0,
                    portType = "bank",
                    installedApplication = InstalledApplication(
                        name = "target-bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("controller-conn", controllerId)
            register("zombie-conn", zombieId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        dispatcher.request(
            command = StartZombieAttackSessionCommand(
                controllerStateId = controllerId,
                zombieStateId = zombieId,
                targetStateId = targetId,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "zombie-empty-cash-start"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedController = requireNotNull(repository.load(controllerId))
        val updatedZombie = requireNotNull(repository.load(zombieId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(160.0, updatedController.economy.pettyCash)
        assertEquals(100.0, updatedZombie.economy.pettyCash)
        assertEquals(0.0, updatedTarget.economy.pettyCash)
        assertEquals(2.2, updatedController.stats.skillExperience(ScriptFamily.ATTACK))
        assertEquals(0.0, updatedZombie.stats.skillExperience(ScriptFamily.ATTACK))
        assertTrue(updatedZombie.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
        assertEquals(setOf("controller-conn"), publisher.programUpdates.single().first)
        assertTrue(publisher.deltas.any { it.first == setOf("controller-conn") && it.second.deltaKeys.containsAll(setOf("economy", "stats")) })
        assertTrue(publisher.deltas.any { it.first == setOf("zombie-conn") && it.second.deltaKeys == setOf("combat", "ports", "runtime") })
    }

    @Test
    fun zombieStealFileUsesControllerFilesystemNotZombieHost() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                controllerId to attackerState(controllerId),
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle(
                        controllerIp = controllerId.value,
                        continueScript = """int main() { stealFile(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    portType = "ftp",
                    installedApplication = InstalledApplication(
                        name = "target-ftp.bin",
                        kind = ApplicationKind.FTP,
                        cpuCost = 2.0,
                    ),
                    filesystemFiles = listOf(
                        storedFile(path = "/Public", name = "loot.txt", quantity = 1),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("controller-conn", controllerId)
            register("zombie-conn", zombieId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        dispatcher.request(
            command = StartZombieAttackSessionCommand(
                controllerStateId = controllerId,
                zombieStateId = zombieId,
                targetStateId = targetId,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "zombie-steal-file-start"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedController = requireNotNull(repository.load(controllerId))
        val updatedZombie = requireNotNull(repository.load(zombieId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertNotNull(updatedController.filesystem.resolveFile("/", "loot.txt"))
        assertNull(updatedZombie.filesystem.resolveFile("/", "loot.txt"))
        assertTrue(updatedTarget.filesystem.listDirectory("/Public").files.isEmpty())
        assertTrue(updatedZombie.combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
        assertEquals(setOf("controller-conn"), publisher.programUpdates.single().first)
        assertTrue(publisher.deltas.any { it.first == setOf("controller-conn") && it.second.deltaKeys == setOf("filesystem", "stats") })
        assertTrue(publisher.deltas.any { it.first == setOf("zombie-conn") && it.second.deltaKeys == setOf("combat", "ports", "runtime") })
        assertTrue(publisher.deltas.any { it.first == setOf("target-conn") && it.second.deltaKeys == setOf("filesystem", "ports", "combat") })
    }

    @Test
    fun showChoicesPublishesOncePerDirectSessionAndPersistsWhileRunning() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { showChoices(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    installedApplication = InstalledApplication(
                        name = "target-http.bin",
                        kind = ApplicationKind.HTTP,
                        cpuCost = 2.0,
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "show-choices-start"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.uiEvents.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        assertEquals(1, publisher.uiEvents.size)
        assertEquals(setOf("attacker-conn"), publisher.uiEvents.single().first)
        val choiceEvent = assertIs<ShowChoicesUiEvent>(publisher.uiEvents.single().second)
        assertEquals(targetId.value, choiceEvent.targetIp)
        assertEquals(25, choiceEvent.targetPort)
        assertEquals(ShowChoicesType.HTTP, choiceEvent.choiceType)
        assertEquals(0, choiceEvent.windowHandle)
        assertTrue(requireNotNull(repository.load(attackerId)).combat.activeAttacksBySourcePort.getValue(12).choicesShown)

        publisher.deltas.clear()
        publisher.uiEvents.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        assertTrue(publisher.uiEvents.isEmpty())
        assertTrue(requireNotNull(repository.load(attackerId)).combat.activeAttacksBySourcePort.getValue(12).choicesShown)
    }

    @Test
    fun switchAttackShowChoicesAndCancelUsesTheRetargetedPort() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { switchAttack(); showChoices(); cancelAttack(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    installedApplication = InstalledApplication(
                        name = "target-http.bin",
                        kind = ApplicationKind.HTTP,
                        cpuCost = 2.0,
                    ),
                    additionalPorts = listOf(
                        PortState(
                            number = 26,
                            type = "ftp",
                            enabled = true,
                            health = 100.0,
                            installedApplication = InstalledApplication(
                                name = "alt-ftp.bin",
                                kind = ApplicationKind.FTP,
                                cpuCost = 2.0,
                            ),
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
                loadout = AttackLoadout(secondaryPorts = listOf(26)),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "switch-show-choices-start"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.uiEvents.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        assertEquals(1, publisher.uiEvents.size)
        assertEquals(setOf("attacker-conn"), publisher.uiEvents.single().first)
        val choiceEvent = assertIs<ShowChoicesUiEvent>(publisher.uiEvents.single().second)
        assertEquals(targetId.value, choiceEvent.targetIp)
        assertEquals(26, choiceEvent.targetPort)
        assertEquals(ShowChoicesType.FTP, choiceEvent.choiceType)
        assertEquals(ProgramLifecycleStatus.CANCELLED, publisher.programUpdates.single().second.status)
        assertTrue(requireNotNull(repository.load(attackerId)).combat.activeAttacksBySourcePort.isEmpty())
    }

    @Test
    fun showChoicesRoutesToZombieControllerListenersOnly() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                controllerId to attackerState(controllerId),
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle(
                        controllerIp = controllerId.value,
                        continueScript = """int main() { showChoices(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    installedApplication = InstalledApplication(
                        name = "target-http.bin",
                        kind = ApplicationKind.HTTP,
                        cpuCost = 2.0,
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("controller-conn", controllerId)
            register("zombie-conn", zombieId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = dispatcher(repository, interests)

        dispatcher.request(
            command = StartZombieAttackSessionCommand(
                controllerStateId = controllerId,
                zombieStateId = zombieId,
                targetStateId = targetId,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "zombie-show-choices-start"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.uiEvents.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        assertEquals(1, publisher.uiEvents.size)
        assertEquals(setOf("controller-conn"), publisher.uiEvents.single().first)
        val choiceEvent = assertIs<ShowChoicesUiEvent>(publisher.uiEvents.single().second)
        assertEquals(targetId.value, choiceEvent.targetIp)
        assertEquals(25, choiceEvent.targetPort)
        assertEquals(ShowChoicesType.HTTP, choiceEvent.choiceType)
        assertTrue(publisher.uiEvents.none { it.first == setOf("zombie-conn") || it.first == setOf("target-conn") })
        assertEquals(ProgramLifecycleStatus.RUNNING, publisher.programUpdates.single().second.status)
        assertEquals(setOf("controller-conn"), publisher.programUpdates.single().first)
    }

    @Test
    fun showChoicesIgnoresUnsupportedTargetKindsWithoutConsumingSessionState() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { showChoices(); return 0; }""",
                    ),
                ),
                targetId to targetState(
                    targetId,
                    installedApplication = InstalledApplication(
                        name = "target-generic.bin",
                        kind = ApplicationKind.GENERIC,
                        cpuCost = 2.0,
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
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "show-choices-generic-start"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.uiEvents.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        assertTrue(publisher.uiEvents.isEmpty())
        assertFalse(requireNotNull(repository.load(attackerId)).combat.activeAttacksBySourcePort.getValue(12).choicesShown)
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
        filesystemFiles: List<StoredFile> = emptyList(),
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
            filesystem = filesystemFiles.fold(FilesystemState()) { filesystem, file ->
                filesystem.saveFile(file)
            },
        )
    }

    private fun targetState(
        stateId: GameStateId,
        health: Double = 100.0,
        pettyCash: Double = 0.0,
        logs: List<ComputerLogEntry> = emptyList(),
        freezeExpiresAtEpochMillis: Long? = null,
        freezeImmune: Boolean = false,
        destroyWatchesImmune: Boolean = false,
        firewallCombatProfile: FirewallCombatProfile = FirewallCombatProfile(),
        firewallActionProfile: FirewallActionProfile = FirewallActionProfile(),
        portType: String = "http",
        installedApplication: InstalledApplication? = null,
        additionalPorts: List<PortState> = emptyList(),
        watches: WatchManagerState = WatchManagerState(),
        currentCpuLoad: Double = 0.0,
        isNpc: Boolean = false,
        filesystemFiles: List<StoredFile> = emptyList(),
    ): ComputerState {
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}", isNpc = isNpc).copy(
            economy = EconomyState(
                pettyCash = pettyCash,
            ),
            logs = LogState(logs),
            filesystem = filesystemFiles.fold(FilesystemState()) { filesystem, file ->
                filesystem.saveFile(file)
            },
            watches = watches,
            runtime = RuntimeState(currentCpuLoad = currentCpuLoad),
            hardware = HardwareState(
                equipmentSlots = buildMap {
                    if (freezeImmune) {
                        put(
                            EquipmentSlot.PCI,
                            InstalledEquipment(
                                slot = EquipmentSlot.PCI,
                                name = "freeze-shield.bin",
                                freezeImmune = true,
                            ),
                        )
                    }
                    if (destroyWatchesImmune) {
                        put(
                            EquipmentSlot.AGP,
                            InstalledEquipment(
                                slot = EquipmentSlot.AGP,
                                name = "watch-shield.bin",
                                destroyWatchesImmune = true,
                            ),
                        )
                    }
                },
            ),
            ports = buildList {
                add(
                    PortState(
                        number = 25,
                        type = portType,
                        enabled = true,
                        health = health,
                        installedApplication = installedApplication,
                        freezeExpiresAtEpochMillis = freezeExpiresAtEpochMillis,
                        installedFirewall = InstalledFirewall(
                            name = "target-wall.bin",
                            combatProfile = firewallCombatProfile,
                            actionProfile = firewallActionProfile,
                        ),
                    ),
                )
                addAll(additionalPorts)
            },
        )
    }

    private fun targetWatch(
        note: String,
        kind: WatchKind,
        installPort: Int,
        cpuCost: Double,
        enabled: Boolean = true,
        quantityThreshold: Double = 50.0,
        baselineQuantity: Double = 100.0,
        fireScript: String? = null,
    ): InstalledWatch {
        return InstalledWatch(
            kind = kind,
            enabled = enabled,
            note = note,
            cpuCost = cpuCost,
            quantityThreshold = quantityThreshold,
            baselineQuantity = baselineQuantity,
            installPort = installPort,
            searchFirewallType = 0,
            observedPorts = listOf(installPort),
            contents = fireScript ?: "watch script",
            scriptBundle = fireScript?.let {
                ProgramScriptBundle(
                    family = ScriptFamily.WATCH,
                    scriptsBySlot = linkedMapOf(ProgramScriptSlot.FIRE to it),
                )
            },
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.WATCH,
                applicationKind = ApplicationKind.WATCH,
                outputName = "watch.bin",
            ),
        )
    }

    private fun storedFile(
        path: String,
        name: String,
        quantity: Int = 1,
        contents: String = "contents",
        description: String = "description",
        kind: StoredFileKind = StoredFileKind.TEXT,
        maker: String = "maker",
        compileCost: Double = 2.0,
        cpuCost: Double = 3.0,
        price: Double = 4.0,
        compiledBinary: CompiledBinaryMetadata? = null,
        scriptBundle: ProgramScriptBundle? = null,
    ): StoredFile {
        return StoredFile(
            path = buildFilePath(path, name),
            name = name,
            kind = kind,
            contents = contents,
            description = description,
            quantity = quantity,
            maker = maker,
            compileCost = compileCost,
            cpuCost = cpuCost,
            price = price,
            compiledBinary = compiledBinary,
            scriptBundle = scriptBundle,
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

    private fun zombieAuthorizedBundle(
        controllerIp: String,
        continueScript: String? = null,
        finalize: String? = null,
    ): ProgramScriptBundle {
        return attackScriptBundle(
            initialize = """int main() { zombie("$controllerIp"); return 0; }""",
            continueScript = continueScript,
            finalize = finalize,
        )
    }
}
