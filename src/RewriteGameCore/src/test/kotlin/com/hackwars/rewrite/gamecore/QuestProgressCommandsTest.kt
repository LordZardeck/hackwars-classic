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
        val sink = RecordingWatchTriggerIntentSink()

        val indexResponse = dispatcher.request(
            command = RequestTriggerCommand(
                stateId = stateId,
                targetStateId = targetId,
                selector = TriggerSelector.ByIndex(2),
                sourceIp = stateId.value,
                triggerParameters = mapOf("mode" to StringHookValue("alpha")),
                watchTriggerIntentSink = sink,
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
                watchTriggerIntentSink = sink,
            ),
            metadata = CommandMetadata(connectionId = "conn-1", requestId = "trigger-2"),
            publisher = publisher,
        )

        assertTrue(indexResponse.accepted)
        assertTrue(noteResponse.accepted)
        assertEquals(2, sink.intents.size)
        assertEquals(TriggerSelector.ByIndex(2), sink.intents[0].selector)
        assertEquals(TriggerSelector.ByNote("quest-step"), sink.intents[1].selector)
        assertEquals("LOCAL-IP", sink.intents[0].sourceIp)
        assertEquals(mapOf("mode" to StringHookValue("alpha")), sink.intents[0].parameters)
        assertEquals(mapOf("count" to IntHookValue(5)), sink.intents[1].parameters)
        assertTrue(publisher.deltas.isEmpty())
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

    private class RecordingWatchTriggerIntentSink : WatchTriggerIntentSink {
        val intents = mutableListOf<WatchTriggerIntent>()

        override suspend fun emitWatchTrigger(intent: WatchTriggerIntent) {
            intents += intent
        }
    }
}
