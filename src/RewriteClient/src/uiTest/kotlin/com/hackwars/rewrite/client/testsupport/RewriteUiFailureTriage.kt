package com.hackwars.rewrite.client.testsupport

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.UIManager
import kotlin.io.path.createDirectories
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val uiArtifactJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = true
}

private val runSequence = AtomicInteger(1)

fun <T> rewriteUiFailureTriage(
    suiteName: String,
    testName: String,
    taskId: String = "RW-TEST-002",
    passId: String = "Pass 1",
    context: Map<String, String> = emptyMap(),
    artifactRoot: Path = rewriteUiArtifactRoot(),
    startedAt: Instant = Instant.now(),
    sequence: Int = runSequence.getAndIncrement(),
    block: () -> T,
): T {
    val runId = rewriteUiRunId(startedAt, sequence)
    val runDirectory = artifactRoot.resolve("ui-tests").resolve(runId)
    val caseDirectory = runDirectory
        .resolve(rewriteUiArtifactSegment(suiteName))
        .resolve(rewriteUiArtifactSegment(testName))
    try {
        return block()
    } catch (failure: Throwable) {
        emitFailureBundle(
            directory = caseDirectory,
            manifest = RewriteUiFailureManifest(
                suiteName = suiteName,
                testName = testName,
                taskId = taskId,
                passId = passId,
                runId = runId,
                createdAtEpochMillis = startedAt.toEpochMilli(),
                runDirectory = runDirectory.toString(),
                caseDirectory = caseDirectory.toString(),
                artifactDirectory = caseDirectory.toString(),
                failureSummary = failure::class.qualifiedName ?: failure::class.simpleName.orEmpty(),
                exceptionType = failure::class.qualifiedName ?: failure::class.simpleName.orEmpty(),
                exceptionMessage = failure.message,
                environment = rewriteUiEnvironmentMetadata(),
                context = context,
            ),
            failure = failure,
            context = context,
        )
        throw failure
    }
}

@Serializable
data class RewriteUiFailureManifest(
    val suiteName: String,
    val testName: String,
    val taskId: String,
    val passId: String,
    val runId: String,
    val createdAtEpochMillis: Long,
    val runDirectory: String,
    val caseDirectory: String,
    val artifactDirectory: String,
    val failureSummary: String,
    val exceptionType: String,
    val exceptionMessage: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val context: Map<String, String> = emptyMap(),
    val baselinePath: String? = null,
    val screenshotPath: String? = null,
    val screenshotDiffPath: String? = null,
    val lookAndFeelId: String? = null,
    val fontFamily: String? = null,
    val captureWidth: Int? = null,
    val captureHeight: Int? = null,
    val captureClockEpochMillis: Long? = null,
)

@Serializable
data class RewriteUiFailureContext(
    val entries: Map<String, String> = emptyMap(),
)

fun rewriteUiArtifactRoot(): Path {
    return rewriteRepoRoot()
        .resolve("artifacts")
        .resolve("rewrite")
}

fun rewriteUiRunDirectory(runId: String): Path {
    return rewriteUiRunDirectory(rewriteUiArtifactRoot(), runId)
}

internal fun rewriteUiRunDirectory(artifactRoot: Path, runId: String): Path {
    return artifactRoot
        .resolve("ui-tests")
        .resolve(runId)
}

fun rewriteUiCaseDirectory(runId: String, suiteName: String, testName: String): Path {
    return rewriteUiCaseDirectory(rewriteUiArtifactRoot(), runId, suiteName, testName)
}

internal fun rewriteUiCaseDirectory(artifactRoot: Path, runId: String, suiteName: String, testName: String): Path {
    return rewriteUiRunDirectory(artifactRoot, runId)
        .resolve(rewriteUiArtifactSegment(suiteName))
        .resolve(rewriteUiArtifactSegment(testName))
}

fun rewriteUiArtifactSegment(value: String): String {
    val camelSeparated = value.trim().replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
    val normalized = camelSeparated.replace(Regex("[^A-Za-z0-9]+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .lowercase()
    return if (normalized.isBlank()) {
        "unnamed"
    } else {
        normalized
    }
}

fun rewriteUiRunId(startedAt: Instant, sequence: Int): String {
    val timestamp = RUN_ID_FORMATTER.format(startedAt.atZone(ZoneOffset.UTC))
    return "run-$timestamp-${sequence.toString().padStart(3, '0')}"
}

private fun emitFailureBundle(
    directory: Path,
    manifest: RewriteUiFailureManifest,
    failure: Throwable,
    context: Map<String, String>,
) {
    runCatching {
        directory.createDirectories()
        Files.writeString(directory.resolve("manifest.json"), uiArtifactJson.encodeToString(manifest), StandardCharsets.UTF_8)
        Files.writeString(directory.resolve("failure.txt"), formatFailureText(failure), StandardCharsets.UTF_8)
        if (context.isNotEmpty()) {
            Files.writeString(
                directory.resolve("context.json"),
                uiArtifactJson.encodeToString(RewriteUiFailureContext(context)),
                StandardCharsets.UTF_8,
            )
        }
    }.exceptionOrNull()?.let { artifactFailure ->
        failure.addSuppressed(artifactFailure)
    }
}

internal fun writeFailureBundle(
    directory: Path,
    manifest: RewriteUiFailureManifest,
    failureText: String,
    context: Map<String, String>,
) {
    runCatching {
        directory.createDirectories()
        Files.writeString(directory.resolve("manifest.json"), uiArtifactJson.encodeToString(manifest), StandardCharsets.UTF_8)
        Files.writeString(directory.resolve("failure.txt"), failureText, StandardCharsets.UTF_8)
        if (context.isNotEmpty()) {
            Files.writeString(
                directory.resolve("context.json"),
                uiArtifactJson.encodeToString(RewriteUiFailureContext(context)),
                StandardCharsets.UTF_8,
            )
        }
    }.getOrThrow()
}

private fun formatFailureText(failure: Throwable): String {
    val writer = StringWriter()
    PrintWriter(writer).use { printer ->
        printer.println("${failure::class.qualifiedName ?: failure::class.simpleName}: ${failure.message.orEmpty()}")
        failure.printStackTrace(printer)
    }
    return writer.toString()
}

private fun rewriteUiEnvironmentMetadata(): Map<String, String> {
    return linkedMapOf(
        "jvmVersion" to System.getProperty("java.version", "unknown"),
        "osName" to System.getProperty("os.name", "unknown"),
        "osVersion" to System.getProperty("os.version", "unknown"),
        "headless" to java.awt.GraphicsEnvironment.isHeadless().toString(),
        "displayScale" to rewriteUiDisplayScale(),
        "lookAndFeelId" to (UIManager.getLookAndFeel()?.id ?: "unknown"),
        "fontFamily" to (UIManager.getFont("Label.font")?.family ?: "unknown"),
        "fontSize" to (UIManager.getFont("Label.font")?.size?.toString() ?: "unknown"),
        "dpi" to rewriteUiDpi(),
    )
}

internal fun rewriteUiDisplayScale(): String {
    if (java.awt.GraphicsEnvironment.isHeadless()) {
        return "unknown"
    }
    val transform = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice
        .defaultConfiguration
        .defaultTransform
    return "${transform.scaleX}x${transform.scaleY}"
}

internal fun rewriteUiDpi(): String {
    if (java.awt.GraphicsEnvironment.isHeadless()) {
        return "unknown"
    }
    return java.awt.Toolkit.getDefaultToolkit().screenResolution.toString()
}

private fun rewriteRepoRoot(start: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()): Path {
    return generateSequence(start) { it.parent }
        .firstOrNull { candidate ->
            Files.exists(candidate.resolve("plans/rewrite/PLAN.md"))
        }
        ?: error("Unable to locate rewrite repository root from $start")
}

private val RUN_ID_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
