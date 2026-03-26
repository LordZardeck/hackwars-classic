package com.hackwars.rewrite.client.testsupport

import java.awt.Color
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.time.Instant
import javax.imageio.ImageIO
import javax.swing.JPanel
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteUiScreenshotHarnessTest {
    @Test
    fun matchingBaselinePassesWithoutWritingFailureArtifacts() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val artifactRoot = Files.createTempDirectory("rewrite-ui-harness-artifacts")
        val baselineRoot = Files.createTempDirectory("rewrite-ui-harness-baselines")
        val baselinePath = baselineRoot.resolve("shell/root-frame.png")
        Files.createDirectories(baselinePath.parent)
        ImageIO.write(renderComponent(coloredPanel(Color(0x22, 0x44, 0x66)), CAPTURE_SIZE), "png", baselinePath.toFile())

        assertRewriteUiScreenshotMatchesBaseline(
            request = RewriteUiScreenshotCaptureRequest(
                suiteName = "RewriteUiScreenshotHarnessTest",
                testName = "matchingBaselinePassesWithoutWritingFailureArtifacts",
                baselineRelativePath = "shell/root-frame.png",
                captureSize = CAPTURE_SIZE,
                artifactRoot = artifactRoot,
                baselineRoot = baselineRoot,
                captureClock = FIXED_CLOCK,
                sequence = 1,
            ),
        ) {
            coloredPanel(Color(0x22, 0x44, 0x66))
        }

        assertTrue(!Files.exists(artifactRoot.resolve("ui-tests").resolve(rewriteUiRunId(FIXED_CLOCK, 1))))
    }

    @Test
    fun mismatchedBaselineWritesScreenshotDiffAndManifestMetadata() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val artifactRoot = Files.createTempDirectory("rewrite-ui-harness-artifacts")
        val baselineRoot = Files.createTempDirectory("rewrite-ui-harness-baselines")
        val baselinePath = baselineRoot.resolve("shell/root-frame.png")
        Files.createDirectories(baselinePath.parent)
        ImageIO.write(renderComponent(coloredPanel(Color(0x11, 0x22, 0x33)), CAPTURE_SIZE), "png", baselinePath.toFile())

        val failure = assertFailsWith<AssertionError> {
            assertRewriteUiScreenshotMatchesBaseline(
                request = RewriteUiScreenshotCaptureRequest(
                    suiteName = "RewriteUiScreenshotHarnessTest",
                    testName = "mismatchedBaselineWritesScreenshotDiffAndManifestMetadata",
                    baselineRelativePath = "shell/root-frame.png",
                    captureSize = CAPTURE_SIZE,
                    artifactRoot = artifactRoot,
                    baselineRoot = baselineRoot,
                    captureClock = FIXED_CLOCK,
                    sequence = 2,
                    context = mapOf("surface" to "shell", "pass" to "1"),
                ),
            ) {
                coloredPanel(Color(0xAA, 0x55, 0x11))
            }
        }

        assertContains(failure.message.orEmpty(), "Screenshot parity mismatch")

        val caseDirectory = artifactRoot
            .resolve("ui-tests")
            .resolve(rewriteUiRunId(FIXED_CLOCK, 2))
            .resolve("rewrite-ui-screenshot-harness-test")
            .resolve("mismatched-baseline-writes-screenshot-diff-and-manifest-metadata")
        assertTrue(caseDirectory.resolve("manifest.json").exists())
        assertTrue(caseDirectory.resolve("source.png").exists())
        assertTrue(caseDirectory.resolve("diff.png").exists())
        assertTrue(caseDirectory.resolve("failure.txt").exists())

        val manifest = caseDirectory.resolve("manifest.json").readText()
        assertContains(manifest, "\"baselinePath\": \"${escapeJson(baselinePath.toString())}\"")
        assertContains(manifest, "\"screenshotPath\": \"${escapeJson(caseDirectory.resolve("source.png").toString())}\"")
        assertContains(manifest, "\"screenshotDiffPath\": \"${escapeJson(caseDirectory.resolve("diff.png").toString())}\"")
        assertContains(manifest, "\"captureWidth\": ${CAPTURE_SIZE.width}")
        assertContains(manifest, "\"captureHeight\": ${CAPTURE_SIZE.height}")
        assertContains(manifest, "\"captureClockEpochMillis\": ${FIXED_CLOCK.toEpochMilli()}")
        assertContains(manifest, "\"lookAndFeelId\": \"RewriteHackWars\"")
        assertContains(manifest, "\"surface\": \"shell\"")

        val diffImage = ImageIO.read(caseDirectory.resolve("diff.png").toFile())
        assertEquals(CAPTURE_SIZE.width, diffImage.width)
        assertEquals(CAPTURE_SIZE.height, diffImage.height)
        assertTrue(hasVisibleDiffPixel(diffImage), "Expected the diff image to contain at least one highlighted pixel.")
    }

    private fun coloredPanel(color: Color): JPanel {
        return object : JPanel() {
            override fun paintComponent(graphics: java.awt.Graphics) {
                super.paintComponent(graphics)
                graphics.color = color
                graphics.fillRect(0, 0, width, height)
                graphics.color = Color.WHITE
                graphics.drawRect(8, 8, width - 16, height - 16)
            }
        }.apply {
            isOpaque = true
            background = Color.BLACK
            preferredSize = CAPTURE_SIZE
            size = CAPTURE_SIZE
        }
    }

    private fun hasVisibleDiffPixel(image: BufferedImage): Boolean {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) != 0x00000000) {
                    return true
                }
            }
        }
        return false
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
    }

    companion object {
        private val CAPTURE_SIZE = Dimension(180, 120)
        private val FIXED_CLOCK: Instant = Instant.parse("2026-03-26T18:42:00Z")
    }
}
