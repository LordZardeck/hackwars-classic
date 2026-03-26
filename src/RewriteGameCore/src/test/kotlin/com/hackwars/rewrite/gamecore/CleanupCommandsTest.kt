package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CleanupCommandsTest {
    @Test
    fun completedShowChoicesAttackPersistsWeakenedAccessForTheAttacker() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to playerState(
                    attackerId,
                    attackScriptBundle = attackScriptBundle(
                        continueScript = """int main() { showChoices(); return 0; }""",
                    ),
                ),
                targetId to breachTargetState(
                    targetId,
                    health = 1.5,
                    installedApplication = InstalledApplication(
                        name = "target-http.bin",
                        kind = ApplicationKind.HTTP,
                        cpuCost = 2.0,
                    ),
                    portType = "http",
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
                clock = { 5_000L },
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "cleanup-show-choices"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()
        publisher.uiEvents.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = requireNotNull(repository.load(targetId))
        val weakenedAccess = requireNotNull(updatedTarget.port(25)?.weakenedAccess)

        assertEquals(0.0, updatedTarget.port(25)?.health)
        assertEquals(attackerId, weakenedAccess.actorStateId)
        assertEquals(5_000L, weakenedAccess.grantedAtEpochMillis)
        assertEquals(setOf("attacker-conn"), publisher.uiEvents.single().first)
        val showChoices = assertIs<ShowChoicesUiEvent>(publisher.uiEvents.single().second)
        assertEquals(targetId.value, showChoices.targetIp)
        assertEquals(25, showChoices.targetPort)
        assertEquals(ShowChoicesType.HTTP, showChoices.choiceType)
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
    }

    @Test
    fun healPortSucceedsChargesMultiplierIncrementsHealCountAndRefreshesHealthWatchBaseline() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val baseState = playerState(stateId)
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to baseState.copy(
                    hardware = baseState.hardware.copy(
                        equipmentSlots = mapOf(
                            EquipmentSlot.CPU to InstalledEquipment(
                                slot = EquipmentSlot.CPU,
                                name = "healer.bin",
                                healCostMultiplier = 0.5,
                            ),
                        ),
                    ),
                    ports = baseState.ports + PortState(
                        number = 22,
                        type = "ssh",
                        enabled = true,
                        health = 80.0,
                    ),
                    watches = WatchManagerState(
                        watches = listOf(
                            installedHealthWatch(
                                installPort = 22,
                                baselineQuantity = 80.0,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("local-conn", stateId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = dispatcher(repository, interests)

        val response = dispatcher.request(
            command = HealPortCommand(
                stateId = stateId,
                portNumber = 22,
            ),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "healport-success"),
            publisher = publisher,
        )

        val updated = requireNotNull(repository.load(stateId))

        assertTrue(response.accepted)
        assertEquals(HealPortOutcome.SUCCESS, response.outcome)
        assertEquals(20.0, response.chargedAmount)
        assertEquals(80.0, response.pettyCashAfter)
        assertEquals(100.0, updated.port(22)?.health)
        assertEquals(1, updated.port(22)?.healCount)
        assertEquals(100.0, updated.watches.watches.single().baselineQuantity)
        assertEquals(setOf("economy", "ports", "watches"), publisher.deltas.flatMapTo(linkedSetOf()) { it.second.deltaKeys })
    }

    @Test
    fun healPortRejectsCleanupFailureModesWithTypedOutcomes() = runTest {
        val stateId = GameStateId("LOCAL-IP")

        suspend fun executeFor(state: ComputerState): HealPortResponse {
            val repository = InMemoryComputerStateRepository(seededStates = mapOf(stateId to state))
            val interests = InMemoryInterestRegistry().apply {
                register("local-conn", stateId)
            }
            val dispatcher = dispatcher(repository, interests)
            return dispatcher.request(
                command = HealPortCommand(
                    stateId = stateId,
                    portNumber = 22,
                ),
                metadata = CommandMetadata(connectionId = "local-conn", requestId = "healport-failure"),
                publisher = RecordingGameStatePublisher(),
            )
        }

        val baseState = playerState(stateId)
        val invalidPort = executeFor(
            state = baseState.copy(
                ports = baseState.ports + PortState(
                    number = 22,
                    type = "ssh",
                    enabled = false,
                    health = 80.0,
                ),
            ),
        )
        val noBank = executeFor(
            state = baseState.copy(
                economy = baseState.economy.copy(defaultBankPort = null),
                ports = baseState.ports + PortState(number = 22, type = "ssh", health = 80.0),
            ),
        )
        val overheated = executeFor(
            state = baseState.copy(
                runtime = RuntimeState(
                    currentCpuLoad = 101.0,
                    overheatStartedAtEpochMillis = System.currentTimeMillis(),
                ),
                ports = baseState.ports + PortState(number = 22, type = "ssh", health = 80.0),
            ),
        )
        val weakened = executeFor(
            state = baseState.copy(
                ports = baseState.ports + PortState(
                    number = 22,
                    type = "ssh",
                    health = 25.0,
                    weakenedAccess = WeakenedPortAccessState(
                        actorStateId = stateId,
                        grantedAtEpochMillis = 4_000L,
                        lastAccessedAtEpochMillis = 4_000L,
                    ),
                ),
            ),
        )
        val healLimit = executeFor(
            state = baseState.copy(
                ports = baseState.ports + PortState(number = 22, type = "ssh", health = 80.0, healCount = 9),
            ),
        )
        val lowCash = executeFor(
            state = baseState.copy(
                economy = baseState.economy.copy(pettyCash = 5.0),
                ports = baseState.ports + PortState(number = 22, type = "ssh", health = 80.0),
            ),
        )

        assertEquals(HealPortOutcome.INVALID_PORT, invalidPort.outcome)
        assertEquals(HealPortOutcome.ACTIVE_BANK_REQUIRED, noBank.outcome)
        assertEquals(HealPortOutcome.OVERHEATED, overheated.outcome)
        assertEquals(HealPortOutcome.WEAKENED, weakened.outcome)
        assertEquals(HealPortOutcome.HEAL_LIMIT_REACHED, healLimit.outcome)
        assertEquals(HealPortOutcome.INSUFFICIENT_PETTY_CASH, lowCash.outcome)
        assertTrue(listOf(invalidPort, noBank, overheated, weakened, healLimit, lowCash).all { !it.accepted })
    }

    @Test
    fun finalizeCancelledResetsOwnedWeakenedPortsAndReturnsTypedNoOpsOtherwise() = runTest {
        val actorId = GameStateId("LOCAL-IP")
        val otherActorId = GameStateId("OTHER-IP")
        val targetId = GameStateId("TARGET-IP")
        val targetState = breachTargetState(
            targetId,
            health = 0.0,
            installedApplication = InstalledApplication(
                name = "target-http.bin",
                kind = ApplicationKind.HTTP,
                cpuCost = 2.0,
            ),
            portType = "http",
        ).copy(
            ports = listOf(
                PortState(
                    number = 25,
                    type = "http",
                    enabled = true,
                    health = 0.0,
                    healCount = 4,
                    weakenedAccess = WeakenedPortAccessState(
                        actorStateId = actorId,
                        grantedAtEpochMillis = 4_000L,
                    ),
                    installedApplication = InstalledApplication(
                        name = "target-http.bin",
                        kind = ApplicationKind.HTTP,
                        cpuCost = 2.0,
                    ),
                ),
            ),
            watches = WatchManagerState(
                watches = listOf(
                    installedHealthWatch(
                        installPort = 25,
                        baselineQuantity = 0.0,
                    ),
                ),
            ),
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                actorId to playerState(actorId),
                otherActorId to playerState(otherActorId),
                targetId to targetState,
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("actor-conn", actorId)
            register("other-conn", otherActorId)
            register("target-conn", targetId)
        }
        val dispatcher = dispatcher(repository, interests)

        val denied = dispatcher.request(
            command = FinalizeCancelledCommand(
                actorStateId = otherActorId,
                targetStateId = targetId,
                targetPort = 25,
            ),
            metadata = CommandMetadata(connectionId = "other-conn", requestId = "finalize-denied"),
            publisher = RecordingGameStatePublisher(),
        )

        val successPublisher = RecordingGameStatePublisher()
        val success = dispatcher.request(
            command = FinalizeCancelledCommand(
                actorStateId = actorId,
                targetStateId = targetId,
                targetPort = 25,
            ),
            metadata = CommandMetadata(connectionId = "actor-conn", requestId = "finalize-success"),
            publisher = successPublisher,
        )

        val stale = dispatcher.request(
            command = FinalizeCancelledCommand(
                actorStateId = actorId,
                targetStateId = targetId,
                targetPort = 25,
            ),
            metadata = CommandMetadata(connectionId = "actor-conn", requestId = "finalize-stale"),
            publisher = RecordingGameStatePublisher(),
        )

        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(FinalizeCancelledOutcome.ACCESS_DENIED, denied.outcome)
        assertEquals(FinalizeCancelledOutcome.SUCCESS, success.outcome)
        assertEquals(FinalizeCancelledOutcome.NOT_WEAKENED, stale.outcome)
        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertEquals(0, updatedTarget.port(25)?.healCount)
        assertNull(updatedTarget.port(25)?.weakenedAccess)
        assertEquals(100.0, updatedTarget.watches.watches.single().baselineQuantity)
        assertEquals(setOf("ports", "watches"), successPublisher.deltas.flatMapTo(linkedSetOf()) { it.second.deltaKeys })
    }

    @Test
    fun zombieAttackCompletionGrantsWeakenedAccessToTheControllerAndOnlyControllerCanFinalize() = runTest {
        val controllerId = GameStateId("CONTROLLER-IP")
        val zombieId = GameStateId("ZOMBIE-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                controllerId to playerState(controllerId),
                zombieId to playerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle(controllerId.value),
                ),
                targetId to breachTargetState(
                    targetId,
                    health = 1.5,
                    installedApplication = InstalledApplication(
                        name = "target-http.bin",
                        kind = ApplicationKind.HTTP,
                        cpuCost = 2.0,
                    ),
                    portType = "http",
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

        val start = dispatcher.request(
            command = StartZombieAttackSessionCommand(
                controllerStateId = controllerId,
                zombieStateId = zombieId,
                targetStateId = targetId,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = registry,
                clock = { 7_000L },
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "cleanup-zombie-start"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val weakenedTarget = requireNotNull(repository.load(targetId))
        val weakenedAccess = requireNotNull(weakenedTarget.port(25)?.weakenedAccess)

        assertTrue(start.accepted)
        assertEquals(controllerId, weakenedAccess.actorStateId)
        assertEquals(7_000L, weakenedAccess.grantedAtEpochMillis)

        val zombieFinalize = dispatcher.request(
            command = FinalizeCancelledCommand(
                actorStateId = zombieId,
                targetStateId = targetId,
                targetPort = 25,
            ),
            metadata = CommandMetadata(connectionId = "zombie-conn", requestId = "cleanup-zombie-finalize-denied"),
            publisher = RecordingGameStatePublisher(),
        )
        val controllerFinalize = dispatcher.request(
            command = FinalizeCancelledCommand(
                actorStateId = controllerId,
                targetStateId = targetId,
                targetPort = 25,
            ),
            metadata = CommandMetadata(connectionId = "controller-conn", requestId = "cleanup-zombie-finalize-success"),
            publisher = RecordingGameStatePublisher(),
        )

        val finalizedTarget = requireNotNull(repository.load(targetId))

        assertEquals(FinalizeCancelledOutcome.ACCESS_DENIED, zombieFinalize.outcome)
        assertEquals(FinalizeCancelledOutcome.SUCCESS, controllerFinalize.outcome)
        assertEquals(100.0, finalizedTarget.port(25)?.health)
        assertNull(finalizedTarget.port(25)?.weakenedAccess)
    }

    @Test
    fun combatMaintenancePassiveHealRefreshesBaselinesWithoutFiringPassiveHealthWatches() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val passiveWatchSink = RecordingPassiveWatchTriggerSink()
        val baseState = playerState(stateId)
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to baseState.copy(
                    ports = baseState.ports + PortState(
                        number = 22,
                        type = "ssh",
                        enabled = true,
                        health = 80.0,
                    ),
                    watches = WatchManagerState(
                        watches = listOf(
                            installedHealthWatch(
                                installPort = 22,
                                baselineQuantity = 80.0,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("local-conn", stateId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = dispatcher(repository, interests, passiveWatchSink = passiveWatchSink)

        dispatcher.request(
            command = RefreshCombatMaintenanceRuntimeCommand(
                stateId = stateId,
                clock = { 10_000L },
            ),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "maintenance-heal"),
            publisher = publisher,
        )

        val updated = requireNotNull(repository.load(stateId))

        assertEquals(81.0, updated.port(22)?.health)
        assertEquals(81.0, updated.watches.watches.single().baselineQuantity)
        assertEquals(1L, updated.runtime.healCounter)
        assertTrue(passiveWatchSink.triggers.isEmpty())
        assertEquals(setOf("ports", "watches", "runtime"), publisher.deltas.flatMapTo(linkedSetOf()) { it.second.deltaKeys })
        assertTrue(publisher.programUpdates.isEmpty())
    }

    @Test
    fun weakenedPortsStayBlockedFromHealButCanBeFinalizeCancelledAfterPassiveHealRaisesHealth() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val baseState = playerState(stateId)
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to baseState.copy(
                    ports = baseState.ports + PortState(
                        number = 22,
                        type = "http",
                        enabled = true,
                        health = 0.0,
                        weakenedAccess = WeakenedPortAccessState(
                            actorStateId = stateId,
                            grantedAtEpochMillis = 1_000L,
                            lastAccessedAtEpochMillis = 1_000L,
                        ),
                        installedApplication = InstalledApplication(
                            name = "target-http.bin",
                            kind = ApplicationKind.HTTP,
                            cpuCost = 2.0,
                        ),
                    ),
                    watches = WatchManagerState(
                        watches = listOf(
                            installedHealthWatch(
                                installPort = 22,
                                baselineQuantity = 0.0,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("local-conn", stateId)
        }
        val dispatcher = dispatcher(repository, interests)

        dispatcher.request(
            command = RefreshCombatMaintenanceRuntimeCommand(
                stateId = stateId,
                clock = { 5_000L },
            ),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "maintenance-passive-heal"),
            publisher = RecordingGameStatePublisher(),
        )

        val healed = requireNotNull(repository.load(stateId))
        val healResponse = dispatcher.request(
            command = HealPortCommand(
                stateId = stateId,
                portNumber = 22,
            ),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "heal-while-weakened"),
            publisher = RecordingGameStatePublisher(),
        )
        val finalizeResponse = dispatcher.request(
            command = FinalizeCancelledCommand(
                actorStateId = stateId,
                targetStateId = stateId,
                targetPort = 22,
            ),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "finalize-after-passive-heal"),
            publisher = RecordingGameStatePublisher(),
        )
        val finalized = requireNotNull(repository.load(stateId))

        assertEquals(1.0, healed.port(22)?.health)
        assertNotNull(healed.port(22)?.weakenedAccess)
        assertEquals(HealPortOutcome.WEAKENED, healResponse.outcome)
        assertEquals(FinalizeCancelledOutcome.SUCCESS, finalizeResponse.outcome)
        assertEquals(100.0, finalized.port(22)?.health)
        assertNull(finalized.port(22)?.weakenedAccess)
        assertEquals(100.0, finalized.watches.watches.single().baselineQuantity)
    }

    @Test
    fun weakenedAccessTimeoutRestoresThePortAndAllowsNewAttacks() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val targetApplication = InstalledApplication(
            name = "target-http.bin",
            kind = ApplicationKind.HTTP,
            cpuCost = 2.0,
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to playerState(attackerId),
                targetId to breachTargetState(
                    targetId,
                    health = 15.0,
                    installedApplication = targetApplication,
                    portType = "http",
                ).copy(
                    ports = listOf(
                        PortState(
                            number = 25,
                            type = "http",
                            enabled = true,
                            health = 15.0,
                            weakenedAccess = WeakenedPortAccessState(
                                actorStateId = attackerId,
                                grantedAtEpochMillis = 1_000L,
                                lastAccessedAtEpochMillis = 1_000L,
                            ),
                            installedApplication = targetApplication,
                        ),
                    ),
                    watches = WatchManagerState(
                        watches = listOf(
                            installedHealthWatch(
                                installPort = 25,
                                baselineQuantity = 15.0,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("attacker-conn", attackerId)
        }
        val dispatcher = dispatcher(repository, interests)
        val attackRegistry = InMemoryAttackProgramRegistry()

        dispatcher.request(
            command = RefreshCombatMaintenanceRuntimeCommand(
                stateId = targetId,
                clock = { 31_001L },
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "timeout-reset"),
            publisher = RecordingGameStatePublisher(),
        )

        val updatedTarget = requireNotNull(repository.load(targetId))
        val attack = dispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = attackRegistry,
                clock = { 31_001L },
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "attack-after-timeout"),
            publisher = RecordingGameStatePublisher(),
        )

        assertEquals(100.0, updatedTarget.port(25)?.health)
        assertNull(updatedTarget.port(25)?.weakenedAccess)
        assertEquals(100.0, updatedTarget.watches.watches.single().baselineQuantity)
        assertTrue(attack.accepted)
    }

    @Test
    fun combatMaintenanceStartsOverheatCooldownMarksPortsAndCancelsActiveAttacks() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val session = AttackSessionState(
            programId = "attack-live",
            sourcePort = 12,
            targetStateId = targetId,
            targetPort = 25,
            startedAtEpochMillis = 1_000L,
        )
        val attackerBase = playerState(attackerId)
        val targetApplication = InstalledApplication(
            name = "target-http.bin",
            kind = ApplicationKind.HTTP,
            cpuCost = 2.0,
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackerBase.copy(
                    hardware = attackerBase.hardware.copy(cpuMax = 7.0),
                    ports = attackerBase.ports.markAttacking(12, true),
                    combat = CombatState(
                        activeAttacksBySourcePort = mapOf(12 to session),
                    ),
                    runtime = RuntimeState(currentCpuLoad = 8.0),
                ),
                targetId to breachTargetState(
                    targetId,
                    installedApplication = targetApplication,
                    portType = "http",
                ).copy(
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
                ),
            ),
        )
        val interests = InMemoryInterestRegistry()
        val dispatcher = dispatcher(repository, interests)
        val attackRegistry = InMemoryAttackProgramRegistry()
        attackRegistry.register(
            stateId = attackerId,
            sourcePort = 12,
            programId = session.programId,
            handle = object : ProgramHandle {
                override val programId: String = session.programId

                override suspend fun cancel(reason: String) {
                    dispatcher.request(
                        command = AttackReleaseCommand(
                            attackerStateId = attackerId,
                            targetStateId = targetId,
                            sourcePort = 12,
                            targetPort = 25,
                        ),
                        publisher = RecordingGameStatePublisher(),
                    )
                    attackRegistry.unregister(programId)
                }
            },
        )

        dispatcher.request(
            command = RefreshCombatMaintenanceRuntimeCommand(
                stateId = attackerId,
                attackProgramRegistry = attackRegistry,
                clock = { 10_000L },
            ),
            publisher = RecordingGameStatePublisher(),
        )

        val updatedAttacker = requireNotNull(repository.load(attackerId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertTrue(updatedAttacker.combat.activeAttacksBySourcePort.isEmpty())
        assertFalse(updatedAttacker.port(12)?.attacking == true)
        assertTrue(updatedTarget.combat.incomingAttacksByTargetPort.isEmpty())
        assertEquals(0.0, updatedAttacker.runtime.currentCpuLoad)
        assertEquals(10_000L, updatedAttacker.runtime.overheatStartedAtEpochMillis)
        assertTrue(updatedAttacker.ports.all { it.overheated })
    }

    @Test
    fun combatMaintenanceCooldownExpiryClearsOverheatAndResetsDamagedOrWeakenedPorts() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val baseState = playerState(stateId)
        val damagedApplication = InstalledApplication(
            name = "target-http.bin",
            kind = ApplicationKind.HTTP,
            cpuCost = 2.0,
        )
        val overheatedPorts = (baseState.ports + PortState(
            number = 22,
            type = "http",
            enabled = true,
            health = 77.0,
            healCount = 4,
            weakenedAccess = WeakenedPortAccessState(
                actorStateId = stateId,
                grantedAtEpochMillis = 1_000L,
                lastAccessedAtEpochMillis = 1_000L,
            ),
            overheated = true,
            installedApplication = damagedApplication,
        )).map { it.copy(overheated = true) }
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to baseState.copy(
                    ports = overheatedPorts,
                    watches = WatchManagerState(
                        watches = listOf(
                            installedHealthWatch(
                                installPort = 22,
                                baselineQuantity = 77.0,
                            ),
                        ),
                    ),
                    runtime = RuntimeState(
                        currentCpuLoad = 0.0,
                        healCounter = 1L,
                        overheatStartedAtEpochMillis = 1_000L,
                    ),
                ),
            ),
        )
        val dispatcher = dispatcher(repository, InMemoryInterestRegistry())

        dispatcher.request(
            command = RefreshCombatMaintenanceRuntimeCommand(
                stateId = stateId,
                clock = { 30_000L },
            ),
            publisher = RecordingGameStatePublisher(),
        )
        val stillOverheated = requireNotNull(repository.load(stateId))

        dispatcher.request(
            command = RefreshCombatMaintenanceRuntimeCommand(
                stateId = stateId,
                clock = { 61_001L },
            ),
            publisher = RecordingGameStatePublisher(),
        )
        val cooled = requireNotNull(repository.load(stateId))

        assertEquals(1_000L, stillOverheated.runtime.overheatStartedAtEpochMillis)
        assertTrue(stillOverheated.ports.all { it.overheated })
        assertNull(cooled.runtime.overheatStartedAtEpochMillis)
        assertTrue(cooled.ports.none { it.overheated })
        assertEquals(100.0, cooled.port(22)?.health)
        assertEquals(0, cooled.port(22)?.healCount)
        assertNull(cooled.port(22)?.weakenedAccess)
        assertEquals(100.0, cooled.watches.watches.single().baselineQuantity)
    }

    @Test
    fun requestSecondaryDirectoryStillAllowsOverheatedPortsWhileOtherRemoteCommandsRejectThem() = runTest {
        val actorId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val targetState = ComputerState.empty(id = targetId, playFabId = "PF-${targetId.value}").copy(
            filesystem = ComputerState.empty(id = targetId).filesystem
                .ensureDirectory("/Secrets")
                .saveFile(
                    StoredFile(
                        path = buildFilePath("/Secrets", "remote.log"),
                        name = "remote.log",
                        kind = StoredFileKind.TEXT,
                        contents = "remote data",
                    ),
                ),
            ports = listOf(
                PortState(
                    number = 17,
                    type = "ftp",
                    enabled = true,
                    overheated = true,
                    installedApplication = InstalledApplication(
                        name = "ftp.bin",
                        kind = ApplicationKind.FTP,
                        cpuCost = 2.0,
                    ),
                ),
                PortState(
                    number = 80,
                    type = "http",
                    enabled = true,
                    overheated = true,
                    installedApplication = InstalledApplication(
                        name = "http.bin",
                        kind = ApplicationKind.HTTP,
                        cpuCost = 2.0,
                    ),
                ),
            ),
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                actorId to playerState(actorId),
                targetId to targetState,
            ),
        )
        val dispatcher = dispatcher(repository, InMemoryInterestRegistry())

        val listing = dispatcher.request(
            command = RequestSecondaryDirectoryCommand(
                stateId = actorId,
                targetStateId = targetId,
                path = "/Secrets",
                portNumber = 17,
                clock = { 10_000L },
            ),
            publisher = RecordingGameStatePublisher(),
        )

        val dailyPayFailure = runCatching {
            dispatcher.request(
                command = ChangeDailyPayCommand(
                    actorStateId = actorId,
                    targetStateId = targetId,
                    targetPort = 80,
                    requestedRevenueTargetStateId = actorId,
                ),
                publisher = RecordingGameStatePublisher(),
            )
        }.exceptionOrNull()

        assertEquals("/Secrets", listing.path)
        assertNotNull(dailyPayFailure)
        assertTrue(dailyPayFailure.message?.contains("overheated") == true)
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

    private fun playerState(
        stateId: GameStateId,
        attackScriptBundle: ProgramScriptBundle? = null,
    ): ComputerState {
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
                    installedApplication = InstalledApplication(
                        name = "bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
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

    private fun breachTargetState(
        stateId: GameStateId,
        health: Double = 100.0,
        installedApplication: InstalledApplication? = null,
        portType: String = "http",
    ): ComputerState {
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            ports = listOf(
                PortState(
                    number = 25,
                    type = portType,
                    enabled = true,
                    health = health,
                    installedApplication = installedApplication,
                ),
            ),
        )
    }

    private fun installedHealthWatch(
        installPort: Int,
        baselineQuantity: Double,
    ): InstalledWatch {
        return InstalledWatch(
            kind = WatchKind.HEALTH,
            enabled = true,
            note = "health-watch",
            cpuCost = 5.0,
            quantityThreshold = 50.0,
            baselineQuantity = baselineQuantity,
            installPort = installPort,
            searchFirewallType = 0,
            observedPorts = listOf(installPort),
            contents = "watch",
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.WATCH,
                applicationKind = ApplicationKind.WATCH,
                outputName = "watch.bin",
            ),
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
