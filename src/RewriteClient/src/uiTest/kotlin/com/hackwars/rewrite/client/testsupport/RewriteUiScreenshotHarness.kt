package com.hackwars.rewrite.client.testsupport

import com.hackwars.rewrite.client.ui.RewriteUiBootstrap
import java.awt.Component
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.io.path.createDirectories

private val screenshotSequence = AtomicInteger(1)

data class RewriteUiScreenshotCaptureRequest(
    val suiteName: String,
    val testName: String,
    val baselineRelativePath: String,
    val captureSize: Dimension,
    val taskId: String = "RW-TEST-004",
    val passId: String = "Pass 1",
    val context: Map<String, String> = emptyMap(),
    val artifactRoot: Path = rewriteUiArtifactRoot(),
    val baselineRoot: Path = rewriteUiArtifactRoot().resolve("baselines"),
    val captureClock: Instant,
    val sequence: Int = screenshotSequence.getAndIncrement(),
)

fun assertRewriteUiScreenshotMatchesBaseline(
    request: RewriteUiScreenshotCaptureRequest,
    componentProvider: () -> Component,
) {
    RewriteUiBootstrap.installHackWarsLookAndFeel()
    val component = componentProvider()
    val actualImage = renderComponent(component, request.captureSize)
    val baselinePath = request.baselineRoot.resolve(request.baselineRelativePath)
    val runId = rewriteUiRunId(request.captureClock, request.sequence)
    val caseDirectory = rewriteUiCaseDirectory(request.artifactRoot, runId, request.suiteName, request.testName)
    val screenshotPath = caseDirectory.resolve("source.png")
    val diffPath = caseDirectory.resolve("diff.png")
    val baselineImage = baselinePath.takeIf(Files::exists)?.let { path -> ImageIO.read(path.toFile()) }

    if (baselineImage == null) {
        emitScreenshotMismatch(
            request = request,
            caseDirectory = caseDirectory,
            baselinePath = baselinePath,
            screenshotPath = screenshotPath,
            diffPath = null,
            actualImage = actualImage,
            diffImage = null,
            reason = "Missing screenshot baseline: $baselinePath",
        )
    }

    if (!imagesMatch(actualImage, baselineImage!!)) {
        emitScreenshotMismatch(
            request = request,
            caseDirectory = caseDirectory,
            baselinePath = baselinePath,
            screenshotPath = screenshotPath,
            diffPath = diffPath,
            actualImage = actualImage,
            diffImage = diffImage(actualImage, baselineImage),
            reason = "Screenshot parity mismatch for ${request.suiteName}/${request.testName}.",
        )
    }
}

internal fun renderComponent(component: Component, captureSize: Dimension): BufferedImage {
    return invokeOnEdt {
        component.size = captureSize
        if (component is JComponent) {
            component.preferredSize = captureSize
            component.revalidate()
            component.doLayout()
        }
        BufferedImage(captureSize.width, captureSize.height, BufferedImage.TYPE_INT_ARGB).also { image ->
            val graphics = image.createGraphics()
            try {
                graphics.clipRect(0, 0, captureSize.width, captureSize.height)
                component.paint(graphics)
            } finally {
                graphics.dispose()
            }
        }
    }
}

internal fun diffImage(actual: BufferedImage, baseline: BufferedImage): BufferedImage {
    val width = maxOf(actual.width, baseline.width)
    val height = maxOf(actual.height, baseline.height)
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { diff ->
        for (y in 0 until height) {
            for (x in 0 until width) {
                val actualPixel = actual.pixelOrNull(x, y)
                val baselinePixel = baseline.pixelOrNull(x, y)
                diff.setRGB(
                    x,
                    y,
                    when {
                        actualPixel == baselinePixel -> 0x00000000
                        else -> 0xFFFF00FF.toInt()
                    },
                )
            }
        }
    }
}

private fun emitScreenshotMismatch(
    request: RewriteUiScreenshotCaptureRequest,
    caseDirectory: Path,
    baselinePath: Path,
    screenshotPath: Path,
    diffPath: Path?,
    actualImage: BufferedImage,
    diffImage: BufferedImage?,
    reason: String,
): Nothing {
    caseDirectory.createDirectories()
    writePng(actualImage, screenshotPath)
    diffImage?.let { writePng(it, diffPath!!) }
    writeFailureBundle(
        directory = caseDirectory,
        manifest = RewriteUiFailureManifest(
            suiteName = request.suiteName,
            testName = request.testName,
            taskId = request.taskId,
            passId = request.passId,
            runId = rewriteUiRunId(request.captureClock, request.sequence),
            createdAtEpochMillis = request.captureClock.toEpochMilli(),
            runDirectory = caseDirectory.parent.parent.toString(),
            caseDirectory = caseDirectory.toString(),
            artifactDirectory = caseDirectory.toString(),
            failureSummary = reason,
            exceptionType = AssertionError::class.qualifiedName ?: "java.lang.AssertionError",
            exceptionMessage = reason,
            environment = linkedMapOf(
                "jvmVersion" to System.getProperty("java.version", "unknown"),
                "osName" to System.getProperty("os.name", "unknown"),
                "osVersion" to System.getProperty("os.version", "unknown"),
                "headless" to java.awt.GraphicsEnvironment.isHeadless().toString(),
                "displayScale" to rewriteUiDisplayScale(),
                "dpi" to rewriteUiDpi(),
            ),
            context = request.context,
            baselinePath = baselinePath.toString(),
            screenshotPath = screenshotPath.toString(),
            screenshotDiffPath = diffPath?.toString(),
            lookAndFeelId = UIManager.getLookAndFeel()?.id,
            fontFamily = UIManager.getFont("Label.font")?.family,
            captureWidth = request.captureSize.width,
            captureHeight = request.captureSize.height,
            captureClockEpochMillis = request.captureClock.toEpochMilli(),
        ),
        failureText = reason,
        context = request.context,
    )
    throw AssertionError(reason)
}

private fun writePng(image: BufferedImage, path: Path) {
    path.parent?.createDirectories()
    ImageIO.write(image, "png", path.toFile())
}

private fun imagesMatch(actual: BufferedImage, baseline: BufferedImage): Boolean {
    if (actual.width != baseline.width || actual.height != baseline.height) {
        return false
    }
    for (y in 0 until actual.height) {
        for (x in 0 until actual.width) {
            if (actual.getRGB(x, y) != baseline.getRGB(x, y)) {
                return false
            }
        }
    }
    return true
}

private fun BufferedImage.pixelOrNull(x: Int, y: Int): Int? {
    if (x !in 0 until width || y !in 0 until height) {
        return null
    }
    return getRGB(x, y)
}

private fun <T> invokeOnEdt(block: () -> T): T {
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
