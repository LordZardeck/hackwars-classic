package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to passiveWatchState(
                    stateId = stateId,
                    pettyCash = 40.0,
                    bankMoney = 40.0,
                    currentCpuLoad = 120.0,
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
}

private fun passiveWatchState(
    stateId: GameStateId,
    pettyCash: Double,
    bankMoney: Double,
    currentCpuLoad: Double = 0.0,
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
        runtime = RuntimeState(currentCpuLoad = currentCpuLoad),
        stats = stats,
        ports = listOf(passiveBankingPort()),
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
