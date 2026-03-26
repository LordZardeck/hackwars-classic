package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.clientdev.RewriteClientDevEnvironment
import com.hackwars.rewrite.client.testsupport.RewriteUiWorkflowArtifactRequest
import com.hackwars.rewrite.client.testsupport.assertOrApproveRewriteUiWorkflowArtifact
import com.hackwars.rewrite.client.testsupport.rewriteUiDeterministicDevModeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiDisposeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiFailureTriage
import com.hackwars.rewrite.client.testsupport.rewriteUiInvokeAndWait
import com.hackwars.rewrite.client.testsupport.rewriteUiInvokeAndWaitResult
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitForWindow
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitUntil
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteClientDevModeUiTest {
    @Test
    fun deterministicDevModeLoginSceneCanSubmitDefaultCredentialsAndReachDesktop() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val environment = RewriteClientDevEnvironment()
        rewriteUiFailureTriage(
            suiteName = "RewriteClientDevModeUiTest",
            testName = "deterministicDevModeLoginSceneCanSubmitDefaultCredentialsAndReachDesktop",
            taskId = "RW-CLIENT-C0C",
            passId = "Pass 2",
            context = mapOf(
                "surface" to "login-scene",
                "route" to "desktop",
                "seedIp" to environment.fixture.local.playerIp,
                "seedLogin" to environment.fixture.login.email,
            ),
            startedAt = environment.fixture.screenshotCaptureClock,
            sequence = 3,
        ) {
            val frame = rewriteUiInvokeAndWaitResult {
                RewriteRootFrame(
                    controller = environment.createController(),
                ).apply { isVisible = true }
            }
            try {
                rewriteUiWaitUntil {
                    rewriteUiInvokeAndWaitResult {
                        frame.loginScene.isShowing && frame.loginScene.loginForm.isVisible
                    }
                }

                rewriteUiInvokeAndWait {
                    frame.loginScene.loginForm.loginButton.doClick()
                }

                rewriteUiWaitUntil {
                    rewriteUiInvokeAndWaitResult {
                        frame.desktopPane.isShowing && frame.jMenuBar != null
                    }
                }

                assertTrue(rewriteUiInvokeAndWaitResult { frame.desktopPane.isShowing })
                assertTrue(rewriteUiInvokeAndWaitResult { frame.jMenuBar != null })
                assertOrApproveRewriteUiWorkflowArtifact(
                    RewriteUiWorkflowArtifactRequest(
                        suiteName = "RewriteClientDevModeUiTest",
                        testName = "deterministicDevModeLoginSceneCanSubmitDefaultCredentialsAndReachDesktop",
                        featureKey = "pass-2/login-and-desktop-entry",
                        artifactFileName = "login_desktop_success.png",
                        workflowAcceptance = "artifacts/rewrite/workflows/pass-2/login-and-desktop-entry/login_desktop_success.png",
                        captureSize = ROOT_CAPTURE_SIZE,
                        taskId = "RW-CLIENT-C0C",
                        passId = "Pass 2",
                        captureClock = environment.fixture.screenshotCaptureClock,
                        sequence = 3,
                        fixtureIdentity = environment.fixture.local.playerIp,
                        context = mapOf(
                            "route" to "desktop",
                            "seedIp" to environment.fixture.local.playerIp,
                            "seedLogin" to environment.fixture.login.email,
                        ),
                    ),
                ) {
                    frame.rootPane
                }
            } finally {
                rewriteUiDisposeFrame(frame)
                environment.close()
            }
        }
    }

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

    companion object {
        private val ROOT_CAPTURE_SIZE: Dimension = Dimension(1280, 800)
    }
}
