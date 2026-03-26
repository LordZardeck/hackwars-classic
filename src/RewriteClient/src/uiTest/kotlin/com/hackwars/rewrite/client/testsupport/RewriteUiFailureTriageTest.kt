package com.hackwars.rewrite.client.testsupport

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewriteUiFailureTriageTest {
    @Test
    fun artifactSegmentNormalizesNamesForStableDirectories() {
        assertEquals("rewrite-root-frame-ui-test", rewriteUiArtifactSegment("RewriteRootFrameUiTest"))
        assertEquals("network-map-pass-3", rewriteUiArtifactSegment("Network Map (Pass 3)"))
        assertEquals("unnamed", rewriteUiArtifactSegment("   "))
    }

    @Test
    fun runIdUsesUtcTimestampAndSequence() {
        val startedAt = Instant.parse("2026-03-26T15:40:30Z")

        assertEquals("run-20260326-154030-007", rewriteUiRunId(startedAt, 7))
    }

    @Test
    fun failureTriageWritesManifestFailureTextAndContextIntoCaseDirectory() {
        val artifactRoot = Files.createTempDirectory("rewrite-ui-failure-triage")
        val startedAt = Instant.parse("2026-03-26T15:40:30Z")

        val failure = assertFailsWith<IllegalStateException> {
            rewriteUiFailureTriage(
                suiteName = "RewriteRootFrameUiTest",
                testName = "writesArtifactsOnFailure",
                taskId = "RW-TEST-002",
                passId = "Pass 1",
                context = mapOf("window" to "network", "seedIp" to "192.0.2.10"),
                artifactRoot = artifactRoot,
                startedAt = startedAt,
                sequence = 7,
            ) {
                error("boom")
            }
        }

        assertEquals("boom", failure.message)

        val caseDirectory = rewriteUiCaseDirectory(
            artifactRoot = artifactRoot,
            runId = "run-20260326-154030-007",
            suiteName = "RewriteRootFrameUiTest",
            testName = "writesArtifactsOnFailure",
        )
        assertTrue(Files.exists(caseDirectory.resolve("manifest.json")))
        assertTrue(Files.exists(caseDirectory.resolve("failure.txt")))
        assertTrue(Files.exists(caseDirectory.resolve("context.json")))

        val manifest = caseDirectory.resolve("manifest.json").readText()
        assertContains(manifest, "\"suiteName\": \"RewriteRootFrameUiTest\"")
        assertContains(manifest, "\"testName\": \"writesArtifactsOnFailure\"")
        assertContains(manifest, "\"taskId\": \"RW-TEST-002\"")
        assertContains(manifest, "\"passId\": \"Pass 1\"")
        assertContains(manifest, "\"runId\": \"run-20260326-154030-007\"")
        assertContains(manifest, "\"artifactDirectory\": \"${escapeJson(caseDirectory.toString())}\"")

        val failureText = caseDirectory.resolve("failure.txt").readText()
        assertContains(failureText, "java.lang.IllegalStateException: boom")

        val context = caseDirectory.resolve("context.json").readText()
        assertContains(context, "\"window\": \"network\"")
        assertContains(context, "\"seedIp\": \"192.0.2.10\"")
    }

    @Test
    fun successPathDoesNotCreateArtifacts() {
        val artifactRoot = Files.createTempDirectory("rewrite-ui-failure-triage-success")
        val startedAt = Instant.parse("2026-03-26T15:40:30Z")

        val result = rewriteUiFailureTriage(
            suiteName = "RewriteUtilitiesUiTest",
            testName = "successPath",
            artifactRoot = artifactRoot,
            startedAt = startedAt,
            sequence = 8,
        ) {
            "ok"
        }

        assertEquals("ok", result)
        val caseDirectory = rewriteUiCaseDirectory(
            artifactRoot = artifactRoot,
            runId = "run-20260326-154030-008",
            suiteName = "RewriteUtilitiesUiTest",
            testName = "successPath",
        )
        assertFalse(Files.exists(caseDirectory), "Successful runs should not emit failure artifacts.")
    }

    private fun rewriteUiCaseDirectory(
        artifactRoot: Path,
        runId: String,
        suiteName: String,
        testName: String,
    ): Path {
        return artifactRoot
            .resolve("ui-tests")
            .resolve(runId)
            .resolve(rewriteUiArtifactSegment(suiteName))
            .resolve(rewriteUiArtifactSegment(testName))
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
    }
}
