package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.clientdev.RewriteClientDevEnvironment
import com.hackwars.rewrite.client.testsupport.rewriteUiFailureTriage
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import java.awt.GraphicsEnvironment
import javax.swing.JInternalFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteClientDevModeUiTest {
    @Test
    fun deterministicDevModeCanReachDesktopAndOpenRepresentativeWindows() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        rewriteUiFailureTriage(
            suiteName = "RewriteClientDevModeUiTest",
            testName = "deterministicDevModeCanReachDesktopAndOpenRepresentativeWindows",
            context = mapOf(
                "surface" to "dev-mode",
                "route" to "desktop",
            ),
        ) {
            val environment = RewriteClientDevEnvironment()
            val frame = invokeAndWaitResult {
                RewriteRootFrame(
                    controller = environment.createController(),
                ).apply { isVisible = true }
            }
            try {
                frame.controller.submitLogin("localuser", "password1234".toCharArray())
                waitUntil { frame.controller.route() == RewriteClientRoute.DESKTOP }
                waitUntil {
                    invokeAndWaitResult {
                        frame.desktopPane.allFrames.any { it.name == "rewrite-shell-window-network" } &&
                            frame.desktopPane.allFrames.any { it.name == "rewrite-log-window" }
                    }
                }

                SwingUtilities.invokeAndWait {
                    frame.controller.launchShellCommand(RewriteShellCommand.HOME)
                    frame.controller.launchShellCommand(RewriteShellCommand.STORE)
                }

                val homeWindow = waitForWindow(frame, "rewrite-home-window")
                val storeWindow = waitForWindow(frame, "rewrite-store-window")

                assertNotNull(homeWindow)
                assertNotNull(storeWindow)
                assertTrue(invokeAndWaitResult { frame.desktopPane.allFrames.any { it.name == "rewrite-shell-window-network" } })
                assertTrue(invokeAndWaitResult { frame.desktopPane.allFrames.any { it.name == "rewrite-log-window" } })
            } finally {
                disposeFrame(frame)
                environment.close()
            }
        }
    }

    private fun waitForWindow(frame: RewriteRootFrame, name: String): JInternalFrame {
        waitUntil { frame.desktopPane.allFrames.any { it.name == name } }
        return frame.desktopPane.allFrames.first { it.name == name }
    }

    private fun disposeFrame(frame: RewriteRootFrame) {
        invokeAndWait {
            frame.isVisible = false
            frame.dispose()
        }
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

    private fun <T> invokeAndWaitResult(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return block()
        }
        var result: T? = null
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            runCatching { block() }
                .onSuccess { result = it }
                .onFailure { failure = it }
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(
        timeoutMillis: Long = 5_000L,
        condition: () -> Boolean,
    ) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMillis) {
            if (condition()) {
                return
            }
            Thread.sleep(25)
        }
        assertTrue(condition(), "Condition was not met within ${timeoutMillis}ms.")
    }
}
