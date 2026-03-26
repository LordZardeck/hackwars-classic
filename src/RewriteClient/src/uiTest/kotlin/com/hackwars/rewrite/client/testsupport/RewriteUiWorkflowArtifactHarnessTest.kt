package com.hackwars.rewrite.client.testsupport

import java.awt.Color
import java.awt.Dimension
import java.nio.file.Files
import java.time.Instant
import javax.swing.JPanel
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class RewriteUiWorkflowArtifactHarnessTest {
    @Test
    fun approveModeWritesWorkflowArtifactImageAndManifest() {
        val artifactRoot = Files.createTempDirectory("rewrite-ui-workflow-approve")
        System.setProperty("rewrite.ui.approveArtifacts", "true")
        try {
            assertOrApproveRewriteUiWorkflowArtifact(
                RewriteUiWorkflowArtifactRequest(
                    suiteName = "RewriteClientDevModeUiTest",
                    testName = "deterministicDevModeLoginSceneCanSubmitDefaultCredentialsAndReachDesktop",
                    featureKey = "pass-2/login-and-desktop-entry",
                    artifactFileName = "login_desktop_success.png",
                    workflowAcceptance = "artifacts/rewrite/workflows/pass-2/login-and-desktop-entry/login_desktop_success.png",
                    captureSize = Dimension(24, 16),
                    taskId = "RW-CLIENT-C0C",
                    passId = "Pass 2",
                    captureClock = FIXED_CLOCK,
                    sequence = 3,
                    fixtureIdentity = "192.0.2.10",
                    context = mapOf("route" to "desktop"),
                    artifactRoot = artifactRoot,
                ),
            ) {
                JPanel().apply {
                    background = Color(0x33, 0x44, 0x55)
                    size = Dimension(24, 16)
                    preferredSize = size
                }
            }
        } finally {
            System.clearProperty("rewrite.ui.approveArtifacts")
        }

        val imagePath = rewriteUiWorkflowImagePath(
            featureKey = "pass-2/login-and-desktop-entry",
            artifactFileName = "login_desktop_success.png",
            artifactRoot = artifactRoot,
        )
        val manifestPath = rewriteUiWorkflowManifestPath(
            featureKey = "pass-2/login-and-desktop-entry",
            artifactFileName = "login_desktop_success.png",
            artifactRoot = artifactRoot,
        )

        assertTrue(Files.exists(imagePath))
        assertTrue(Files.exists(manifestPath))
        val manifest = manifestPath.readText()
        assertContains(manifest, "\"taskId\": \"RW-CLIENT-C0C\"")
        assertContains(manifest, "\"passId\": \"Pass 2\"")
        assertContains(manifest, "\"fixtureIdentity\": \"192.0.2.10\"")
    }

    @Test
    fun verifyModeAcceptsExistingWorkflowArtifact() {
        val artifactRoot = Files.createTempDirectory("rewrite-ui-workflow-verify")
        writeApprovedRewriteUiWorkflowArtifactCapture(
            RewriteUiWorkflowArtifactCapture(
                featureKey = "pass-2/login-and-desktop-entry",
                artifactFileName = "login_desktop_success.png",
                suiteName = "RewriteClientDevModeUiTest",
                testName = "deterministicDevModeLoginSceneCanSubmitDefaultCredentialsAndReachDesktop",
                workflowAcceptance = "artifacts/rewrite/workflows/pass-2/login-and-desktop-entry/login_desktop_success.png",
                capture = renderComponent(
                    JPanel().apply {
                        background = Color(0x55, 0x66, 0x77)
                        size = Dimension(24, 16)
                        preferredSize = size
                    },
                    Dimension(24, 16),
                ),
                capturedAt = FIXED_CLOCK,
                taskId = "RW-CLIENT-C0C",
                passId = "Pass 2",
                fixtureIdentity = "192.0.2.10",
                context = mapOf("route" to "desktop"),
                artifactRoot = artifactRoot,
                sequence = 3,
            ),
        )

        assertOrApproveRewriteUiWorkflowArtifact(
            RewriteUiWorkflowArtifactRequest(
                suiteName = "RewriteClientDevModeUiTest",
                testName = "deterministicDevModeLoginSceneCanSubmitDefaultCredentialsAndReachDesktop",
                featureKey = "pass-2/login-and-desktop-entry",
                artifactFileName = "login_desktop_success.png",
                workflowAcceptance = "artifacts/rewrite/workflows/pass-2/login-and-desktop-entry/login_desktop_success.png",
                captureSize = Dimension(24, 16),
                taskId = "RW-CLIENT-C0C",
                passId = "Pass 2",
                captureClock = FIXED_CLOCK,
                sequence = 3,
                fixtureIdentity = "192.0.2.10",
                context = mapOf("route" to "desktop"),
                artifactRoot = artifactRoot,
            ),
        ) {
            JPanel().apply {
                size = Dimension(24, 16)
                preferredSize = size
            }
        }
    }

    companion object {
        private val FIXED_CLOCK: Instant = Instant.parse("2026-03-26T18:42:00Z")
    }
}
