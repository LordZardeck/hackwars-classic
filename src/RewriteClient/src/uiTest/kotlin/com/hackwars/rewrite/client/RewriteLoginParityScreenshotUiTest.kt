package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.testsupport.assertOrApproveRewriteUiBaseline
import com.hackwars.rewrite.client.testsupport.rewriteUiDisposeFrame
import com.hackwars.rewrite.client.testsupport.rewriteUiInvokeAndWaitResult
import com.hackwars.rewrite.client.testsupport.RewriteUiParityBaselineRequest
import com.hackwars.rewrite.client.testsupport.rewriteUiWaitUntil
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.time.Instant
import kotlin.test.Test
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteLoginParityScreenshotUiTest {
    @Test
    fun loginSceneMainMatchesApprovedBaseline() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = rewriteUiInvokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            rewriteUiWaitUntil {
                rewriteUiInvokeAndWaitResult {
                    frame.loginScene.isShowing && frame.loginScene.loginForm.isVisible
                }
            }
            assertOrApproveRewriteUiBaseline(
                RewriteUiParityBaselineRequest(
                    suiteName = "RewriteLoginParityScreenshotUiTest",
                    testName = "loginSceneMainMatchesApprovedBaseline",
                    featureKey = "pass-2/login-and-desktop-entry",
                    baselineFileName = "login_scene_main.png",
                    legacyUiReference = "LoginScene",
                    parityAcceptance = "artifacts/rewrite/baselines/pass-2/login-and-desktop-entry/login_scene_main.png",
                    captureSize = LOGIN_CAPTURE_SIZE,
                    taskId = "RW-CLIENT-C0C",
                    passId = "Pass 2",
                    captureClock = BASELINE_CLOCK,
                    sequence = 1,
                ),
            ) {
                frame.loginScene
            }
        } finally {
            rewriteUiDisposeFrame(frame)
        }
    }

    @Test
    fun loginSceneErrorMatchesApprovedBaseline() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = rewriteUiInvokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            rewriteUiWaitUntil {
                rewriteUiInvokeAndWaitResult {
                    frame.loginScene.isShowing && frame.loginScene.loginForm.isVisible
                }
            }
            rewriteUiInvokeAndWaitResult {
                frame.controller.submitLogin("   ", "password1234".toCharArray())
            }
            rewriteUiWaitUntil {
                rewriteUiInvokeAndWaitResult {
                    frame.loginScene.loginForm.displayedErrorText() == "Username is required."
                }
            }
            assertOrApproveRewriteUiBaseline(
                RewriteUiParityBaselineRequest(
                    suiteName = "RewriteLoginParityScreenshotUiTest",
                    testName = "loginSceneErrorMatchesApprovedBaseline",
                    featureKey = "pass-2/login-and-desktop-entry",
                    baselineFileName = "login_scene_error.png",
                    legacyUiReference = "LoginScene",
                    parityAcceptance = "artifacts/rewrite/baselines/pass-2/login-and-desktop-entry/login_scene_error.png",
                    captureSize = LOGIN_CAPTURE_SIZE,
                    taskId = "RW-CLIENT-C0C",
                    passId = "Pass 2",
                    captureClock = BASELINE_CLOCK,
                    sequence = 2,
                ),
            ) {
                frame.loginScene
            }
        } finally {
            rewriteUiDisposeFrame(frame)
        }
    }

    companion object {
        private val LOGIN_CAPTURE_SIZE: Dimension = Dimension(1280, 800)
        private val BASELINE_CLOCK: Instant = Instant.parse("2026-03-26T18:42:00Z")
    }
}
