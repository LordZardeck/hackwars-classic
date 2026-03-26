package com.hackwars.rewrite.clientmodel

import com.hackwars.rewrite.protocol.ClientAttackMessageUiEvent
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientFilesystemState
import com.hackwars.rewrite.protocol.ClientGameDeltaProjection
import com.hackwars.rewrite.protocol.ClientGameSectionsProjection
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientGameStateSummaryProjection
import com.hackwars.rewrite.protocol.ClientGameUiEvent
import com.hackwars.rewrite.protocol.ClientPopupUiEvent
import com.hackwars.rewrite.protocol.ClientPopupUiStyle
import com.hackwars.rewrite.protocol.ClientProgramLifecycleStatus
import com.hackwars.rewrite.protocol.ClientProgramProgress
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.ClientShowChoicesType
import com.hackwars.rewrite.protocol.ClientShowChoicesUiEvent
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientTextMessageUiEvent
import com.hackwars.rewrite.protocol.ClientZombieAttackUiEvent
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.ConnectionLifecycleState
import com.hackwars.rewrite.protocol.FrameCodec
import com.hackwars.rewrite.protocol.FramePayloadType
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertIs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteClientModelTest {
    @Test
    fun serviceSelectorEmitsOnlyWhenProjectedValueChanges() = runTest {
        val store = RewriteClientStore()
        val values = mutableListOf<ConnectionLifecycleState>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.serviceConnectionSelector(RewriteService.GAME)
                .take(2)
                .toList(values)
        }

        store.recordInboundFrame(
            service = RewriteService.CHAT,
            frame = RewriteFrames.authRejected("INVALID_AUTH", "bad ticket"),
        )
        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.authAccepted(
                connectionId = "game-1",
                playFabId = "PF-LOCAL",
                playerIp = "LOCAL-IP",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(ConnectionLifecycleState.CLOSED, ConnectionLifecycleState.AUTHENTICATED), values)
    }

    @Test
    fun authAcceptedAndRejectedUpdateServiceStateDeterministically() {
        val store = RewriteClientStore()

        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.authAccepted(
                connectionId = "game-1",
                playFabId = "PF-LOCAL",
                playerIp = "LOCAL-IP",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        store.recordInboundFrame(
            service = RewriteService.CHAT,
            frame = RewriteFrames.authRejected("INVALID_AUTH", "bad ticket"),
        )

        val state = store.snapshot()
        assertEquals(ConnectionLifecycleState.AUTHENTICATED, state.game.connectionState)
        assertEquals("game-1", state.game.latestAcceptedSession?.connectionId)
        assertEquals("PF-LOCAL", state.game.latestAcceptedSession?.playFabId)
        assertTrue(state.game.authState is RewriteServiceAuthState.Accepted)
        assertEquals(ConnectionLifecycleState.CLOSED, state.chat.connectionState)
        assertTrue(state.chat.authState is RewriteServiceAuthState.Rejected)
    }

    @Test
    fun rawFramesAreRecordedOpaqueByPayloadType() {
        val store = RewriteClientStore()
        val snapshotFrame = RewriteFrames.snapshot(
            "LOCAL-IP",
            sequence = 7,
            payload = RewriteClientJson.encode(
                ClientGameSnapshot.serializer(),
                ClientGameSnapshot(
                    id = "LOCAL-IP",
                    version = 7,
                ),
            ),
        )
        val deltaFrame = RewriteFrames.delta(
            "LOCAL-IP",
            sequence = 8,
            changedPaths = listOf("/x"),
            deltaKeys = listOf("runtime"),
            payload = RewriteClientJson.encode(
                ClientGameDeltaProjection.serializer(),
                ClientGameSectionsProjection(),
            ),
        )
        val programFrame = RewriteFrames.programUpdate(
            "program-1",
            "attack",
            hackwars.rewrite.v1.ProgramStatus.PROGRAM_STATUS_RUNNING,
            payload = RewriteClientJson.encode(
                ClientProgramUpdate.serializer(),
                ClientProgramUpdate(
                    programId = "program-1",
                    programType = "attack",
                    status = ClientProgramLifecycleStatus.RUNNING,
                ),
            ),
        )
        val chatFrame = RewriteFrames.chatEvent("event-1", "message", channelName = "general")
        val uiFrame = RewriteFrames.gameUiEvent(
            "ui-1",
            "popup",
            payload = RewriteClientJson.encode(
                com.hackwars.rewrite.protocol.ClientGameUiEvent.serializer(),
                ClientPopupUiEvent(
                    message = "Warning",
                    style = ClientPopupUiStyle.ERROR,
                ),
            ),
        )
        val pingFrame = RewriteFrames.ping("conn-1", 11, 12)
        val errorFrame = RewriteFrames.error("BAD", "bad things", retryable = false)

        listOf(snapshotFrame, deltaFrame, programFrame, chatFrame, uiFrame, pingFrame, errorFrame).forEach {
            store.recordInboundFrame(RewriteService.GAME, it)
        }

        val inbox = store.snapshot().game.inbox
        assertEquals(FramePayloadType.SNAPSHOT, inbox.lastSnapshot?.payloadType)
        assertEquals(FramePayloadType.DELTA, inbox.lastDelta?.payloadType)
        assertEquals(FramePayloadType.PROGRAM_UPDATE, inbox.lastProgramUpdate?.payloadType)
        assertEquals(FramePayloadType.CHAT_EVENT, inbox.lastChatEvent?.payloadType)
        assertEquals(FramePayloadType.GAME_UI_EVENT, inbox.lastGameUiEvent?.payloadType)
        assertEquals(FramePayloadType.PING, inbox.lastPing?.payloadType)
        assertEquals(FramePayloadType.ERROR, inbox.lastError?.payloadType)
        assertEquals(7, inbox.history.size)
        assertTrue(inbox.lastSnapshot?.encodedFrameBytes?.contentEquals(FrameCodec.encode(snapshotFrame)) == true)
    }

    @Test
    fun snapshotAndSectionDeltaProduceMergedDecodedShellState() {
        val store = RewriteClientStore()
        val snapshot = ClientGameSnapshot(
            id = "LOCAL-IP",
            version = 7,
            identity = com.hackwars.rewrite.protocol.ClientComputerIdentity(
                playerIp = "LOCAL-IP",
                displayName = "Local User",
            ),
            economy = com.hackwars.rewrite.protocol.ClientEconomyState(
                pettyCash = 125.0,
                bankMoney = 25.0,
                defaultBankPort = 4,
            ),
            filesystem = ClientFilesystemState(
                currentPath = "/Public",
                directoriesByPath = mapOf(
                    "/Public" to ClientDirectoryEntry(
                        path = "/Public",
                        name = "Public",
                    ),
                ),
                filesByPath = mapOf(
                    "/Public/readme.txt" to ClientStoredFile(
                        path = "/Public/readme.txt",
                        name = "readme.txt",
                        contents = "hello",
                    ),
                ),
            ),
            runtime = com.hackwars.rewrite.protocol.ClientRuntimeState(
                countdownSeconds = 12,
                currentCpuLoad = 8.5,
            ),
        )
        val delta = ClientGameSectionsProjection(
            economy = com.hackwars.rewrite.protocol.ClientEconomyState(
                pettyCash = 250.0,
                bankMoney = 25.0,
                defaultBankPort = 4,
            ),
            filesystem = ClientFilesystemState(
                currentPath = "/Public",
                directoriesByPath = mapOf(
                    "/Public" to ClientDirectoryEntry(
                        path = "/Public",
                        name = "Public",
                    ),
                    "/Public/Archive" to ClientDirectoryEntry(
                        path = "/Public/Archive",
                        name = "Archive",
                    ),
                ),
                filesByPath = mapOf(
                    "/Public/readme.txt" to ClientStoredFile(
                        path = "/Public/readme.txt",
                        name = "readme.txt",
                        contents = "hello",
                    ),
                    "/Public/notes.txt" to ClientStoredFile(
                        path = "/Public/notes.txt",
                        name = "notes.txt",
                        contents = "notes",
                    ),
                ),
            ),
            runtime = com.hackwars.rewrite.protocol.ClientRuntimeState(
                countdownSeconds = 11,
                currentCpuLoad = 9.0,
            ),
        )

        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.snapshot(
                gameStateId = "LOCAL-IP",
                sequence = 7,
                payload = RewriteClientJson.encode(ClientGameSnapshot.serializer(), snapshot),
            ),
        )
        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.delta(
                gameStateId = "LOCAL-IP",
                sequence = 8,
                changedPaths = listOf(
                    "economy.pettyCash",
                    "runtime.countdownSeconds",
                    "filesystem.filesByPath./Public/notes.txt",
                ),
                deltaKeys = listOf("economy", "filesystem", "runtime"),
                payload = RewriteClientJson.encode(ClientGameDeltaProjection.serializer(), delta),
            ),
        )

        val decoded = store.snapshot().game.decodedGame
        assertEquals(125.0, decoded.latestSnapshot?.economy?.pettyCash)
        assertEquals(250.0, decoded.shellState?.economy?.pettyCash)
        assertEquals("/Public", decoded.shellState?.filesystem?.currentPath)
        assertTrue(decoded.shellState?.filesystem?.filesByPath?.containsKey("/Public/notes.txt") == true)
        assertEquals(11, decoded.shellState?.runtime?.countdownSeconds)
        assertIs<ClientGameSectionsProjection>(decoded.lastDelta?.projection)
    }

    @Test
    fun summaryProgramAndUiFramesUpdateDecodedBucketsWithoutWipingShellState() {
        val store = RewriteClientStore()
        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.snapshot(
                gameStateId = "LOCAL-IP",
                sequence = 7,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        version = 7,
                        identity = com.hackwars.rewrite.protocol.ClientComputerIdentity(playerIp = "LOCAL-IP"),
                        economy = com.hackwars.rewrite.protocol.ClientEconomyState(pettyCash = 125.0),
                    ),
                ),
            ),
        )
        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.delta(
                gameStateId = "LOCAL-IP",
                sequence = 8,
                changedPaths = listOf("version"),
                deltaKeys = listOf("identity"),
                payload = RewriteClientJson.encode(
                    ClientGameDeltaProjection.serializer(),
                    ClientGameStateSummaryProjection(
                        version = 9,
                        playerIp = "LOCAL-IP",
                    ),
                ),
            ),
        )
        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.programUpdate(
                programId = "program-1",
                programType = "attack",
                status = hackwars.rewrite.v1.ProgramStatus.PROGRAM_STATUS_RUNNING,
                payload = RewriteClientJson.encode(
                    ClientProgramUpdate.serializer(),
                    ClientProgramUpdate(
                        programId = "program-1",
                        programType = "attack",
                        status = ClientProgramLifecycleStatus.RUNNING,
                        progress = ClientProgramProgress(message = "tick", completedSteps = 1, totalSteps = 3),
                    ),
                ),
            ),
        )
        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.gameUiEvent(
                eventId = "ui-1",
                eventType = "popup",
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

        val decoded = store.snapshot().game.decodedGame
        assertEquals(9, decoded.shellState?.version)
        assertEquals(125.0, decoded.shellState?.economy?.pettyCash)
        assertEquals(ClientProgramLifecycleStatus.RUNNING, decoded.programUpdatesById["program-1"]?.status)
        assertEquals(1, decoded.uiNotices.size)
        assertIs<ClientAttackMessageUiEvent>(decoded.uiNotices.single().event)
        assertIs<ClientGameStateSummaryProjection>(decoded.lastDelta?.projection)
    }

    @Test
    fun malformedPayloadsRecordDecodeErrorsWithoutCorruptingDecodedState() {
        val store = RewriteClientStore()
        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.snapshot(
                gameStateId = "LOCAL-IP",
                sequence = 7,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        version = 7,
                        economy = com.hackwars.rewrite.protocol.ClientEconomyState(pettyCash = 125.0),
                    ),
                ),
            ),
        )

        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.programUpdate(
                programId = "program-1",
                programType = "attack",
                status = hackwars.rewrite.v1.ProgramStatus.PROGRAM_STATUS_RUNNING,
                payload = "not-json".encodeToByteArray(),
            ),
        )

        val decoded = store.snapshot().game.decodedGame
        assertEquals(125.0, decoded.shellState?.economy?.pettyCash)
        assertEquals(1, decoded.decodeErrors.size)
        assertEquals(FramePayloadType.PROGRAM_UPDATE, decoded.decodeErrors.single().payloadType)
        assertTrue(decoded.programUpdatesById.isEmpty())
        assertNotNull(store.snapshot().game.inbox.lastProgramUpdate)
    }

    @Test
    fun malformedFilesystemDeltaDoesNotCorruptExistingDecodedFilesystemState() {
        val store = RewriteClientStore()
        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.snapshot(
                gameStateId = "LOCAL-IP",
                sequence = 7,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        version = 7,
                        filesystem = ClientFilesystemState(
                            currentPath = "/Public",
                            filesByPath = mapOf(
                                "/Public/readme.txt" to ClientStoredFile(
                                    path = "/Public/readme.txt",
                                    name = "readme.txt",
                                    contents = "hello",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        store.recordInboundFrame(
            service = RewriteService.GAME,
            frame = RewriteFrames.delta(
                gameStateId = "LOCAL-IP",
                sequence = 8,
                changedPaths = listOf("filesystem.filesByPath./Public/readme.txt"),
                deltaKeys = listOf("filesystem"),
                payload = "not-json".encodeToByteArray(),
            ),
        )

        val decoded = store.snapshot().game.decodedGame
        assertEquals("/Public", decoded.shellState?.filesystem?.currentPath)
        assertTrue(decoded.shellState?.filesystem?.filesByPath?.containsKey("/Public/readme.txt") == true)
        assertEquals(FramePayloadType.DELTA, decoded.decodeErrors.single().payloadType)
        assertNotNull(store.snapshot().game.inbox.lastDelta)
    }

    @Test
    fun gameUiVariantsLandInDecodedNoticeFeed() {
        val store = RewriteClientStore()
        val uiEvents: List<ClientGameUiEvent> = listOf(
            ClientPopupUiEvent(message = "Warning", style = ClientPopupUiStyle.ERROR),
            ClientTextMessageUiEvent(message = "Daily pay successfully changed."),
            ClientAttackMessageUiEvent(message = "Redirect receipt", port = 9, ip = "TARGET-IP"),
            ClientZombieAttackUiEvent(message = "Computer at ZOMBIE-IP just overheated!", zombieIp = "ZOMBIE-IP", sourcePort = 8),
            ClientShowChoicesUiEvent(targetIp = "TARGET-IP", targetPort = 6, choiceType = ClientShowChoicesType.HTTP, windowHandle = 99),
        )

        uiEvents.forEachIndexed { index, event ->
            store.recordInboundFrame(
                service = RewriteService.GAME,
                frame = RewriteFrames.gameUiEvent(
                    eventId = "ui-$index",
                    eventType = "event-$index",
                    payload = RewriteClientJson.encode(ClientGameUiEvent.serializer(), event),
                ),
            )
        }

        val notices = store.snapshot().game.decodedGame.uiNotices
        assertEquals(5, notices.size)
        assertIs<ClientPopupUiEvent>(notices[0].event)
        assertIs<ClientTextMessageUiEvent>(notices[1].event)
        assertIs<ClientAttackMessageUiEvent>(notices[2].event)
        assertIs<ClientZombieAttackUiEvent>(notices[3].event)
        assertIs<ClientShowChoicesUiEvent>(notices[4].event)
        assertEquals(ClientShowChoicesType.HTTP, (notices[4].event as ClientShowChoicesUiEvent).choiceType)
    }
}
