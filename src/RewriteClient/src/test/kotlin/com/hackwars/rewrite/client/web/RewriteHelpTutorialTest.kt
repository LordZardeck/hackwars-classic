package com.hackwars.rewrite.client.web

import com.hackwars.rewrite.client.RewriteGameConnectionConfig
import com.hackwars.rewrite.client.RewriteLoginAuthGateway
import com.hackwars.rewrite.client.RewriteLoginAuthResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.RewriteServiceSession
import com.hackwars.rewrite.client.RewriteServiceSessionGateway
import com.hackwars.rewrite.protocol.ClientHelpTopicEntry
import com.hackwars.rewrite.protocol.ClientHelpTopicListResponse
import com.hackwars.rewrite.protocol.ClientRequestHelpTopicListPayload
import com.hackwars.rewrite.protocol.ClientRequestTutorialPayload
import com.hackwars.rewrite.protocol.ClientTutorialResponse
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteHelpTutorialTest {
    @Test
    fun controllerHelpAndTutorialHelpersSendExpectedPayloads() = runTest {
        val sessionGateway = FakeHelpTutorialSessionGateway()
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

        val helpDeferred = backgroundScope.async {
            controller.requestHelpTopicList("Banking")
        }
        runCurrent()

        val session = sessionGateway.requireLatestGameSession()
        val helpCommand = session.sentFrames.last().command!!
        val helpPayload = RewriteClientJson.decode(
            ClientRequestHelpTopicListPayload.serializer(),
            helpCommand.payload.toByteArray(),
        )
        assertEquals("requesthelptopiclist", helpCommand.command_name)
        assertEquals("Banking", helpPayload.topicGroup)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = helpCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientHelpTopicListResponse.serializer(),
                    ClientHelpTopicListResponse(
                        topicGroup = "Banking",
                        topics = listOf(
                            ClientHelpTopicEntry(
                                id = "banking-deposit",
                                name = "Deposit Money",
                                targetUrl = "http://203.0.113.211/",
                            ),
                        ),
                    ),
                ),
            ),
        )
        runCurrent()
        assertTrue(helpDeferred.await() is com.hackwars.rewrite.client.RewriteGameCommandResult.Success)

        val tutorialDeferred = backgroundScope.async {
            controller.requestTutorial("first-attack")
        }
        runCurrent()

        val tutorialCommand = session.sentFrames.last().command!!
        val tutorialPayload = RewriteClientJson.decode(
            ClientRequestTutorialPayload.serializer(),
            tutorialCommand.payload.toByteArray(),
        )
        assertEquals("requesttutorial", tutorialCommand.command_name)
        assertEquals("first-attack", tutorialPayload.tutorialId)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = tutorialCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientTutorialResponse.serializer(),
                    ClientTutorialResponse(
                        tutorialId = "first-attack",
                        title = "First Attack",
                        body = "<p>Welcome</p>",
                    ),
                ),
            ),
        )
        runCurrent()
        assertTrue(tutorialDeferred.await() is com.hackwars.rewrite.client.RewriteGameCommandResult.Success)
    }

    private fun testController(
        sessionGateway: FakeHelpTutorialSessionGateway,
        scheduler: TestCoroutineScheduler,
    ): RewriteRootController {
        return RewriteRootController(
            gameConnectionConfig = RewriteGameConnectionConfig(),
            authGateway = FakeHelpTutorialAuthGateway(),
            sessionGateway = sessionGateway,
            workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler)),
        )
    }

    private class FakeHelpTutorialAuthGateway : RewriteLoginAuthGateway {
        override suspend fun authenticate(email: String, password: CharArray): RewriteLoginAuthResult {
            password.fill('\u0000')
            return RewriteLoginAuthResult.success("PF-LOCAL", "SESSION-LOCAL")
        }
    }

    private class FakeHelpTutorialSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeHelpTutorialSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeHelpTutorialSession(service, onInboundFrame).also { sessions += it }
        }

        fun requireLatestGameSession(): FakeHelpTutorialSession {
            return sessions.last { it.service == RewriteService.GAME }
        }
    }

    private class FakeHelpTutorialSession(
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

        override fun close() = Unit
    }
}
