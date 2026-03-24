package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkCommandsTest {
    @Test
    fun changeNetworkUpdatesCurrentNetworkStoreAndNpcDirectoryLists() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(stateId to localPlayerState(stateId)),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = ChangeNetworkCommand(
                stateId = stateId,
                targetNetworkName = "ProgNet",
                networkDirectoryRepository = testNetworkRepository(),
                clock = { NETWORK_SWITCH_COOLDOWN_MS + 1_000L },
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        val updated = repository.load(stateId)
        requireNotNull(updated)
        assertTrue(response.accepted)
        assertEquals("ProgNet", response.currentNetworkName)
        assertEquals(GameStateId("store1"), response.storeStateId)
        assertEquals("ProgNet", updated.network.currentNetworkName)
        assertEquals(setOf("network"), publisher.deltas.single().second.deltaKeys)
        assertEquals("Prog Attack", updated.network.regularNpcs.single().displayName)
    }

    @Test
    fun changeNetworkReturnsTypedFailuresForBlankSameJailedCooldownAndDisallowed() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localPlayerState(stateId),
                GameStateId("JAILED-IP") to localPlayerState(GameStateId("JAILED-IP")).copy(
                    network = NetworkState(currentNetworkName = JAIL_NETWORK_NAME),
                ),
                GameStateId("COOLDOWN-IP") to localPlayerState(GameStateId("COOLDOWN-IP")).copy(
                    network = localPlayerState(GameStateId("COOLDOWN-IP")).network.copy(
                        lastNetworkSwitchAtEpochMillis = NETWORK_SWITCH_COOLDOWN_MS,
                    ),
                ),
            ),
        )
        val dispatcher = DefaultCommandDispatcher(repository, InMemoryInterestRegistry())

        val blank = dispatcher.request(
            ChangeNetworkCommand(stateId, " ", testNetworkRepository()),
            publisher = RecordingGameStatePublisher(),
        )
        val same = dispatcher.request(
            ChangeNetworkCommand(stateId, ROOT_NETWORK_NAME, testNetworkRepository()),
            publisher = RecordingGameStatePublisher(),
        )
        val jailed = dispatcher.request(
            ChangeNetworkCommand(GameStateId("JAILED-IP"), "ProgNet", testNetworkRepository()),
            publisher = RecordingGameStatePublisher(),
        )
        val cooldown = dispatcher.request(
            ChangeNetworkCommand(
                stateId = GameStateId("COOLDOWN-IP"),
                targetNetworkName = "ProgNet",
                networkDirectoryRepository = testNetworkRepository(),
                clock = { NETWORK_SWITCH_COOLDOWN_MS + 500L },
            ),
            publisher = RecordingGameStatePublisher(),
        )
        val disallowed = dispatcher.request(
            ChangeNetworkCommand(
                stateId = stateId,
                targetNetworkName = "ForbiddenNet",
                networkDirectoryRepository = testNetworkRepository(),
                clock = { NETWORK_SWITCH_COOLDOWN_MS + 10_000L },
            ),
            publisher = RecordingGameStatePublisher(),
        )

        assertEquals(NetworkSwitchFailureCode.INVALID_TARGET, blank.failureCode)
        assertEquals(NetworkSwitchFailureCode.ALREADY_ON_NETWORK, same.failureCode)
        assertEquals(NetworkSwitchFailureCode.JAILED, jailed.failureCode)
        assertEquals(NetworkSwitchFailureCode.COOLDOWN, cooldown.failureCode)
        assertEquals(NetworkSwitchFailureCode.DISALLOWED, disallowed.failureCode)
        assertFalse(blank.accepted)
        assertFalse(disallowed.accepted)
    }

    @Test
    fun changeNetworkDirectBypassesCooldownAndAccessChecks() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localPlayerState(stateId).copy(
                    network = localPlayerState(stateId).network.copy(
                        currentNetworkName = JAIL_NETWORK_NAME,
                        allowedNetworks = emptySet(),
                        lastNetworkSwitchAtEpochMillis = NETWORK_SWITCH_COOLDOWN_MS,
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = ChangeNetworkDirectCommand(
                stateId = stateId,
                targetNetworkName = "ProgNet",
                networkDirectoryRepository = testNetworkRepository(),
                clock = { NETWORK_SWITCH_COOLDOWN_MS + 500L },
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertTrue(response.accepted)
        assertEquals("ProgNet", repository.load(stateId)?.network?.currentNetworkName)
        assertEquals(setOf("network"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun requestScanRejectsMissingBankLowMoneyAndOverheatWithoutMutation() = runTest {
        val requesterId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val targetState = scanTargetState(targetId)

        val missingBankRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                requesterId to localPlayerState(requesterId).copy(
                    ports = emptyList(),
                    economy = EconomyState(pettyCash = 100.0, defaultBankPort = 6),
                ),
                targetId to targetState,
            ),
        )
        val lowMoneyRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                requesterId to localPlayerState(requesterId).copy(
                    economy = EconomyState(pettyCash = 5.0, defaultBankPort = 6),
                ),
                targetId to targetState,
            ),
        )
        val overheatedRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                requesterId to localPlayerState(requesterId).copy(
                    runtime = RuntimeState(currentCpuLoad = 60.0),
                ),
                targetId to targetState,
            ),
        )

        val missingBank = DefaultCommandDispatcher(missingBankRepository, InMemoryInterestRegistry()).request(
            RequestScanCommand(requesterId, targetId),
            publisher = RecordingGameStatePublisher(),
        )
        val lowMoney = DefaultCommandDispatcher(lowMoneyRepository, InMemoryInterestRegistry()).request(
            RequestScanCommand(requesterId, targetId),
            publisher = RecordingGameStatePublisher(),
        )
        val overheated = DefaultCommandDispatcher(overheatedRepository, InMemoryInterestRegistry()).request(
            RequestScanCommand(requesterId, targetId),
            publisher = RecordingGameStatePublisher(),
        )

        assertEquals(ScanFailureCode.ACTIVE_BANK_REQUIRED, missingBank.failureCode)
        assertEquals(ScanFailureCode.INSUFFICIENT_PETTY_CASH, lowMoney.failureCode)
        assertEquals(ScanFailureCode.OVERHEATED, overheated.failureCode)
        assertFalse(missingBank.accepted)
        assertFalse(lowMoney.accepted)
        assertFalse(overheated.accepted)
        assertEquals(100.0, requireNotNull(missingBankRepository.load(requesterId)).economy.pettyCash)
        assertEquals(5.0, requireNotNull(lowMoneyRepository.load(requesterId)).economy.pettyCash)
    }

    @Test
    fun requestScanChargesCashAwardsXpAndMasksPortDetailsByRevealThreshold() = runTest {
        val requesterId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", requesterId)
        val publisher = RecordingGameStatePublisher()

        val highRevealRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                requesterId to localPlayerState(requesterId).copy(
                    stats = PlayerStatsState(
                        experienceByFamily = mapOf(ScriptFamily.SCANNING to 10_000_000),
                    ),
                ),
                targetId to scanTargetState(targetId).copy(
                    stats = PlayerStatsState(
                        experienceByFamily = mapOf(ScriptFamily.FIREWALL to 0),
                    ),
                ),
            ),
        )
        val highReveal = DefaultCommandDispatcher(highRevealRepository, interests).request(
            command = RequestScanCommand(requesterId, targetId),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertTrue(highReveal.accepted)
        assertEquals(10.0, highReveal.chargedAmount)
        assertEquals(60, highReveal.experienceAwarded)
        assertEquals(setOf("economy", "stats"), publisher.deltas.single().second.deltaKeys)
        assertEquals(DefaultPortVisibility.YES, highReveal.ports.first().defaultVisibility)
        assertEquals("LOCAL-IP", highReveal.ports.first().note)
        assertTrue(highReveal.ports.first().firewall != null)
        assertEquals(90.0, requireNotNull(highRevealRepository.load(requesterId)).economy.pettyCash)

        val lowRevealRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                requesterId to localPlayerState(requesterId),
                targetId to scanTargetState(targetId).copy(
                    stats = PlayerStatsState(
                        experienceByFamily = mapOf(ScriptFamily.FIREWALL to 10_000_000),
                    ),
                ),
            ),
        )
        val lowReveal = DefaultCommandDispatcher(lowRevealRepository, InMemoryInterestRegistry()).request(
            command = RequestScanCommand(requesterId, targetId),
            publisher = RecordingGameStatePublisher(),
        )

        assertTrue(lowReveal.accepted)
        assertEquals(20, lowReveal.experienceAwarded)
        assertEquals(DefaultPortVisibility.UNKNOWN, lowReveal.ports.first().defaultVisibility)
        assertNull(lowReveal.ports.first().firewall)
        assertEquals("", lowReveal.ports.first().note)
    }

    private fun localPlayerState(stateId: GameStateId): ComputerState {
        return ComputerState.empty(
            id = stateId,
            playFabId = "PF-${stateId.value}",
            playerIp = stateId.value,
            displayName = stateId.value,
        ).copy(
            hardware = HardwareState(cpuMax = 50.0, hdMaximum = 50),
            economy = EconomyState(
                pettyCash = 100.0,
                bankMoney = 50.0,
                defaultBankPort = 6,
            ),
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
                ),
            ),
            network = NetworkState(
                currentNetworkName = ROOT_NETWORK_NAME,
                storeStateId = GameStateId("store1"),
                allowedNetworks = setOf("ProgNet"),
            ),
            runtime = RuntimeState(currentCpuLoad = 5.0),
        )
    }

    private fun scanTargetState(stateId: GameStateId): ComputerState {
        return ComputerState.empty(
            id = stateId,
            playerIp = stateId.value,
            displayName = "Target",
        ).copy(
            ports = listOf(
                PortState(
                    number = 22,
                    type = "banking",
                    enabled = true,
                    defaultPort = true,
                    maxCpuCost = 8.0,
                    installedApplication = InstalledApplication(
                        name = "bank.bin",
                        kind = ApplicationKind.BANKING,
                        cpuCost = 5.0,
                        banking = true,
                    ),
                    installedFirewall = InstalledFirewall(
                        name = "Shield",
                        kind = FirewallKind.BASIC,
                        maker = "Rewrite",
                        strength = 12,
                        cpuCost = 2.0,
                    ),
                ),
                PortState(
                    number = 80,
                    type = "http",
                    enabled = true,
                    defaultPort = false,
                    attacking = true,
                    installedApplication = InstalledApplication(
                        name = "http.bin",
                        kind = ApplicationKind.HTTP,
                        cpuCost = 3.0,
                    ),
                ),
            ),
        )
    }

    private fun testNetworkRepository(): NetworkDirectoryRepository {
        return InMemoryNetworkDirectoryRepository(
            definitions = mapOf(
                ROOT_NETWORK_NAME to NetworkDirectoryDefinition(
                    name = ROOT_NETWORK_NAME,
                    storeStateId = GameStateId("store1"),
                    switchMessagesByTarget = mapOf(
                        "ForbiddenNet" to "There is no connection between UGOPNet and ForbiddenNet.",
                    ),
                ),
                "ProgNet" to NetworkDirectoryDefinition(
                    name = "ProgNet",
                    storeStateId = GameStateId("store1"),
                    regularNpcs = listOf(
                        NpcDirectoryEntry(
                            stateId = GameStateId("PROG-ATTACK-1"),
                            displayName = "Prog Attack",
                            title = "Attack NPC",
                            category = NpcCategory.REGULAR,
                        ),
                    ),
                    questNpcs = listOf(
                        NpcDirectoryEntry(
                            stateId = GameStateId("PROG-QUEST-1"),
                            displayName = "Prog Quest",
                            title = "Quest NPC",
                            category = NpcCategory.QUEST,
                        ),
                    ),
                ),
                "ForbiddenNet" to NetworkDirectoryDefinition(
                    name = "ForbiddenNet",
                    storeStateId = GameStateId("store1"),
                ),
                JAIL_NETWORK_NAME to NetworkDirectoryDefinition(
                    name = JAIL_NETWORK_NAME,
                    storeStateId = null,
                ),
            ),
        )
    }
}
