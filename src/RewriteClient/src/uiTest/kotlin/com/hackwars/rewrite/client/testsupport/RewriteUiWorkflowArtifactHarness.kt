package com.hackwars.rewrite.client.testsupport

import java.awt.Component
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val workflowJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = true
}

@Serializable
data class RewriteUiWorkflowArtifactManifest(
    val featureKey: String,
    val artifactFileName: String,
    val suiteName: String,
    val testName: String,
    val taskId: String,
    val passId: String,
    val runId: String,
    val capturedAtEpochMillis: Long,
    val artifactDirectory: String,
    val artifactImagePath: String,
    val workflowAcceptance: String,
    val captureWidth: Int,
    val captureHeight: Int,
    val environment: Map<String, String> = emptyMap(),
    val fixtureIdentity: String? = null,
    val context: Map<String, String> = emptyMap(),
    val source: String = "rewrite-ui-test-workflow",
)

data class RewriteUiWorkflowArtifactCapture(
    val featureKey: String,
    val artifactFileName: String,
    val suiteName: String,
    val testName: String,
    val workflowAcceptance: String,
    val capture: BufferedImage,
    val capturedAt: Instant,
    val taskId: String,
    val passId: String,
    val fixtureIdentity: String? = null,
    val context: Map<String, String> = emptyMap(),
    val artifactRoot: Path = rewriteUiArtifactRoot(),
    val sequence: Int,
)

data class RewriteUiWorkflowArtifactRequest(
    val suiteName: String,
    val testName: String,
    val featureKey: String,
    val artifactFileName: String,
    val workflowAcceptance: String,
    val captureSize: Dimension,
    val taskId: String,
    val passId: String,
    val captureClock: Instant,
    val sequence: Int,
    val fixtureIdentity: String? = null,
    val context: Map<String, String> = emptyMap(),
    val artifactRoot: Path = rewriteUiArtifactRoot(),
)

fun rewriteUiWorkflowRoot(artifactRoot: Path = rewriteUiArtifactRoot()): Path {
    return artifactRoot.resolve("workflows")
}

fun rewriteUiWorkflowFeatureDirectory(featureKey: String, artifactRoot: Path = rewriteUiArtifactRoot()): Path {
    return rewriteUiWorkflowRoot(artifactRoot).resolve(featureKey)
}

fun rewriteUiWorkflowImagePath(
    featureKey: String,
    artifactFileName: String,
    artifactRoot: Path = rewriteUiArtifactRoot(),
): Path {
    return rewriteUiWorkflowFeatureDirectory(featureKey, artifactRoot).resolve(artifactFileName)
}

fun rewriteUiWorkflowManifestPath(
    featureKey: String,
    artifactFileName: String,
    artifactRoot: Path = rewriteUiArtifactRoot(),
): Path {
    return rewriteUiWorkflowFeatureDirectory(featureKey, artifactRoot)
        .resolve(artifactFileName.removeSuffix(".png") + ".json")
}

fun writeApprovedRewriteUiWorkflowArtifactCapture(
    capture: RewriteUiWorkflowArtifactCapture,
): RewriteUiWorkflowArtifactManifest {
    val artifactDirectory = rewriteUiWorkflowFeatureDirectory(
        featureKey = capture.featureKey,
        artifactRoot = capture.artifactRoot,
    )
    val imagePath = rewriteUiWorkflowImagePath(
        featureKey = capture.featureKey,
        artifactFileName = capture.artifactFileName,
        artifactRoot = capture.artifactRoot,
    )
    val manifestPath = rewriteUiWorkflowManifestPath(
        featureKey = capture.featureKey,
        artifactFileName = capture.artifactFileName,
        artifactRoot = capture.artifactRoot,
    )

    artifactDirectory.createDirectories()
    ImageIO.write(capture.capture, "png", imagePath.toFile())

    val manifest = RewriteUiWorkflowArtifactManifest(
        featureKey = capture.featureKey,
        artifactFileName = capture.artifactFileName,
        suiteName = capture.suiteName,
        testName = capture.testName,
        taskId = capture.taskId,
        passId = capture.passId,
        runId = rewriteUiRunId(capture.capturedAt, capture.sequence),
        capturedAtEpochMillis = capture.capturedAt.toEpochMilli(),
        artifactDirectory = artifactDirectory.toString(),
        artifactImagePath = imagePath.toString(),
        workflowAcceptance = capture.workflowAcceptance,
        captureWidth = capture.capture.width,
        captureHeight = capture.capture.height,
        environment = rewriteUiEnvironmentMetadata(),
        fixtureIdentity = capture.fixtureIdentity,
        context = capture.context,
    )
    Files.writeString(manifestPath, workflowJson.encodeToString(manifest), StandardCharsets.UTF_8)
    return manifest
}

fun assertOrApproveRewriteUiWorkflowArtifact(
    request: RewriteUiWorkflowArtifactRequest,
    componentProvider: () -> Component,
) {
    val imagePath = rewriteUiWorkflowImagePath(
        featureKey = request.featureKey,
        artifactFileName = request.artifactFileName,
        artifactRoot = request.artifactRoot,
    )
    val manifestPath = rewriteUiWorkflowManifestPath(
        featureKey = request.featureKey,
        artifactFileName = request.artifactFileName,
        artifactRoot = request.artifactRoot,
    )

    if (rewriteUiShouldApproveArtifacts()) {
        writeApprovedRewriteUiWorkflowArtifactCapture(
            RewriteUiWorkflowArtifactCapture(
                featureKey = request.featureKey,
                artifactFileName = request.artifactFileName,
                suiteName = request.suiteName,
                testName = request.testName,
                workflowAcceptance = request.workflowAcceptance,
                capture = renderComponent(componentProvider(), request.captureSize),
                capturedAt = request.captureClock,
                taskId = request.taskId,
                passId = request.passId,
                fixtureIdentity = request.fixtureIdentity,
                context = request.context,
                artifactRoot = request.artifactRoot,
                sequence = request.sequence,
            ),
        )
        return
    }

    require(Files.exists(imagePath)) {
        "Missing workflow artifact screenshot: $imagePath"
    }
    require(Files.exists(manifestPath)) {
        "Missing workflow artifact manifest: $manifestPath"
    }
    val manifestText = Files.readString(manifestPath)
    require(manifestText.contains("\"taskId\": \"${request.taskId}\"")) {
        "Workflow artifact manifest is missing taskId ${request.taskId}: $manifestPath"
    }
    require(manifestText.contains("\"passId\": \"${request.passId}\"")) {
        "Workflow artifact manifest is missing passId ${request.passId}: $manifestPath"
    }
    require(manifestText.contains("\"workflowAcceptance\": \"${request.workflowAcceptance}\"")) {
        "Workflow artifact manifest is missing workflowAcceptance ${request.workflowAcceptance}: $manifestPath"
    }
}

internal fun rewriteUiShouldApproveArtifacts(): Boolean {
    return listOf(
        System.getProperty("rewrite.ui.approveArtifacts"),
        System.getProperty("rewrite.ui.approveBaselines"),
        System.getenv("REWRITE_UI_APPROVE_ARTIFACTS"),
        System.getenv("REWRITE_UI_APPROVE_BASELINES"),
    ).any { value ->
        value?.equals("true", ignoreCase = true) == true
    }
}
