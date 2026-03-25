package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.CompileFilePayload
import com.hackwars.rewrite.gamecore.CompileFileResponse
import com.hackwars.rewrite.gamecore.DeltaProjection
import com.hackwars.rewrite.gamecore.DecompileFilePayload
import com.hackwars.rewrite.gamecore.DecompileFileResponse
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.InstalledFirewall
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.RequestDirectoryPayload
import com.hackwars.rewrite.gamecore.RequestFilePayload
import com.hackwars.rewrite.gamecore.RequestSecondaryDirectoryPayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.SaveFilePayload
import com.hackwars.rewrite.gamecore.SecondaryDirectoryListingResponse
import com.hackwars.rewrite.gamecore.StateSectionsDeltaProjection
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.FirewallKind
import com.hackwars.rewrite.gamecore.DirectoryListingResponse
import com.hackwars.rewrite.gamecore.FileContentsResponse
import com.hackwars.rewrite.gamecore.InstallApplicationPayload
import com.hackwars.rewrite.gamecore.InstallApplicationResponse
import com.hackwars.rewrite.gamecore.InstallFirewallPayload
import com.hackwars.rewrite.gamecore.InstallFirewallResponse
import com.hackwars.rewrite.gamecore.CreateFolderPayload
import com.hackwars.rewrite.gamecore.DeleteFilePayload
import com.hackwars.rewrite.gamecore.DeleteMultiPayload
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.gamecore.buildFilePath
import com.hackwars.rewrite.gamecore.saveFile
import com.hackwars.rewrite.gamecore.ensureDirectory
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.testkit.FakeSessionTicketVerifier
import com.hackwars.rewrite.testkit.InMemoryAuthenticatedSession
import com.hackwars.rewrite.testkit.InMemoryClientConnection
import com.hackwars.rewrite.testkit.InMemoryRewriteServiceHarness
import com.hackwars.rewrite.testkit.RewriteServiceAdapter
import hackwars.rewrite.v1.CommandEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteGameProtocolAdapterTest {
    @Test
    fun authSuccessProducesExactlyOneBootstrapSnapshot() = runTest {
        val fixture = createFixture()
        val connection = fixture.harness.connect()

        connection.send(authRequest())

        val auth = connection.awaitFrame()
        val snapshot = connection.awaitFrame()

        assertNotNull(auth.auth_response?.accepted)
        assertEquals("LOCAL-IP", snapshot.snapshot?.game_state_id)
        val state = RewriteGameJson.decode(
            serializer = ComputerState.serializer(),
            payload = snapshot.snapshot!!.payload.toByteArray(),
        )
        assertEquals(GameStateId("LOCAL-IP"), state.id)
        assertFalse(connection.drainFrames().any { it.snapshot != null })
    }

    @Test
    fun requestDirectoryReturnsOneTypedResponseWithoutDeltas() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()

        connection.send(
            RewriteFrames.command(
                commandId = "dir-1",
                commandName = "requestdirectory",
                payload = RewriteGameJson.encode(
                    serializer = RequestDirectoryPayload.serializer(),
                    value = RequestDirectoryPayload("/Public"),
                ),
                expectsResponse = true,
            ),
        )

        val response = connection.awaitFrame()
        val directory = RewriteGameJson.decode(
            serializer = DirectoryListingResponse.serializer(),
            payload = response.command_response!!.payload.toByteArray(),
        )

        assertEquals("dir-1", response.command_response?.command_id)
        assertEquals("/Public", directory.path)
        assertEquals(listOf("bank", "bank.bin", "notes.txt", "wall.bin"), directory.files.map { it.name }.sorted())
        assertFalse(connection.drainFrames().any { it.delta != null })
    }

    @Test
    fun requestSecondaryDirectoryStillSucceedsAgainstFrozenTargetPortWithoutSubscriptions() = runTest {
        val fixture = createFixture(
            targetState = targetState(freezeExpiresAtEpochMillis = Long.MAX_VALUE),
        )
        val connection = fixture.authenticatedConnection()

        connection.send(
            RewriteFrames.command(
                commandId = "secondary-1",
                commandName = "requestsecondarydirectory",
                payload = RewriteGameJson.encode(
                    serializer = RequestSecondaryDirectoryPayload.serializer(),
                    value = RequestSecondaryDirectoryPayload(
                        path = "/Secrets",
                        targetIp = "TARGET-IP",
                        port = 17,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val response = connection.awaitFrame()
        val directory = RewriteGameJson.decode(
            serializer = SecondaryDirectoryListingResponse.serializer(),
            payload = response.command_response!!.payload.toByteArray(),
        )

        assertEquals("TARGET-IP", directory.targetStateId.value)
        assertEquals(17, directory.portNumber)
        assertEquals(listOf("remote.log"), directory.files.map { it.name })
        assertFalse(connection.drainFrames().any { it.delta != null })
    }

    @Test
    fun requestFileReturnsOneTypedResponseWithoutDeltas() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()

        connection.send(
            RewriteFrames.command(
                commandId = "file-1",
                commandName = "requestfile",
                payload = RewriteGameJson.encode(
                    serializer = RequestFilePayload.serializer(),
                    value = RequestFilePayload(
                        path = "/Public",
                        name = "notes.txt",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val response = connection.awaitFrame()
        val file = RewriteGameJson.decode(
            serializer = FileContentsResponse.serializer(),
            payload = response.command_response!!.payload.toByteArray(),
        )

        assertEquals("notes.txt", file.file?.name)
        assertEquals("hello world", file.file?.contents)
        assertFalse(connection.drainFrames().any { it.delta != null })
    }

    @Test
    fun saveCreateDeleteAndDeleteMultiFanOutFilesystemDeltas() = runTest {
        val fixture = createFixture()
        val primary = fixture.authenticatedConnection()
        val secondary = fixture.authenticatedConnection()

        primary.send(
            RewriteFrames.command(
                commandId = "folder-1",
                commandName = "createfolder",
                payload = RewriteGameJson.encode(
                    serializer = CreateFolderPayload.serializer(),
                    value = CreateFolderPayload(path = "/Public", name = "Archive"),
                ),
                expectsResponse = true,
            ),
        )
        val createDeltaPrimary = primary.awaitFrame()
        val createDeltaSecondary = secondary.awaitFrame()
        primary.awaitFrame()

        assertEquals(listOf("filesystem"), createDeltaPrimary.delta?.delta_keys)
        assertEquals(createDeltaPrimary.delta, createDeltaSecondary.delta)

        primary.send(
            RewriteFrames.command(
                commandId = "save-1",
                commandName = "savefile",
                payload = RewriteGameJson.encode(
                    serializer = SaveFilePayload.serializer(),
                    value = SaveFilePayload(
                        path = "/Public/Archive",
                        file = StoredFile(
                            path = buildFilePath("/Public/Archive", "saved.txt"),
                            name = "saved.txt",
                            kind = StoredFileKind.TEXT,
                            contents = "saved payload",
                        ),
                    ),
                ),
                expectsResponse = true,
            ),
        )
        val saveDeltaPrimary = primary.awaitFrame()
        val saveDeltaSecondary = secondary.awaitFrame()
        primary.awaitFrame()
        assertEquals(listOf("filesystem"), saveDeltaPrimary.delta?.delta_keys)
        assertEquals(saveDeltaPrimary.delta, saveDeltaSecondary.delta)

        primary.send(
            RewriteFrames.command(
                commandId = "delete-1",
                commandName = "deletefile",
                payload = RewriteGameJson.encode(
                    serializer = DeleteFilePayload.serializer(),
                    value = DeleteFilePayload(path = "/Public/Archive", name = "saved.txt"),
                ),
                expectsResponse = true,
            ),
        )
        val deleteDeltaPrimary = primary.awaitFrame()
        val deleteDeltaSecondary = secondary.awaitFrame()
        primary.awaitFrame()
        assertEquals(listOf("filesystem"), deleteDeltaPrimary.delta?.delta_keys)
        assertEquals(deleteDeltaPrimary.delta, deleteDeltaSecondary.delta)

        primary.send(
            RewriteFrames.command(
                commandId = "delete-multi-1",
                commandName = "deletemulti",
                payload = RewriteGameJson.encode(
                    serializer = DeleteMultiPayload.serializer(),
                    value = DeleteMultiPayload(
                        path = "/Public",
                        fileNames = listOf("notes.txt"),
                        directoryNames = listOf("Archive"),
                    ),
                ),
                expectsResponse = true,
            ),
        )
        val multiDeltaPrimary = primary.awaitFrame()
        val multiDeltaSecondary = secondary.awaitFrame()
        primary.awaitFrame()
        assertEquals(listOf("filesystem"), multiDeltaPrimary.delta?.delta_keys)
        assertEquals(multiDeltaPrimary.delta, multiDeltaSecondary.delta)
    }

    @Test
    fun compileAndDecompilePublishFilesystemEconomyAndStatsDeltas() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()

        connection.send(
            RewriteFrames.command(
                commandId = "compile-1",
                commandName = "compilefile",
                payload = RewriteGameJson.encode(
                    serializer = CompileFilePayload.serializer(),
                    value = CompileFilePayload(path = "/Public", name = "bank"),
                ),
                expectsResponse = true,
            ),
        )

        val compileDelta = connection.awaitFrame()
        val compileResponseFrame = connection.awaitFrame()
        val compileResponse = RewriteGameJson.decode(
            serializer = CompileFileResponse.serializer(),
            payload = compileResponseFrame.command_response!!.payload.toByteArray(),
        )
        val compileProjection = RewriteGameJson.decode(
            serializer = DeltaProjection.serializer(),
            payload = compileDelta.delta!!.payload.toByteArray(),
        )

        assertEquals(listOf("filesystem", "economy", "stats"), compileDelta.delta?.delta_keys)
        assertIs<StateSectionsDeltaProjection>(compileProjection)
        assertEquals(425.0, compileResponse.pettyCashAfter)
        assertEquals(4.0, compileResponse.experienceAfter)
        assertEquals("bank.bin", compileResponse.compiledFile.name)

        connection.send(
            RewriteFrames.command(
                commandId = "decompile-1",
                commandName = "decompilefile",
                payload = RewriteGameJson.encode(
                    serializer = DecompileFilePayload.serializer(),
                    value = DecompileFilePayload(path = "/Public", name = "bank.bin"),
                ),
                expectsResponse = true,
            ),
        )

        val decompileDelta = connection.awaitFrame()
        val decompileResponseFrame = connection.awaitFrame()
        val decompileResponse = RewriteGameJson.decode(
            serializer = DecompileFileResponse.serializer(),
            payload = decompileResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(listOf("filesystem", "economy", "stats"), decompileDelta.delta?.delta_keys)
        assertEquals(500.0, decompileResponse.pettyCashAfter)
        assertEquals(0.0, decompileResponse.experienceAfter)
        assertEquals("bank", decompileResponse.decompiledFile.name)
    }

    @Test
    fun installApplicationCreatesDefaultBankPortAndPublishesTargetedDelta() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()

        connection.send(
            RewriteFrames.command(
                commandId = "install-app-1",
                commandName = "installapplication",
                payload = RewriteGameJson.encode(
                    serializer = InstallApplicationPayload.serializer(),
                    value = InstallApplicationPayload(
                        path = "/Public",
                        name = "bank.bin",
                        portNumber = 6,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = connection.awaitFrame()
        val responseFrame = connection.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = InstallApplicationResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )
        val projection = RewriteGameJson.decode(
            serializer = DeltaProjection.serializer(),
            payload = delta.delta!!.payload.toByteArray(),
        )

        assertEquals(listOf("filesystem", "ports", "economy"), delta.delta?.delta_keys)
        assertIs<StateSectionsDeltaProjection>(projection)
        assertEquals(6, response.defaultBankPort)
        assertEquals(ApplicationKind.BANKING, response.installedApplication.kind)
        assertEquals("bank.bin", response.installedApplication.name)
    }

    @Test
    fun installFirewallReturnsReplacedFirewallToDiskAndPublishesDelta() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()

        connection.send(
            RewriteFrames.command(
                commandId = "install-firewall-1",
                commandName = "installfirewall",
                payload = RewriteGameJson.encode(
                    serializer = InstallFirewallPayload.serializer(),
                    value = InstallFirewallPayload(
                        path = "/Public",
                        name = "wall.bin",
                        portNumber = 19,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = connection.awaitFrame()
        val responseFrame = connection.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = InstallFirewallResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(listOf("filesystem", "ports"), delta.delta?.delta_keys)
        assertEquals("wall.bin", response.installedFirewall.name)
        assertEquals("OldWall.bin", response.returnedFirewall?.name)
        assertEquals("/firewalls/OldWall.bin", response.returnedFirewall?.path)
    }

    private fun TestScope.createFixture(
        localState: ComputerState = localState(),
        targetState: ComputerState = targetState(),
    ): Fixture {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                GameStateId("LOCAL-IP") to localState,
                GameStateId("TARGET-IP") to targetState,
            ),
        )
        val interests = InMemoryInterestRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
            ),
            interestRegistry = interests,
            networkDirectoryRepository = InMemoryNetworkDirectoryRepository.defaultWorld("1"),
        )
        val harnessAdapter = HarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
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
            repository = repository,
        )
    }

    private fun localState(): ComputerState {
        var filesystem = ComputerState.empty(
            id = GameStateId("LOCAL-IP"),
            playFabId = "PF-LOCALUSER",
        ).filesystem
            .withCurrentPath("/Public")
            .ensureDirectory("/Public")
            .ensureDirectory("/Docs")
        filesystem = filesystem
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "notes.txt"),
                    name = "notes.txt",
                    kind = StoredFileKind.TEXT,
                    contents = "hello world",
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "bank"),
                    name = "bank",
                    kind = StoredFileKind.SCRIPT_SOURCE,
                    contents = "bank source",
                    compileCost = 75.0,
                    compiledBinary = CompiledBinaryMetadata(
                        applicationKind = ApplicationKind.BANKING,
                        bankingApplication = true,
                        outputName = "bank.bin",
                        experienceAward = 4.0,
                    ),
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "bank.bin"),
                    name = "bank.bin",
                    kind = StoredFileKind.APPLICATION_BINARY,
                    contents = "bank source",
                    quantity = 1,
                    compiledBinary = CompiledBinaryMetadata(
                        applicationKind = ApplicationKind.BANKING,
                        bankingApplication = true,
                        outputName = "bank.bin",
                        experienceAward = 4.0,
                    ),
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "wall.bin"),
                    name = "wall.bin",
                    kind = StoredFileKind.FIREWALL_BINARY,
                    contents = "firewall source",
                    quantity = 1,
                    compiledBinary = CompiledBinaryMetadata(
                        firewallKind = FirewallKind.CUSTOM,
                        outputName = "wall.bin",
                        strength = 9,
                    ),
                ),
            )

        return ComputerState.empty(
            id = GameStateId("LOCAL-IP"),
            playFabId = "PF-LOCALUSER",
        ).copy(
            economy = ComputerState.empty(GameStateId("LOCAL-IP")).economy.copy(pettyCash = 500.0),
            filesystem = filesystem,
            ports = listOf(
                PortState(number = 19, type = "ssh", installedFirewall = InstalledFirewall(name = "OldWall")),
                PortState(number = 22, type = "ssh"),
                PortState(number = 80, type = "http"),
                PortState(number = 443, type = "https"),
            ),
        )
    }

    private fun targetState(): ComputerState {
        return targetState(freezeExpiresAtEpochMillis = null)
    }

    private fun targetState(
        freezeExpiresAtEpochMillis: Long?,
    ): ComputerState {
        var filesystem = ComputerState.empty(
            id = GameStateId("TARGET-IP"),
            playerIp = "TARGET-IP",
        ).filesystem
            .ensureDirectory("/Secrets")
        filesystem = filesystem.saveFile(
            StoredFile(
                path = buildFilePath("/Secrets", "remote.log"),
                name = "remote.log",
                kind = StoredFileKind.TEXT,
                contents = "target remote file",
            ),
        )
        return ComputerState.empty(GameStateId("TARGET-IP"), playerIp = "TARGET-IP").copy(
            filesystem = filesystem,
            ports = listOf(
                PortState(number = 17, type = "ftp", freezeExpiresAtEpochMillis = freezeExpiresAtEpochMillis),
                PortState(number = 22, type = "ssh"),
                PortState(number = 80, type = "http"),
                PortState(number = 443, type = "https"),
            ),
        )
    }

    private suspend fun Fixture.authenticatedConnection(): InMemoryClientConnection {
        val connection = harness.connect()
        connection.send(authRequest())
        connection.awaitFrame()
        connection.awaitFrame()
        return connection
    }

    private fun authRequest(): FrameEnvelope {
        return RewriteFrames.authRequest(
            service = RewriteService.GAME,
            sessionTicket = "SESSION-LOCALUSER",
            clientBuild = "rewrite-it",
            playFabIdHint = "PF-LOCALUSER",
            requestedIp = "LOCAL-IP",
        )
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
        val repository: InMemoryComputerStateRepository,
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
                session = session.toGameSession(),
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }

        override suspend fun onCommand(
            session: InMemoryAuthenticatedSession,
            command: CommandEnvelope,
        ): List<FrameEnvelope> {
            return adapter.onCommand(
                session = session.toGameSession(),
                command = command,
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }

        override suspend fun onSessionEnded(session: InMemoryAuthenticatedSession) {
            adapter.onSessionEnded(session.toGameSession())
        }
    }
}

private fun InMemoryAuthenticatedSession.toGameSession(): AuthenticatedGameSession {
    return AuthenticatedGameSession(
        connectionId = connectionId,
        playFabId = verifiedSession.playFabId,
        playerIp = verifiedSession.playerIp,
    )
}
