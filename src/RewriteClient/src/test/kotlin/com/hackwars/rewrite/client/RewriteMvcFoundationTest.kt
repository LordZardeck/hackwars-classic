package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.mvc.RewriteControllerBase
import com.hackwars.rewrite.client.mvc.RewriteFrameBinding
import com.hackwars.rewrite.client.mvc.RewriteViewModel
import com.hackwars.rewrite.client.shell.RewritePlaceholderInternalFrame
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.shell.RewriteShellWindowCoordinator
import com.hackwars.rewrite.client.shell.RewriteShellWindowHost
import com.hackwars.rewrite.client.shell.RewriteShellChromeState
import com.hackwars.rewrite.client.shell.RewriteShellCountdownState
import com.hackwars.rewrite.client.shell.RewriteShellStatsRailState
import javax.swing.JInternalFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RewriteMvcFoundationTest {
    @Test
    fun controllerBaseRunsCloseLifecycleOnlyOnce() {
        val controller = RecordingController()

        controller.close()
        controller.close()

        assertEquals(1, controller.closedCount)
    }

    @Test
    fun shellWindowCoordinatorClosesBindingControllerWhenFrameDisposes() {
        val controller = RecordingController()
        val host = RecordingShellWindowHost()
        val coordinator = RewriteShellWindowCoordinator { command, _ ->
            RewriteFrameBinding(
                frame = RewritePlaceholderInternalFrame(command),
                controller = controller,
            )
        }
        coordinator.attachHost(host)

        invokeAndWait {
            coordinator.open(RewriteShellCommand.HOME)
            host.frames.single().dispose()
        }

        assertEquals(1, controller.closedCount)
    }

    @Test
    fun shellStateModelsImplementImmutableViewModelContract() {
        assertIs<RewriteViewModel>(RewriteShellChromeState())
        assertIs<RewriteViewModel>(RewriteShellStatsRailState())
        assertIs<RewriteViewModel>(RewriteShellCountdownState())
    }

    private class RecordingController : RewriteControllerBase() {
        var closedCount: Int = 0
            private set

        override fun onClosed() {
            closedCount += 1
        }
    }

    private class RecordingShellWindowHost : RewriteShellWindowHost {
        val frames = mutableListOf<JInternalFrame>()

        override fun showWindow(frame: JInternalFrame) {
            frames += frame
            frame.isVisible = true
        }

        override fun focusWindow(frame: JInternalFrame) {
            frame.toFront()
        }

        override fun disposeAllWindows() = Unit
    }

    private inline fun invokeAndWait(crossinline block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
            return
        }
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            runCatching { block() }.exceptionOrNull()?.also { failure = it }
        }
        failure?.let { throw it }
    }
}
