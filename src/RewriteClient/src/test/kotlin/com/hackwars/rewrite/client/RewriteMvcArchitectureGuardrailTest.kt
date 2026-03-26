package com.hackwars.rewrite.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class RewriteMvcArchitectureGuardrailTest {
    @Test
    fun retainedPostLoginViewFilesStayAccountedForByGuardrailSuite() {
        assertEquals(expectedViewFiles, scanRetainedViewFiles().map(::relativeSourcePath).toSet())
    }

    @Test
    fun retainedPostLoginViewFilesStayWithinKnownMvcDebtEnvelope() {
        val actualViolations = scanRetainedViewFiles().associate { file ->
            relativeSourcePath(file) to detectDebt(file)
        }.filterValues { it.isNotEmpty() }

        assertEquals(expectedDebtByFile, actualViolations)
    }

    private fun scanRetainedViewFiles(): List<Path> {
        val sourceRoot = resolveClientSourceRoot()
        return retainedPackages
            .flatMap { packageName ->
                Files.list(sourceRoot.resolve(packageName)).use { paths ->
                    paths
                        .filter { path -> path.fileName.toString().matches(viewFilePattern) }
                        .toList()
                }
            }
            .sortedBy(::relativeSourcePath)
    }

    private fun detectDebt(file: Path): Set<MvcDebt> {
        val source = file.readText()
        val debt = linkedSetOf<MvcDebt>()
        if (controllerImportPattern.containsMatchIn(source)) {
            debt += MvcDebt.CONTROLLER_IMPORT
        }
        if (protocolImportPattern.containsMatchIn(source)) {
            debt += MvcDebt.PROTOCOL_IMPORT
        }
        if (clientModelImportPattern.containsMatchIn(source)) {
            debt += MvcDebt.CLIENT_MODEL_IMPORT
        }
        if (coroutinesImportPattern.containsMatchIn(source)) {
            debt += MvcDebt.COROUTINES_IMPORT
        }
        if (listenerRegistrationTokens.any(source::contains)) {
            debt += MvcDebt.LISTENER_REGISTRATION
        }
        if (mixedControllerPattern.containsMatchIn(source)) {
            debt += MvcDebt.MIXED_CONTROLLER_FILE
        }
        return debt
    }

    private fun resolveClientSourceRoot(): Path {
        val moduleRootCandidate = Path.of(System.getProperty("user.dir"), "src", "main", "kotlin", "com", "hackwars", "rewrite", "client")
        if (moduleRootCandidate.exists()) {
            return moduleRootCandidate
        }

        val repoRootCandidate = Path.of(
            System.getProperty("user.dir"),
            "src",
            "RewriteClient",
            "src",
            "main",
            "kotlin",
            "com",
            "hackwars",
            "rewrite",
            "client",
        )
        if (repoRootCandidate.exists()) {
            return repoRootCandidate
        }

        error("Unable to locate RewriteClient Kotlin source root from ${System.getProperty("user.dir")}")
    }

    private fun relativeSourcePath(file: Path): String {
        return retainedPackages.firstNotNullOfOrNull { packageName ->
            if (file.parent?.name == packageName) {
                "${packageName}/${file.fileName}"
            } else {
                null
            }
        } ?: file.invariantSeparatorsPathString
    }

    private enum class MvcDebt {
        CONTROLLER_IMPORT,
        PROTOCOL_IMPORT,
        CLIENT_MODEL_IMPORT,
        COROUTINES_IMPORT,
        LISTENER_REGISTRATION,
        MIXED_CONTROLLER_FILE,
    }

    companion object {
        private val retainedPackages = listOf(
            "economy",
            "files",
            "network",
            "systems",
            "utilities",
            "web",
            "shell",
        )

        private val viewFilePattern = Regex(""".*(Window|Windows|Dialog|Dialogs|View|Panel|Form|Bar|Rail|Frame|Browser)\.kt$""")

        private val controllerImportPattern = Regex(
            pattern = """^import .*RewriteRootController\s*$|^import .*\.\\w*Controller\s*$""",
            options = setOf(RegexOption.MULTILINE),
        )
        private val protocolImportPattern = Regex(
            pattern = """^import com\.hackwars\.rewrite\.protocol\..*$""",
            options = setOf(RegexOption.MULTILINE),
        )
        private val clientModelImportPattern = Regex(
            pattern = """^import com\.hackwars\.rewrite\.clientmodel\..*$""",
            options = setOf(RegexOption.MULTILINE),
        )
        private val coroutinesImportPattern = Regex(
            pattern = """^import kotlinx\.coroutines\..*$""",
            options = setOf(RegexOption.MULTILINE),
        )
        private val mixedControllerPattern = Regex("""\b(?:class|object)\s+\w*Controller\b""")

        private val listenerRegistrationTokens = setOf(
            "addActionListener",
            "addMouseListener",
            "addFocusListener",
            "addDocumentListener",
            "addChangeListener",
            "addHyperlinkListener",
            "addInternalFrameListener",
            "addWindowListener",
        )

        private val expectedViewFiles = setOf(
            "economy/RewriteBankingWindows.kt",
            "economy/RewriteBountyDialog.kt",
            "files/RewriteFileWindows.kt",
            "files/RewriteLocalDirectoryBrowser.kt",
            "files/RewriteScriptEditorWindow.kt",
            "network/RewriteAttackFollowupWindows.kt",
            "network/RewriteAttackWindows.kt",
            "network/RewriteFtpWindows.kt",
            "network/RewriteNetworkWindows.kt",
            "network/RewriteZombieAttackWindows.kt",
            "systems/RewriteInventoryWindows.kt",
            "systems/RewritePortManagementWindow.kt",
            "systems/RewriteWatchManagerWindow.kt",
            "utilities/RewriteUtilityWindows.kt",
            "web/RewriteHtmlView.kt",
            "web/RewriteSiteEditorWindow.kt",
            "web/RewriteWebBrowserWindow.kt",
            "shell/RewriteDesktopMenuBar.kt",
            "shell/RewriteDesktopShellView.kt",
            "shell/RewriteDesktopTaskBar.kt",
            "shell/RewritePreferredPortWindow.kt",
            "shell/RewriteShellStatsRail.kt",
        )

        private val expectedDebtByFile = mapOf(
            "economy/RewriteBankingWindows.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "economy/RewriteBountyDialog.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "files/RewriteFileWindows.kt" to setOf(
                MvcDebt.PROTOCOL_IMPORT,
            ),
            "files/RewriteLocalDirectoryBrowser.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.CLIENT_MODEL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
                MvcDebt.MIXED_CONTROLLER_FILE,
            ),
            "files/RewriteScriptEditorWindow.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "network/RewriteAttackFollowupWindows.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.CLIENT_MODEL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
                MvcDebt.MIXED_CONTROLLER_FILE,
            ),
            "network/RewriteAttackWindows.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.CLIENT_MODEL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "network/RewriteFtpWindows.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "network/RewriteNetworkWindows.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "network/RewriteZombieAttackWindows.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.CLIENT_MODEL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "systems/RewriteInventoryWindows.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "systems/RewritePortManagementWindow.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "systems/RewriteWatchManagerWindow.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "utilities/RewriteUtilityWindows.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.CLIENT_MODEL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "web/RewriteHtmlView.kt" to setOf(
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "web/RewriteSiteEditorWindow.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "web/RewriteWebBrowserWindow.kt" to setOf(
                MvcDebt.CONTROLLER_IMPORT,
                MvcDebt.PROTOCOL_IMPORT,
                MvcDebt.COROUTINES_IMPORT,
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "shell/RewriteDesktopMenuBar.kt" to setOf(
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "shell/RewriteDesktopShellView.kt" to setOf(
                MvcDebt.LISTENER_REGISTRATION,
            ),
            "shell/RewriteDesktopTaskBar.kt" to setOf(
                MvcDebt.LISTENER_REGISTRATION,
            ),
        )
    }
}
