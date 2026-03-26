package com.hackwars.rewrite.client.testsupport

import java.awt.Color
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.time.Instant
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteUiBaselineWorkflowTest {
    @Test
    fun approvedBaselineCaptureWritesNamedPngAndManifest() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val baselineRoot = Files.createTempDirectory("rewrite-ui-baseline-root")
        val capture = renderComponent(coloredPanel(Color(0x21, 0x3A, 0x6B)), CAPTURE_SIZE)

        val manifest = writeApprovedRewriteUiBaselineCapture(
            RewriteUiApprovedBaselineCapture(
                featureKey = "pass-2/login-and-desktop-entry",
                baselineFileName = "login_scene_main.png",
                suiteName = "RewriteClientDevModeUiTest",
                testName = "deterministicDevModeCanReachDesktopAndOpenRepresentativeWindows",
                legacyUiReference = "LoginScene",
                parityAcceptance = "artifacts/rewrite/baselines/pass-2/login-and-desktop-entry/login_scene_main.png",
                capture = capture,
                approvedAt = FIXED_CLOCK,
                baselineRoot = baselineRoot,
                sequence = 3,
            ),
        )

        val imagePath = rewriteUiBaselineImagePath(
            featureKey = "pass-2/login-and-desktop-entry",
            baselineFileName = "login_scene_main.png",
            baselineRoot = baselineRoot,
        )
        val manifestPath = rewriteUiBaselineManifestPath(
            featureKey = "pass-2/login-and-desktop-entry",
            baselineFileName = "login_scene_main.png",
            baselineRoot = baselineRoot,
        )

        assertTrue(imagePath.exists())
        assertTrue(manifestPath.exists())
        assertEquals("pass-2/login-and-desktop-entry", manifest.featureKey)
        assertEquals("login_scene_main.png", manifest.baselineFileName)
        assertEquals("LoginScene", manifest.legacyUiReference)
        assertEquals("artifacts/rewrite/baselines/pass-2/login-and-desktop-entry/login_scene_main.png", manifest.parityAcceptance)

        val serialized = manifestPath.readText()
        assertContains(serialized, "\"runId\": \"run-20260326-184200-003\"")
        assertContains(serialized, "\"baselineImagePath\": \"${escapeJson(imagePath.toString())}\"")
        assertContains(serialized, "\"captureWidth\": ${CAPTURE_SIZE.width}")
        assertContains(serialized, "\"captureHeight\": ${CAPTURE_SIZE.height}")
    }

    private fun coloredPanel(color: Color) = object : javax.swing.JPanel() {
        override fun paintComponent(graphics: java.awt.Graphics) {
            super.paintComponent(graphics)
            graphics.color = color
            graphics.fillRect(0, 0, width, height)
            graphics.color = Color.WHITE
            graphics.drawLine(0, 0, width, height)
        }
    }.apply {
        isOpaque = true
        background = Color.BLACK
        preferredSize = CAPTURE_SIZE
        size = CAPTURE_SIZE
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
    }

    companion object {
        private val CAPTURE_SIZE = Dimension(180, 120)
        private val FIXED_CLOCK: Instant = Instant.parse("2026-03-26T18:42:00Z")
    }
}
