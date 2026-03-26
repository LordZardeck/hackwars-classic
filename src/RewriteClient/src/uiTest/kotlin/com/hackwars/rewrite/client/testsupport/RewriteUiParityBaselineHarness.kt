package com.hackwars.rewrite.client.testsupport

import java.awt.Component
import java.awt.Dimension
import java.nio.file.Path
import java.time.Instant

data class RewriteUiParityBaselineRequest(
    val suiteName: String,
    val testName: String,
    val featureKey: String,
    val baselineFileName: String,
    val legacyUiReference: String,
    val parityAcceptance: String,
    val captureSize: Dimension,
    val taskId: String,
    val passId: String,
    val captureClock: Instant,
    val sequence: Int,
    val artifactRoot: Path = rewriteUiArtifactRoot(),
)

fun assertOrApproveRewriteUiBaseline(
    request: RewriteUiParityBaselineRequest,
    componentProvider: () -> Component,
) {
    val component = componentProvider()
    if (rewriteUiShouldApproveArtifacts()) {
        writeApprovedRewriteUiBaselineCapture(
            RewriteUiApprovedBaselineCapture(
                featureKey = request.featureKey,
                baselineFileName = request.baselineFileName,
                suiteName = request.suiteName,
                testName = request.testName,
                legacyUiReference = request.legacyUiReference,
                parityAcceptance = request.parityAcceptance,
                capture = renderComponent(component, request.captureSize),
                approvedAt = request.captureClock,
                taskId = request.taskId,
                passId = request.passId,
                baselineRoot = rewriteUiBaselineRoot(request.artifactRoot),
                sequence = request.sequence,
            ),
        )
        return
    }

    assertRewriteUiScreenshotMatchesBaseline(
        RewriteUiScreenshotCaptureRequest(
            suiteName = request.suiteName,
            testName = request.testName,
            baselineRelativePath = "${request.featureKey}/${request.baselineFileName}",
            captureSize = request.captureSize,
            taskId = request.taskId,
            passId = request.passId,
            artifactRoot = request.artifactRoot,
            baselineRoot = rewriteUiBaselineRoot(request.artifactRoot),
            captureClock = request.captureClock,
            sequence = request.sequence,
        ),
    ) {
        component
    }
}
