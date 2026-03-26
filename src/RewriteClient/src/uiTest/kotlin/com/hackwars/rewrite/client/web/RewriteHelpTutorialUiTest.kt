package com.hackwars.rewrite.client.web

import com.hackwars.rewrite.client.RewriteRootFrame
import com.hackwars.rewrite.client.RewriteServiceSession
import com.hackwars.rewrite.client.RewriteServiceSessionGateway
import com.hackwars.rewrite.client.testsupport.rewriteUiAuthenticatedDesktopFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiDisposeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiFindNamedComponent
import com.hackwars.rewrite.client.testsupport.rewriteUiInvokeAndWaitResult
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForWindow
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitUntil
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientHelpTopicEntry
import com.hackwars.rewrite.protocol.ClientHelpTopicListResponse
import com.hackwars.rewrite.protocol.ClientPreferenceState
import com.hackwars.rewrite.protocol.ClientRequestHelpTopicListPayload
import com.hackwars.rewrite.protocol.ClientRequestTutorialPayload
import com.hackwars.rewrite.protocol.ClientTutorialResponse
import com.hackwars.rewrite.protocol.ClientWebsiteRenderResponse
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.ErrorEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.GraphicsEnvironment
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteHelpTutorialUiTest {
    @Test
    fun tutorialLaunchesRealWindowReusesInstanceAndLoadsTutorialContent() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeHelpTutorialUiSessionGateway()
        val frame = helpTutorialReadyFrame(sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.TUTORIAL_FIRST_ATTACK)
                frame.controller.launchShellCommand(RewriteShellCommand.TUTORIAL_FIRST_ATTACK)
            }

            val tutorialWindow = rewriteUiWaitForWindow(frame, "rewrite-tutorial-window")
            rewriteUiWaitUntil {
                rewriteUiInvokeAndWaitResult {
                    frame.desktopPane.allFrames.count { it.name == "rewrite-tutorial-window" } == 1
                }
            }

            rewriteUiWaitUntil { latestGameCommand(sessionGateway, "requesttutorial") != null }
            val tutorialCommand = latestGameCommand(sessionGateway, "requesttutorial")!!
            val payload = RewriteClientJson.decode(
                ClientRequestTutorialPayload.serializer(),
                tutorialCommand.payload.toByteArray(),
            )
            assertEquals("first-attack", payload.tutorialId)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = tutorialCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientTutorialResponse.serializer(),
                        ClientTutorialResponse(
                            tutorialId = "first-attack",
                            title = "First Attack",
                            body = "<p>Welcome to Hack Wars!</p>",
                        ),
                    ),
                ),
            )

            rewriteUiWaitUntil {
                label(tutorialWindow, "rewrite-tutorial-title").text == "First Attack" &&
                    label(tutorialWindow, "rewrite-tutorial-status").text == "Tutorial loaded."
            }
            assertTrue(editorPane(tutorialWindow, "rewrite-tutorial-html-pane").text.contains("Welcome to Hack Wars!"))
        } finally {
            rewriteUiDisposeFrame(frame)
        }
    }

    @Test
    fun tutorialHelpButtonOpensReusableHelpWindowLoadsTopicsAndRendersTopicPage() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeHelpTutorialUiSessionGateway()
        val frame = helpTutorialReadyFrame(sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.TUTORIAL_FIRST_ATTACK)
            }

            val tutorialWindow = rewriteUiWaitForWindow(frame, "rewrite-tutorial-window")
            val tutorialCommand = waitForCommand(sessionGateway, "requesttutorial")
            frame.controller.accept(
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
            rewriteUiWaitUntil { label(tutorialWindow, "rewrite-tutorial-status").text == "Tutorial loaded." }

            SwingUtilities.invokeAndWait {
                button(tutorialWindow, "rewrite-tutorial-help-button").doClick()
            }

            val tutorialsCommand = waitForCommand(sessionGateway, "requesthelptopiclist", index = 0)
            assertEquals("Tutorials", decodeHelpPayload(tutorialsCommand).topicGroup)
            respondHelpTopics(
                frame = frame,
                command = tutorialsCommand,
                group = "Tutorials",
                entries = listOf(
                    ClientHelpTopicEntry(
                        id = "first-attack",
                        name = "First Attack",
                        targetUrl = "http://203.0.113.210/",
                    ),
                ),
            )

            val expectedApiGroups = listOf("Banking", "Attack", "FTP", "Watch", "Challenge", "Other")
            expectedApiGroups.forEachIndexed { index, group ->
                val command = waitForCommand(sessionGateway, "requesthelptopiclist", index = index + 1)
                assertEquals(group, decodeHelpPayload(command).topicGroup)
                respondHelpTopics(frame, command, group, emptyList())
            }

            val challengesCommand = waitForCommand(sessionGateway, "requesthelptopiclist", index = 7)
            assertEquals("Challenges", decodeHelpPayload(challengesCommand).topicGroup)
            respondHelpTopics(frame, challengesCommand, "Challenges", emptyList())

            val helpWindow = rewriteUiWaitForWindow(frame, "rewrite-help-window")
            val webpageCommand = waitForCommand(sessionGateway, "requestwebpage")
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = webpageCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientWebsiteRenderResponse.serializer(),
                        ClientWebsiteRenderResponse(
                            resolvedTargetStateId = "203.0.113.210",
                            title = "First Attack",
                            body = "<html><body><p>Visit the Store to buy a basic banking binary.</p></body></html>",
                            version = 1,
                        ),
                    ),
                ),
            )

            rewriteUiWaitUntil {
                label(helpWindow, "rewrite-help-status").text == "Viewing First Attack."
            }
            assertTrue(editorPane(helpWindow, "rewrite-help-html-pane").text.contains("Visit the Store"))

            SwingUtilities.invokeAndWait {
                button(tutorialWindow, "rewrite-tutorial-help-button").doClick()
            }

            rewriteUiWaitUntil {
                rewriteUiInvokeAndWaitResult {
                    frame.desktopPane.allFrames.count { it.name == "rewrite-help-window" } == 1
                }
            }
        } finally {
            rewriteUiDisposeFrame(frame)
        }
    }

    @Test
    fun tutorialFailureKeepsWindowOpenAndShowsInlineError() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeHelpTutorialUiSessionGateway()
        val frame = helpTutorialReadyFrame(sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.TUTORIAL_FIRST_ATTACK)
            }

            val tutorialWindow = rewriteUiWaitForWindow(frame, "rewrite-tutorial-window")
            val tutorialCommand = waitForCommand(sessionGateway, "requesttutorial")
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = tutorialCommand.command_id,
                    status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                    error = ErrorEnvelope(
                        code = "TUTORIAL_UNAVAILABLE",
                        message = "Tutorial unavailable.",
                        retryable = false,
                    ),
                ),
            )

            rewriteUiWaitUntil {
                label(tutorialWindow, "rewrite-tutorial-error").text == "Tutorial unavailable."
            }
            assertTrue(tutorialWindow.isDisplayable)
        } finally {
            rewriteUiDisposeFrame(frame)
        }
    }

    private fun helpTutorialReadyFrame(
        sessionGateway: RewriteServiceSessionGateway,
    ): RewriteRootFrame {
        return rewriteUiAuthenticatedDesktopFrame(
            sessionGateway = sessionGateway,
            snapshot = ClientGameSnapshot(
                id = "192.0.2.10",
                identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                preferences = ClientPreferenceState(
                    values = mapOf(
                        "attacktutorial" to "true",
                    ),
                ),
            ),
        )
    }

    private fun waitForCommand(
        sessionGateway: FakeHelpTutorialUiSessionGateway,
        commandName: String,
        index: Int = 0,
    ): hackwars.rewrite.v1.CommandEnvelope {
        rewriteUiWaitUntil {
            gameCommands(sessionGateway, commandName).size > index
        }
        return gameCommands(sessionGateway, commandName)[index]
    }

    private fun respondHelpTopics(
        frame: RewriteRootFrame,
        command: hackwars.rewrite.v1.CommandEnvelope,
        group: String,
        entries: List<ClientHelpTopicEntry>,
    ) {
        frame.controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientHelpTopicListResponse.serializer(),
                    ClientHelpTopicListResponse(
                        topicGroup = group,
                        topics = entries,
                    ),
                ),
            ),
        )
    }

    private fun decodeHelpPayload(
        command: hackwars.rewrite.v1.CommandEnvelope,
    ): ClientRequestHelpTopicListPayload {
        return RewriteClientJson.decode(
            ClientRequestHelpTopicListPayload.serializer(),
            command.payload.toByteArray(),
        )
    }

    private fun latestGameCommand(
        sessionGateway: FakeHelpTutorialUiSessionGateway,
        commandName: String,
    ): hackwars.rewrite.v1.CommandEnvelope? = gameCommands(sessionGateway, commandName).lastOrNull()

    private fun gameCommands(
        sessionGateway: FakeHelpTutorialUiSessionGateway,
        commandName: String,
    ): List<hackwars.rewrite.v1.CommandEnvelope> {
        return sessionGateway.latestGameSession()
            ?.sentFrames
            .orEmpty()
            .mapNotNull { it.command }
            .filter { it.command_name == commandName }
    }

    private fun button(
        root: JInternalFrame,
        name: String,
    ): JButton = rewriteUiFindNamedComponent(root, name) as JButton

    private fun label(
        root: JInternalFrame,
        name: String,
    ): JLabel = rewriteUiFindNamedComponent(root, name) as JLabel

    private fun editorPane(
        root: JInternalFrame,
        name: String,
    ): JEditorPane = rewriteUiFindNamedComponent(root, name) as JEditorPane

    private class FakeHelpTutorialUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeHelpTutorialUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeHelpTutorialUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakeHelpTutorialUiSession? {
            return sessions.lastOrNull { it.service == RewriteService.GAME }
        }
    }

    private class FakeHelpTutorialUiSession(
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
