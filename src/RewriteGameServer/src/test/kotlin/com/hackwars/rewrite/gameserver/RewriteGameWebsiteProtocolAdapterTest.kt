package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.DeltaProjection
import com.hackwars.rewrite.gamecore.EconomyState
import com.hackwars.rewrite.gamecore.ExitWebpagePayload
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.PageEditorResponse
import com.hackwars.rewrite.gamecore.PlayerStatsState
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.RequestPagePayload
import com.hackwars.rewrite.gamecore.RequestWebpagePayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.SavePagePayload
import com.hackwars.rewrite.gamecore.SavePageResponse
import com.hackwars.rewrite.gamecore.StateSectionsDeltaProjection
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.SubmitWebpagePayload
import com.hackwars.rewrite.gamecore.VotePayload
import com.hackwars.rewrite.gamecore.VoteResponse
import com.hackwars.rewrite.gamecore.WebsiteRenderResponse
import com.hackwars.rewrite.gamecore.WebsiteState
import com.hackwars.rewrite.gamecore.buildFilePath
import com.hackwars.rewrite.gamecore.ensureDirectory
import com.hackwars.rewrite.gamecore.saveFile
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteGameWebsiteProtocolAdapterTest {
    @Test
    fun requestPageReturnsOneTypedResponseWithoutDeltas() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.send(
            RewriteFrames.command(
                commandId = "page-1",
                commandName = "requestpage",
                payload = RewriteGameJson.encode(
                    serializer = RequestPagePayload.serializer(),
                    value = RequestPagePayload(ip = "LOCAL-IP"),
                ),
                expectsResponse = true,
            ),
        )

        val responseFrame = local.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = PageEditorResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals("Home Page", response.title)
        assertEquals("<html>Welcome</html>", response.body)
        assertFalse(local.drainFrames().any { it.delta != null })
    }

    @Test
    fun savePagePublishesWebsiteDeltaThenTypedResponse() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.send(
            RewriteFrames.command(
                commandId = "save-page-1",
                commandName = "savepage",
                payload = RewriteGameJson.encode(
                    serializer = SavePagePayload.serializer(),
                    value = SavePagePayload(
                        ip = "LOCAL-IP",
                        title = "Updated Title",
                        body = "<html>Updated</html>",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = local.awaitFrame()
        val responseFrame = local.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = SavePageResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )
        val projection = RewriteGameJson.decode(
            serializer = DeltaProjection.serializer(),
            payload = delta.delta!!.payload.toByteArray(),
        )

        assertEquals(listOf("website"), delta.delta?.delta_keys)
        assertIs<StateSectionsDeltaProjection>(projection)
        assertEquals("Updated Title", response.title)
    }

    @Test
    fun requestWebpageAndSubmitReturnOneCorrelatedRenderResponseAndNoDeltas() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.send(
            RewriteFrames.command(
                commandId = "web-1",
                commandName = "requestwebpage",
                payload = RewriteGameJson.encode(
                    serializer = RequestWebpagePayload.serializer(),
                    value = RequestWebpagePayload(
                        targetIp = "store999",
                        sourceIp = "LOCAL-IP",
                        parameters = mapOf("q" to "shop"),
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val pageResponseFrame = local.awaitFrame()
        val pageResponse = RewriteGameJson.decode(
            serializer = WebsiteRenderResponse.serializer(),
            payload = pageResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals("store1", pageResponse.resolvedTargetStateId.value)
        assertEquals("Remote Shop", pageResponse.title)
        assertEquals(listOf("merchant.bin"), pageResponse.storeFiles.map { it.name })
        assertFalse(pageResponse.fallback)
        assertNull(local.drainFrames().firstOrNull { it.delta != null })

        local.send(
            RewriteFrames.command(
                commandId = "submit-1",
                commandName = "submit",
                payload = RewriteGameJson.encode(
                    serializer = SubmitWebpagePayload.serializer(),
                    value = SubmitWebpagePayload(
                        targetIp = "store",
                        sourceIp = "LOCAL-IP",
                        parameters = mapOf("field" to "value"),
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val submitResponseFrame = local.awaitFrame()
        val submitResponse = RewriteGameJson.decode(
            serializer = WebsiteRenderResponse.serializer(),
            payload = submitResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals("store1", submitResponse.resolvedTargetStateId.value)
        assertFalse(submitResponse.fallback)
        assertNull(local.drainFrames().firstOrNull { it.delta != null })
    }

    @Test
    fun requestWebpageFallbackAndExitProduceNoSubscriptionsOrMutations() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.send(
            RewriteFrames.command(
                commandId = "fallback-1",
                commandName = "requestwebpage",
                payload = RewriteGameJson.encode(
                    serializer = RequestWebpagePayload.serializer(),
                    value = RequestWebpagePayload(
                        targetIp = "OFFLINE-IP",
                        sourceIp = "LOCAL-IP",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val fallbackResponseFrame = local.awaitFrame()
        val fallbackResponse = RewriteGameJson.decode(
            serializer = WebsiteRenderResponse.serializer(),
            payload = fallbackResponseFrame.command_response!!.payload.toByteArray(),
        )
        assertTrue(fallbackResponse.fallback)

        local.send(
            RewriteFrames.command(
                commandId = "exit-1",
                commandName = "exit",
                payload = RewriteGameJson.encode(
                    serializer = ExitWebpagePayload.serializer(),
                    value = ExitWebpagePayload(
                        targetIp = "TARGET-IP",
                        sourceIp = "LOCAL-IP",
                    ),
                ),
                expectsResponse = false,
            ),
        )

        assertTrue(local.drainFrames().isEmpty())
    }

    @Test
    fun voteReturnsCorrelatedResponseAndTargetsVoterAndWebsiteListeners() = runTest {
        val fixture = createFixture()
        val voter = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        voter.send(
            RewriteFrames.command(
                commandId = "vote-1",
                commandName = "vote",
                payload = RewriteGameJson.encode(
                    serializer = VotePayload.serializer(),
                    value = VotePayload(
                        targetIp = "TARGET-IP",
                        sourceIp = "LOCAL-IP",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val voterDelta = voter.awaitFrame()
        val targetDelta = target.awaitFrame()
        val responseFrame = voter.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = VoteResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )
        val targetProjection = RewriteGameJson.decode(
            serializer = DeltaProjection.serializer(),
            payload = targetDelta.delta!!.payload.toByteArray(),
        )

        assertEquals("LOCAL-IP", voterDelta.delta?.game_state_id)
        assertEquals(listOf("website"), voterDelta.delta?.delta_keys)
        assertEquals("TARGET-IP", targetDelta.delta?.game_state_id)
        assertEquals(listOf("website", "stats"), targetDelta.delta?.delta_keys)
        assertEquals(1, response.votesAvailableAfter)
        assertEquals(8, response.targetVoteCountAfter)
        assertEquals(500, response.targetHttpExperienceAfter)
        assertIs<StateSectionsDeltaProjection>(targetProjection)
    }

    private fun TestScope.createFixture(): Fixture {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                GameStateId("LOCAL-IP") to localState(),
                GameStateId("TARGET-IP") to targetState(),
                GameStateId("store1") to storeState(),
                GameStateId("OFFLINE-IP") to offlineState(),
            ),
        )
        val interests = InMemoryInterestRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
            ),
            interestRegistry = interests,
            serverId = "1",
        )
        val harnessAdapter = HarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(
                        FakePlayerAccount("PF-LOCALUSER", "LOCAL-IP", "SESSION-LOCALUSER"),
                        FakePlayerAccount("PF-TARGETUSER", "TARGET-IP", "SESSION-TARGETUSER"),
                        FakePlayerAccount("PF-STOREUSER", "store1", "SESSION-STOREUSER"),
                        FakePlayerAccount("PF-OFFLINE", "OFFLINE-IP", "SESSION-OFFLINE"),
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

    private suspend fun Fixture.authenticatedConnection(requestedIp: String): InMemoryClientConnection {
        val connection = harness.connect()
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = sessionTicketFor(requestedIp),
                clientBuild = "rewrite-it",
                playFabIdHint = playFabIdFor(requestedIp),
                requestedIp = requestedIp,
            ),
        )
        connection.awaitFrame()
        connection.awaitFrame()
        return connection
    }

    private fun localState(): ComputerState {
        return ComputerState.empty(GameStateId("LOCAL-IP"), playFabId = "PF-LOCALUSER").copy(
            website = WebsiteState(
                title = "Home Page",
                body = "<html>Welcome</html>",
                votesAvailable = 2,
                voteCount = 3,
            ),
            stats = PlayerStatsState(
                totalLevel = 5,
                noobProtectionLevel = 3,
            ),
            economy = EconomyState(
                pettyCash = 500.0,
                bankMoney = 200.0,
                defaultBankPort = 6,
            ),
            ports = listOf(
                bankingPort(),
                ftpPort(),
                httpPort(),
            ),
        )
    }

    private fun targetState(): ComputerState {
        return ComputerState.empty(GameStateId("TARGET-IP"), playFabId = "PF-TARGETUSER").copy(
            website = WebsiteState(
                title = "Target Page",
                body = "<html>Target</html>",
                voteCount = 7,
            ),
            economy = EconomyState(defaultBankPort = 6),
            ports = listOf(
                bankingPort(),
                ftpPort(),
                httpPort(),
            ),
        )
    }

    private fun storeState(): ComputerState {
        var filesystem = ComputerState.empty(GameStateId("store1"), playerIp = "store1").filesystem
            .ensureDirectory("/Store")
        filesystem = filesystem.saveFile(
            StoredFile(
                path = buildFilePath("/Store", "merchant.bin"),
                name = "merchant.bin",
                kind = StoredFileKind.APPLICATION_BINARY,
                contents = "compiled merchant",
                quantity = 2,
                price = 196.0,
            ),
        )
        return ComputerState.empty(GameStateId("store1"), playFabId = "PF-STOREUSER").copy(
            filesystem = filesystem,
            website = WebsiteState(
                title = "Remote Shop",
                body = "<html>Remote</html>",
            ),
            economy = EconomyState(defaultBankPort = 6),
            ports = listOf(
                bankingPort(),
                ftpPort(),
                httpPort(),
            ),
        )
    }

    private fun offlineState(): ComputerState {
        return ComputerState.empty(GameStateId("OFFLINE-IP"), playFabId = "PF-OFFLINE").copy(
            website = WebsiteState(
                title = "Offline",
                body = "<html>Offline</html>",
                voteCount = 1,
            ),
            economy = EconomyState(defaultBankPort = 6),
            ports = listOf(
                bankingPort(),
                ftpPort(),
            ),
        )
    }

    private fun bankingPort(): PortState {
        return PortState(
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
        )
    }

    private fun ftpPort(): PortState {
        return PortState(
            number = 21,
            type = "ftp",
            enabled = true,
            defaultPort = true,
            installedApplication = InstalledApplication(
                name = "ftp.bin",
                kind = ApplicationKind.FTP,
                binaryPath = "/system/ftp.bin",
            ),
        )
    }

    private fun httpPort(): PortState {
        return PortState(
            number = 80,
            type = "http",
            enabled = true,
            defaultPort = true,
            installedApplication = InstalledApplication(
                name = "http.bin",
                kind = ApplicationKind.HTTP,
                binaryPath = "/system/http.bin",
            ),
        )
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
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
                session = session.toWebsiteGameSession(),
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
                session = session.toWebsiteGameSession(),
                command = command,
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }
    }
}

private fun InMemoryAuthenticatedSession.toWebsiteGameSession(): AuthenticatedGameSession {
    return AuthenticatedGameSession(
        connectionId = connectionId,
        playFabId = verifiedSession.playFabId,
        playerIp = verifiedSession.playerIp,
    )
}

private fun sessionTicketFor(requestedIp: String): String = when (requestedIp) {
    "LOCAL-IP" -> "SESSION-LOCALUSER"
    "TARGET-IP" -> "SESSION-TARGETUSER"
    "store1" -> "SESSION-STOREUSER"
    "OFFLINE-IP" -> "SESSION-OFFLINE"
    else -> "SESSION-LOCALUSER"
}

private fun playFabIdFor(requestedIp: String): String = when (requestedIp) {
    "LOCAL-IP" -> "PF-LOCALUSER"
    "TARGET-IP" -> "PF-TARGETUSER"
    "store1" -> "PF-STOREUSER"
    "OFFLINE-IP" -> "PF-OFFLINE"
    else -> "PF-LOCALUSER"
}
