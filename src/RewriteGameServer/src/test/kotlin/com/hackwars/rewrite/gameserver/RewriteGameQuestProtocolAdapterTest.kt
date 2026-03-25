package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ActiveQuestProgress
import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.BountyCreatedResponse
import com.hackwars.rewrite.gamecore.ClueDataPayload
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.DeltaProjection
import com.hackwars.rewrite.gamecore.EconomyState
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.HardwareState
import com.hackwars.rewrite.gamecore.HookSideEffectSink
import com.hackwars.rewrite.gamecore.InMemoryAttackProgramRegistry
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.MakeBountyPayload
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.QuestState
import com.hackwars.rewrite.gamecore.RequestSavePayload
import com.hackwars.rewrite.gamecore.RequestTaskPayload
import com.hackwars.rewrite.gamecore.RequestTriggerPayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.SaveFileRequestResponse
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.StateSectionsDeltaProjection
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.TaskProgressResponse
import com.hackwars.rewrite.gamecore.TriggerRequestResponse
import com.hackwars.rewrite.gamecore.TriggerSelector
import com.hackwars.rewrite.gamecore.WatchTriggerIntent
import com.hackwars.rewrite.gamecore.InstalledWatch
import com.hackwars.rewrite.gamecore.WatchKind
import com.hackwars.rewrite.gamecore.WatchManagerState
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.gamecore.buildFilePath
import com.hackwars.rewrite.gamecore.ensureDirectory
import com.hackwars.rewrite.gamecore.saveFile
import com.hackwars.rewrite.hackscript.BooleanHookValue
import com.hackwars.rewrite.hackscript.IntHookValue
import com.hackwars.rewrite.hackscript.StringHookValue
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.testkit.FakePlayerAccount
import com.hackwars.rewrite.testkit.FakeSessionCatalog
import com.hackwars.rewrite.testkit.FakeSessionTicketVerifier
import com.hackwars.rewrite.testkit.InMemoryAuthenticatedSession
import com.hackwars.rewrite.testkit.InMemoryClientConnection
import com.hackwars.rewrite.testkit.InMemoryRewriteServiceHarness
import com.hackwars.rewrite.testkit.RewriteServiceAdapter
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteGameQuestProtocolAdapterTest {
    @Test
    fun requestTaskReturnsCorrelatedResponseAfterQuestDelta() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.send(
            RewriteFrames.command(
                commandId = "task-1",
                commandName = "requesttask",
                payload = RewriteGameJson.encode(
                    serializer = RequestTaskPayload.serializer(),
                    value = RequestTaskPayload(
                        fileName = "intro",
                        questId = "quest-1",
                        taskName = "download",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = local.awaitFrame()
        val responseFrame = local.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = TaskProgressResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )
        val projection = RewriteGameJson.decode(
            serializer = DeltaProjection.serializer(),
            payload = delta.delta!!.payload.toByteArray(),
        )

        assertEquals(listOf("quests"), delta.delta?.delta_keys)
        assertTrue(response.changed)
        assertTrue(response.progress?.completed == true)
        assertIs<StateSectionsDeltaProjection>(projection)
    }

    @Test
    fun requestSaveReturnsCorrelatedResponseAfterFilesystemDelta() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.send(
            RewriteFrames.command(
                commandId = "save-1",
                commandName = "requestsave",
                payload = RewriteGameJson.encode(
                    serializer = RequestSavePayload.serializer(),
                    value = RequestSavePayload(
                        fileName = "quest-progress",
                        triggerParameters = linkedMapOf(
                            "name" to StringHookValue("starter"),
                            "count" to IntHookValue(2),
                            "enabled" to BooleanHookValue(true),
                        ),
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = local.awaitFrame()
        val responseFrame = local.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = SaveFileRequestResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(listOf("filesystem"), delta.delta?.delta_keys)
        assertEquals("/quest-progress.save", response.file.path)
        assertEquals(StoredFileKind.SAVE_DATA, response.file.kind)
    }

    @Test
    fun clueDataIsFireAndForgetAndOnlyEmitsChangedQuestDelta() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        local.send(
            RewriteFrames.command(
                commandId = "clue-1",
                commandName = "cluedata",
                payload = RewriteGameJson.encode(
                    serializer = ClueDataPayload.serializer(),
                    value = ClueDataPayload(
                        ip = "TARGET-IP",
                        data = "alpha clue",
                    ),
                ),
                expectsResponse = false,
            ),
        )

        val delta = target.awaitFrame()
        assertEquals(listOf("quests"), delta.delta?.delta_keys)
        assertTrue(local.drainFrames().isEmpty())

        local.send(
            RewriteFrames.command(
                commandId = "clue-2",
                commandName = "cluedata",
                payload = RewriteGameJson.encode(
                    serializer = ClueDataPayload.serializer(),
                    value = ClueDataPayload(
                        ip = "TARGET-IP",
                        data = "alpha clue",
                    ),
                ),
                expectsResponse = false,
            ),
        )

        assertTrue(target.drainFrames().isEmpty())
        assertTrue(local.drainFrames().isEmpty())
    }

    @Test
    fun makeBountyAndRequestTriggerExecuteEnabledWatchAndEmitExpectedFrames() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")
        val store = fixture.authenticatedConnection("store1")

        local.send(
            RewriteFrames.command(
                commandId = "bounty-1",
                commandName = "makebounty",
                payload = RewriteGameJson.encode(
                    serializer = MakeBountyPayload.serializer(),
                    value = MakeBountyPayload(
                        sourceIp = "LOCAL-IP",
                        anonymous = false,
                        target = "ENEMY-IP",
                        type = 2,
                        fname = "installer.bin",
                        folder = "/Public",
                        iterations = 2,
                        reward = 125.0,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val storeDelta = store.awaitFrame()
        val creatorDelta = local.awaitFrame()
        val bountyResponseFrame = local.awaitFrame()
        val bountyResponse = RewriteGameJson.decode(
            serializer = BountyCreatedResponse.serializer(),
            payload = bountyResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals("store1", storeDelta.delta?.game_state_id)
        assertEquals(listOf("filesystem"), storeDelta.delta?.delta_keys)
        assertEquals("LOCAL-IP", creatorDelta.delta?.game_state_id)
        assertEquals(listOf("economy"), creatorDelta.delta?.delta_keys)
        assertTrue(bountyResponse.bountyFile.path.startsWith("/Store/"))

        local.send(
            RewriteFrames.command(
                commandId = "trigger-1",
                commandName = "requesttrigger",
                payload = RewriteGameJson.encode(
                    serializer = RequestTriggerPayload.serializer(),
                    value = RequestTriggerPayload(
                        selector = TriggerSelector.ByNote("quest-step"),
                        triggerParameters = mapOf("mode" to StringHookValue("alpha")),
                        sourceIp = "LOCAL-IP",
                        targetIp = "TARGET-IP",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val triggerDelta = target.awaitFrame()
        val triggerResponseFrame = local.awaitFrame()
        val triggerResponse = RewriteGameJson.decode(
            serializer = TriggerRequestResponse.serializer(),
            payload = triggerResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertTrue(triggerResponse.accepted)
        assertEquals(0, triggerResponse.matchedWatchIndex)
        assertTrue(triggerResponse.executed)
        assertEquals("TARGET-IP", triggerDelta.delta?.game_state_id)
        assertEquals(listOf("logs"), triggerDelta.delta?.delta_keys)
        assertEquals(1, fixture.sink.intents.size)
        assertEquals(TriggerSelector.ByNote("quest-step"), fixture.sink.intents.single().selector)
        assertNull(store.drainFrames().firstOrNull { it.command_response != null || it.delta != null })
        assertTrue(local.drainFrames().isEmpty())
    }

    @Test
    fun requestTriggerCanStartZombieAttackWithoutANewCorrelatedZombieResponse() = runTest {
        val victimId = GameStateId("VICTIM-IP")
        val fixture = createFixture(
            targetState = watchZombieControllerState(),
            extraStates = mapOf(victimId to zombieVictimState(victimId)),
        )
        val local = fixture.authenticatedConnection("LOCAL-IP")
        val watchHost = fixture.authenticatedConnection("TARGET-IP")
        val victim = fixture.authenticatedConnection("VICTIM-IP")

        local.send(
            RewriteFrames.command(
                commandId = "trigger-zombie-1",
                commandName = "requesttrigger",
                payload = RewriteGameJson.encode(
                    serializer = RequestTriggerPayload.serializer(),
                    value = RequestTriggerPayload(
                        selector = TriggerSelector.ByIndex(0),
                        triggerParameters = emptyMap(),
                        sourceIp = "LOCAL-IP",
                        targetIp = "TARGET-IP",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val watchDelta = watchHost.awaitFrame()
        val victimDelta = victim.awaitFrame()
        val triggerResponseFrame = local.awaitFrame()
        val triggerResponse = RewriteGameJson.decode(
            serializer = TriggerRequestResponse.serializer(),
            payload = triggerResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertTrue(triggerResponse.accepted)
        assertTrue(triggerResponse.executed)
        assertEquals("TARGET-IP", watchDelta.delta?.game_state_id)
        assertTrue(
            watchDelta.delta?.delta_keys?.toSet()?.containsAll(setOf("economy", "ports", "combat", "runtime")) == true,
        )
        assertEquals("VICTIM-IP", victimDelta.delta?.game_state_id)
        assertEquals(setOf("combat"), victimDelta.delta?.delta_keys?.toSet())
        assertNull(watchHost.drainFrames().firstOrNull { it.command_response != null })
        assertNull(victim.drainFrames().firstOrNull { it.command_response != null })
    }

    private fun TestScope.createFixture(
        localState: ComputerState = localState(),
        targetState: ComputerState = targetState(),
        extraStates: Map<GameStateId, ComputerState> = emptyMap(),
    ): Fixture {
        val seededStates = linkedMapOf(
            GameStateId("LOCAL-IP") to localState,
            GameStateId("TARGET-IP") to targetState,
            GameStateId("store1") to storeState(),
        ).apply {
            putAll(extraStates)
        }
        val repository = InMemoryComputerStateRepository(seededStates = seededStates)
        val interests = InMemoryInterestRegistry()
        val sink = RecordingHookSideEffectSink()
        val registry = InMemoryAttackProgramRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
                attackProgramRegistry = registry,
                watchTriggerIntentSink = sink,
            ),
            interestRegistry = interests,
            serverId = "1",
            attackProgramRegistry = registry,
            hookSideEffectSink = sink,
            networkDirectoryRepository = InMemoryNetworkDirectoryRepository.defaultWorld("1"),
        )
        val harnessAdapter = HarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = seededStates.values.map { state ->
                        FakePlayerAccount(
                            playFabId = state.identity.playFabId.ifBlank { "PF-${state.id.value}" },
                            playerIp = state.id.value,
                            sessionTicket = "SESSION-${state.id.value}",
                        )
                    },
                ),
                clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
            ),
            scope = backgroundScope,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = 5.seconds,
                idleTimeout = 45.seconds,
            ),
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )
        harnessAdapter.attachHarness(harness)
        return Fixture(
            harness = harness,
            sink = sink,
            sessionTicketsByIp = seededStates.values.associate { state ->
                state.id.value to "SESSION-${state.id.value}"
            },
            playFabIdsByIp = seededStates.values.associate { state ->
                state.id.value to state.identity.playFabId.ifBlank { "PF-${state.id.value}" }
            },
        )
    }

    private suspend fun Fixture.authenticatedConnection(requestedIp: String): InMemoryClientConnection {
        val connection = harness.connect()
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = sessionTicketsByIp[requestedIp] ?: error("No session ticket for $requestedIp"),
                clientBuild = "rewrite-it",
                playFabIdHint = playFabIdsByIp[requestedIp] ?: error("No PlayFab id for $requestedIp"),
                requestedIp = requestedIp,
            ),
        )
        connection.awaitFrame()
        connection.awaitFrame()
        return connection
    }

    private fun localState(): ComputerState {
        var filesystem = ComputerState.empty(
            id = GameStateId("LOCAL-IP"),
            playFabId = "PF-LOCALUSER",
        ).filesystem
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
        return ComputerState.empty(
            id = GameStateId("LOCAL-IP"),
            playFabId = "PF-LOCALUSER",
        ).copy(
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
            ),
        )
    }

    private fun targetState(): ComputerState {
        return ComputerState.empty(GameStateId("TARGET-IP"), playFabId = "PF-TARGETUSER").copy(
            quests = QuestState(),
            watches = WatchManagerState(
                watches = listOf(
                    InstalledWatch(
                        kind = WatchKind.PETTY_CASH,
                        enabled = true,
                        note = "quest-step",
                        cpuCost = 5.0,
                        quantityThreshold = 0.0,
                        baselineQuantity = 0.0,
                        installPort = 6,
                        searchFirewallType = 0,
                        observedPorts = listOf(6),
                        contents = """
                            int main() {
                                logMessage(getTriggerParameter("mode"));
                                return 0;
                            }
                        """.trimIndent(),
                        compiledBinary = CompiledBinaryMetadata(
                            scriptFamily = ScriptFamily.WATCH,
                            applicationKind = ApplicationKind.WATCH,
                            outputName = "quest-watch.bin",
                        ),
                    ),
                ),
            ),
        )
    }

    private fun watchZombieControllerState(): ComputerState {
        return ComputerState.empty(GameStateId("TARGET-IP"), playFabId = "PF-TARGETUSER").copy(
            hardware = HardwareState(cpuMax = 100.0),
            economy = EconomyState(
                pettyCash = 60.0,
                bankMoney = 10.0,
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
                        binaryPath = "/Public/bank.bin",
                        banking = true,
                    ),
                ),
                PortState(
                    number = 12,
                    type = "attack",
                    enabled = true,
                    defaultPort = false,
                    installedApplication = InstalledApplication(
                        name = "attack.bin",
                        kind = ApplicationKind.ATTACK,
                        binaryPath = "/Public/attack.bin",
                        scriptBundle = com.hackwars.rewrite.gamecore.ProgramScriptBundle(
                            family = ScriptFamily.ATTACK,
                            scriptsBySlot = linkedMapOf(
                                com.hackwars.rewrite.gamecore.ProgramScriptSlot.INITIALIZE to """
                                    int main() {
                                        zombie("TARGET-IP");
                                        return 0;
                                    }
                                """.trimIndent(),
                            ),
                        ),
                    ),
                ),
            ),
            watches = WatchManagerState(
                watches = listOf(
                    InstalledWatch(
                        kind = WatchKind.PETTY_CASH,
                        enabled = true,
                        note = "zombie-step",
                        cpuCost = 5.0,
                        quantityThreshold = 0.0,
                        baselineQuantity = 0.0,
                        installPort = 6,
                        searchFirewallType = 0,
                        observedPorts = listOf(6),
                        contents = """
                            int main() {
                                zombieAttack("LOCAL-IP", 12, "VICTIM-IP", 25);
                                return 0;
                            }
                        """.trimIndent(),
                        compiledBinary = CompiledBinaryMetadata(
                            scriptFamily = ScriptFamily.WATCH,
                            applicationKind = ApplicationKind.WATCH,
                            outputName = "zombie-watch.bin",
                        ),
                    ),
                ),
            ),
        )
    }

    private fun zombieVictimState(stateId: GameStateId): ComputerState {
        return ComputerState.empty(stateId, playFabId = "PF-${stateId.value}").copy(
            ports = listOf(
                PortState(
                    number = 25,
                    type = "http",
                    enabled = true,
                    installedApplication = InstalledApplication(
                        name = "victim-http.bin",
                        kind = ApplicationKind.HTTP,
                        binaryPath = "/Public/http.bin",
                    ),
                ),
            ),
        )
    }

    private fun storeState(): ComputerState {
        return ComputerState.empty(GameStateId("store1"), playFabId = "PF-STOREUSER").copy(
            filesystem = ComputerState.empty(GameStateId("store1"), playerIp = "store1").filesystem.ensureDirectory("/Store"),
        )
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
        val sink: RecordingHookSideEffectSink,
        val sessionTicketsByIp: Map<String, String>,
        val playFabIdsByIp: Map<String, String>,
    )

    private class RecordingHookSideEffectSink : HookSideEffectSink {
        val intents = mutableListOf<WatchTriggerIntent>()

        override suspend fun emitWatchTrigger(intent: WatchTriggerIntent) {
            intents += intent
        }
    }

    private class HarnessBackedGameAdapter(
        private val adapter: RewriteGameProtocolAdapter,
    ) : RewriteServiceAdapter {
        override val service = RewriteService.GAME

        private lateinit var harness: InMemoryRewriteServiceHarness

        fun attachHarness(harness: InMemoryRewriteServiceHarness) {
            this.harness = harness
        }

        override suspend fun onSessionStarted(session: InMemoryAuthenticatedSession): List<FrameEnvelope> {
            return adapter.onSessionStarted(
                session = session.toQuestGameSession(),
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }

        override suspend fun onCommand(
            session: InMemoryAuthenticatedSession,
            command: hackwars.rewrite.v1.CommandEnvelope,
        ): List<FrameEnvelope> {
            return adapter.onCommand(
                session = session.toQuestGameSession(),
                command = command,
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }
    }
}

private fun InMemoryAuthenticatedSession.toQuestGameSession(): AuthenticatedGameSession {
    return AuthenticatedGameSession(
        connectionId = connectionId,
        playFabId = verifiedSession.playFabId,
        playerIp = verifiedSession.playerIp,
    )
}
