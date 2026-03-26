package com.hackwars.rewrite.client

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RewriteMvcArchitectureGuardrailTest {
    @Test
    fun retainedViewAuditInventoryCoversCurrentVisualFiles() {
        assertEquals(
            retainedViewViolationInventory.keys,
            retainedViewCandidateFiles(),
            buildString {
                appendLine("Retained MVC audit inventory is out of date.")
                appendLine("Expected:")
                retainedViewViolationInventory.keys.forEach { appendLine("  $it") }
                appendLine("Actual:")
                retainedViewCandidateFiles().forEach { appendLine("  $it") }
            },
        )
    }

    @Test
    fun retainedViewViolationInventoryRemainsStable() {
        val actualViolations = retainedViewViolationInventory.keys.associateWith { relativePath ->
            detectViolations(sourcePath(relativePath).readText())
        }

        assertEquals(
            retainedViewViolationInventory,
            actualViolations,
            buildString {
                appendLine("Retained MVC view violations changed.")
                appendLine("Update the audited inventory only when the change is intentional.")
                appendLine("Expected:")
                retainedViewViolationInventory.forEach { (path, violations) ->
                    appendLine("  $path -> ${violations.sorted()}")
                }
                appendLine("Actual:")
                actualViolations.forEach { (path, violations) ->
                    appendLine("  $path -> ${violations.sorted()}")
                }
            },
        )
    }

    @Test
    fun mvcFoundationPackageStaysClean() {
        val mvcFiles = Files.walk(mvcRoot())
            .use { paths ->
                paths.asSequence()
                    .filter { Files.isRegularFile(it) }
                    .filter { it.name.endsWith(".kt") }
                    .sortedBy { it.invariantSeparatorsPathString }
                    .toList()
            }

        assertTrue(mvcFiles.isNotEmpty(), "Expected rewrite MVC foundation files to exist.")

        mvcFiles.forEach { file ->
            val violations = detectViolations(file.readText())
            assertTrue(
                violations.isEmpty(),
                "MVC foundation file ${relativeToModule(file)} must stay clean, found $violations",
            )
        }
    }

    private fun retainedViewCandidateFiles(): Set<String> {
        val filePattern = Regex("""Rewrite.*(Window|Windows|Dialog|View|Browser|Rail|MenuBar|TaskBar)\.kt$""")
        return Files.walk(clientRoot())
            .use { paths ->
                paths.asSequence()
                    .filter { Files.isRegularFile(it) }
                    .filter { path ->
                        val relative = relativeToModule(path)
                        relative.startsWith("src/main/kotlin/com/hackwars/rewrite/client/economy/") ||
                            relative.startsWith("src/main/kotlin/com/hackwars/rewrite/client/files/") ||
                            relative.startsWith("src/main/kotlin/com/hackwars/rewrite/client/network/") ||
                            relative.startsWith("src/main/kotlin/com/hackwars/rewrite/client/systems/") ||
                            relative.startsWith("src/main/kotlin/com/hackwars/rewrite/client/utilities/") ||
                            relative.startsWith("src/main/kotlin/com/hackwars/rewrite/client/web/") ||
                            relative.startsWith("src/main/kotlin/com/hackwars/rewrite/client/shell/")
                    }
                    .filter { path -> filePattern.matches(path.name) }
                    .filterNot { path -> relativeToModule(path) == "src/main/kotlin/com/hackwars/rewrite/client/RewriteRootFrame.kt" }
                    .map(::relativeToModule)
                    .sorted()
                    .toSet()
            }
    }

    private fun detectViolations(source: String): Set<String> {
        val violations = linkedSetOf<String>()
        if (source.contains("import com.hackwars.rewrite.client.RewriteRootController")) {
            violations += "controller_import"
        }
        if (source.contains("import com.hackwars.rewrite.protocol.")) {
            violations += "protocol_import"
        }
        if (source.contains("import com.hackwars.rewrite.clientmodel.")) {
            violations += "clientmodel_import"
        }
        if (
            source.contains("import kotlinx.coroutines.") ||
            source.contains("CoroutineScope(") ||
            source.contains("SupervisorJob(") ||
            source.contains("launch(") ||
            source.contains("collect(")
        ) {
            violations += "coroutine_api"
        }
        if (source.contains("selector(")) {
            violations += "selector_subscription"
        }
        if (listenerRegistrationPattern.containsMatchIn(source)) {
            violations += "listener_registration"
        }
        if (controllerDeclarationPattern.containsMatchIn(source)) {
            violations += "mixed_controller_file"
        }
        return violations
    }

    private fun sourcePath(relativePath: String): Path {
        val path = moduleRoot().resolve(relativePath)
        assertTrue(path.exists(), "Expected source file $relativePath to exist.")
        return path
    }

    private fun mvcRoot(): Path = moduleRoot().resolve("src/main/kotlin/com/hackwars/rewrite/client/mvc")

    private fun clientRoot(): Path = moduleRoot().resolve("src/main/kotlin/com/hackwars/rewrite/client")

    private fun moduleRoot(): Path = Paths.get("").toAbsolutePath()

    private fun relativeToModule(path: Path): String =
        moduleRoot().relativize(path.toAbsolutePath()).invariantSeparatorsPathString

    companion object {
        private val listenerRegistrationPattern = Regex(
            """\badd[A-Z][A-Za-z0-9_]*Listener\b""",
        )
        private val controllerDeclarationPattern = Regex(
            """\b(class|object)\s+[A-Za-z0-9_]*Controller\b""",
        )

        private val retainedViewViolationInventory: Map<String, Set<String>> = linkedMapOf(
            "src/main/kotlin/com/hackwars/rewrite/client/economy/RewriteBankingWindows.kt" to setOf(
                "controller_import",
                "protocol_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/economy/RewriteBountyDialog.kt" to setOf(
                "controller_import",
                "protocol_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/files/RewriteFileWindows.kt" to setOf(
                "protocol_import",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/files/RewriteLocalDirectoryBrowser.kt" to setOf(
                "controller_import",
                "protocol_import",
                "clientmodel_import",
                "coroutine_api",
                "selector_subscription",
                "listener_registration",
                "mixed_controller_file",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/files/RewriteScriptEditorWindow.kt" to setOf(
                "controller_import",
                "protocol_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/network/RewriteAttackFollowupWindows.kt" to setOf(
                "controller_import",
                "protocol_import",
                "clientmodel_import",
                "coroutine_api",
                "selector_subscription",
                "listener_registration",
                "mixed_controller_file",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/network/RewriteAttackWindows.kt" to setOf(
                "controller_import",
                "protocol_import",
                "clientmodel_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/network/RewriteFtpWindows.kt" to setOf(
                "controller_import",
                "protocol_import",
                "coroutine_api",
                "selector_subscription",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/network/RewriteNetworkWindows.kt" to setOf(
                "controller_import",
                "protocol_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/network/RewriteZombieAttackWindows.kt" to setOf(
                "controller_import",
                "protocol_import",
                "clientmodel_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/shell/RewriteDesktopMenuBar.kt" to setOf(
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/shell/RewriteDesktopShellView.kt" to setOf(
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/shell/RewriteDesktopTaskBar.kt" to setOf(
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/shell/RewritePreferredPortWindow.kt" to emptySet(),
            "src/main/kotlin/com/hackwars/rewrite/client/shell/RewriteShellStatsRail.kt" to emptySet(),
            "src/main/kotlin/com/hackwars/rewrite/client/systems/RewriteInventoryWindows.kt" to setOf(
                "controller_import",
                "protocol_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/systems/RewritePortManagementWindow.kt" to setOf(
                "controller_import",
                "protocol_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/systems/RewriteWatchManagerWindow.kt" to setOf(
                "controller_import",
                "protocol_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/utilities/RewriteUtilityWindows.kt" to setOf(
                "controller_import",
                "protocol_import",
                "clientmodel_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/web/RewriteHtmlView.kt" to setOf(
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/web/RewriteSiteEditorWindow.kt" to setOf(
                "controller_import",
                "protocol_import",
                "coroutine_api",
                "listener_registration",
            ),
            "src/main/kotlin/com/hackwars/rewrite/client/web/RewriteWebBrowserWindow.kt" to setOf(
                "controller_import",
                "protocol_import",
                "coroutine_api",
                "listener_registration",
            ),
        )
    }
}
