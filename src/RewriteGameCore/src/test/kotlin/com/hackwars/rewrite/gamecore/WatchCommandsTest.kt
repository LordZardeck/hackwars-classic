package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.StringHookValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WatchCommandsTest {
    @Test
    fun fetchWatchesReturnsTypedListAndEmitsNoDeltas() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(
                    stateId = stateId,
                    installedWatches = listOf(
                        seededWatch(enabled = false, note = "alpha"),
                        seededWatch(enabled = true, note = "beta", installPort = 80),
                    ),
                    currentCpuLoad = 5.0,
                ),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = FetchWatchesCommand(stateId),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertEquals(2, response.installedCount)
        assertEquals(1, response.activeCount)
        assertEquals(4, response.maximumActiveCount)
        assertEquals(5.0, response.currentCpuLoad)
        assertTrue(publisher.deltas.isEmpty())
    }

    @Test
    fun installWatchAcceptsCompiledWatchBinaryAndReturnsAffectedIndex() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(stateId = stateId, pettyCash = 450.0),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = InstallWatchCommand(
                stateId = stateId,
                path = "/Public",
                fileName = "watch.bin",
                typeCode = WatchKind.PETTY_CASH.legacyCode,
                portNumber = 6,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val updated = repository.load(stateId)

        requireNotNull(updated)
        assertTrue(response.accepted)
        assertEquals(0, response.affectedWatchIndex)
        assertEquals(1, response.snapshot.installedCount)
        assertEquals(450.0, response.snapshot.watches.single().baselineQuantity)
        assertEquals("watch.bin", response.snapshot.watches.single().note)
        assertTrue(updated.filesystem.filesByPath["/Public/watch.bin"] == null)
        assertEquals(setOf("filesystem", "watches"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun installWatchRejectsMissingFileWrongBinaryTypeInstalledCapAndCpuHeadroom() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val missingFileRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(stateId = stateId).copy(
                    filesystem = ComputerState.empty(id = stateId).filesystem.ensureDirectory("/Public"),
                ),
            ),
        )
        val wrongTypeRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(
                    stateId = stateId,
                    binaryMetadata = CompiledBinaryMetadata(
                        scriptFamily = ScriptFamily.GENERAL,
                        applicationKind = ApplicationKind.GENERIC,
                        outputName = "watch.bin",
                    ),
                ),
            ),
        )
        val installedCapRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(
                    stateId = stateId,
                    installedWatches = List(21) { index -> seededWatch(note = "watch-$index") },
                ),
            ),
        )
        val cpuRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(
                    stateId = stateId,
                    currentCpuLoad = 98.0,
                    cpuMax = 100.0,
                    watchCpuCost = 5.0,
                ),
            ),
        )
        val dispatcherFor: (InMemoryComputerStateRepository) -> DefaultCommandDispatcher = { repository ->
            DefaultCommandDispatcher(repository, InMemoryInterestRegistry())
        }

        val missingFile = dispatcherFor(missingFileRepository).request(
            InstallWatchCommand(stateId, "/Public", "watch.bin", WatchKind.PETTY_CASH.legacyCode, 6),
            publisher = RecordingGameStatePublisher(),
        )
        val wrongType = dispatcherFor(wrongTypeRepository).request(
            InstallWatchCommand(stateId, "/Public", "watch.bin", WatchKind.PETTY_CASH.legacyCode, 6),
            publisher = RecordingGameStatePublisher(),
        )
        val installedCap = dispatcherFor(installedCapRepository).request(
            InstallWatchCommand(stateId, "/Public", "watch.bin", WatchKind.PETTY_CASH.legacyCode, 6),
            publisher = RecordingGameStatePublisher(),
        )
        val cpuHeadroom = dispatcherFor(cpuRepository).request(
            InstallWatchCommand(stateId, "/Public", "watch.bin", WatchKind.PETTY_CASH.legacyCode, 6),
            publisher = RecordingGameStatePublisher(),
        )

        assertEquals(WatchMutationFailureCode.MISSING_FILE, missingFile.failureCode)
        assertEquals(WatchMutationFailureCode.INVALID_FILE_TYPE, wrongType.failureCode)
        assertEquals(WatchMutationFailureCode.INSTALLED_LIMIT_REACHED, installedCap.failureCode)
        assertEquals(WatchMutationFailureCode.CPU_HEADROOM_EXCEEDED, cpuHeadroom.failureCode)
        assertFalse(missingFile.accepted)
        assertFalse(cpuHeadroom.accepted)
    }

    @Test
    fun watchConfigCommandsMutateOnlyTargetedWatchAndPreserveOrder() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val first = seededWatch(enabled = false, note = "first", installPort = 6)
        val second = seededWatch(enabled = false, note = "second", installPort = 21)
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(
                    stateId = stateId,
                    pettyCash = 375.0,
                    installedWatches = listOf(first, second),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        dispatcher.request(
            SetWatchNoteCommand(stateId, 0, "primary"),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        dispatcher.request(
            SetWatchQuantityCommand(stateId, 0, 42.5),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        dispatcher.request(
            SetWatchObservedPortsCommand(stateId, 0, listOf(80, 6, 21)),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        dispatcher.request(
            SetWatchSearchFirewallCommand(stateId, 0, 7),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        dispatcher.request(
            ChangeWatchPortCommand(stateId, 0, 80),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val typeChange = dispatcher.request(
            ChangeWatchTypeCommand(stateId, 0, WatchKind.HEALTH.legacyCode),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        val updated = repository.load(stateId)

        requireNotNull(updated)
        val mutated = updated.watches.watches.first()
        assertEquals("primary", mutated.note)
        assertEquals(42.5, mutated.quantityThreshold)
        assertEquals(listOf(80, 6, 21), mutated.observedPorts)
        assertEquals(7, mutated.searchFirewallType)
        assertEquals(80, mutated.installPort)
        assertEquals(WatchKind.HEALTH, mutated.kind)
        assertEquals(100.0, mutated.baselineQuantity)
        assertEquals("second", updated.watches.watches[1].note)
        assertEquals(listOf("primary", "second"), updated.watches.watches.map { it.note })
        assertTrue(publisher.deltas.all { it.second.deltaKeys == setOf("watches") })
        assertEquals(WatchKind.HEALTH, typeChange.snapshot.watches.first().kind)
    }

    @Test
    fun setWatchOnOffEnforcesActiveCapAndCpuLimitsAndDeletePreservesOverheatGuard() = runTest {
        val stateId = GameStateId("LOCAL-IP")

        val activeLimitRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(
                    stateId = stateId,
                    installedWatches = listOf(
                        seededWatch(enabled = true, note = "one"),
                        seededWatch(enabled = true, note = "two"),
                        seededWatch(enabled = true, note = "three"),
                        seededWatch(enabled = true, note = "four"),
                        seededWatch(enabled = false, note = "five"),
                    ),
                    currentCpuLoad = 20.0,
                ),
            ),
        )
        val cpuLimitRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(
                    stateId = stateId,
                    installedWatches = listOf(seededWatch(enabled = false, note = "five", cpuCost = 5.0)),
                    currentCpuLoad = 98.0,
                    cpuMax = 100.0,
                ),
            ),
        )
        val overheatedRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(
                    stateId = stateId,
                    installedWatches = listOf(seededWatch(enabled = true, note = "hot", cpuCost = 5.0)),
                    currentCpuLoad = 105.0,
                    cpuMax = 100.0,
                ),
            ),
        )
        val successRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localWatchState(
                    stateId = stateId,
                    installedWatches = listOf(
                        seededWatch(enabled = false, note = "alpha", cpuCost = 5.0),
                        seededWatch(enabled = true, note = "beta", cpuCost = 5.0),
                    ),
                    currentCpuLoad = 5.0,
                ),
            ),
        )

        val activeLimit = DefaultCommandDispatcher(activeLimitRepository, InMemoryInterestRegistry()).request(
            SetWatchOnOffCommand(stateId, 4, true),
            publisher = RecordingGameStatePublisher(),
        )
        val cpuLimit = DefaultCommandDispatcher(cpuLimitRepository, InMemoryInterestRegistry()).request(
            SetWatchOnOffCommand(stateId, 0, true),
            publisher = RecordingGameStatePublisher(),
        )
        val disableOverheated = DefaultCommandDispatcher(overheatedRepository, InMemoryInterestRegistry()).request(
            SetWatchOnOffCommand(stateId, 0, false),
            publisher = RecordingGameStatePublisher(),
        )
        val deleteOverheated = DefaultCommandDispatcher(overheatedRepository, InMemoryInterestRegistry()).request(
            DeleteWatchCommand(stateId, 0),
            publisher = RecordingGameStatePublisher(),
        )

        assertEquals(WatchMutationFailureCode.ACTIVE_LIMIT_REACHED, activeLimit.failureCode)
        assertEquals(WatchMutationFailureCode.CPU_HEADROOM_EXCEEDED, cpuLimit.failureCode)
        assertEquals(WatchMutationFailureCode.OVERHEATED, disableOverheated.failureCode)
        assertEquals(WatchMutationFailureCode.OVERHEATED, deleteOverheated.failureCode)

        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(successRepository, interests)
        val enable = dispatcher.request(
            SetWatchOnOffCommand(stateId, 0, true),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val delete = dispatcher.request(
            DeleteWatchCommand(stateId, 1),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val updated = successRepository.load(stateId)

        requireNotNull(updated)
        assertTrue(enable.accepted)
        assertTrue(delete.accepted)
        assertEquals(1, updated.watches.watches.size)
        assertTrue(updated.watches.watches.single().enabled)
        assertEquals(5.0, updated.runtime.currentCpuLoad)
        assertEquals(setOf("watches", "runtime"), publisher.deltas.first().second.deltaKeys)
        assertEquals(setOf("watches", "runtime"), publisher.deltas.last().second.deltaKeys)
    }

    @Test
    fun requestTriggerStillOnlyEmitsWatchIntentAndDoesNotMutateWatchState() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val sourceState = localWatchState(
            stateId = sourceId,
            installedWatches = listOf(seededWatch(enabled = true, note = "armed")),
            currentCpuLoad = 5.0,
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to sourceState,
                targetId to ComputerState.empty(id = targetId, playerIp = targetId.value),
            ),
        )
        val dispatcher = DefaultCommandDispatcher(repository, InMemoryInterestRegistry())
        val publisher = RecordingGameStatePublisher()

        val response = dispatcher.request(
            command = RequestTriggerCommand(
                stateId = sourceId,
                targetStateId = targetId,
                selector = TriggerSelector.ByNote("armed"),
                sourceIp = sourceId.value,
                triggerParameters = mapOf("mode" to StringHookValue("alpha")),
            ),
            publisher = publisher,
        )

        val updated = repository.load(sourceId)

        requireNotNull(updated)
        assertTrue(response.accepted)
        assertEquals(null, response.matchedWatchIndex)
        assertFalse(response.executed)
        assertEquals(sourceState.watches, updated.watches)
        assertEquals(sourceState.runtime.currentCpuLoad, updated.runtime.currentCpuLoad)
        assertTrue(publisher.deltas.isEmpty())
    }
}

private fun localWatchState(
    stateId: GameStateId,
    pettyCash: Double = 250.0,
    installedWatches: List<InstalledWatch> = emptyList(),
    currentCpuLoad: Double = 0.0,
    cpuMax: Double = 100.0,
    memoryType: Int = 0,
    watchCpuCost: Double = 5.0,
    binaryMetadata: CompiledBinaryMetadata = CompiledBinaryMetadata(
        scriptFamily = ScriptFamily.WATCH,
        applicationKind = ApplicationKind.WATCH,
        outputName = "watch.bin",
    ),
): ComputerState {
    val state = ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}", playerIp = stateId.value)
    val filesystem = state.filesystem
        .ensureDirectory("/Public")
        .saveFile(
            StoredFile(
                path = buildFilePath("/Public", "watch.bin"),
                name = "watch.bin",
                kind = StoredFileKind.APPLICATION_BINARY,
                contents = "watch script",
                maker = "Rewrite",
                cpuCost = watchCpuCost,
                compiledBinary = binaryMetadata,
            ),
        )
    return state.copy(
        economy = state.economy.copy(pettyCash = pettyCash, defaultBankPort = 6),
        hardware = state.hardware.copy(cpuMax = cpuMax, memoryType = memoryType),
        ports = listOf(
            PortState(number = 6, type = "banking", enabled = true, defaultPort = true),
            PortState(number = 21, type = "ftp", enabled = true),
            PortState(number = 80, type = "http", enabled = true),
        ),
        filesystem = filesystem,
        watches = WatchManagerState(watches = installedWatches),
        runtime = state.runtime.copy(currentCpuLoad = currentCpuLoad),
    )
}

private fun seededWatch(
    enabled: Boolean = false,
    note: String = "watch",
    cpuCost: Double = 5.0,
    installPort: Int = 6,
    kind: WatchKind = WatchKind.PETTY_CASH,
): InstalledWatch {
    return InstalledWatch(
        kind = kind,
        enabled = enabled,
        note = note,
        cpuCost = cpuCost,
        quantityThreshold = 0.0,
        baselineQuantity = when (kind) {
            WatchKind.PETTY_CASH -> 250.0
            WatchKind.HEALTH -> 100.0
            WatchKind.SCAN -> 0.0
        },
        installPort = installPort,
        searchFirewallType = 0,
        observedPorts = listOf(installPort),
        contents = "watch script",
        compiledBinary = CompiledBinaryMetadata(
            scriptFamily = ScriptFamily.WATCH,
            applicationKind = ApplicationKind.WATCH,
            outputName = "watch.bin",
        ),
    )
}
