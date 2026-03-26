package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PassiveWatchCommandsTest {
    @Test
    fun requestScanExecutesEnabledScanWatchesAndAwardsFractionalWatchXpOnce() = runTest {
        val requesterId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                requesterId to passiveWatchState(
                    stateId = requesterId,
                    pettyCash = 100.0,
                    bankMoney = 25.0,
                    stats = PlayerStatsState(
                        experienceByFamily = mapOf(
                            ScriptFamily.WATCH to 0.0,
                            ScriptFamily.SCANNING to 10_000_000.0,
                        ),
                    ),
                    watches = listOf(
                        passiveWatch(
                            kind = WatchKind.SCAN,
                            searchFirewallType = 3,
                            contents = """
                                int main() {
                                    logMessage(getSearchFireWall());
                                    logMessage("" + getTransactionAmount());
                                    return 0;
                                }
                            """.trimIndent(),
                        ),
                    ),
                ),
                targetId to scanTargetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("local-conn", requesterId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestScanCommand(requesterStateId = requesterId, targetStateId = targetId),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "scan-passive"),
            publisher = publisher,
        )
        val updated = requireNotNull(repository.load(requesterId))

        assertTrue(response.accepted)
        assertEquals(60.0, response.experienceAwarded)
        assertEquals(0.25, updated.stats.skillExperience(ScriptFamily.WATCH))
        assertEquals(listOf("DataShield", "0"), updated.logs.entries.map { it.renderedLine.substringAfterLast(' ') })
        assertEquals(setOf("economy", "stats", "logs"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun pettyCashThresholdCrossingExecutesEligibleWatchesInOrderAndAwardsXpOnce() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to passiveWatchState(
                    stateId = stateId,
                    pettyCash = 40.0,
                    bankMoney = 40.0,
                    stats = PlayerStatsState(experienceByFamily = mapOf(ScriptFamily.WATCH to 0.0)),
                    watches = listOf(
                        passiveWatch(
                            note = "first",
                            quantityThreshold = 50.0,
                            baselineQuantity = 40.0,
                            contents = """int main() { logMessage("first"); return 0; }""",
                        ),
                        passiveWatch(
                            note = "second",
                            quantityThreshold = 50.0,
                            baselineQuantity = 10.0,
                            contents = """int main() { logMessage("second"); return 0; }""",
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("local-conn", stateId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        dispatcher.request(
            command = WithdrawCommand(
                stateId = stateId,
                amount = 20.0,
                portNumber = 6,
            ),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "withdraw-passive"),
            publisher = publisher,
        )
        val updated = requireNotNull(repository.load(stateId))

        assertEquals(listOf("first", "second"), updated.logs.entries.map { it.renderedLine.substringAfterLast(' ') })
        assertEquals(0.4, updated.stats.skillExperience(ScriptFamily.WATCH))
        assertEquals(listOf(60.0, 60.0), updated.watches.watches.map { it.baselineQuantity })
        assertEquals(setOf("economy", "logs", "watches", "stats"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun negativePettyCashChangesRefreshEnabledBaselinesButLeaveDisabledWatchesUntouched() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to passiveWatchState(
                    stateId = stateId,
                    pettyCash = 100.0,
                    bankMoney = 20.0,
                    watches = listOf(
                        passiveWatch(
                            note = "enabled",
                            enabled = true,
                            quantityThreshold = 150.0,
                            baselineQuantity = 100.0,
                            contents = """int main() { logMessage("enabled"); return 0; }""",
                        ),
                        passiveWatch(
                            note = "disabled",
                            enabled = false,
                            quantityThreshold = 150.0,
                            baselineQuantity = 100.0,
                            contents = """int main() { logMessage("disabled"); return 0; }""",
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("local-conn", stateId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        dispatcher.request(
            command = DepositCommand(
                stateId = stateId,
                amount = 30.0,
                portNumber = 6,
            ),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "deposit-passive"),
            publisher = publisher,
        )
        val updated = requireNotNull(repository.load(stateId))

        assertTrue(updated.logs.entries.isEmpty())
        assertEquals(70.0, updated.watches.watches[0].baselineQuantity)
        assertEquals(100.0, updated.watches.watches[1].baselineQuantity)
        assertEquals(0.0, updated.stats.skillExperience(ScriptFamily.WATCH))
        assertEquals(setOf("economy", "watches"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun overheatedPettyCashWatchesNeitherFireNorRefreshBaseline() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val overheatStartedAtEpochMillis = System.currentTimeMillis()
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to passiveWatchState(
                    stateId = stateId,
                    pettyCash = 40.0,
                    bankMoney = 40.0,
                    currentCpuLoad = 120.0,
                    overheatStartedAtEpochMillis = overheatStartedAtEpochMillis,
                    cpuMax = 100.0,
                    watches = listOf(
                        passiveWatch(
                            quantityThreshold = 50.0,
                            baselineQuantity = 40.0,
                            contents = """int main() { logMessage("should-not-fire"); return 0; }""",
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("local-conn", stateId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        dispatcher.request(
            command = WithdrawCommand(
                stateId = stateId,
                amount = 20.0,
                portNumber = 6,
            ),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "withdraw-overheated"),
            publisher = publisher,
        )
        val updated = requireNotNull(repository.load(stateId))

        assertTrue(updated.logs.entries.isEmpty())
        assertEquals(40.0, updated.watches.watches.single().baselineQuantity)
        assertEquals(0.0, updated.stats.skillExperience(ScriptFamily.WATCH))
        assertEquals(setOf("economy"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun healthThresholdCrossingExecutesMatchingWatchesInOrderAndAwardsXpOnce() = runTest {
        val stateId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to healthWatchState(
                    stateId = stateId,
                    watches = listOf(
                        passiveWatch(
                            kind = WatchKind.HEALTH,
                            note = "first",
                            quantityThreshold = 50.0,
                            baselineQuantity = 100.0,
                            installPort = 25,
                            contents = """int main() { logMessage("first:" + getTargetIP() + ":" + getTargetPort()); return 0; }""",
                        ),
                        passiveWatch(
                            kind = WatchKind.HEALTH,
                            note = "second",
                            quantityThreshold = 75.0,
                            baselineQuantity = 100.0,
                            installPort = 25,
                            contents = """int main() { logMessage("second:" + getTargetIP() + ":" + getTargetPort()); return 0; }""",
                        ),
                        passiveWatch(
                            kind = WatchKind.HEALTH,
                            note = "other-port",
                            quantityThreshold = 50.0,
                            baselineQuantity = 100.0,
                            installPort = 80,
                            contents = """int main() { logMessage("other-port"); return 0; }""",
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("target-conn", stateId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        dispatcher.dispatch(
            command = ApplyHealthChangeCommand(
                stateId = stateId,
                portNumber = 25,
                sourceIp = "ATTACKER-IP",
                sourcePort = 12,
                previousHealth = 100.0,
                newHealth = 47.0,
            ),
            metadata = CommandMetadata(connectionId = "target-conn", requestId = "health-cross"),
            publisher = publisher,
        )
        val updated = requireNotNull(repository.load(stateId))

        assertEquals(47.0, updated.port(25)?.health)
        assertEquals(1.0, updated.stats.skillExperience(ScriptFamily.WATCH))
        assertEquals(listOf(47.0, 47.0, 100.0), updated.watches.watches.map { it.baselineQuantity })
        assertTrue(updated.logs.entries[0].renderedLine.contains("first:ATTACKER-IP:12"))
        assertTrue(updated.logs.entries[1].renderedLine.contains("second:ATTACKER-IP:12"))
        assertEquals(setOf("ports", "logs", "watches", "stats"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun healthChangesThatDoNotCrossThresholdOnlyRefreshEnabledBaselines() = runTest {
        val stateId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to healthWatchState(
                    stateId = stateId,
                    watches = listOf(
                        passiveWatch(
                            kind = WatchKind.HEALTH,
                            quantityThreshold = 50.0,
                            baselineQuantity = 100.0,
                            installPort = 25,
                            contents = """int main() { logMessage("no-fire"); return 0; }""",
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("target-conn", stateId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        dispatcher.dispatch(
            command = ApplyHealthChangeCommand(
                stateId = stateId,
                portNumber = 25,
                sourceIp = "ATTACKER-IP",
                sourcePort = 12,
                previousHealth = 100.0,
                newHealth = 75.0,
            ),
            metadata = CommandMetadata(connectionId = "target-conn", requestId = "health-refresh"),
            publisher = publisher,
        )
        val updated = requireNotNull(repository.load(stateId))

        assertTrue(updated.logs.entries.isEmpty())
        assertEquals(75.0, updated.watches.watches.single().baselineQuantity)
        assertEquals(0.0, updated.stats.skillExperience(ScriptFamily.WATCH))
        assertEquals(setOf("ports", "watches"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun healthWhileAlreadyBelowThresholdDoesNotRefireAndDisabledOrOverheatedWatchesStayUnchanged() = runTest {
        val alreadyBelowId = GameStateId("TARGET-BELOW")
        val disabledId = GameStateId("TARGET-DISABLED")
        val overheatedId = GameStateId("TARGET-OVERHEATED")
        val overheatStartedAtEpochMillis = System.currentTimeMillis()
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                alreadyBelowId to healthWatchState(
                    stateId = alreadyBelowId,
                    watches = listOf(
                        passiveWatch(
                            kind = WatchKind.HEALTH,
                            quantityThreshold = 50.0,
                            baselineQuantity = 40.0,
                            installPort = 25,
                            contents = """int main() { logMessage("below"); return 0; }""",
                        ),
                    ),
                ),
                disabledId to healthWatchState(
                    stateId = disabledId,
                    watches = listOf(
                        passiveWatch(
                            kind = WatchKind.HEALTH,
                            enabled = false,
                            quantityThreshold = 50.0,
                            baselineQuantity = 100.0,
                            installPort = 25,
                            contents = """int main() { logMessage("disabled"); return 0; }""",
                        ),
                    ),
                ),
                overheatedId to healthWatchState(
                    stateId = overheatedId,
                    currentCpuLoad = 120.0,
                    overheatStartedAtEpochMillis = overheatStartedAtEpochMillis,
                    cpuMax = 100.0,
                    watches = listOf(
                        passiveWatch(
                            kind = WatchKind.HEALTH,
                            quantityThreshold = 50.0,
                            baselineQuantity = 100.0,
                            installPort = 25,
                            contents = """int main() { logMessage("overheated"); return 0; }""",
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("below-conn", alreadyBelowId)
            register("disabled-conn", disabledId)
            register("overheated-conn", overheatedId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        dispatcher.dispatch(
            command = ApplyHealthChangeCommand(
                stateId = alreadyBelowId,
                portNumber = 25,
                sourceIp = "ATTACKER-IP",
                sourcePort = 12,
                previousHealth = 40.0,
                newHealth = 30.0,
            ),
            metadata = CommandMetadata(connectionId = "below-conn", requestId = "health-below"),
            publisher = publisher,
        )
        dispatcher.dispatch(
            command = ApplyHealthChangeCommand(
                stateId = disabledId,
                portNumber = 25,
                sourceIp = "ATTACKER-IP",
                sourcePort = 12,
                previousHealth = 100.0,
                newHealth = 47.0,
            ),
            metadata = CommandMetadata(connectionId = "disabled-conn", requestId = "health-disabled"),
            publisher = publisher,
        )
        dispatcher.dispatch(
            command = ApplyHealthChangeCommand(
                stateId = overheatedId,
                portNumber = 25,
                sourceIp = "ATTACKER-IP",
                sourcePort = 12,
                previousHealth = 100.0,
                newHealth = 47.0,
            ),
            metadata = CommandMetadata(connectionId = "overheated-conn", requestId = "health-overheated"),
            publisher = publisher,
        )

        val alreadyBelow = requireNotNull(repository.load(alreadyBelowId))
        val disabled = requireNotNull(repository.load(disabledId))
        val overheated = requireNotNull(repository.load(overheatedId))

        assertTrue(alreadyBelow.logs.entries.isEmpty())
        assertEquals(30.0, alreadyBelow.watches.watches.single().baselineQuantity)
        assertEquals(0.0, alreadyBelow.stats.skillExperience(ScriptFamily.WATCH))

        assertTrue(disabled.logs.entries.isEmpty())
        assertEquals(100.0, disabled.watches.watches.single().baselineQuantity)
        assertEquals(0.0, disabled.stats.skillExperience(ScriptFamily.WATCH))

        assertTrue(overheated.logs.entries.isEmpty())
        assertEquals(100.0, overheated.watches.watches.single().baselineQuantity)
        assertEquals(0.0, overheated.stats.skillExperience(ScriptFamily.WATCH))

        assertEquals(setOf("ports", "watches"), publisher.deltas[0].second.deltaKeys)
        assertEquals(setOf("ports"), publisher.deltas[1].second.deltaKeys)
        assertEquals(setOf("ports"), publisher.deltas[2].second.deltaKeys)
    }

    @Test
    fun attackCompletionStillFiresHealthWatchesBeforeCleanupAndCompletedUpdate() = runTest {
        val attackerId = GameStateId("ATTACKER-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                attackerId to attackPassiveAttackerState(attackerId),
                targetId to healthWatchState(
                    stateId = targetId,
                    health = 1.5,
                    watches = listOf(
                        passiveWatch(
                            kind = WatchKind.HEALTH,
                            quantityThreshold = 50.0,
                            baselineQuantity = 100.0,
                            installPort = 25,
                            contents = """int main() { logMessage("final:" + getTargetIP() + ":" + getTargetPort()); return 0; }""",
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
        val dispatcher = DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = interests,
            programScheduler = CoroutineProgramScheduler(
                dispatcher = null,
                interestRegistry = interests,
                coroutineScope = backgroundScope,
            ),
        )

        dispatcher.request(
            command = RequestAttackCommand(
                attackerStateId = attackerId,
                targetStateId = targetId,
                sourceIp = attackerId.value,
                sourcePort = 12,
                targetPort = 25,
                loadout = AttackLoadout(),
                attackProgramRegistry = InMemoryAttackProgramRegistry(),
            ),
            metadata = CommandMetadata(connectionId = "attacker-conn", requestId = "health-complete"),
            publisher = publisher,
        )
        runCurrent()
        publisher.deltas.clear()
        publisher.programUpdates.clear()

        advanceTimeBy(180_100)
        runCurrent()

        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(0.0, updatedTarget.port(25)?.health)
        assertTrue(updatedTarget.logs.entries.single().renderedLine.contains("final:ATTACKER-IP:12"))
        assertEquals(0.0, updatedTarget.watches.watches.single().baselineQuantity)
        assertEquals(1.0, updatedTarget.stats.skillExperience(ScriptFamily.WATCH))
        assertEquals(setOf("ports", "combat", "logs", "watches", "stats"), publisher.deltas[0].second.deltaKeys)
        assertEquals(ProgramLifecycleStatus.COMPLETED, publisher.programUpdates.single().second.status)
    }
}

private fun passiveWatchState(
    stateId: GameStateId,
    pettyCash: Double,
    bankMoney: Double,
    currentCpuLoad: Double = 0.0,
    overheatStartedAtEpochMillis: Long? = null,
    cpuMax: Double = 100.0,
    stats: PlayerStatsState = PlayerStatsState(),
    watches: List<InstalledWatch>,
): ComputerState {
    return ComputerState.empty(
        id = stateId,
        playFabId = "PF-${stateId.value}",
        playerIp = stateId.value,
    ).copy(
        economy = EconomyState(
            pettyCash = pettyCash,
            bankMoney = bankMoney,
            defaultBankPort = 6,
        ),
        hardware = HardwareState(cpuMax = cpuMax),
        runtime = RuntimeState(
            currentCpuLoad = currentCpuLoad,
            overheatStartedAtEpochMillis = overheatStartedAtEpochMillis,
        ),
        stats = stats,
        ports = listOf(passiveBankingPort()),
        watches = WatchManagerState(watches = watches),
    )
}

private fun healthWatchState(
    stateId: GameStateId,
    health: Double = 100.0,
    currentCpuLoad: Double = 0.0,
    overheatStartedAtEpochMillis: Long? = null,
    cpuMax: Double = 100.0,
    stats: PlayerStatsState = PlayerStatsState(),
    watches: List<InstalledWatch>,
): ComputerState {
    return ComputerState.empty(
        id = stateId,
        playFabId = "PF-${stateId.value}",
        playerIp = stateId.value,
    ).copy(
        hardware = HardwareState(cpuMax = cpuMax),
        runtime = RuntimeState(
            currentCpuLoad = currentCpuLoad,
            overheatStartedAtEpochMillis = overheatStartedAtEpochMillis,
        ),
        stats = stats,
        ports = listOf(
            PortState(
                number = 25,
                type = "http",
                enabled = true,
                health = health,
            ),
            PortState(
                number = 80,
                type = "http",
                enabled = true,
                health = 100.0,
            ),
        ),
        watches = WatchManagerState(watches = watches),
    )
}

private fun scanTargetState(stateId: GameStateId): ComputerState {
    return ComputerState.empty(
        id = stateId,
        playFabId = "PF-${stateId.value}",
        playerIp = stateId.value,
    ).copy(
        ports = listOf(
            PortState(
                number = 6,
                type = "banking",
                enabled = true,
                defaultPort = true,
                installedApplication = InstalledApplication(
                    name = "bank.bin",
                    kind = ApplicationKind.BANKING,
                    banking = true,
                ),
                installedFirewall = InstalledFirewall(
                    name = "DataShield",
                    kind = FirewallKind.CUSTOM,
                    strength = 10,
                ),
            ),
        ),
    )
}

private fun passiveWatch(
    kind: WatchKind = WatchKind.PETTY_CASH,
    enabled: Boolean = true,
    note: String = "watch",
    quantityThreshold: Double = 0.0,
    baselineQuantity: Double = 0.0,
    installPort: Int = 6,
    searchFirewallType: Int = 0,
    contents: String,
): InstalledWatch {
    return InstalledWatch(
        kind = kind,
        enabled = enabled,
        note = note,
        cpuCost = 5.0,
        quantityThreshold = quantityThreshold,
        baselineQuantity = baselineQuantity,
        installPort = installPort,
        searchFirewallType = searchFirewallType,
        observedPorts = listOf(installPort),
        contents = contents,
        compiledBinary = CompiledBinaryMetadata(
            scriptFamily = ScriptFamily.WATCH,
            applicationKind = ApplicationKind.WATCH,
            outputName = "watch.bin",
        ),
    )
}

private fun passiveBankingPort(): PortState {
    return PortState(
        number = 6,
        type = "banking",
        enabled = true,
        defaultPort = true,
        installedApplication = InstalledApplication(
            name = "bank.bin",
            kind = ApplicationKind.BANKING,
            binaryPath = "/system/bank.bin",
            banking = true,
        ),
    )
}

private fun attackPassiveAttackerState(stateId: GameStateId): ComputerState {
    return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
        economy = EconomyState(
            pettyCash = 100.0,
            bankMoney = 50.0,
            defaultBankPort = 6,
        ),
        hardware = HardwareState(cpuMax = 100.0, hdMaximum = 100),
        ports = listOf(
            passiveBankingPort().copy(type = "bank"),
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

private class ApplyHealthChangeCommand(
    private val stateId: GameStateId,
    private val portNumber: Int,
    private val sourceIp: String,
    private val sourcePort: Int,
    private val previousHealth: Double,
    private val newHealth: Double,
) : FireAndForgetCommand {
    override val name: String = "test-health-change"
    override val lifetime: CommandLifetime = CommandLifetime.defaultFireAndForget
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext) {
        val state = context.requireExistingState(stateId)
        val port = requireNotNull(state.port(portNumber))
        context.appendEvents(
            id = stateId,
            events = listOf(
                CombatStateUpdatedEvent(
                    changedPathList = setOf("ports.$portNumber.health"),
                    deltaKeyList = setOf("ports"),
                    combat = state.combat,
                    ports = state.ports.map {
                        if (it.number == portNumber) {
                            port.copy(health = newHealth)
                        } else {
                            it
                        }
                    },
                    currentCpuLoad = state.runtime.currentCpuLoad,
                    includePorts = true,
                ),
            ),
        )
        context.emitPassiveHealthChange(
            targetStateId = stateId,
            sourceIp = sourceIp,
            portNumber = portNumber,
            sourcePort = sourcePort,
            previousHealth = previousHealth,
            newHealth = newHealth,
        )
    }
}
