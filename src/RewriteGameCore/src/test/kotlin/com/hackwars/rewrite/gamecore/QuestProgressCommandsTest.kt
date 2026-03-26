package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.ArrayHookValue
import com.hackwars.rewrite.hackscript.BooleanHookValue
import com.hackwars.rewrite.hackscript.FloatHookValue
import com.hackwars.rewrite.hackscript.IntHookValue
import com.hackwars.rewrite.hackscript.StringHookValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class QuestProgressCommandsTest {
    @Test
    fun requestTaskMarksActiveQuestTaskAndSkipsCompletedOrMissingQuests() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(stateId to localQuestState(stateId)),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val activeResponse = dispatcher.request(
            command = RequestTaskCommand(
                stateId = stateId,
                fileName = "intro",
                questId = "quest-1",
                taskName = "download",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val completedResponse = dispatcher.request(
            command = RequestTaskCommand(
                stateId = stateId,
                fileName = "intro",
                questId = "quest-done",
                taskName = "noop",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertTrue(activeResponse.changed)
        assertEquals(QuestTaskProgress(completed = true, note = ""), activeResponse.progress)
        assertFalse(completedResponse.changed)
        assertEquals(listOf(setOf("quests")), publisher.deltas.map { it.second.deltaKeys })
        assertEquals(
            QuestTaskProgress(completed = true, note = ""),
            repository.load(stateId)?.quests?.activeQuestsById?.get("quest-1")?.tasksByName?.get("download"),
        )
    }

    @Test
    fun requestSaveSerializesScalarValuesAndRejectsArrayValues() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(stateId to localQuestState(stateId)),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestSaveCommand(
                stateId = stateId,
                fileName = "quest-progress",
                triggerParameters = linkedMapOf(
                    "name" to StringHookValue("starter"),
                    "count" to IntHookValue(3),
                    "ratio" to FloatHookValue(1.5),
                    "enabled" to BooleanHookValue(true),
                ),
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertEquals("/quest-progress.save", response.file.path)
        assertEquals(StoredFileKind.SAVE_DATA, response.file.kind)
        assertEquals("A save file for quest-progress.", response.file.description)
        assertEquals(
            "name\tstring\tstarter\ncount\tint\t3\nratio\tfloat\t1.5\nenabled\tbool\ttrue\n",
            response.file.contents,
        )
        assertEquals(setOf("filesystem"), publisher.deltas.single().second.deltaKeys)
        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = RequestSaveCommand(
                    stateId = stateId,
                    fileName = "bad-save",
                    triggerParameters = mapOf(
                        "items" to ArrayHookValue(listOf(StringHookValue("a"), StringHookValue("b"))),
                    ),
                ),
                metadata = CommandMetadata(connectionId = "conn-1"),
                publisher = publisher,
            )
        }
    }

    @Test
    fun requestGameReturnsFullFileAndTypedLoadValuesWithoutDeltas() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(stateId to hacktendoState(stateId)),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val metadataBacked = dispatcher.request(
            command = RequestGameCommand(
                stateId = stateId,
                path = "/Games",
                fileName = "adventure",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val legacyBacked = dispatcher.request(
            command = RequestGameCommand(
                stateId = stateId,
                path = "/Games",
                fileName = "arcade",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val missingFile = dispatcher.request(
            command = RequestGameCommand(
                stateId = stateId,
                path = "/Games",
                fileName = "missing",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val missingSave = dispatcher.request(
            command = RequestGameCommand(
                stateId = stateId,
                path = "/Games",
                fileName = "sandbox",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertEquals("adventure", metadataBacked.file?.name)
        assertEquals("<game>adventure</game>", metadataBacked.file?.contents)
        assertEquals(
            linkedMapOf(
                "name" to StringHookValue("starter"),
                "score" to IntHookValue(7),
                "enabled" to BooleanHookValue(true),
            ),
            metadataBacked.loadValues,
        )
        assertEquals(
            linkedMapOf(
                "name" to StringHookValue("player"),
                "alive" to BooleanHookValue(true),
                "score" to IntHookValue(7),
                "ratio" to FloatHookValue(1.5),
            ),
            legacyBacked.loadValues,
        )
        assertEquals("sandbox", missingSave.file?.name)
        assertTrue(missingSave.loadValues.isEmpty())
        assertEquals(null, missingFile.file)
        assertTrue(missingFile.loadValues.isEmpty())
        assertTrue(publisher.deltas.isEmpty())
    }

    @Test
    fun hacktendoCommandsExecuteAsNoOpsWithoutMutatingState() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val seeded = hacktendoState(stateId)
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(stateId to seeded),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        dispatcher.dispatch(
            command = HacktendoActivateCommand(
                stateId = stateId,
                activateId = 5,
                activateType = 6,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        dispatcher.dispatch(
            command = HacktendoTargetCommand(
                stateId = stateId,
                targetX = 1,
                targetY = 2,
                currentX = 3,
                currentY = 4,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        val persisted = repository.load(stateId)
        assertEquals(seeded, persisted)
        assertTrue(publisher.deltas.isEmpty())
        assertTrue(publisher.uiEvents.isEmpty())
        assertTrue(publisher.programUpdates.isEmpty())
        assertNull(repository.load(GameStateId("OTHER-IP")))
    }

    @Test
    fun clueDataOnlyEmitsQuestDeltaWhenStoredValueChanges() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(stateId to localQuestState(stateId)),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val first = dispatcher.request(
            command = ClueDataCommand(
                stateId = stateId,
                targetIp = "TARGET-IP",
                data = "alpha clue",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val second = dispatcher.request(
            command = ClueDataCommand(
                stateId = stateId,
                targetIp = "TARGET-IP",
                data = "alpha clue",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertTrue(first.changed)
        assertFalse(second.changed)
        assertEquals(listOf(setOf("quests")), publisher.deltas.map { it.second.deltaKeys })
        assertEquals("alpha clue", repository.load(stateId)?.quests?.lastClueDataByIp?.get("TARGET-IP"))
    }

    @Test
    fun makeBountyDebitsCreatorAndWritesTypedStoreFile() = runTest {
        val creatorId = GameStateId("LOCAL-IP")
        val storeId = GameStateId("store1")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                creatorId to localQuestState(creatorId),
                storeId to storeState(storeId),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("creator-conn", creatorId)
        interests.register("store-conn", storeId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = MakeBountyCommand(
                creatorStateId = creatorId,
                storeStateId = storeId,
                anonymous = false,
                target = "ENEMY-IP",
                type = BountyTypes.INSTALL,
                fileName = "installer.bin",
                folder = "/Public",
                iterations = 2,
                reward = 125.0,
            ),
            metadata = CommandMetadata(connectionId = "creator-conn"),
            publisher = publisher,
        )

        val creatorState = repository.load(creatorId)
        val storeState = repository.load(storeId)

        requireNotNull(creatorState)
        requireNotNull(storeState)
        assertEquals(375.0, creatorState.economy.pettyCash)
        assertTrue(response.bountyFile.path.startsWith("/Store/"))
        assertEquals(StoredFileKind.BOUNTY, response.bountyFile.kind)
        assertEquals("installer.bin", response.bountyFile.bountyMetadata?.requiredScriptName)
        assertEquals("Rewrite", response.bountyFile.bountyMetadata?.requiredMaker)
        assertTrue(storeState.filesystem.filesByPath.containsKey(response.bountyFile.path))
        assertEquals(
            listOf(setOf("filesystem"), setOf("economy")),
            publisher.deltas.map { it.second.deltaKeys },
        )

        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = MakeBountyCommand(
                    creatorStateId = creatorId,
                    storeStateId = storeId,
                    anonymous = true,
                    target = "ENEMY-IP",
                    type = BountyTypes.INSTALL,
                    fileName = "missing.bin",
                    folder = "/Public",
                    iterations = 1,
                    reward = 25.0,
                ),
                metadata = CommandMetadata(connectionId = "creator-conn"),
                publisher = publisher,
            )
        }
    }

    @Test
    fun requestTriggerAndRequestTriggerNoteEmitTypedIntentsWithoutStateMutation() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localQuestState(stateId),
                targetId to ComputerState.empty(targetId, playerIp = targetId.value),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val indexResponse = dispatcher.request(
            command = RequestTriggerCommand(
                stateId = stateId,
                targetStateId = targetId,
                selector = TriggerSelector.ByIndex(2),
                sourceIp = stateId.value,
                triggerParameters = mapOf("mode" to StringHookValue("alpha")),
            ),
            metadata = CommandMetadata(connectionId = "conn-1", requestId = "trigger-1"),
            publisher = publisher,
        )
        val noteResponse = dispatcher.request(
            command = RequestTriggerNoteCommand(
                stateId = stateId,
                targetStateId = targetId,
                note = "quest-step",
                sourceIp = stateId.value,
                triggerParameters = mapOf("count" to IntHookValue(5)),
            ),
            metadata = CommandMetadata(connectionId = "conn-1", requestId = "trigger-2"),
            publisher = publisher,
        )

        assertTrue(indexResponse.accepted)
        assertTrue(noteResponse.accepted)
        assertEquals(null, indexResponse.matchedWatchIndex)
        assertFalse(indexResponse.executed)
        assertEquals(null, noteResponse.matchedWatchIndex)
        assertFalse(noteResponse.executed)
        assertTrue(publisher.deltas.isEmpty())
    }

    @Test
    fun launchNetworkAttackDispatchesNetbombWithResolvedDefaultPorts() = runTest {
        val playerId = GameStateId("PLAYER-IP")
        val npcId = GameStateId("NPC-IP")
        val sink = RecordingWatchTriggerIntentSink()
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                playerId to launchNetworkPlayerState(playerId),
                npcId to ComputerState.empty(id = npcId, playerIp = npcId.value, isNpc = true),
            ),
        )
        val dispatcher = DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = InMemoryInterestRegistry(),
            watchTriggerIntentSink = sink,
        )

        val response = dispatcher.request(
            command = LaunchNetworkAttackCommand(
                playerStateId = playerId,
                npcStateId = npcId,
            ),
            metadata = CommandMetadata(),
            publisher = RecordingGameStatePublisher(),
        )

        assertTrue(response.accepted)
        assertTrue(response.dispatched)
        assertEquals("launchnetworkattack-dispatched", response.message)
        assertEquals(null, response.failureCode)
        assertEquals(1, sink.intents.size)
        val intent = sink.intents.single()
        assertEquals(npcId, intent.targetStateId)
        assertEquals(TriggerSelector.ByNote("netbomb"), intent.selector)
        assertEquals(playerId.value, intent.sourceIp)
        assertEquals(
            listOf("playerip", "defaultattack", "defaultbank", "defaulthttp", "defaultredirecting"),
            intent.parameters.keys.toList(),
        )
        assertEquals(StringHookValue(playerId.value), intent.parameters.getValue("playerip"))
        assertEquals(IntHookValue(12), intent.parameters.getValue("defaultattack"))
        assertEquals(IntHookValue(6), intent.parameters.getValue("defaultbank"))
        assertEquals(IntHookValue(7), intent.parameters.getValue("defaulthttp"))
        assertEquals(IntHookValue(9), intent.parameters.getValue("defaultredirecting"))
    }

    @Test
    fun launchNetworkAttackFailsWithoutDispatchWhenPlayerOrNpcStateIsMissing() = runTest {
        val playerId = GameStateId("PLAYER-IP")
        val npcId = GameStateId("NPC-IP")
        val sink = RecordingWatchTriggerIntentSink()
        val dispatcherWithMissingNpc = DefaultCommandDispatcher(
            repository = InMemoryComputerStateRepository(
                seededStates = mapOf(playerId to launchNetworkPlayerState(playerId)),
            ),
            interestRegistry = InMemoryInterestRegistry(),
            watchTriggerIntentSink = sink,
        )
        val dispatcherWithMissingPlayer = DefaultCommandDispatcher(
            repository = InMemoryComputerStateRepository(
                seededStates = mapOf(npcId to ComputerState.empty(id = npcId, playerIp = npcId.value, isNpc = true)),
            ),
            interestRegistry = InMemoryInterestRegistry(),
            watchTriggerIntentSink = sink,
        )

        val missingNpc = dispatcherWithMissingNpc.request(
            command = LaunchNetworkAttackCommand(
                playerStateId = playerId,
                npcStateId = npcId,
            ),
            metadata = CommandMetadata(),
            publisher = RecordingGameStatePublisher(),
        )
        val missingPlayer = dispatcherWithMissingPlayer.request(
            command = LaunchNetworkAttackCommand(
                playerStateId = playerId,
                npcStateId = npcId,
            ),
            metadata = CommandMetadata(),
            publisher = RecordingGameStatePublisher(),
        )

        assertFalse(missingNpc.accepted)
        assertEquals(LaunchNetworkAttackFailureCode.NPC_STATE_NOT_FOUND, missingNpc.failureCode)
        assertFalse(missingPlayer.accepted)
        assertEquals(LaunchNetworkAttackFailureCode.PLAYER_STATE_NOT_FOUND, missingPlayer.failureCode)
        assertTrue(sink.intents.isEmpty())
    }

    private fun localQuestState(stateId: GameStateId): ComputerState {
        var filesystem = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER").filesystem
            .ensureDirectory("/Public")
            .ensureDirectory("/Store")
        filesystem = filesystem.saveFile(
            StoredFile(
                path = buildFilePath("/Public", "installer.bin"),
                name = "installer.bin",
                kind = StoredFileKind.APPLICATION_BINARY,
                contents = "install payload",
                maker = "Rewrite",
                compiledBinary = CompiledBinaryMetadata(
                    applicationKind = ApplicationKind.GENERIC,
                    outputName = "installer.bin",
                ),
            ),
        )

        return ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER").copy(
            economy = EconomyState(
                pettyCash = 500.0,
                bankMoney = 100.0,
                defaultBankPort = 6,
            ),
            filesystem = filesystem,
            ports = listOf(
                PortState(
                    number = 6,
                    type = "banking",
                    enabled = true,
                    defaultPort = true,
                    installedApplication = InstalledApplication(
                        name = "bank.bin",
                        kind = ApplicationKind.BANKING,
                        binaryPath = "/Public/bank.bin",
                        banking = true,
                    ),
                ),
            ),
            quests = QuestState(
                activeQuestsById = mapOf(
                    "quest-1" to ActiveQuestProgress(
                        questId = "quest-1",
                        label = "Starter Quest",
                    ),
                ),
                completedQuestIds = listOf("quest-done"),
            ),
        )
    }

    private fun storeState(stateId: GameStateId): ComputerState {
        return ComputerState.empty(id = stateId, playerIp = stateId.value).copy(
            filesystem = ComputerState.empty(id = stateId, playerIp = stateId.value).filesystem.ensureDirectory("/Store"),
        )
    }

    private fun hacktendoState(stateId: GameStateId): ComputerState {
        var filesystem = ComputerState.empty(id = stateId, playerIp = stateId.value).filesystem
            .ensureDirectory("/Games")
        filesystem = filesystem
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Games", "adventure"),
                    name = "adventure",
                    kind = StoredFileKind.TEXT,
                    contents = "<game>adventure</game>",
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/", "adventure.save"),
                    name = "adventure.save",
                    kind = StoredFileKind.SAVE_DATA,
                    contents = "unused\tstring\tignored\n",
                    saveMetadata = SaveFileMetadata(
                        valuesByKey = linkedMapOf(
                            "name" to StringHookValue("starter"),
                            "score" to IntHookValue(7),
                            "enabled" to BooleanHookValue(true),
                        ),
                    ),
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Games", "arcade"),
                    name = "arcade",
                    kind = StoredFileKind.TEXT,
                    contents = "<game>arcade</game>",
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/", "arcade.save"),
                    name = "arcade.save",
                    kind = StoredFileKind.TEXT,
                    contents = """
                        name	string	player
                        alive	bool	true
                        broken	row
                        score	int	7
                        ratio	float	1.5
                        badint	int	nope
                    """.trimIndent(),
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Games", "sandbox"),
                    name = "sandbox",
                    kind = StoredFileKind.TEXT,
                    contents = "<game>sandbox</game>",
                ),
            )
        return ComputerState.empty(id = stateId, playerIp = stateId.value).copy(filesystem = filesystem)
    }

    private fun launchNetworkPlayerState(stateId: GameStateId): ComputerState {
        return ComputerState.empty(id = stateId, playerIp = stateId.value).copy(
            economy = EconomyState(
                pettyCash = 250.0,
                bankMoney = 100.0,
                defaultBankPort = 6,
                defaultRedirectPort = 9,
            ),
            ports = listOf(
                applicationPort(12, ApplicationKind.ATTACK),
                applicationPort(6, ApplicationKind.BANKING),
                applicationPort(7, ApplicationKind.HTTP),
                applicationPort(9, ApplicationKind.REDIRECT),
            ),
        )
    }

    private fun applicationPort(
        number: Int,
        kind: ApplicationKind,
    ): PortState {
        return PortState(
            number = number,
            type = kind.name.lowercase(),
            enabled = true,
            defaultPort = true,
            installedApplication = InstalledApplication(
                name = "${kind.name.lowercase()}.bin",
                kind = kind,
                binaryPath = "/Public/${kind.name.lowercase()}.bin",
                banking = kind == ApplicationKind.BANKING,
            ),
        )
    }

    private class RecordingWatchTriggerIntentSink : WatchTriggerIntentSink {
        val intents = mutableListOf<WatchTriggerIntent>()

        override suspend fun emitWatchTrigger(intent: WatchTriggerIntent) {
            intents += intent
        }
    }
}
