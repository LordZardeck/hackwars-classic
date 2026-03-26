package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.InstalledWatch
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.WatchKind
import com.hackwars.rewrite.gamecore.WatchListResponse
import com.hackwars.rewrite.gamecore.WatchManagerState
import com.hackwars.rewrite.gamecore.WatchMutationResponse
import com.hackwars.rewrite.gamecore.buildFilePath
import com.hackwars.rewrite.gamecore.ensureDirectory
import com.hackwars.rewrite.gamecore.saveFile
import com.hackwars.rewrite.gamecore.ChangeWatchPortPayload
import com.hackwars.rewrite.gamecore.DeleteWatchPayload
import com.hackwars.rewrite.gamecore.FetchWatchesPayload
import com.hackwars.rewrite.gamecore.InstallWatchPayload
import com.hackwars.rewrite.gamecore.SetWatchOnOffPayload
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
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteGameWatchProtocolAdapterTest {
    @Test
    fun bootstrapSnapshotIncludesWatchState() = runTest {
        val fixture = createFixture()

        val authenticated = fixture.authenticatedConnection("LOCAL-IP")

        assertEquals(1, authenticated.bootstrap.watches.watches.size)
        assertFalse(authenticated.bootstrap.watches.watches.single().enabled)
    }

    @Test
    fun fetchWatchesReturnsOneTypedResponseWithoutDeltas() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.connection.send(
            RewriteFrames.command(
                commandId = "watch-fetch-1",
                commandName = "fetchwatches",
                payload = RewriteGameJson.encode(
                    serializer = FetchWatchesPayload.serializer(),
                    value = FetchWatchesPayload(ip = "LOCAL-IP"),
                ),
                expectsResponse = true,
            ),
        )

        val responseFrame = local.connection.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = WatchListResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(1, response.installedCount)
        assertTrue(local.connection.drainFrames().isEmpty())
    }

    @Test
    fun installWatchPublishesFilesystemAndWatchesDeltaBeforeResponse() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.connection.send(
            RewriteFrames.command(
                commandId = "watch-install-1",
                commandName = "installwatch",
                payload = RewriteGameJson.encode(
                    serializer = InstallWatchPayload.serializer(),
                    value = InstallWatchPayload(
                        ip = "LOCAL-IP",
                        path = "/Public",
                        name = "watch.bin",
                        type = WatchKind.PETTY_CASH.legacyCode,
                        port = 6,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = local.connection.awaitFrame()
        val responseFrame = local.connection.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = WatchMutationResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(listOf("filesystem", "watches"), delta.delta?.delta_keys)
        assertTrue(response.accepted)
        assertEquals(1, response.affectedWatchIndex)
        assertEquals(2, response.snapshot.installedCount)
    }

    @Test
    fun setWatchOnOffPublishesWatchesAndRuntimeDeltaBeforeResponse() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.connection.send(
            RewriteFrames.command(
                commandId = "watch-enable-1",
                commandName = "setwatchonoff",
                payload = RewriteGameJson.encode(
                    serializer = SetWatchOnOffPayload.serializer(),
                    value = SetWatchOnOffPayload(
                        ip = "LOCAL-IP",
                        watchId = 0,
                        state = true,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = local.connection.awaitFrame()
        val responseFrame = local.connection.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = WatchMutationResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(listOf("watches", "runtime"), delta.delta?.delta_keys)
        assertTrue(response.accepted)
        assertTrue(response.snapshot.watches.single().enabled)
        assertEquals(5.0, response.snapshot.currentCpuLoad)
    }

    @Test
    fun deleteEnabledWatchPublishesWatchesAndRuntimeDeltaBeforeResponse() = runTest {
        val fixture = createFixture(
            localState = localState(
                installedWatches = listOf(seededWatch(enabled = true, note = "armed")),
                currentCpuLoad = 5.0,
            ),
        )
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.connection.send(
            RewriteFrames.command(
                commandId = "watch-delete-1",
                commandName = "deletewatch",
                payload = RewriteGameJson.encode(
                    serializer = DeleteWatchPayload.serializer(),
                    value = DeleteWatchPayload(
                        ip = "LOCAL-IP",
                        watchId = 0,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = local.connection.awaitFrame()
        val responseFrame = local.connection.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = WatchMutationResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(listOf("watches", "runtime"), delta.delta?.delta_keys)
        assertTrue(response.accepted)
        assertEquals(0, response.snapshot.installedCount)
        assertEquals(0.0, response.snapshot.currentCpuLoad)
    }

    private fun TestScope.createFixture(localState: ComputerState = localState()): Fixture {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                GameStateId("LOCAL-IP") to localState,
            ),
        )
        val interests = InMemoryInterestRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
            ),
            combatMaintenanceProgramRegistry = DisabledCombatMaintenanceProgramRegistry,
            interestRegistry = interests,
            serverId = "1",
            networkDirectoryRepository = InMemoryNetworkDirectoryRepository.defaultWorld("1"),
        )
        val harnessAdapter = HarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(
                        FakePlayerAccount("PF-LOCALUSER", "LOCAL-IP", "SESSION-LOCALUSER"),
                    ),
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
        return Fixture(harness)
    }

    private suspend fun Fixture.authenticatedConnection(requestedIp: String): AuthenticatedConnection {
        val connection = harness.connect()
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-watch-it",
                playFabIdHint = "PF-LOCALUSER",
                requestedIp = requestedIp,
            ),
        )
        connection.awaitFrame()
        val snapshotFrame = connection.awaitFrame()
        yield()
        connection.drainFrames()
        val snapshot = RewriteGameJson.decode(
            serializer = ComputerState.serializer(),
            payload = snapshotFrame.snapshot!!.payload.toByteArray(),
        )
        return AuthenticatedConnection(connection = connection, bootstrap = snapshot)
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
    )

    private data class AuthenticatedConnection(
        val connection: InMemoryClientConnection,
        val bootstrap: ComputerState,
    )

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
                session = session.toWatchGameSession(),
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
                session = session.toWatchGameSession(),
                command = command,
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }
    }
}

private fun localState(
    installedWatches: List<InstalledWatch> = listOf(seededWatch(enabled = false, note = "seeded")),
    currentCpuLoad: Double = 0.0,
): ComputerState {
    val stateId = GameStateId("LOCAL-IP")
    val filesystem = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER").filesystem
        .ensureDirectory("/Public")
        .saveFile(
            StoredFile(
                path = buildFilePath("/Public", "watch.bin"),
                name = "watch.bin",
                kind = StoredFileKind.APPLICATION_BINARY,
                contents = "watch script",
                maker = "Rewrite",
                cpuCost = 5.0,
                compiledBinary = CompiledBinaryMetadata(
                    scriptFamily = ScriptFamily.WATCH,
                    applicationKind = ApplicationKind.WATCH,
                    outputName = "watch.bin",
                ),
            ),
        )

    return ComputerState.empty(
        id = stateId,
        playFabId = "PF-LOCALUSER",
    ).copy(
        hardware = ComputerState.empty(id = stateId).hardware.copy(cpuMax = 100.0, memoryType = 0),
        ports = listOf(
            PortState(number = 6, type = "banking", enabled = true, defaultPort = true),
            PortState(number = 21, type = "ftp", enabled = true),
            PortState(number = 80, type = "http", enabled = true),
        ),
        filesystem = filesystem,
        watches = WatchManagerState(watches = installedWatches),
        runtime = ComputerState.empty(id = stateId).runtime.copy(currentCpuLoad = currentCpuLoad),
    )
}

private fun seededWatch(
    enabled: Boolean,
    note: String,
): InstalledWatch {
    return InstalledWatch(
        kind = WatchKind.PETTY_CASH,
        enabled = enabled,
        note = note,
        cpuCost = 5.0,
        quantityThreshold = 0.0,
        baselineQuantity = 250.0,
        installPort = 6,
        searchFirewallType = 0,
        observedPorts = listOf(6),
        contents = "watch script",
        compiledBinary = CompiledBinaryMetadata(
            scriptFamily = ScriptFamily.WATCH,
            applicationKind = ApplicationKind.WATCH,
            outputName = "watch.bin",
        ),
    )
}

private fun InMemoryAuthenticatedSession.toWatchGameSession(): AuthenticatedGameSession {
    return AuthenticatedGameSession(
        connectionId = connectionId,
        playFabId = verifiedSession.playFabId,
        playerIp = verifiedSession.playerIp,
    )
}
