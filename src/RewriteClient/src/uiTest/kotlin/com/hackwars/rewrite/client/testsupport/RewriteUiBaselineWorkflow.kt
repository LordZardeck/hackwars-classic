package com.hackwars.rewrite.client.testsupport

import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import javax.imageio.ImageIO
import javax.swing.UIManager
import kotlin.io.path.createDirectories
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val baselineJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = true
}

@Serializable
data class RewriteUiApprovedBaselineManifest(
    val featureKey: String,
    val baselineFileName: String,
    val suiteName: String,
    val testName: String,
    val taskId: String,
    val passId: String,
    val runId: String,
    val approvedAtEpochMillis: Long,
    val baselineDirectory: String,
    val baselineImagePath: String,
    val legacyUiReference: String,
    val parityAcceptance: String,
    val captureWidth: Int,
    val captureHeight: Int,
    val lookAndFeelId: String? = null,
    val fontFamily: String? = null,
    val source: String = "rewrite-ui-test-baseline",
)

data class RewriteUiApprovedBaselineCapture(
    val featureKey: String,
    val baselineFileName: String,
    val suiteName: String,
    val testName: String,
    val legacyUiReference: String,
    val parityAcceptance: String,
    val capture: BufferedImage,
    val approvedAt: Instant,
    val taskId: String = "RW-TEST-006",
    val passId: String = "Pass 1",
    val baselineRoot: Path = rewriteUiBaselineRoot(),
    val sequence: Int = 1,
)

fun rewriteUiBaselineRoot(artifactRoot: Path = rewriteUiArtifactRoot()): Path {
    return artifactRoot.resolve("baselines")
}

fun rewriteUiBaselineFeatureDirectory(featureKey: String, baselineRoot: Path = rewriteUiBaselineRoot()): Path {
    return baselineRoot.resolve(featureKey)
}

fun rewriteUiBaselineImagePath(
    featureKey: String,
    baselineFileName: String,
    baselineRoot: Path = rewriteUiBaselineRoot(),
): Path {
    return rewriteUiBaselineFeatureDirectory(featureKey, baselineRoot)
        .resolve(baselineFileName)
}

fun rewriteUiBaselineManifestPath(
    featureKey: String,
    baselineFileName: String,
    baselineRoot: Path = rewriteUiBaselineRoot(),
): Path {
    return rewriteUiBaselineFeatureDirectory(featureKey, baselineRoot)
        .resolve(baselineFileName.removeSuffix(".png") + ".json")
}

fun writeApprovedRewriteUiBaselineCapture(
    capture: RewriteUiApprovedBaselineCapture,
): RewriteUiApprovedBaselineManifest {
    val baselineDirectory = rewriteUiBaselineFeatureDirectory(
        featureKey = capture.featureKey,
        baselineRoot = capture.baselineRoot,
    )
    val imagePath = rewriteUiBaselineImagePath(
        featureKey = capture.featureKey,
        baselineFileName = capture.baselineFileName,
        baselineRoot = capture.baselineRoot,
    )
    val manifestPath = rewriteUiBaselineManifestPath(
        featureKey = capture.featureKey,
        baselineFileName = capture.baselineFileName,
        baselineRoot = capture.baselineRoot,
    )

    baselineDirectory.createDirectories()
    ImageIO.write(capture.capture, "png", imagePath.toFile())

    val manifest = RewriteUiApprovedBaselineManifest(
        featureKey = capture.featureKey,
        baselineFileName = capture.baselineFileName,
        suiteName = capture.suiteName,
        testName = capture.testName,
        taskId = capture.taskId,
        passId = capture.passId,
        runId = rewriteUiRunId(capture.approvedAt, capture.sequence),
        approvedAtEpochMillis = capture.approvedAt.toEpochMilli(),
        baselineDirectory = baselineDirectory.toString(),
        baselineImagePath = imagePath.toString(),
        legacyUiReference = capture.legacyUiReference,
        parityAcceptance = capture.parityAcceptance,
        captureWidth = capture.capture.width,
        captureHeight = capture.capture.height,
        lookAndFeelId = UIManager.getLookAndFeel()?.id,
        fontFamily = UIManager.getFont("Label.font")?.family,
    )
    Files.writeString(manifestPath, baselineJson.encodeToString(manifest), StandardCharsets.UTF_8)
    return manifest
}
