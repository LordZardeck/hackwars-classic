package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserController
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientFilesystemState
import com.hackwars.rewrite.protocol.ClientGameDeltaProjection
import com.hackwars.rewrite.protocol.ClientGameSectionsProjection
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientRequestDirectoryPayload
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteLocalDirectoryBrowserTest {
    @Test
    fun browserControllerIssuesInitialRequestAndResolvesListingByCommandId() = runTest {
        val sessionGateway = FakeDirectorySessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        val browserController = RewriteLocalDirectoryBrowserController(
            rootController = controller,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        acceptGameAuth(controller)

        try {
            browserController.activate()
            runCurrent()

            val command = sessionGateway.requireLatestGameCommand()
            val payload = RewriteClientJson.decode(
                ClientRequestDirectoryPayload.serializer(),
                command.payload.toByteArray(),
            )

            assertEquals("requestdirectory", command.command_name)
            assertNull(payload.path)

            controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    payload = RewriteClientJson.encode(
                        ClientDirectoryListingResponse.serializer(),
                        directoryListing(
                            path = "/",
                            directories = listOf(directory("/Public")),
                            files = listOf(file("/readme.txt")),
                        ),
                    ),
                ),
            )
            flushEdt()
            advanceUntilIdle()
            flushEdt()

            assertEquals("/", browserController.snapshot().displayedPath)
            assertEquals(listOf("/Public", "/readme.txt"), browserController.snapshot().entries.map { it.path })
        } finally {
            browserController.close()
            controller.shutdown()
        }
    }

    @Test
    fun browserControllerNavigatesIntoFoldersAndSupportsUpAndHome() = runTest {
        val sessionGateway = FakeDirectorySessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        val browserController = RewriteLocalDirectoryBrowserController(
            rootController = controller,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        acceptGameAuth(controller)

        try {
            browserController.activate()
            runCurrent()
            respondToLatestCommand(
                controller = controller,
                sessionGateway = sessionGateway,
                response = directoryListing(
                    path = "/",
                    directories = listOf(directory("/Public")),
                ),
            )
            flushEdt()
            advanceUntilIdle()
            flushEdt()

            browserController.updateSelection("/Public")
            browserController.openSelectedDirectory()
            runCurrent()
            assertEquals("/Public", decodeLatestRequestPath(sessionGateway))

            respondToLatestCommand(
                controller = controller,
                sessionGateway = sessionGateway,
                response = directoryListing(
                    path = "/Public",
                    directories = listOf(directory("/Public/Archive")),
                    files = listOf(file("/Public/notes.txt")),
                ),
            )
            flushEdt()
            advanceUntilIdle()
            flushEdt()

            browserController.navigateUp()
            runCurrent()
            assertEquals("/", decodeLatestRequestPath(sessionGateway))

            respondToLatestCommand(
                controller = controller,
                sessionGateway = sessionGateway,
                response = directoryListing(path = "/"),
            )
            flushEdt()
            advanceUntilIdle()
            flushEdt()

            browserController.navigateHome()
            runCurrent()
            assertEquals("/", decodeLatestRequestPath(sessionGateway))
        } finally {
            browserController.close()
            controller.shutdown()
        }
    }

    @Test
    fun relevantFilesystemDeltaTriggersRefreshForDisplayedDirectory() = runTest {
        val sessionGateway = FakeDirectorySessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        val browserController = RewriteLocalDirectoryBrowserController(
            rootController = controller,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        acceptGameAuth(controller)

        try {
            browserController.activate()
            runCurrent()
            respondToLatestCommand(
                controller = controller,
                sessionGateway = sessionGateway,
                response = directoryListing(
                    path = "/Public",
                    files = listOf(file("/Public/readme.txt")),
                ),
            )
            flushEdt()
            advanceUntilIdle()
            flushEdt()
            val commandCountBeforeDelta = sessionGateway.requireLatestSession(RewriteService.GAME).sentFrames.size

            controller.accept(
                RewriteService.GAME,
                RewriteFrames.delta(
                    gameStateId = "192.0.2.10",
                    sequence = 8,
                    changedPaths = listOf("filesystem.filesByPath./Public/readme.txt"),
                    deltaKeys = listOf("filesystem"),
                    payload = RewriteClientJson.encode(
                        ClientGameDeltaProjection.serializer(),
                        ClientGameSectionsProjection(
                            filesystem = ClientFilesystemState(
                                currentPath = "/Public",
                                filesByPath = mapOf(
                                    "/Public/readme.txt" to file("/Public/readme.txt"),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            runCurrent()
            flushEdt()
            runCurrent()
            advanceUntilIdle()
            flushEdt()

            val session = sessionGateway.requireLatestSession(RewriteService.GAME)
            assertEquals(commandCountBeforeDelta + 1, session.sentFrames.size)
            assertEquals("/Public", decodeLatestRequestPath(sessionGateway))
        } finally {
            browserController.close()
            controller.shutdown()
        }
    }

    private fun testController(
        sessionGateway: RewriteServiceSessionGateway,
        scheduler: TestCoroutineScheduler,
    ): RewriteRootController {
        return RewriteRootController(
            authGateway = DeterministicRewriteLoginAuthGateway(),
            sessionGateway = sessionGateway,
            workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler)),
        )
    }

    private fun acceptGameAuth(controller: RewriteRootController) {
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.snapshot(
                gameStateId = "192.0.2.10",
                sequence = 1,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(id = "192.0.2.10", version = 1),
                ),
            ),
        )
    }

    private fun respondToLatestCommand(
        controller: RewriteRootController,
        sessionGateway: FakeDirectorySessionGateway,
        response: ClientDirectoryListingResponse,
    ) {
        val command = sessionGateway.requireLatestGameCommand()
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientDirectoryListingResponse.serializer(),
                    response,
                ),
            ),
        )
    }

    private fun decodeLatestRequestPath(sessionGateway: FakeDirectorySessionGateway): String? {
        val command = sessionGateway.requireLatestGameCommand()
        return RewriteClientJson.decode(
            ClientRequestDirectoryPayload.serializer(),
            command.payload.toByteArray(),
        ).path
    }

    private fun directoryListing(
        path: String,
        directories: List<ClientDirectoryEntry> = emptyList(),
        files: List<ClientStoredFile> = emptyList(),
    ): ClientDirectoryListingResponse {
        return ClientDirectoryListingResponse(
            stateId = "192.0.2.10",
            path = path,
            directories = directories,
            files = files,
            version = 5,
        )
    }

    private fun directory(path: String): ClientDirectoryEntry {
        return ClientDirectoryEntry(
            path = path,
            name = path.substringAfterLast('/').ifBlank { "/" },
        )
    }

    private fun file(path: String): ClientStoredFile {
        return ClientStoredFile(
            path = path,
            name = path.substringAfterLast('/'),
            contents = "file:$path",
        )
    }

    private fun flushEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            return
        }
        SwingUtilities.invokeAndWait {}
    }

    private class FakeDirectorySessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeDirectorySession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeDirectorySession(
                service = service,
                onInboundFrame = onInboundFrame,
            ).also { sessions += it }
        }

        fun requireLatestSession(service: RewriteService): FakeDirectorySession {
            return sessions.last { it.service == service }
        }

        fun requireLatestGameCommand() = requireLatestSession(RewriteService.GAME).sentFrames.last().command!!
    }

    private class FakeDirectorySession(
        override val service: RewriteService,
        private val onInboundFrame: (FrameEnvelope) -> Unit,
    ) : RewriteServiceSession {
        val sentFrames = mutableListOf<FrameEnvelope>()

        override suspend fun send(frame: FrameEnvelope) {
            sentFrames += frame
        }

        override fun receive(frame: FrameEnvelope) {
            onInboundFrame(frame)
        }

        override fun close() {
            Unit
        }
    }
}
