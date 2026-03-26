package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.clientdev.RewriteClientDevEnvironment
import com.hackwars.rewrite.client.testsupport.rewriteUiDeterministicDevModeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiDisposeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiFailureTriage
import com.hackwars.rewrite.client.testsupport.rewriteUiInvokeAndWait
import com.hackwars.rewrite.client.testsupport.rewriteUiInvokeAndWaitResult
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForWindow
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitUntil
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import java.awt.GraphicsEnvironment
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
            val frame = rewriteUiDeterministicDevModeFrame(environment)
            try {
                rewriteUiWaitUntil { frame.controller.route() == RewriteClientRoute.DESKTOP }
                rewriteUiWaitUntil {
                    rewriteUiInvokeAndWaitResult {
                        frame.desktopPane.allFrames.any { it.name == "rewrite-shell-window-network" } &&
                            frame.desktopPane.allFrames.any { it.name == "rewrite-log-window" }
                    }
                }

                rewriteUiInvokeAndWait {
                    frame.controller.launchShellCommand(RewriteShellCommand.HOME)
                    frame.controller.launchShellCommand(RewriteShellCommand.STORE)
                }

                val homeWindow = rewriteUiWaitForWindow(frame, "rewrite-home-window")
                val storeWindow = rewriteUiWaitForWindow(frame, "rewrite-store-window")

                assertNotNull(homeWindow)
                assertNotNull(storeWindow)
                assertTrue(rewriteUiInvokeAndWaitResult { frame.desktopPane.allFrames.any { it.name == "rewrite-shell-window-network" } })
                assertTrue(rewriteUiInvokeAndWaitResult { frame.desktopPane.allFrames.any { it.name == "rewrite-log-window" } })
            } finally {
                rewriteUiDisposeFrame(frame)
                environment.close()
            }
        }
    }
}
