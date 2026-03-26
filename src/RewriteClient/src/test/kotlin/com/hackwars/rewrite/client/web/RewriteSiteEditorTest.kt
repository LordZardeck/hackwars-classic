package com.hackwars.rewrite.client.web

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteGameConnectionConfig
import com.hackwars.rewrite.client.RewriteLoginAuthGateway
import com.hackwars.rewrite.client.RewriteLoginAuthResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.RewriteServiceSession
import com.hackwars.rewrite.client.RewriteServiceSessionGateway
import com.hackwars.rewrite.protocol.ClientPageEditorResponse
import com.hackwars.rewrite.protocol.ClientRequestPagePayload
import com.hackwars.rewrite.protocol.ClientSavePagePayload
import com.hackwars.rewrite.protocol.ClientSavePageResponse
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteSiteEditorTest {
    @Test
    fun controllerPageHelpersSendExpectedPayloads() = runTest {
        val sessionGateway = FakeSiteEditorSessionGateway()
        val controller = testController(sessionGateway, testScheduler)
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

        val requestPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestPage()
        }
        runCurrent()
        val session = sessionGateway.requireLatestGameSession()
        val requestCommand = session.sentFrames.last().command!!
        val requestPayload = RewriteClientJson.decode(
            ClientRequestPagePayload.serializer(),
            requestCommand.payload.toByteArray(),
        )
        assertEquals("requestpage", requestCommand.command_name)
        assertEquals("192.0.2.10", requestPayload.ip)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = requestCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientPageEditorResponse.serializer(),
                    ClientPageEditorResponse(
                        stateId = "192.0.2.10",
                        title = "Homepage",
                        body = "<h1>Hello</h1>",
                        version = 3,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientPageEditorResponse>>(requestPending.await())

        val savePending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.savePage(
                title = "Homepage",
                body = "<center>Updated</center>",
            )
        }
        runCurrent()
        val saveCommand = session.sentFrames.last().command!!
        val savePayload = RewriteClientJson.decode(
            ClientSavePagePayload.serializer(),
            saveCommand.payload.toByteArray(),
        )
        assertEquals("savepage", saveCommand.command_name)
        assertEquals("192.0.2.10", savePayload.ip)
        assertEquals("Homepage", savePayload.title)
        assertEquals("<center>Updated</center>", savePayload.body)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = saveCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientSavePageResponse.serializer(),
                    ClientSavePageResponse(
                        stateId = "192.0.2.10",
                        title = "Homepage",
                        body = "<center>Updated</center>",
                        version = 4,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientSavePageResponse>>(savePending.await())
    }

    @Test
    fun siteEditorFormattingHelpersProduceLegacyHtmlInsertions() {
        val boldEdit = wrapSiteEditorSelection(
            text = "Alpha",
            selectionStart = 0,
            selectionEnd = 5,
            prefix = "<b>",
            suffix = "</b>",
        )
        val emptyUnderline = wrapSiteEditorSelection(
            text = "Alpha",
            selectionStart = 2,
            selectionEnd = 2,
            prefix = "<u>",
            suffix = "</u>",
        )
        val insertedLink = insertSiteEditorMarkup(
            text = "<p>Hello</p>",
            caretPosition = 3,
            insertion = "<a href=\"target\">name</a>",
        )

        assertEquals("<b>Alpha</b>", boldEdit.text)
        assertEquals("Al<u></u>pha", emptyUnderline.text)
        assertEquals("<p><a href=\"target\">name</a>Hello</p>", insertedLink.text)
    }

    @Test
    fun siteEditorTracksDirtyTitlePreviewRefreshAndSaveReset() {
        val window = invokeAndWaitResult {
            RewriteSiteEditorWindow(
                controller = RewriteRootController(
                    authGateway = FakeSiteEditorAuthGateway(),
                    sessionGateway = FakeSiteEditorSessionGateway(),
                ),
            )
        }
        try {
            SwingUtilities.invokeAndWait {
                window.loadPageForTest(
                    ClientPageEditorResponse(
                        stateId = "192.0.2.10",
                        title = "Homepage",
                        body = "<h1>Hello</h1>",
                        version = 5,
                    ),
                )
                window.applyTitleForTest("Updated Homepage")
                window.setSourceBodyForTest("<center>Updated</center>")
                window.selectPreviewTabForTest()
            }

            assertTrue(window.isDirtyForTest())
            assertEquals("Updated Homepage", window.currentTitleForTest())
            assertTrue(window.previewHtmlForTest().contains("Updated"))

            SwingUtilities.invokeAndWait {
                window.applySaveResultForTest(
                    ClientSavePageResponse(
                        stateId = "192.0.2.10",
                        title = "Updated Homepage",
                        body = "<center>Updated</center>",
                        version = 6,
                    ),
                )
            }

            assertFalse(window.isDirtyForTest())
            assertEquals("Updated Homepage", window.currentTitleForTest())
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }

    @Test
    fun buildSitePreviewHtmlWrapsBodyWhenNeeded() {
        assertTrue(
            buildSiteEditorPreviewHtml(
                title = "Homepage",
                body = "<h1>Hello</h1>",
            ).contains("<title>Homepage</title>"),
        )
        assertEquals(
            "<html><body>Ready</body></html>",
            buildSiteEditorPreviewHtml(
                title = "Ignored",
                body = "<html><body>Ready</body></html>",
            ),
        )
    }

    private fun testController(
        sessionGateway: FakeSiteEditorSessionGateway,
        scheduler: TestCoroutineScheduler,
    ): RewriteRootController {
        return RewriteRootController(
            gameConnectionConfig = RewriteGameConnectionConfig(),
            authGateway = FakeSiteEditorAuthGateway(),
            sessionGateway = sessionGateway,
            workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler)),
        )
    }

    private fun <T> invokeAndWaitResult(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait {
            result = runCatching(block)
        }
        return result!!.getOrThrow()
    }

    private class FakeSiteEditorAuthGateway : RewriteLoginAuthGateway {
        override suspend fun authenticate(
            email: String,
            password: CharArray,
        ): RewriteLoginAuthResult {
            return RewriteLoginAuthResult.success("PF-LOCAL", "SESSION-LOCAL")
        }
    }

    private class FakeSiteEditorSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeSiteEditorSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeSiteEditorSession(service, onInboundFrame).also { sessions += it }
        }

        fun requireLatestGameSession(): FakeSiteEditorSession {
            return sessions.lastOrNull { it.service == RewriteService.GAME }
                ?: error("No GAME session was opened.")
        }
    }

    private class FakeSiteEditorSession(
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
