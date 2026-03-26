package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.files.RewriteLocalFileOpenTarget
import com.hackwars.rewrite.client.files.RewriteScriptEditorTemplate
import com.hackwars.rewrite.client.files.buildReadOnlyEditorTabs
import com.hackwars.rewrite.client.files.buildNewEditorDocumentSpec
import com.hackwars.rewrite.client.files.routeLocalFileTarget
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameState
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameUiNotice
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientAttackMessageUiEvent
import com.hackwars.rewrite.protocol.ClientBankTransactionResponse
import com.hackwars.rewrite.protocol.ClientBountyCreatedResponse
import com.hackwars.rewrite.protocol.ClientCompileFilePayload
import com.hackwars.rewrite.protocol.ClientCompileFileResponse
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientDecompileFilePayload
import com.hackwars.rewrite.protocol.ClientDecompileFileResponse
import com.hackwars.rewrite.protocol.ClientDepositPayload
import com.hackwars.rewrite.protocol.ClientEconomyState
import com.hackwars.rewrite.protocol.ClientFileContentsResponse
import com.hackwars.rewrite.protocol.ClientGameDeltaProjection
import com.hackwars.rewrite.protocol.ClientGameSectionsProjection
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientMakeBountyPayload
import com.hackwars.rewrite.protocol.ClientMutationAcceptedResponse
import com.hackwars.rewrite.protocol.ClientProgramScriptBundle
import com.hackwars.rewrite.protocol.ClientProgramScriptSlot
import com.hackwars.rewrite.protocol.ClientProgramLifecycleStatus
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.ClientRequestFilePayload
import com.hackwars.rewrite.protocol.ClientSaveFilePayload
import com.hackwars.rewrite.protocol.ClientScriptFamily
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.ClientTransferPayload
import com.hackwars.rewrite.protocol.ClientTransferResponse
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.ErrorEnvelope
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteClientTest {
    @Test
    fun rootControllerHandlesFakeSessionsAndShutdownClosesBothSessions() {
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = RewriteRootController(
            sessionGateway = sessionGateway,
            authGateway = RecordingRewriteLoginAuthGateway(),
        )

        controller.connect(RewriteService.GAME)
        controller.connect(RewriteService.CHAT)

        sessionGateway.requireLatestSession(RewriteService.GAME).receive(
            RewriteFrames.authAccepted(
                connectionId = "game-1",
                playFabId = "PF-LOCAL",
                playerIp = "LOCAL-IP",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        sessionGateway.requireLatestSession(RewriteService.CHAT).receive(
            RewriteFrames.authRejected("INVALID_AUTH", "bad ticket"),
        )

        assertEquals(2, sessionGateway.openedSessions.size)
        assertNotNull(controller.snapshot().game.latestAcceptedSession)
        assertTrue(controller.snapshot().chat.authState is com.hackwars.rewrite.clientmodel.RewriteServiceAuthState.Rejected)

        controller.shutdown()

        assertTrue(sessionGateway.openedSessions.all { it.closed })
    }

    @Test
    fun rootControllerSelectorsObserveDecodedGameStateProgramUpdatesAndUiNotices() = runTest {
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = RewriteRootController(
            sessionGateway = sessionGateway,
            authGateway = RecordingRewriteLoginAuthGateway(),
        )
        val decodedStates = mutableListOf<RewriteDecodedGameState>()
        val programUpdates = mutableListOf<Map<String, ClientProgramUpdate>>()
        val notices = mutableListOf<List<RewriteDecodedGameUiNotice>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.gameDecodedStateSelector().take(5).toList(decodedStates)
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.gameProgramUpdatesSelector().take(2).toList(programUpdates)
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.gameUiNoticesSelector().take(2).toList(notices)
        }

        controller.connect(RewriteService.GAME)
        sessionGateway.requireLatestSession(RewriteService.GAME).receive(
            RewriteFrames.snapshot(
                gameStateId = "LOCAL-IP",
                sequence = 7,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        version = 7,
                        economy = ClientEconomyState(pettyCash = 125.0),
                    ),
                ),
            ),
        )
        sessionGateway.requireLatestSession(RewriteService.GAME).receive(
            RewriteFrames.delta(
                gameStateId = "LOCAL-IP",
                sequence = 8,
                changedPaths = listOf("economy.pettyCash"),
                deltaKeys = listOf("economy"),
                payload = RewriteClientJson.encode(
                    ClientGameDeltaProjection.serializer(),
                    ClientGameSectionsProjection(
                        economy = ClientEconomyState(pettyCash = 250.0),
                    ),
                ),
            ),
        )
        sessionGateway.requireLatestSession(RewriteService.GAME).receive(
            RewriteFrames.programUpdate(
                programId = "program-1",
                programType = "attack",
                status = hackwars.rewrite.v1.ProgramStatus.PROGRAM_STATUS_RUNNING,
                payload = RewriteClientJson.encode(
                    ClientProgramUpdate.serializer(),
                    ClientProgramUpdate(
                        programId = "program-1",
                        programType = "attack",
                        status = ClientProgramLifecycleStatus.RUNNING,
                    ),
                ),
            ),
        )
        sessionGateway.requireLatestSession(RewriteService.GAME).receive(
            RewriteFrames.gameUiEvent(
                eventId = "ui-1",
                eventType = "attack_message",
                payload = RewriteClientJson.encode(
                    com.hackwars.rewrite.protocol.ClientGameUiEvent.serializer(),
                    ClientAttackMessageUiEvent(
                        message = "Redirect receipt",
                        port = 9,
                        ip = "TARGET-IP",
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(5, decodedStates.size)
        assertNull(decodedStates.first().shellState)
        assertEquals(125.0, decodedStates[1].shellState?.economy?.pettyCash)
        assertEquals(250.0, decodedStates[2].shellState?.economy?.pettyCash)
        assertEquals(ClientProgramLifecycleStatus.RUNNING, decodedStates[3].programUpdatesById["program-1"]?.status)
        assertIs<ClientAttackMessageUiEvent>(decodedStates.last().uiNotices.single().event)
        assertEquals(2, programUpdates.size)
        assertTrue(programUpdates.first().isEmpty())
        assertEquals(ClientProgramLifecycleStatus.RUNNING, programUpdates.last()["program-1"]?.status)
        assertEquals(2, notices.size)
        assertTrue(notices.first().isEmpty())
        assertIs<ClientAttackMessageUiEvent>(notices.last().single().event)
        assertEquals(250.0, controller.gameDecodedState().shellState?.economy?.pettyCash)
    }

    @Test
    fun blankUsernameFailsLocallyAndZeroesPassword() = runTest {
        val authGateway = RecordingRewriteLoginAuthGateway(
            nextResult = RewriteLoginAuthResult.success("PF-TEST", "SESSION-TEST"),
        )
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = testController(
            authGateway = authGateway,
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        val password = "password1234".toCharArray()

        controller.submitLogin("   ", password)
        advanceUntilIdle()

        assertTrue(password.all { it == '\u0000' })
        assertTrue(authGateway.requests.isEmpty())
        assertTrue(sessionGateway.openedSessions.isEmpty())
        assertEquals(RewriteClientRoute.LOGIN, controller.route())
        assertEquals("Username is required.", controller.bootstrapState().loginError)
    }

    @Test
    fun successfulAuthAndFirstSnapshotTransitionToDesktop() = runTest {
        val authGateway = RecordingRewriteLoginAuthGateway(
            nextResult = RewriteLoginAuthResult.success("PF-LOCALUSER", "SESSION-LOCALUSER"),
        )
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = testController(
            authGateway = authGateway,
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        val password = "password1234".toCharArray()

        controller.submitLogin("localuser", password)
        runCurrent()

        val session = sessionGateway.requireLatestSession(RewriteService.GAME)
        val authRequest = session.sentFrames.single().auth_request
        assertNotNull(authRequest)
        assertEquals(RewriteClientRoute.BOOTSTRAPPING_GAME, controller.route())
        assertTrue(password.all { it == '\u0000' })
        assertEquals(RewriteService.GAME.toProto(), authRequest.service)
        assertEquals("SESSION-LOCALUSER", authRequest.session_ticket)
        assertEquals("PF-LOCALUSER", authRequest.playfab_id_hint)
        assertEquals("", authRequest.requested_ip)

        session.receive(
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCALUSER",
                playerIp = "LOCAL-IP",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        runCurrent()
        assertEquals(RewriteClientRoute.BOOTSTRAPPING_GAME, controller.route())

        session.receive(
            RewriteFrames.snapshot(
                gameStateId = "LOCAL-IP",
                sequence = 1,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        version = 1,
                        economy = ClientEconomyState(pettyCash = 100.0),
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(RewriteClientRoute.DESKTOP, controller.route())
        assertEquals("LOCAL-IP", controller.snapshot().game.latestAcceptedSession?.playerIp)
        assertEquals("LOCAL-IP", controller.gameShellState()?.id)
    }

    @Test
    fun authRejectedKeepsAppOnLoginAndSurfacesFailure() = runTest {
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = testController(
            authGateway = RecordingRewriteLoginAuthGateway(
                nextResult = RewriteLoginAuthResult.success("PF-LOCALUSER", "SESSION-LOCALUSER"),
            ),
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )

        controller.submitLogin("localuser", "password1234".toCharArray())
        runCurrent()

        val session = sessionGateway.requireLatestSession(RewriteService.GAME)
        session.receive(RewriteFrames.authRejected("INVALID_AUTH", "bad ticket"))
        advanceUntilIdle()

        assertEquals(RewriteClientRoute.LOGIN, controller.route())
        assertEquals("bad ticket", controller.bootstrapState().loginError)
    }

    @Test
    fun missingBootstrapSnapshotTimesOutBackToLogin() = runTest {
        val controller = testController(
            authGateway = RecordingRewriteLoginAuthGateway(
                nextResult = RewriteLoginAuthResult.success("PF-LOCALUSER", "SESSION-LOCALUSER"),
            ),
            sessionGateway = FakeRewriteServiceSessionGateway(),
            config = RewriteGameConnectionConfig(bootstrapTimeout = kotlin.time.Duration.parse("100ms")),
            scheduler = testScheduler,
        )

        controller.submitLogin("localuser", "password1234".toCharArray())
        runCurrent()
        advanceTimeBy(101)
        advanceUntilIdle()

        assertEquals(RewriteClientRoute.LOGIN, controller.route())
        assertEquals(
            "The rewrite game server did not finish bootstrapping in time.",
            controller.bootstrapState().loginError,
        )
    }

    @Test
    fun secondLoginAttemptClosesPriorGameSessionAndStartsFreshBootstrap() = runTest {
        val authGateway = RecordingRewriteLoginAuthGateway(
            results = ArrayDeque(
                listOf(
                    RewriteLoginAuthResult.success("PF-ONE", "SESSION-ONE"),
                    RewriteLoginAuthResult.success("PF-TWO", "SESSION-TWO"),
                ),
            ),
        )
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = testController(
            authGateway = authGateway,
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )

        controller.submitLogin("localuser", "password1234".toCharArray())
        runCurrent()
        val firstSession = sessionGateway.requireLatestSession(RewriteService.GAME)

        controller.submitLogin("localuser", "password1234".toCharArray())
        runCurrent()
        val secondSession = sessionGateway.requireLatestSession(RewriteService.GAME)

        assertTrue(firstSession.closed)
        assertFalse(secondSession.closed)
        assertEquals(2, sessionGateway.sessionsFor(RewriteService.GAME).size)
        assertEquals("SESSION-TWO", secondSession.sentFrames.single().auth_request?.session_ticket)
        assertEquals(RewriteClientRoute.BOOTSTRAPPING_GAME, controller.route())
    }

    @Test
    fun requestDepositResolvesByCommandIdAndKeepsRawCommandResponseInInbox() = runTest {
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = testController(
            authGateway = RecordingRewriteLoginAuthGateway(),
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "LOCAL-IP",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )

        val pending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestDeposit(amount = 25.0, portNumber = 4)
        }
        runCurrent()

        val session = sessionGateway.requireLatestSession(RewriteService.GAME)
        val command = session.sentFrames.single().command!!
        val payload = RewriteClientJson.decode(
            ClientDepositPayload.serializer(),
            command.payload.toByteArray(),
        )
        assertEquals("deposit", command.command_name)
        assertEquals(25.0, payload.amount)
        assertEquals("LOCAL-IP", payload.ip)
        assertEquals(4, payload.port)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientBankTransactionResponse.serializer(),
                    ClientBankTransactionResponse(
                        stateId = "LOCAL-IP",
                        operation = "deposit",
                        portNumber = 4,
                        requestedAmount = 25.0,
                        appliedAmount = 25.0,
                        pettyCashAfter = 100.0,
                        bankMoneyAfter = 50.0,
                        version = 8,
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        val result = pending.await()
        assertIs<RewriteGameCommandResult.Success<ClientBankTransactionResponse>>(result)
        assertEquals(25.0, result.value.appliedAmount)
        assertEquals(command.command_id, controller.snapshot().game.inbox.lastCommandResponse?.metadata?.commandId)
    }

    @Test
    fun requestMakeBountyUsesExpectedPayloadAndResolvesByCommandId() = runTest {
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = testController(
            authGateway = RecordingRewriteLoginAuthGateway(),
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "LOCAL-IP",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )

        val pending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestMakeBounty(
                anonymous = true,
                target = "*",
                type = 2,
                fileName = "installer.bin",
                folder = "/Scripts",
                iterations = 3,
                reward = 125.0,
            )
        }
        runCurrent()

        val session = sessionGateway.requireLatestSession(RewriteService.GAME)
        val command = session.sentFrames.single().command!!
        val payload = RewriteClientJson.decode(
            ClientMakeBountyPayload.serializer(),
            command.payload.toByteArray(),
        )
        assertEquals("makebounty", command.command_name)
        assertEquals("LOCAL-IP", payload.sourceIp)
        assertTrue(payload.anonymous)
        assertEquals("*", payload.target)
        assertEquals(2, payload.type)
        assertEquals("installer.bin", payload.fname)
        assertEquals("/Scripts", payload.folder)
        assertEquals(3, payload.iterations)
        assertEquals(125.0, payload.reward)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientBountyCreatedResponse.serializer(),
                    ClientBountyCreatedResponse(
                        creatorStateId = "LOCAL-IP",
                        storeStateId = "store1",
                        bountyFile = ClientStoredFile(
                            path = "/Store/LOCAL-IP-install-1.bnty",
                            name = "LOCAL-IP-install-1.bnty",
                        ),
                        reward = 125.0,
                        creatorVersion = 4,
                        storeVersion = 8,
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        val result = pending.await()
        assertIs<RewriteGameCommandResult.Success<ClientBountyCreatedResponse>>(result)
        assertEquals("/Store/LOCAL-IP-install-1.bnty", result.value.bountyFile.path)
    }

    @Test
    fun requestFileUsesExpectedPayloadAndResolvesByCommandId() = runTest {
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = testController(
            authGateway = RecordingRewriteLoginAuthGateway(),
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "LOCAL-IP",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )

        val pending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestFile(path = "/Scripts", name = "attack.src")
        }
        runCurrent()

        val session = sessionGateway.requireLatestSession(RewriteService.GAME)
        val command = session.sentFrames.single().command!!
        val payload = RewriteClientJson.decode(
            ClientRequestFilePayload.serializer(),
            command.payload.toByteArray(),
        )
        assertEquals("requestfile", command.command_name)
        assertEquals("/Scripts", payload.path)
        assertEquals("attack.src", payload.name)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientFileContentsResponse.serializer(),
                    ClientFileContentsResponse(
                        stateId = "LOCAL-IP",
                        file = ClientStoredFile(
                            path = "/Scripts/attack.src",
                            name = "attack.src",
                            kind = ClientStoredFileKind.SCRIPT_SOURCE,
                            contents = "legacy",
                        ),
                        version = 9,
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        val result = pending.await()
        assertIs<RewriteGameCommandResult.Success<ClientFileContentsResponse>>(result)
        assertEquals("/Scripts/attack.src", result.value.file?.path)
        assertEquals(command.command_id, controller.snapshot().game.inbox.lastCommandResponse?.metadata?.commandId)
    }

    @Test
    fun newTemplateSpecsProduceExpectedKindsSlotsAndCompileMetadata() {
        val banking = buildNewEditorDocumentSpec(RewriteScriptEditorTemplate.BANKING)
        val attack = buildNewEditorDocumentSpec(RewriteScriptEditorTemplate.ATTACK)
        val ftp = buildNewEditorDocumentSpec(RewriteScriptEditorTemplate.FTP)
        val watch = buildNewEditorDocumentSpec(RewriteScriptEditorTemplate.WATCH)
        val http = buildNewEditorDocumentSpec(RewriteScriptEditorTemplate.HTTP)
        val redirect = buildNewEditorDocumentSpec(RewriteScriptEditorTemplate.REDIRECT)
        val text = buildNewEditorDocumentSpec(RewriteScriptEditorTemplate.TEXT)

        assertEquals(ClientStoredFileKind.SCRIPT_SOURCE, banking.kind)
        assertEquals(listOf("Deposit", "Withdraw", "Transfer"), banking.tabDefinitions.map { it.title })
        assertEquals(ClientScriptFamily.BANKING, banking.compiledBinary?.scriptFamily)
        assertEquals(ClientApplicationKind.BANKING, banking.compiledBinary?.applicationKind)

        assertEquals(listOf("Initialize", "Finalize", "Continue"), attack.tabDefinitions.map { it.title })
        assertEquals(ClientScriptFamily.ATTACK, attack.scriptBundleFamily)
        assertEquals(ClientApplicationKind.ATTACK, attack.compiledBinary?.applicationKind)

        assertEquals(listOf("Put", "Get"), ftp.tabDefinitions.map { it.title })
        assertEquals(ClientScriptFamily.GENERAL, ftp.scriptBundleFamily)
        assertEquals(ClientApplicationKind.FTP, ftp.compiledBinary?.applicationKind)

        assertEquals(listOf("Fire"), watch.tabDefinitions.map { it.title })
        assertEquals(ClientScriptFamily.WATCH, watch.compiledBinary?.scriptFamily)

        assertEquals(listOf("Enter", "Exit", "Submit"), http.tabDefinitions.map { it.title })
        assertEquals(ClientScriptFamily.HTTP, http.compiledBinary?.scriptFamily)

        assertEquals(listOf("Initialize", "Finalize", "Continue"), redirect.tabDefinitions.map { it.title })
        assertEquals(ClientScriptFamily.REDIRECT, redirect.compiledBinary?.scriptFamily)

        assertEquals(ClientStoredFileKind.TEXT, text.kind)
        assertEquals(listOf("Content"), text.tabDefinitions.map { it.title })
        assertNull(text.compiledBinary)
    }

    @Test
    fun requestSaveCompileAndDecompileUseExpectedPayloads() = runTest {
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = testController(
            authGateway = RecordingRewriteLoginAuthGateway(),
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "LOCAL-IP",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )

        val sourceFile = ClientStoredFile(
            path = "/Scripts/attack.src",
            name = "attack.src",
            kind = ClientStoredFileKind.SCRIPT_SOURCE,
            contents = "[Initialize]\nint main(){}",
            compiledBinary = ClientCompiledBinaryMetadata(
                scriptFamily = ClientScriptFamily.ATTACK,
                applicationKind = ClientApplicationKind.ATTACK,
            ),
            scriptBundle = ClientProgramScriptBundle(
                family = ClientScriptFamily.ATTACK,
                scriptsBySlot = mapOf(
                    ClientProgramScriptSlot.INITIALIZE to "int main(){}",
                ),
            ),
        )

        val savePending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestSaveFile(path = "/Scripts", file = sourceFile)
        }
        runCurrent()

        val session = sessionGateway.requireLatestSession(RewriteService.GAME)
        val saveCommand = session.sentFrames.last().command!!
        val savePayload = RewriteClientJson.decode(
            ClientSaveFilePayload.serializer(),
            saveCommand.payload.toByteArray(),
        )
        assertEquals("savefile", saveCommand.command_name)
        assertEquals("/Scripts", savePayload.path)
        assertEquals("attack.src", savePayload.file.name)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = saveCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientMutationAcceptedResponse.serializer(),
                    ClientMutationAcceptedResponse(
                        stateId = "LOCAL-IP",
                        version = 10,
                        message = "file-saved",
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientMutationAcceptedResponse>>(savePending.await())

        val compilePending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestCompileFile(path = "/Scripts", name = "attack.src")
        }
        runCurrent()
        val compileCommand = session.sentFrames.last().command!!
        val compilePayload = RewriteClientJson.decode(
            ClientCompileFilePayload.serializer(),
            compileCommand.payload.toByteArray(),
        )
        assertEquals("compilefile", compileCommand.command_name)
        assertEquals("/Scripts", compilePayload.path)
        assertEquals("attack.src", compilePayload.name)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = compileCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientCompileFileResponse.serializer(),
                    ClientCompileFileResponse(
                        stateId = "LOCAL-IP",
                        compiledFile = sourceFile.copy(
                            path = "/Scripts/attack.bin",
                            name = "attack.bin",
                            kind = ClientStoredFileKind.APPLICATION_BINARY,
                        ),
                        pettyCashAfter = 95.0,
                        experienceAfter = 2.0,
                        version = 11,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientCompileFileResponse>>(compilePending.await())

        val decompilePending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestDecompileFile(path = "/Scripts", name = "attack.bin")
        }
        runCurrent()
        val decompileCommand = session.sentFrames.last().command!!
        val decompilePayload = RewriteClientJson.decode(
            ClientDecompileFilePayload.serializer(),
            decompileCommand.payload.toByteArray(),
        )
        assertEquals("decompilefile", decompileCommand.command_name)
        assertEquals("/Scripts", decompilePayload.path)
        assertEquals("attack.bin", decompilePayload.name)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = decompileCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientDecompileFileResponse.serializer(),
                    ClientDecompileFileResponse(
                        stateId = "LOCAL-IP",
                        decompiledFile = sourceFile,
                        pettyCashAfter = 100.0,
                        experienceAfter = 1.0,
                        version = 12,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientDecompileFileResponse>>(decompilePending.await())
    }

    @Test
    fun localFileRoutingAndReadOnlyEditorTabsFollowLockedRules() {
        val scriptSource = ClientStoredFile(
            path = "/Scripts/bank.src",
            name = "bank.src",
            kind = ClientStoredFileKind.SCRIPT_SOURCE,
            scriptBundle = ClientProgramScriptBundle(
                scriptsBySlot = mapOf(
                    ClientProgramScriptSlot.DEPOSIT to "deposit()",
                    ClientProgramScriptSlot.WITHDRAW to "withdraw()",
                    ClientProgramScriptSlot.TRANSFER to "transfer()",
                ),
            ),
        )
        val ftpSource = ClientStoredFile(
            path = "/Scripts/ftp.src",
            name = "ftp.src",
            kind = ClientStoredFileKind.SCRIPT_SOURCE,
            scriptBundle = ClientProgramScriptBundle(
                scriptsBySlot = mapOf(
                    ClientProgramScriptSlot.PUT to "put()",
                    ClientProgramScriptSlot.GET to "get()",
                ),
            ),
        )
        val noteFile = ClientStoredFile(
            path = "/Public/readme.note",
            name = "readme.note",
            kind = ClientStoredFileKind.NOTE,
            contents = "hello",
        )
        val binaryFile = ClientStoredFile(
            path = "/Public/http.bin",
            name = "http.bin",
            kind = ClientStoredFileKind.APPLICATION_BINARY,
        )

        assertEquals(RewriteLocalFileOpenTarget.SCRIPT_EDITOR, routeLocalFileTarget(scriptSource))
        assertEquals(RewriteLocalFileOpenTarget.SCRIPT_EDITOR, routeLocalFileTarget(noteFile))
        assertEquals(RewriteLocalFileOpenTarget.FILE_PROPERTIES, routeLocalFileTarget(binaryFile))

        assertEquals(listOf("Deposit", "Withdraw", "Transfer"), buildReadOnlyEditorTabs(scriptSource).map { it.title })
        assertEquals(listOf("Put", "Get"), buildReadOnlyEditorTabs(ftpSource).map { it.title })
        assertEquals(listOf("Content"), buildReadOnlyEditorTabs(noteFile).map { it.title })
        assertEquals("hello", buildReadOnlyEditorTabs(noteFile).single().content)
    }

    @Test
    fun requestTransferSurfacesServerErrorAndTimeoutAsFailures() = runTest {
        val sessionGateway = FakeRewriteServiceSessionGateway()
        val controller = testController(
            authGateway = RecordingRewriteLoginAuthGateway(),
            sessionGateway = sessionGateway,
            config = RewriteGameConnectionConfig(bootstrapTimeout = kotlin.time.Duration.parse("100ms")),
            scheduler = testScheduler,
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "LOCAL-IP",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )

        val failedRequest = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestTransfer(amount = 15.0, targetIp = "TARGET-IP", portNumber = 4)
        }
        runCurrent()

        val session = sessionGateway.requireLatestSession(RewriteService.GAME)
        val transferCommand = session.sentFrames.single().command!!
        val transferPayload = RewriteClientJson.decode(
            ClientTransferPayload.serializer(),
            transferCommand.payload.toByteArray(),
        )
        assertEquals("TARGET-IP", transferPayload.targetIp)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = transferCommand.command_id,
                status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                error = ErrorEnvelope(
                    code = "INVALID_TARGET",
                    message = "Transfer target does not exist.",
                    retryable = false,
                ),
            ),
        )
        advanceUntilIdle()

        val failedResult = failedRequest.await()
        assertIs<RewriteGameCommandResult.Failure>(failedResult)
        assertEquals("Transfer target does not exist.", failedResult.message)

        val timedOutRequest = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestTransfer(amount = 20.0, targetIp = "TARGET-IP", portNumber = 4)
        }
        runCurrent()
        advanceTimeBy(5_001)
        advanceUntilIdle()

        val timedOutResult = timedOutRequest.await()
        assertIs<RewriteGameCommandResult.Failure>(timedOutResult)
        assertEquals("The rewrite game server did not respond in time.", timedOutResult.message)
    }

    @Test
    fun tcpGatewaySendsAndReceivesFramesWithLocalServer() = runTest {
        val server = java.net.ServerSocket(0)
        val acceptedFrames = LinkedBlockingQueue<FrameEnvelope>()
        val latch = CountDownLatch(1)
        val serverThread = Thread {
            server.use { socketServer ->
                val client = socketServer.accept()
                client.use { socket ->
                    val input = socket.getInputStream()
                    val header = input.readNBytes(4)
                    val length = java.nio.ByteBuffer.wrap(header).int
                    val payload = input.readNBytes(length)
                    acceptedFrames.put(com.hackwars.rewrite.protocol.FrameCodec.decode(header + payload))
                    socket.getOutputStream().write(
                        com.hackwars.rewrite.protocol.FrameCodec.encode(
                            RewriteFrames.ping(
                                connectionId = "conn-1",
                                sentAtEpochMillis = 1,
                                acknowledgedAtEpochMillis = 2,
                            ),
                        ),
                    )
                    socket.getOutputStream().flush()
                    latch.countDown()
                }
            }
        }.apply { start() }

        val inboundFrames = CopyOnWriteArrayList<FrameEnvelope>()
        val gateway = RewriteTcpServiceSessionGateway(
            gameConnectionConfig = RewriteGameConnectionConfig(
                host = "127.0.0.1",
                port = server.localPort,
            ),
        )
        val session = gateway.open(RewriteService.GAME) { frame ->
            inboundFrames += frame
        }

        session.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-client-dev",
                playFabIdHint = "PF-LOCALUSER",
            ),
        )

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        eventually(3_000) { inboundFrames.any { it.ping != null } }
        assertEquals("SESSION-LOCALUSER", acceptedFrames.take().auth_request?.session_ticket)
        session.close()
        serverThread.join(3_000)
    }

    @Test
    fun tcpGatewayReportsEarlyDisconnectAsErrorFrame() {
        val server = java.net.ServerSocket(0)
        val latch = CountDownLatch(1)
        val serverThread = Thread {
            server.use { socketServer ->
                socketServer.accept().use { socket ->
                    socket.close()
                    latch.countDown()
                }
            }
        }.apply { start() }

        val inboundFrames = CopyOnWriteArrayList<FrameEnvelope>()
        val gateway = RewriteTcpServiceSessionGateway(
            gameConnectionConfig = RewriteGameConnectionConfig(
                host = "127.0.0.1",
                port = server.localPort,
            ),
        )
        val session = gateway.open(RewriteService.GAME) { frame ->
            inboundFrames += frame
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        eventually(3_000) { inboundFrames.any { it.error?.code == "SERVER_DISCONNECTED" } }
        session.close()
        serverThread.join(3_000)
    }

    private fun testController(
        authGateway: RewriteLoginAuthGateway,
        sessionGateway: RewriteServiceSessionGateway,
        config: RewriteGameConnectionConfig = RewriteGameConnectionConfig(),
        scheduler: TestCoroutineScheduler,
    ): RewriteRootController {
        return RewriteRootController(
            gameConnectionConfig = config,
            authGateway = authGateway,
            sessionGateway = sessionGateway,
            workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler)),
        )
    }

    private fun eventually(timeoutMillis: Long, assertion: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (assertion()) {
                return
            }
            Thread.sleep(10)
        }
        assertTrue(assertion())
    }

    private class RecordingRewriteLoginAuthGateway(
        var nextResult: RewriteLoginAuthResult? = null,
        private val results: ArrayDeque<RewriteLoginAuthResult> = ArrayDeque(),
    ) : RewriteLoginAuthGateway {
        val requests = mutableListOf<Pair<String, String>>()

        override suspend fun authenticate(email: String, password: CharArray): RewriteLoginAuthResult {
            requests += email to String(password)
            return (results.removeFirstOrNull() ?: nextResult ?: RewriteLoginAuthResult.failure("missing test result")).also {
                password.fill('\u0000')
            }
        }
    }

    private class FakeRewriteServiceSessionGateway : RewriteServiceSessionGateway {
        val openedSessions = mutableListOf<FakeRewriteServiceSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeRewriteServiceSession(
                service = service,
                onInboundFrame = onInboundFrame,
            ).also { openedSessions += it }
        }

        fun requireLatestSession(service: RewriteService): FakeRewriteServiceSession {
            return sessionsFor(service).last()
        }

        fun sessionsFor(service: RewriteService): List<FakeRewriteServiceSession> {
            return openedSessions.filter { it.service == service }
        }
    }

    private class FakeRewriteServiceSession(
        override val service: RewriteService,
        private val onInboundFrame: (FrameEnvelope) -> Unit,
    ) : RewriteServiceSession {
        val sentFrames = mutableListOf<FrameEnvelope>()
        var closed: Boolean = false

        override suspend fun send(frame: FrameEnvelope) {
            sentFrames += frame
        }

        override fun receive(frame: FrameEnvelope) {
            onInboundFrame(frame)
        }

        override fun close() {
            closed = true
        }
    }
}
