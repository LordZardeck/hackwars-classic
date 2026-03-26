package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientPageEditorResponse
import com.hackwars.rewrite.protocol.ClientRequestPagePayload
import com.hackwars.rewrite.protocol.ClientSavePagePayload
import com.hackwars.rewrite.protocol.ClientSavePageResponse
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.ErrorEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.time.Instant
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JEditorPane
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteSiteEditorUiTest {
    @Test
    fun siteEditorLaunchesAsRealWindowRequestsPageAndReusesSingleInstance() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeSiteEditorUiSessionGateway()
        val frame = siteEditorReadyFrame(sessionGateway)
        try {
            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.SITE_EDITOR)
                frame.controller.launchShellCommand(RewriteShellCommand.SITE_EDITOR)
            }

            val editorWindow = waitForWindow(frame, "rewrite-site-editor-window")
            waitUntil { sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true }
            val requestCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val requestPayload = RewriteClientJson.decode(
                ClientRequestPagePayload.serializer(),
                requestCommand.payload.toByteArray(),
            )
            assertEquals("requestpage", requestCommand.command_name)
            assertEquals("192.0.2.10", requestPayload.ip)
            waitUntil {
                frame.desktopPane.allFrames.toList().count { it.name == "rewrite-site-editor-window" } == 1
            }

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = requestCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientPageEditorResponse.serializer(),
                        ClientPageEditorResponse(
                            stateId = "192.0.2.10",
                            title = "Homepage",
                            body = "<h1>Hello</h1>",
                            version = 2,
                        ),
                    ),
                ),
            )

            waitUntil {
                editorWindow.title == "Website Editor - Homepage" &&
                    textArea(editorWindow, "rewrite-site-editor-source-area").text == "<h1>Hello</h1>" &&
                    textPane(editorWindow, "rewrite-site-editor-preview-pane").text.contains("Hello")
            }
            assertEquals("Website Editor - Homepage", editorWindow.title)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun insertLinkDialogInsertsExpectedAnchorMarkup() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeSiteEditorUiSessionGateway()
        val frame = siteEditorReadyFrame(sessionGateway)
        try {
            val editorWindow = openAndLoadSiteEditor(frame, sessionGateway)

            SwingUtilities.invokeAndWait {
                val area = textArea(editorWindow, "rewrite-site-editor-source-area")
                area.caretPosition = area.text.length
                button(editorWindow, "rewrite-site-editor-insert-link-button").doClick()
            }

            val dialog = waitForDialog("Insert Link")
            SwingUtilities.invokeAndWait {
                textField(dialog, "rewrite-site-editor-link-url-field").text = "198.51.100.40"
                textField(dialog, "rewrite-site-editor-link-name-field").text = "Store"
                button(dialog, "rewrite-site-editor-link-insert-button").doClick()
            }

            waitUntil {
                textArea(editorWindow, "rewrite-site-editor-source-area").text.contains("<a href=\"198.51.100.40\">Store</a>")
            }
            assertTrue(textArea(editorWindow, "rewrite-site-editor-source-area").text.contains("<a href=\"198.51.100.40\">Store</a>"))
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun successfulSaveKeepsEditorOpenAndFailedSaveShowsInlineError() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeSiteEditorUiSessionGateway()
        val frame = siteEditorReadyFrame(sessionGateway)
        try {
            val editorWindow = openAndLoadSiteEditor(frame, sessionGateway)

            SwingUtilities.invokeAndWait {
                textArea(editorWindow, "rewrite-site-editor-source-area").text = "<center>Updated</center>"
                button(editorWindow, "rewrite-site-editor-save-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.size >= 2 }
            val saveCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            val savePayload = RewriteClientJson.decode(
                ClientSavePagePayload.serializer(),
                saveCommand.payload.toByteArray(),
            )
            assertEquals("savepage", saveCommand.command_name)
            assertEquals("192.0.2.10", savePayload.ip)
            assertEquals("<center>Updated</center>", savePayload.body)

            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = saveCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientSavePageResponse.serializer(),
                        ClientSavePageResponse(
                            stateId = "192.0.2.10",
                            title = "Homepage",
                            body = "<center>Updated</center>",
                            version = 3,
                        ),
                    ),
                ),
            )

            waitUntil {
                label(editorWindow, "rewrite-site-editor-status").text == "Website saved."
            }
            assertTrue(editorWindow.isDisplayable)

            SwingUtilities.invokeAndWait {
                textArea(editorWindow, "rewrite-site-editor-source-area").text = "<h1>Broken</h1>"
                button(editorWindow, "rewrite-site-editor-save-button").doClick()
            }

            waitUntil { sessionGateway.latestGameSession()!!.sentFrames.size >= 3 }
            val failedSaveCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = failedSaveCommand.command_id,
                    status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                    error = ErrorEnvelope(
                        code = "SAVE_FAILED",
                        message = "Website save failed.",
                        retryable = false,
                    ),
                ),
            )

            waitUntil {
                label(editorWindow, "rewrite-site-editor-error").text == "Website save failed."
            }
            assertTrue(editorWindow.isDisplayable)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun dirtyClosePromptOpensAndCancelKeepsEditorOpen() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val sessionGateway = FakeSiteEditorUiSessionGateway()
        val frame = siteEditorReadyFrame(sessionGateway)
        try {
            val editorWindow = openAndLoadSiteEditor(frame, sessionGateway)

            SwingUtilities.invokeAndWait {
                textArea(editorWindow, "rewrite-site-editor-source-area").text = "<h1>Changed</h1>"
            }
            SwingUtilities.invokeLater {
                editorWindow.doDefaultCloseAction()
            }

            val cancelDialog = waitForDialog("Unsaved Changes")
            SwingUtilities.invokeLater {
                buttonByText(cancelDialog, "Cancel").doClick()
            }
            waitUntil { !editorWindow.isClosed }
            assertTrue(!editorWindow.isClosed)

        } finally {
            disposeFrame(frame)
        }
    }

    private fun openAndLoadSiteEditor(
        frame: RewriteRootFrame,
        sessionGateway: FakeSiteEditorUiSessionGateway,
    ): JInternalFrame {
        SwingUtilities.invokeAndWait {
            frame.controller.launchShellCommand(RewriteShellCommand.SITE_EDITOR)
        }
        val editorWindow = waitForWindow(frame, "rewrite-site-editor-window")
        waitUntil { sessionGateway.latestGameSession()?.sentFrames?.isNotEmpty() == true }
        val requestCommand = sessionGateway.latestGameSession()!!.sentFrames.last().command!!
        frame.controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = requestCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientPageEditorResponse.serializer(),
                    ClientPageEditorResponse(
                        stateId = "192.0.2.10",
                        title = "Homepage",
                        body = "<h1>Hello</h1>",
                        version = 2,
                    ),
                ),
            ),
        )
        waitUntil { editorWindow.title == "Website Editor - Homepage" }
        return editorWindow
    }

    private fun siteEditorReadyFrame(
        sessionGateway: RewriteServiceSessionGateway,
    ): RewriteRootFrame {
        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = sessionGateway,
                ),
            ).apply { isVisible = true }
        }
        frame.controller.store.showDesktop()
        frame.controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
        frame.controller.accept(
            RewriteService.GAME,
            snapshotFrame(
                ClientGameSnapshot(
                    id = "192.0.2.10",
                    identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                ),
            ),
        )
        waitUntil { frame.desktopPane.isShowing && frame.jMenuBar != null }
        return frame
    }

    private fun snapshotFrame(
        snapshot: ClientGameSnapshot,
    ): FrameEnvelope {
        return RewriteFrames.snapshot(
            gameStateId = snapshot.id,
            sequence = snapshot.version,
            payload = RewriteClientJson.encode(
                ClientGameSnapshot.serializer(),
                snapshot,
            ),
        )
    }

    private fun disposeFrame(frame: RewriteRootFrame) {
        SwingUtilities.invokeAndWait {
            frame.dispose()
        }
    }

    private fun waitForWindow(
        frame: RewriteRootFrame,
        windowName: String,
    ): JInternalFrame {
        waitUntil { frame.desktopPane.allFrames.toList().any { it.name == windowName } }
        return frame.desktopPane.allFrames.toList().first { it.name == windowName }
    }

    private fun waitForDialog(
        title: String,
    ): JDialog {
        waitUntil {
            java.awt.Window.getWindows().any { window ->
                window is JDialog && window.isDisplayable && window.title == title
            }
        }
        return java.awt.Window.getWindows()
            .filterIsInstance<JDialog>()
            .first { it.isDisplayable && it.title == title }
    }

    private fun label(root: Component, name: String): JLabel {
        return findComponent(root, name) as? JLabel
            ?: error("Unable to find JLabel named $name")
    }

    private fun button(root: Component, name: String): JButton {
        return findComponent(root, name) as? JButton
            ?: error("Unable to find JButton named $name")
    }

    private fun buttonByText(root: Component, text: String): JButton {
        return findComponents(root)
            .filterIsInstance<JButton>()
            .firstOrNull { it.text == text }
            ?: error("Unable to find JButton with text $text")
    }

    private fun textArea(root: Component, name: String): JTextArea {
        return findComponent(root, name) as? JTextArea
            ?: error("Unable to find JTextArea named $name")
    }

    private fun textField(root: Component, name: String): JTextField {
        return findComponent(root, name) as? JTextField
            ?: error("Unable to find JTextField named $name")
    }

    private fun textPane(root: Component, name: String): JEditorPane {
        return findComponent(root, name) as? JEditorPane
            ?: error("Unable to find JEditorPane named $name")
    }

    private fun findComponent(root: Component, name: String): Component? {
        if (root.name == name) {
            return root
        }
        if (root is Container) {
            root.components.forEach { child ->
                findComponent(child, name)?.let { return it }
            }
        }
        return null
    }

    private fun findComponents(root: Component): List<Component> {
        if (root !is Container) {
            return listOf(root)
        }
        return listOf(root) + root.components.flatMap(::findComponents)
    }

    private fun waitUntil(
        timeoutMillis: Long = 5_000,
        predicate: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            if (predicate()) {
                return
            }
            Thread.sleep(25)
        }
        error("Condition was not met within ${timeoutMillis}ms")
    }

    private fun <T> invokeAndWaitResult(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait {
            result = runCatching(block)
        }
        return result!!.getOrThrow()
    }

    private class FakeSiteEditorUiSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeSiteEditorUiSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeSiteEditorUiSession(service, onInboundFrame).also { sessions += it }
        }

        fun latestGameSession(): FakeSiteEditorUiSession? = sessions.lastOrNull { it.service == RewriteService.GAME }
    }

    private class FakeSiteEditorUiSession(
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
