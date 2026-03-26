package com.hackwars.rewrite.client

import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RewriteUiArtifactConventionsTest {
    @Test
    fun artifactPathsUseStableHierarchyAndSanitizedSegments() {
        val runId = runId(Instant.parse("2026-03-26T18:42:00Z"), 7)
        val casePath = caseDirectory(
            repoRoot = Path.of("/workspace/hackwars-classic-original"),
            runId = runId,
            suiteName = "RewriteClient DevMode UiTest",
            testName = "deterministic dev-mode can reach desktop!",
        )

        assertEquals(
            Path.of(
                "/workspace/hackwars-classic-original/artifacts/rewrite/ui-tests/run-20260326-184200-007/rewrite-client-dev-mode-ui-test/deterministic-dev-mode-can-reach-desktop",
            ),
            casePath,
        )
        assertEquals("rewrite-client-dev-mode-ui-test", sanitize("RewriteClient DevMode UiTest"))
        assertEquals("deterministic-dev-mode-can-reach-desktop", sanitize("deterministic dev-mode can reach desktop!"))
    }

    @Test
    fun manifestSerializationReservesScreenshotFields() {
        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            explicitNulls = true
        }

        val manifest = UiFailureManifest(
            suiteName = "RewriteClientDevModeUiTest",
            testName = "deterministicDevModeCanReachDesktopAndOpenRepresentativeWindows",
            taskId = "RW-TEST-002",
            passId = "Pass 1",
            runId = "run-20260326-184200-007",
            createdAtEpochMillis = 1_234_567_890L,
            runDirectory = "/workspace/hackwars-classic-original/artifacts/rewrite/ui-tests/run-20260326-184200-007",
            caseDirectory = "/workspace/hackwars-classic-original/artifacts/rewrite/ui-tests/run-20260326-184200-007/rewrite-client-dev-mode-ui-test/deterministic-dev-mode-can-reach-desktop-and-open-representative-windows",
            artifactDirectory = "/workspace/hackwars-classic-original/artifacts/rewrite/ui-tests/run-20260326-184200-007/rewrite-client-dev-mode-ui-test/deterministic-dev-mode-can-reach-desktop-and-open-representative-windows",
            failureSummary = "java.lang.IllegalStateException",
            exceptionType = "java.lang.IllegalStateException",
            exceptionMessage = "boom",
            environment = linkedMapOf(
                "jvmVersion" to "21.0.6",
                "osName" to "Mac OS X",
                "osVersion" to "15.0",
                "headless" to "false",
                "displayScale" to "unknown",
            ),
            context = linkedMapOf(
                "surface" to "dev-mode",
                "route" to "desktop",
            ),
        )

        val serialized = json.encodeToString(UiFailureManifest.serializer(), manifest)
        assertTrue(serialized.contains("\"screenshotPath\": null"))
        assertTrue(serialized.contains("\"screenshotDiffPath\": null"))
        assertTrue(serialized.contains("\"surface\": \"dev-mode\""))
        assertTrue(serialized.contains("\"runId\": \"run-20260326-184200-007\""))
    }

    private fun caseDirectory(
        repoRoot: Path,
        runId: String,
        suiteName: String,
        testName: String,
    ): Path {
        return repoRoot
            .resolve("artifacts")
            .resolve("rewrite")
            .resolve("ui-tests")
            .resolve(runId)
            .resolve(sanitize(suiteName))
            .resolve(sanitize(testName))
    }

    private fun sanitize(value: String): String {
        val camelSeparated = value.trim().replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
        val normalized = camelSeparated.replace(Regex("[^A-Za-z0-9]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .lowercase()
        return if (normalized.isBlank()) "unnamed" else normalized
    }

    private fun runId(startedAt: Instant, sequence: Int): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
        return "run-${formatter.format(startedAt)}-${sequence.toString().padStart(3, '0')}"
    }

    @Serializable
    private data class UiFailureManifest(
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
        val screenshotPath: String? = null,
        val screenshotDiffPath: String? = null,
    )
}
