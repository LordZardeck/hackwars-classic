package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.ComputerEvent
import com.hackwars.rewrite.gamecore.AttackScriptReference
import com.hackwars.rewrite.gamecore.AttackSessionState
import com.hackwars.rewrite.gamecore.AttackTargetView
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.CombatState
import com.hackwars.rewrite.gamecore.CombatStateUpdatedEvent
import com.hackwars.rewrite.gamecore.DirectoryCreatedEvent
import com.hackwars.rewrite.gamecore.DirectoryEntry
import com.hackwars.rewrite.gamecore.EquipmentInstalledEvent
import com.hackwars.rewrite.gamecore.EquipmentSlot
import com.hackwars.rewrite.gamecore.FileCompiledEvent
import com.hackwars.rewrite.gamecore.FileSavedEvent
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.ComputerLogEntry
import com.hackwars.rewrite.gamecore.InstalledEquipment
import com.hackwars.rewrite.gamecore.IncomingAttackState
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.PreferenceSetEvent
import com.hackwars.rewrite.gamecore.LastLoginRecordedEvent
import com.hackwars.rewrite.gamecore.ProgramScriptBundle
import com.hackwars.rewrite.gamecore.ProgramScriptSlot
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.SnapshotCoordinator
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.ApplicationInstalledEvent
import com.hackwars.rewrite.gamecore.BountyMetadata
import com.hackwars.rewrite.gamecore.BountyTypes
import com.hackwars.rewrite.gamecore.ClueDataStoredEvent
import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.EconomyState
import com.hackwars.rewrite.gamecore.EconomyBalanceAdjustedEvent
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.PurchasedFileReceivedEvent
import com.hackwars.rewrite.gamecore.QuestTaskProgressRecordedEvent
import com.hackwars.rewrite.gamecore.SaveFileMetadata
import com.hackwars.rewrite.gamecore.StoreFilePricedEvent
import com.hackwars.rewrite.gamecore.StoreFilesLiquidatedEvent
import com.hackwars.rewrite.gamecore.StoreInventoryReceivedEvent
import com.hackwars.rewrite.gamecore.StoreLiquidationLineItem
import com.hackwars.rewrite.gamecore.StoreListingPurchasedEvent
import com.hackwars.rewrite.gamecore.HttpExperienceAdjustedEvent
import com.hackwars.rewrite.gamecore.HostLogAppendedEvent
import com.hackwars.rewrite.gamecore.NetworkState
import com.hackwars.rewrite.gamecore.NetworkStateChangedEvent
import com.hackwars.rewrite.gamecore.NpcCategory
import com.hackwars.rewrite.gamecore.NpcDirectoryEntry
import com.hackwars.rewrite.gamecore.ROOT_NETWORK_NAME
import com.hackwars.rewrite.gamecore.SkillExperienceAdjustedEvent
import com.hackwars.rewrite.gamecore.WatchInstalledEvent
import com.hackwars.rewrite.gamecore.WatchKind
import com.hackwars.rewrite.gamecore.WatchManagerState
import com.hackwars.rewrite.gamecore.WatchManagerUpdatedEvent
import com.hackwars.rewrite.gamecore.WebsiteSavedEvent
import com.hackwars.rewrite.gamecore.WebsiteVoteCountAdjustedEvent
import com.hackwars.rewrite.gamecore.WebsiteVotesAvailableAdjustedEvent
import com.hackwars.rewrite.gamecore.buildFilePath
import com.hackwars.rewrite.gamecore.ensureDirectory
import com.hackwars.rewrite.gamecore.InstalledWatch
import com.hackwars.rewrite.gamecore.saveFile
import com.hackwars.rewrite.hackscript.BooleanHookValue
import com.hackwars.rewrite.hackscript.IntHookValue
import com.hackwars.rewrite.hackscript.StringHookValue
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class JdbcComputerStateRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_state_repository_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    private val serializer = ComputerStateSerializer()

    @Test
    fun appendsTypedEventsAndReplaysDeterministicState() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER", isNpc = true),
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
        )

        val updated = runBlockingAppend(repository, stateId, listOf(
            LastLoginRecordedEvent(occurredAtEpochMillis = 123_456L),
            PreferenceSetEvent("show_clock", "true"),
            PreferenceSetEvent("show_logs", "false"),
            HostLogAppendedEvent(
                ComputerLogEntry(
                    createdAtEpochMillis = 0L,
                    renderedLine = "1-Jan-1970 (12:00:00 AM) hook hit",
                    sourceIp = "REMOTE-IP",
                ),
            ),
        ))
        val reloaded = runBlockingLoad(repository, stateId)

        assertEquals(updated, reloaded)
        assertEquals(4, updated.version)
        assertEquals(123_456L, updated.identity.lastLoginAtEpochMillis)
        assertEquals("true", updated.preferences.values["show_clock"])
        assertEquals("false", updated.preferences.values["show_logs"])
        assertTrue(updated.identity.isNpc)
        assertEquals(1, updated.logs.entries.size)
        assertEquals("REMOTE-IP", updated.logs.entries.single().sourceIp)
        assertEquals(4, countRows("rewrite_state_event"))
    }

    @Test
    fun replaysFilesystemAndInstallEventsIntoDeterministicTypedState() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(
                id = stateId,
                playFabId = "PF-LOCALUSER",
            ).copy(
                economy = ComputerState.empty(id = stateId).economy.copy(pettyCash = 500.0),
            ),
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(eventThreshold = 1, timeThreshold = 5.seconds),
        )

        val sourceFile = StoredFile(
            path = buildFilePath("/Public", "bank.hws"),
            name = "bank.hws",
            kind = StoredFileKind.SCRIPT_SOURCE,
            contents = "bank script",
            compileCost = 75.0,
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.BANKING,
                outputName = "bank.bin",
                applicationKind = ApplicationKind.BANKING,
                bankingApplication = true,
                experienceAward = 4.0,
            ),
        )
        val compiledFile = sourceFile.copy(
            path = buildFilePath("/Public", "bank.bin"),
            name = "bank.bin",
            kind = StoredFileKind.APPLICATION_BINARY,
        )
        val httpSource = StoredFile(
            path = buildFilePath("/Public", "site"),
            name = "site",
            kind = StoredFileKind.SCRIPT_SOURCE,
            contents = "http script",
            compileCost = 40.0,
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.HTTP,
                outputName = "site.bin",
                applicationKind = ApplicationKind.HTTP,
                experienceAward = 5.0,
            ),
            scriptBundle = ProgramScriptBundle(
                family = ScriptFamily.HTTP,
                scriptsBySlot = linkedMapOf(
                    ProgramScriptSlot.ENTER to "int main() { return 0; }",
                    ProgramScriptSlot.EXIT to "int main() { return 0; }",
                    ProgramScriptSlot.SUBMIT to "int main() { return 0; }",
                ),
            ),
        )
        val httpBinary = httpSource.copy(
            path = buildFilePath("/Public", "site.bin"),
            name = "site.bin",
            kind = StoredFileKind.APPLICATION_BINARY,
        )
        val equipmentFile = StoredFile(
            path = buildFilePath("/Public", "cpu-card.bin"),
            name = "cpu-card.bin",
            kind = StoredFileKind.EQUIPMENT_BINARY,
            contents = "cpu boost",
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.GENERAL,
                equipmentSlot = EquipmentSlot.CPU,
                outputName = "cpu-card.bin",
            ),
        )

        runBlockingAppend(
            repository,
            stateId,
            listOf(
                DirectoryCreatedEvent(DirectoryEntry(path = "/Public", name = "Public")),
                FileSavedEvent(sourceFile),
                FileSavedEvent(httpSource),
                FileSavedEvent(equipmentFile),
                FileCompiledEvent(
                    sourceFilePath = sourceFile.path,
                    remainingSourceFile = sourceFile,
                    compiledFile = compiledFile,
                    pettyCashDelta = -75.0,
                    scriptFamily = ScriptFamily.BANKING,
                    experienceDelta = 4.0,
                ),
                FileCompiledEvent(
                    sourceFilePath = httpSource.path,
                    remainingSourceFile = httpSource,
                    compiledFile = httpBinary,
                    pettyCashDelta = -40.0,
                    scriptFamily = ScriptFamily.HTTP,
                    experienceDelta = 5.0,
                ),
                ApplicationInstalledEvent(
                    sourceFilePath = compiledFile.path,
                    remainingSourceFile = null,
                    portState = PortState(
                        number = 6,
                        type = "banking",
                        installedApplication = InstalledApplication(
                            name = "bank.bin",
                            kind = ApplicationKind.BANKING,
                            binaryPath = compiledFile.path,
                            banking = true,
                        ),
                    ),
                    defaultBankPort = 6,
                ),
                ApplicationInstalledEvent(
                    sourceFilePath = httpBinary.path,
                    remainingSourceFile = null,
                    portState = PortState(
                        number = 80,
                        type = "http",
                        defaultPort = true,
                        installedApplication = InstalledApplication(
                            name = "site.bin",
                            kind = ApplicationKind.HTTP,
                            binaryPath = httpBinary.path,
                            scriptBundle = httpBinary.scriptBundle,
                        ),
                    ),
                    defaultBankPort = null,
                ),
                EquipmentInstalledEvent(
                    sourceFilePath = equipmentFile.path,
                    remainingSourceFile = null,
                    slot = EquipmentSlot.CPU,
                    equipment = InstalledEquipment(
                        slot = EquipmentSlot.CPU,
                        name = "cpu-card.bin",
                        binaryPath = equipmentFile.path,
                    ),
                ),
            ),
        )

        val reloaded = runBlockingLoad(repository, stateId)

        requireNotNull(reloaded)
        assertEquals(6, reloaded.economy.defaultBankPort)
        assertEquals(385.0, reloaded.economy.pettyCash)
        assertEquals(4.0, reloaded.stats.experienceByFamily[ScriptFamily.BANKING])
        assertEquals(5.0, reloaded.stats.experienceByFamily[ScriptFamily.HTTP])
        assertEquals(1, reloaded.filesystem.directoriesByPath.count { it.key == "/Public" })
        assertEquals("bank.hws", reloaded.filesystem.filesByPath[sourceFile.path]?.name)
        assertEquals("bank.bin", reloaded.ports.single { it.number == 6 }.installedApplication?.name)
        assertEquals(httpBinary.scriptBundle, reloaded.ports.single { it.number == 80 }.installedApplication?.scriptBundle)
        assertEquals("cpu-card.bin", reloaded.hardware.equipmentSlots[EquipmentSlot.CPU]?.name)
        assertTrue(countRows("rewrite_state_snapshot") >= 1)
    }

    @Test
    fun replaysAttackStartEconomyRuntimeAndCombatStateDeterministically() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        val baseState = ComputerState.empty(
            id = stateId,
            playFabId = "PF-LOCALUSER",
        ).copy(
            economy = EconomyState(
                pettyCash = 100.0,
                bankMoney = 50.0,
                defaultBankPort = 6,
            ),
            hardware = ComputerState.empty(id = stateId).hardware.copy(cpuMax = 100.0),
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
                    ),
                ),
                PortState(
                    number = 25,
                    type = "http",
                    enabled = true,
                    health = 100.0,
                ),
            ),
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
        )
        val attackScriptBundle = ProgramScriptBundle(
            family = ScriptFamily.ATTACK,
            scriptsBySlot = linkedMapOf(
                ProgramScriptSlot.INITIALIZE to """int main() { logMessage("init"); return 0; }""",
                ProgramScriptSlot.CONTINUE to """int main() { logMessage("continue"); return 0; }""",
                ProgramScriptSlot.FINALIZE to """int main() { logMessage("finalize"); return 0; }""",
            ),
        )
        val initialState = baseState.copy(
            ports = baseState.ports.map { port ->
                if (port.number == 12) {
                    port.copy(
                        installedApplication = port.installedApplication?.copy(
                            scriptBundle = attackScriptBundle,
                        ),
                    )
                } else {
                    port
                }
            },
        )
        seedPlayerAndComputer(stateId = stateId, state = initialState)
        val session = AttackSessionState(
            programId = "attack-session-1",
            sourcePort = 12,
            targetStateId = GameStateId("TARGET-IP"),
            targetPort = 25,
            targetView = AttackTargetView(
                targetStateId = GameStateId("TARGET-IP"),
                targetPort = 25,
                health = 97.8,
                pettyCash = 0.0,
                cpuCost = 0.0,
                watchPresent = false,
                npc = false,
                lastAppliedDamage = 2.2,
                completed = false,
            ),
            windowHandle = 4,
            secondaryPorts = listOf(7, 8),
            maliciousScripts = listOf(AttackScriptReference("/Public", "worm.bin")),
            startedAtEpochMillis = 1_000L,
        )

        val updated = runBlockingAppend(
            repository,
            stateId,
            listOf(
                EconomyBalanceAdjustedEvent(pettyCashDelta = -10.0),
                HostLogAppendedEvent(
                    entry = ComputerLogEntry(
                        createdAtEpochMillis = 1_500L,
                        renderedLine = "1-Jan-1970 (12:00:01 AM) continue",
                        sourceIp = stateId.value,
                    ),
                ),
                SkillExperienceAdjustedEvent(
                    family = ScriptFamily.ATTACK,
                    delta = 2.2,
                ),
                CombatStateUpdatedEvent(
                    changedPathList = setOf(
                        "combat.activeAttacksBySourcePort.12",
                        "combat.incomingAttacksByTargetPort.25",
                        "ports.25.health",
                        "ports.12.attacking",
                        "runtime.currentCpuLoad",
                    ),
                    deltaKeyList = setOf("ports", "combat", "runtime"),
                    combat = CombatState(
                        activeAttacksBySourcePort = mapOf(12 to session),
                        incomingAttacksByTargetPort = mapOf(
                            25 to IncomingAttackState(
                                attackerStateId = stateId,
                                attackerSourcePort = 12,
                                targetPort = 25,
                                startedAtEpochMillis = 1_000L,
                                windowHandle = 4,
                            ),
                        ),
                    ),
                    ports = initialState.ports.map { port ->
                        when (port.number) {
                            12 -> port.copy(attacking = true)
                            25 -> port.copy(health = 97.8)
                            else -> port
                        }
                    },
                    currentCpuLoad = 8.0,
                    includePorts = true,
                    includeRuntime = true,
                ),
            ),
        )
        val reloaded = runBlockingLoad(repository, stateId)

        requireNotNull(reloaded)
        assertEquals(updated, reloaded)
        assertEquals(90.0, reloaded.economy.pettyCash)
        assertTrue(reloaded.ports.single { it.number == 12 }.attacking)
        assertEquals(97.8, reloaded.ports.single { it.number == 25 }.health)
        assertEquals(8.0, reloaded.runtime.currentCpuLoad)
        assertEquals(2.2, reloaded.stats.experienceByFamily[ScriptFamily.ATTACK])
        assertEquals("attack-session-1", reloaded.combat.activeAttacksBySourcePort.getValue(12).programId)
        assertEquals(97.8, reloaded.combat.activeAttacksBySourcePort.getValue(12).targetView.health)
        assertEquals(2.2, reloaded.combat.activeAttacksBySourcePort.getValue(12).targetView.lastAppliedDamage)
        assertEquals(stateId, reloaded.combat.incomingAttacksByTargetPort.getValue(25).attackerStateId)
        assertEquals("worm.bin", reloaded.combat.activeAttacksBySourcePort.getValue(12).maliciousScripts.single()?.name)
        assertEquals(attackScriptBundle, reloaded.ports.single { it.number == 12 }.installedApplication?.scriptBundle)
        assertEquals("1-Jan-1970 (12:00:01 AM) continue", reloaded.logs.entries.single().renderedLine)
    }

    @Test
    fun replaysWatchManagerStateCpuLoadAndCapacityBoostDeterministically() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        val watchBinary = StoredFile(
            path = buildFilePath("/Public", "watch.bin"),
            name = "watch.bin",
            kind = StoredFileKind.APPLICATION_BINARY,
            contents = "watch script",
            cpuCost = 5.0,
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.WATCH,
                applicationKind = ApplicationKind.WATCH,
                outputName = "watch.bin",
            ),
        )
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER").copy(
                hardware = ComputerState.empty(id = stateId).hardware.copy(
                    cpuMax = 75.0,
                    memoryType = 1,
                    equipmentSlots = mapOf(
                        EquipmentSlot.PCI to InstalledEquipment(
                            slot = EquipmentSlot.PCI,
                            name = "watch-booster.bin",
                            watchCapacityBoost = 3,
                        ),
                    ),
                ),
                filesystem = ComputerState.empty(id = stateId).filesystem
                    .ensureDirectory("/Public")
                    .saveFile(watchBinary),
            ),
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(eventThreshold = 1, timeThreshold = 5.seconds),
        )

        val installedWatch = InstalledWatch(
            kind = WatchKind.PETTY_CASH,
            enabled = false,
            note = "watch.bin",
            cpuCost = 5.0,
            quantityThreshold = 25.0,
            baselineQuantity = 100.0,
            installPort = 6,
            searchFirewallType = 0,
            observedPorts = listOf(6),
            contents = "watch script",
            scriptBundle = ProgramScriptBundle(
                family = ScriptFamily.WATCH,
                scriptsBySlot = linkedMapOf(
                    ProgramScriptSlot.FIRE to """
                        int main() {
                            logMessage("watch");
                            return 0;
                        }
                    """.trimIndent(),
                ),
            ),
            compiledBinary = watchBinary.compiledBinary,
        )

        runBlockingAppend(
            repository,
            stateId,
            listOf(
                WatchInstalledEvent(
                    sourceFilePath = watchBinary.path,
                    remainingSourceFile = null,
                    installedWatch = installedWatch,
                ),
                WatchManagerUpdatedEvent(
                    changedPathList = setOf(
                        "watches.watches.0.enabled",
                        "watches.watches.0.searchFirewallType",
                        "watches.watches.0.observedPorts",
                        "runtime.currentCpuLoad",
                    ),
                    deltaKeyList = setOf("watches", "runtime"),
                    watches = WatchManagerState(
                        watches = listOf(
                            installedWatch.copy(
                                enabled = true,
                                searchFirewallType = 4,
                                observedPorts = listOf(80, 21, 6),
                            ),
                        ),
                    ),
                    currentCpuLoad = 5.0,
                    includeRuntime = true,
                ),
            ),
        )

        val reloaded = runBlockingLoad(repository, stateId)

        requireNotNull(reloaded)
        assertEquals(1, reloaded.watches.watches.size)
        assertTrue(reloaded.watches.watches.single().enabled)
        assertEquals(listOf(80, 21, 6), reloaded.watches.watches.single().observedPorts)
        assertEquals(4, reloaded.watches.watches.single().searchFirewallType)
        assertEquals(ScriptFamily.WATCH, reloaded.watches.watches.single().compiledBinary?.scriptFamily)
        assertEquals(
            installedWatch.scriptBundle?.script(ProgramScriptSlot.FIRE),
            reloaded.watches.watches.single().scriptBundle?.script(ProgramScriptSlot.FIRE),
        )
        assertEquals(3, reloaded.hardware.equipmentSlots[EquipmentSlot.PCI]?.watchCapacityBoost)
        assertEquals(5.0, reloaded.runtime.currentCpuLoad)
        assertTrue(countRows("rewrite_state_snapshot") >= 1)
    }

    @Test
    fun snapshotsAfterEventOrTimeThresholds() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER"),
        )
        var now = Instant.parse("2026-03-24T12:00:00Z")
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(
                eventThreshold = 2,
                timeThreshold = 5.seconds,
            ),
            clock = { now },
        )

        runBlockingAppend(repository, stateId, listOf(PreferenceSetEvent("show_clock", "true")))
        assertEquals(0, countRows("rewrite_state_snapshot"))

        now = now.plusSeconds(6)
        runBlockingAppend(repository, stateId, listOf(PreferenceSetEvent("show_logs", "false")))
        assertEquals(1, countRows("rewrite_state_snapshot"))
    }

    @Test
    fun replaysNetworkStateAndScanSideEffectsDeterministically() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER").copy(
                economy = ComputerState.empty(id = stateId).economy.copy(pettyCash = 100.0, defaultBankPort = 6),
            ),
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(eventThreshold = 1, timeThreshold = 5.seconds),
        )

        runBlockingAppend(
            repository,
            stateId,
            listOf(
                NetworkStateChangedEvent(
                    network = NetworkState(
                        currentNetworkName = ROOT_NETWORK_NAME,
                        storeStateId = GameStateId("store1"),
                        allowedNetworks = setOf("ProgNet"),
                        lastNetworkSwitchAtEpochMillis = 180001L,
                        regularNpcs = listOf(
                            NpcDirectoryEntry(
                                stateId = GameStateId("PROG-ATTACK-1"),
                                displayName = "Prog Attack",
                                title = "Attack NPC",
                                category = NpcCategory.REGULAR,
                            ),
                        ),
                    ),
                ),
                EconomyBalanceAdjustedEvent(pettyCashDelta = -10.0),
                SkillExperienceAdjustedEvent(family = ScriptFamily.SCANNING, delta = 60.0),
            ),
        )

        val reloaded = runBlockingLoad(repository, stateId)

        requireNotNull(reloaded)
        assertEquals(ROOT_NETWORK_NAME, reloaded.network.currentNetworkName)
        assertEquals(GameStateId("store1"), reloaded.network.storeStateId)
        assertEquals(setOf("ProgNet"), reloaded.network.allowedNetworks)
        assertEquals(180001L, reloaded.network.lastNetworkSwitchAtEpochMillis)
        assertEquals("Prog Attack", reloaded.network.regularNpcs.single().displayName)
        assertEquals(90.0, reloaded.economy.pettyCash)
        assertEquals(60.0, reloaded.stats.experienceByFamily[ScriptFamily.SCANNING])
    }

    @Test
    fun replaysWebsiteEditorAndVoteEventsIntoDeterministicTypedState() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER"),
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(eventThreshold = 1, timeThreshold = 5.seconds),
        )

        runBlockingAppend(
            repository,
            stateId,
            listOf(
                WebsiteSavedEvent(
                    title = "Seeded Title",
                    body = "<html>Seeded Body</html>",
                ),
                WebsiteVotesAvailableAdjustedEvent(delta = 3),
                WebsiteVoteCountAdjustedEvent(delta = 5),
                HttpExperienceAdjustedEvent(delta = 500.0),
            ),
        )

        val reloaded = runBlockingLoad(repository, stateId)

        requireNotNull(reloaded)
        assertEquals("Seeded Title", reloaded.website.title)
        assertEquals("<html>Seeded Body</html>", reloaded.website.body)
        assertEquals(3, reloaded.website.votesAvailable)
        assertEquals(5, reloaded.website.voteCount)
        assertEquals(500.0, reloaded.stats.experienceByFamily[ScriptFamily.HTTP])
        assertTrue(countRows("rewrite_state_snapshot") >= 1)
    }

    @Test
    fun replaysHealthWatchBaselineAndWatchXpAfterCombatDamage() {
        resetDatabase()
        val stateId = GameStateId("TARGET-IP")
        val installedWatch = InstalledWatch(
            kind = WatchKind.HEALTH,
            enabled = true,
            note = "health-watch",
            cpuCost = 5.0,
            quantityThreshold = 50.0,
            baselineQuantity = 100.0,
            installPort = 25,
            searchFirewallType = 0,
            observedPorts = listOf(25),
            contents = """int main() { logMessage("health"); return 0; }""",
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.WATCH,
                applicationKind = ApplicationKind.WATCH,
                outputName = "watch.bin",
            ),
        )
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(id = stateId, playFabId = "PF-TARGET").copy(
                ports = listOf(
                    PortState(
                        number = 25,
                        type = "http",
                        enabled = true,
                        health = 100.0,
                    ),
                ),
                watches = WatchManagerState(watches = listOf(installedWatch)),
            ),
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
        )

        runBlockingAppend(
            repository,
            stateId,
            listOf(
                CombatStateUpdatedEvent(
                    changedPathList = setOf("ports.25.health"),
                    deltaKeyList = setOf("ports"),
                    combat = CombatState(),
                    ports = listOf(
                        PortState(
                            number = 25,
                            type = "http",
                            enabled = true,
                            health = 0.0,
                        ),
                    ),
                    currentCpuLoad = 0.0,
                    includePorts = true,
                ),
                HostLogAppendedEvent(
                    ComputerLogEntry(
                        createdAtEpochMillis = 1_000L,
                        renderedLine = "1-Jan-1970 (12:00:01 AM) health",
                        sourceIp = "ATTACKER-IP",
                    ),
                ),
                WatchManagerUpdatedEvent(
                    changedPathList = setOf("watches.watches"),
                    deltaKeyList = setOf("watches"),
                    watches = WatchManagerState(
                        watches = listOf(installedWatch.copy(baselineQuantity = 0.0)),
                    ),
                    currentCpuLoad = 0.0,
                    includeRuntime = false,
                ),
                SkillExperienceAdjustedEvent(
                    family = ScriptFamily.WATCH,
                    delta = 1.0,
                ),
            ),
        )

        val reloaded = runBlockingLoad(repository, stateId)

        requireNotNull(reloaded)
        assertEquals(0.0, reloaded.ports.first { it.number == 25 }.health)
        assertEquals(0.0, reloaded.watches.watches.single().baselineQuantity)
        assertEquals(1.0, reloaded.stats.experienceByFamily[ScriptFamily.WATCH])
        assertEquals(1, reloaded.logs.entries.size)
        assertEquals("ATTACKER-IP", reloaded.logs.entries.single().sourceIp)
    }

    @Test
    fun replaysEconomyAndStoreEventsAcrossBuyerSellerRevenueAndShardStore() {
        resetDatabase()
        val buyerId = GameStateId("BUYER-IP")
        val sellerId = GameStateId("SELLER-IP")
        val revenueId = GameStateId("REV-IP")
        val storeId = GameStateId("store1")
        seedPlayerAndComputer(
            stateId = buyerId,
            state = ComputerState.empty(id = buyerId, playFabId = "PF-BUYER").copy(
                economy = ComputerState.empty(id = buyerId).economy.copy(
                    pettyCash = 1000.0,
                    bankMoney = 100.0,
                    defaultBankPort = 6,
                ),
            ),
            playerId = "buyer-user",
        )
        seedPlayerAndComputer(
            stateId = sellerId,
            state = ComputerState.empty(id = sellerId, playFabId = "PF-SELLER").copy(
                economy = ComputerState.empty(id = sellerId).economy.copy(pettyCash = 400.0),
            ),
            playerId = "seller-user",
        )
        seedPlayerAndComputer(
            stateId = revenueId,
            state = ComputerState.empty(id = revenueId, playFabId = "PF-REV").copy(
                economy = ComputerState.empty(id = revenueId).economy.copy(pettyCash = 10.0),
            ),
            playerId = "revenue-user",
        )
        seedPlayerAndComputer(
            stateId = storeId,
            state = ComputerState.empty(id = storeId, playFabId = "PF-STORE"),
            playerId = "store-user",
        )

        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(eventThreshold = 1, timeThreshold = 5.seconds),
        )

        val pricedListing = StoredFile(
            path = buildFilePath("/Store", "merchant.bin"),
            name = "merchant.bin",
            kind = StoredFileKind.APPLICATION_BINARY,
            quantity = 3,
            maker = "Medium",
            compileCost = 100.0,
            price = 196.0,
            compiledBinary = CompiledBinaryMetadata(
                applicationKind = ApplicationKind.BANKING,
                bankingApplication = true,
                outputName = "merchant.bin",
            ),
        )
        val shardCopy = StoredFile(
            path = buildFilePath("/Store", "rare.bin"),
            name = "rare.bin",
            kind = StoredFileKind.APPLICATION_BINARY,
            quantity = 1,
            maker = "High",
            price = 1500.0,
            compiledBinary = CompiledBinaryMetadata(
                applicationKind = ApplicationKind.GENERIC,
                outputName = "rare.bin",
            ),
        )

        runBlockingAppend(
            repository,
            sellerId,
            listOf(
                FileSavedEvent(pricedListing),
                StoreFilePricedEvent(
                    filePath = pricedListing.path,
                    price = 196.0,
                ),
                StoreFilesLiquidatedEvent(
                    soldItems = listOf(
                        StoreLiquidationLineItem(
                            sourceFilePath = buildFilePath("/Public", "rare.bin"),
                            remainingSourceFile = StoredFile(
                                path = buildFilePath("/Public", "rare.bin"),
                                name = "rare.bin",
                                kind = StoredFileKind.APPLICATION_BINARY,
                                quantity = 1,
                                maker = "High",
                                compiledBinary = CompiledBinaryMetadata(
                                    applicationKind = ApplicationKind.GENERIC,
                                    outputName = "rare.bin",
                                ),
                            ),
                            creditedPettyCash = 1500.0,
                        ),
                    ),
                ),
                StoreListingPurchasedEvent(
                    listingPath = pricedListing.path,
                    remainingListing = pricedListing.copy(quantity = 1),
                ),
            ),
        )
        runBlockingAppend(
            repository,
            buyerId,
            listOf(
                PurchasedFileReceivedEvent(
                    file = pricedListing.copy(
                        path = buildFilePath("/", "merchant.bin"),
                        quantity = 2,
                    ),
                    pettyCashDelta = -392.0,
                ),
            ),
        )
        runBlockingAppend(
            repository,
            revenueId,
            listOf(
                EconomyBalanceAdjustedEvent(pettyCashDelta = 392.0),
            ),
        )
        runBlockingAppend(
            repository,
            storeId,
            listOf(
                StoreInventoryReceivedEvent(files = listOf(shardCopy)),
            ),
        )

        val buyer = runBlockingLoad(repository, buyerId)
        val seller = runBlockingLoad(repository, sellerId)
        val revenue = runBlockingLoad(repository, revenueId)
        val store = runBlockingLoad(repository, storeId)

        requireNotNull(buyer)
        requireNotNull(seller)
        requireNotNull(revenue)
        requireNotNull(store)
        assertEquals(608.0, buyer.economy.pettyCash)
        assertEquals(2, buyer.filesystem.filesByPath["/merchant.bin"]?.quantity)
        assertEquals(1900.0, seller.economy.pettyCash)
        assertEquals(1, seller.filesystem.filesByPath["/Store/merchant.bin"]?.quantity)
        assertEquals(402.0, revenue.economy.pettyCash)
        assertEquals(1, store.filesystem.filesByPath["/Store/rare.bin"]?.quantity)
        assertTrue(countRows("rewrite_state_snapshot") >= 4)
    }

    @Test
    fun replaysQuestSaveAndBountyStateDeterministically() {
        resetDatabase()
        val playerId = GameStateId("LOCAL-IP")
        val storeId = GameStateId("store1")
        seedPlayerAndComputer(
            stateId = playerId,
            state = ComputerState.empty(id = playerId, playFabId = "PF-LOCALUSER").copy(
                economy = ComputerState.empty(id = playerId).economy.copy(
                    pettyCash = 500.0,
                    defaultBankPort = 6,
                ),
                quests = com.hackwars.rewrite.gamecore.QuestState(
                    activeQuestsById = mapOf(
                        "quest-1" to com.hackwars.rewrite.gamecore.ActiveQuestProgress(
                            questId = "quest-1",
                            label = "Starter Quest",
                        ),
                    ),
                ),
            ),
        )
        seedPlayerAndComputer(
            stateId = storeId,
            state = ComputerState.empty(id = storeId, playFabId = "PF-STORE").copy(
                filesystem = ComputerState.empty(id = storeId, playerIp = storeId.value).filesystem.ensureDirectory("/Store"),
            ),
            playerId = "store-user",
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(eventThreshold = 1, timeThreshold = 5.seconds),
        )
        val saveFile = StoredFile(
            path = buildFilePath("/", "quest-progress.save"),
            name = "quest-progress.save",
            kind = StoredFileKind.SAVE_DATA,
            contents = "stage\tstring\tstarter\ncount\tint\t3\nenabled\tbool\ttrue\n",
            description = "A save file for quest-progress.",
            maker = "quest-progress",
            saveMetadata = SaveFileMetadata(
                valuesByKey = linkedMapOf(
                    "stage" to StringHookValue("starter"),
                    "count" to IntHookValue(3),
                    "enabled" to BooleanHookValue(true),
                ),
            ),
        )
        val bountyFile = StoredFile(
            path = buildFilePath("/Store", "Install By (LOCAL-IP)"),
            name = "Install By (LOCAL-IP)",
            kind = StoredFileKind.BOUNTY,
            contents = "count=2\ntype=2\nreward=125.0\ntarget=ENEMY-IP\nbountyip=LOCAL-IP\nmaker=Rewrite\nscript=installer.bin\n",
            description = "Bounty Type: Install\nTarget: ENEMY-IP\nReward: \$125.00\nMust Install: installer.bin Maker: Rewrite\n",
            maker = "LOCAL-IP",
            bountyMetadata = BountyMetadata(
                type = BountyTypes.INSTALL,
                target = "ENEMY-IP",
                iterationsRemaining = 2,
                reward = 125.0,
                bountySourceStateId = playerId,
                requiredMaker = "Rewrite",
                requiredScriptName = "installer.bin",
                anonymous = false,
            ),
        )

        runBlockingAppend(
            repository,
            playerId,
            listOf(
                QuestTaskProgressRecordedEvent(
                    questId = "quest-1",
                    taskName = "download",
                ),
                ClueDataStoredEvent(
                    targetIp = "TARGET-IP",
                    data = "alpha clue",
                ),
                FileSavedEvent(saveFile),
                EconomyBalanceAdjustedEvent(pettyCashDelta = -125.0),
            ),
        )
        runBlockingAppend(
            repository,
            storeId,
            listOf(FileSavedEvent(bountyFile)),
        )

        val player = runBlockingLoad(repository, playerId)
        val store = runBlockingLoad(repository, storeId)

        requireNotNull(player)
        requireNotNull(store)
        assertTrue(player.quests.activeQuestsById["quest-1"]?.tasksByName?.get("download")?.completed == true)
        assertEquals("alpha clue", player.quests.lastClueDataByIp["TARGET-IP"])
        assertEquals("starter", (player.filesystem.filesByPath["/quest-progress.save"]?.saveMetadata?.valuesByKey?.get("stage") as? StringHookValue)?.value)
        assertEquals(375.0, player.economy.pettyCash)
        assertEquals(BountyTypes.INSTALL, store.filesystem.filesByPath["/Store/Install By (LOCAL-IP)"]?.bountyMetadata?.type)
        assertEquals("installer.bin", store.filesystem.filesByPath["/Store/Install By (LOCAL-IP)"]?.bountyMetadata?.requiredScriptName)
    }

    private fun resetDatabase() {
        newConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("drop schema if exists public cascade")
                statement.execute("create schema public")
            }
            RewriteLiquibase.update(connection)
        }
    }

    private fun seedPlayerAndComputer(
        stateId: GameStateId,
        state: ComputerState,
        playerId: String = "local-user",
    ) {
        newConnection().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_player_account(player_id, playfab_id, player_ip, account_payload)
                values (?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, playerId)
                statement.setString(2, state.identity.playFabId.ifBlank { "PF-${playerId.uppercase()}" })
                statement.setString(3, state.identity.playerIp.ifBlank { stateId.value })
                statement.setString(
                    4,
                    """{"playerId":"$playerId","playFabId":"${state.identity.playFabId.ifBlank { "PF-${playerId.uppercase()}" }}","playerIp":"${state.identity.playerIp.ifBlank { stateId.value }}"}""",
                )
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                insert into rewrite_computer_state(computer_id, player_id, ip_address, state_payload)
                values (?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, stateId.value)
                statement.setString(2, playerId)
                statement.setString(3, stateId.value)
                statement.setString(4, serializer.encodeStateJson(state))
                statement.executeUpdate()
            }
        }
    }

    private fun countRows(tableName: String): Int {
        newConnection().use { connection ->
            connection.prepareStatement("select count(*) from $tableName").use { statement ->
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getInt(1)
                }
            }
        }
    }

    private fun runBlockingAppend(
        repository: JdbcComputerStateRepository,
        stateId: GameStateId,
        events: List<ComputerEvent>,
    ): ComputerState {
        return kotlinx.coroutines.runBlocking {
            repository.appendEvents(stateId, events)
        }
    }

    private fun runBlockingLoad(
        repository: JdbcComputerStateRepository,
        stateId: GameStateId,
    ): ComputerState? {
        return kotlinx.coroutines.runBlocking {
            repository.load(stateId)
        }
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
