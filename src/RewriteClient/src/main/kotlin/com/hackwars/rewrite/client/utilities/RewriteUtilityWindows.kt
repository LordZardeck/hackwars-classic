package com.hackwars.rewrite.client.utilities

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientLogState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private sealed interface RewritePreferenceSpec {
    val label: String

    data class Section(
        override val label: String,
    ) : RewritePreferenceSpec

    data class Checkbox(
        override val label: String,
        val key: String,
    ) : RewritePreferenceSpec

    data class TriState(
        override val label: String,
        val key: String,
    ) : RewritePreferenceSpec
}

internal enum class RewritePreferenceTriStateValue(
    val persistedValue: String,
    val displayLabel: String,
) {
    ALWAYS("always", "Always"),
    NEVER("never", "Never"),
    ASK("ask", "Ask");

    companion object {
        fun fromPersisted(rawValue: String?): RewritePreferenceTriStateValue {
            return entries.firstOrNull { it.persistedValue.equals(rawValue?.trim(), ignoreCase = true) } ?: ASK
        }

        fun fromDisplay(displayLabel: String?): RewritePreferenceTriStateValue {
            return entries.firstOrNull { it.displayLabel == displayLabel } ?: ASK
        }
    }
}

private val REWRITE_LEGACY_PREFERENCE_SPECS: List<RewritePreferenceSpec> = listOf(
    RewritePreferenceSpec.Section(" Startup Options"),
    RewritePreferenceSpec.Checkbox("Start network application on startup", "network"),
    RewritePreferenceSpec.Checkbox("Show log window on startup", "logwindow"),
    RewritePreferenceSpec.Checkbox("Show 'First Attack' Tutorial on startup", "attacktutorial"),
    RewritePreferenceSpec.Section(" Port Management Options"),
    RewritePreferenceSpec.TriState(
        "Replace the port note with the name of the newly installed application when replacing an application?",
        "appnote",
    ),
    RewritePreferenceSpec.Checkbox("Show popup when you've successfully replaced an application.", "appreplaced"),
    RewritePreferenceSpec.Checkbox("Show popup when you've successfully replaced a firewall.", "firewallreplaced"),
    RewritePreferenceSpec.Checkbox("Show popup when you've successfully removed a firewall.", "firewallremoved"),
    RewritePreferenceSpec.Checkbox("Show heal successful message", "healing"),
    RewritePreferenceSpec.Checkbox("Always ask for confirmation when healing", "healconfirm"),
    RewritePreferenceSpec.Section(" Equipment Manager Options"),
    RewritePreferenceSpec.Checkbox("Show popup when a card is repaired", "cardrepaired"),
    RewritePreferenceSpec.Checkbox("Show popup when a card is replaced", "cardreplaced"),
    RewritePreferenceSpec.Section(" Scan/Attack/Redirect Options"),
    RewritePreferenceSpec.Checkbox("Show attack confirmation", "attackconfirm"),
    RewritePreferenceSpec.Checkbox("Show redirect confimation", "redirectconfirm"),
    RewritePreferenceSpec.Checkbox("Show scan confirmation", "scanconfirm"),
    RewritePreferenceSpec.Section(" Commodity Converstion Options"),
    RewritePreferenceSpec.Checkbox("Show popup when successfully converted commodities to file.", "commodtofile"),
    RewritePreferenceSpec.Checkbox("Show popup when successfully converted file to commodities.", "filetocommod"),
    RewritePreferenceSpec.Section(" Banking Options"),
    RewritePreferenceSpec.Checkbox("Show popup when you've successfully transferred money.", "transferto"),
    RewritePreferenceSpec.Checkbox("Show popup when you've received a transfer.", "transferfrom"),
    RewritePreferenceSpec.Section(" Web Browser"),
    RewritePreferenceSpec.Checkbox("Show popup when files successfully purchased.", "filepurchased"),
    RewritePreferenceSpec.Checkbox("Show popup when you've successfully voted.", "votesuccessful"),
)

internal fun legacyCheckboxPreferenceSelected(rawValue: String?): Boolean {
    return rawValue.isNullOrBlank() || rawValue.equals("true", ignoreCase = true)
}

internal fun normalizeLegacyCheckboxPreference(rawValue: String?): String {
    return if (legacyCheckboxPreferenceSelected(rawValue)) "true" else "false"
}

internal fun renderLogEntries(logState: ClientLogState?): String {
    return logState?.entries.orEmpty().joinToString(separator = "\n") { entry -> entry.renderedLine }
}

internal class RewriteStartupUtilityCoordinator(
    private val launchCommand: (RewriteShellCommand) -> Unit,
) {
    private var pendingSessionKey: String? = null
    private var appliedSessionKey: String? = null

    fun noteAuthenticatedSessionReady(sessionKey: String?) {
        pendingSessionKey = sessionKey
    }

    fun reset() {
        pendingSessionKey = null
        appliedSessionKey = null
    }

    fun maybeLaunch(
        route: RewriteClientRoute,
        playerIp: String?,
        shellState: ClientGameSnapshot?,
    ) {
        val sessionKey = playerIp ?: return
        if (route != RewriteClientRoute.DESKTOP) {
            return
        }
        if (pendingSessionKey != sessionKey || appliedSessionKey == sessionKey) {
            return
        }
        val currentShellState = shellState ?: return
        appliedSessionKey = sessionKey
        pendingSessionKey = null

        if (legacyCheckboxPreferenceSelected(currentShellState.preferences.values["network"])) {
            launchCommand(RewriteShellCommand.NETWORK)
        }
        if (
            legacyCheckboxPreferenceSelected(currentShellState.preferences.values["logwindow"]) &&
            currentShellState.logs.entries.isNotEmpty()
        ) {
            launchCommand(RewriteShellCommand.LOG_WINDOW)
        }
    }
}

internal class RewriteLogWindow(
    private val controller: RewriteRootController,
) : JInternalFrame("Log Window", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logArea = JTextArea().apply {
        name = "rewrite-log-window-text"
        isEditable = false
        lineWrap = false
        wrapStyleWord = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    init {
        name = "rewrite-log-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(640, 400)
        contentPane = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JScrollPane(logArea).apply { name = "rewrite-log-window-scroll" }, BorderLayout.CENTER)
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                windowScope.cancel()
            }
        })
        renderLogs(controller.gameLogState())
        observeLogs()
    }

    private fun observeLogs() {
        windowScope.launch {
            controller.gameLogStateSelector().collect { state ->
                SwingUtilities.invokeLater {
                    renderLogs(state)
                }
            }
        }
    }

    private fun renderLogs(state: ClientLogState?) {
        logArea.text = renderLogEntries(state)
        logArea.caretPosition = logArea.document.length
    }
}

internal class RewritePreferencesWindow(
    private val controller: RewriteRootController,
) : JInternalFrame("Preferences", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val baselineValuesByKey = linkedMapOf<String, String>()
    private val controlsByKey = linkedMapOf<String, RewritePreferenceControl>()
    private val contentPanel = JPanel().apply {
        name = "rewrite-preferences-content"
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }
    private val statusLabel = JLabel(" ").apply {
        name = "rewrite-preferences-status"
        foreground = Color(0x1F, 0x4D, 0x24)
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-preferences-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val applyButton = JButton("Apply").apply {
        name = "rewrite-preferences-apply-button"
        addActionListener { requestApply() }
    }
    private val cancelButton = JButton("Cancel").apply {
        name = "rewrite-preferences-cancel-button"
        addActionListener { dispose() }
    }

    private var requestInFlight: Boolean = false
    private var suppressControlEvents: Boolean = false

    init {
        name = "rewrite-preferences-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(760, 620)
        buildContent()
        loadInitialValues()
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JScrollPane(contentPanel).apply { name = "rewrite-preferences-scroll" }, BorderLayout.CENTER)
            add(buildFooterPanel(), BorderLayout.SOUTH)
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                windowScope.cancel()
            }
        })
        renderControlState()
    }

    private fun buildContent() {
        REWRITE_LEGACY_PREFERENCE_SPECS.forEachIndexed { index, spec ->
            when (spec) {
                is RewritePreferenceSpec.Section -> {
                    contentPanel.add(JSeparator().apply {
                        name = "rewrite-preferences-section-separator-$index"
                    })
                    contentPanel.add(
                        JLabel(spec.label).apply {
                            name = "rewrite-preferences-section-$index"
                            foreground = Color(0x1C, 0x4D, 0xD5)
                            alignmentX = LEFT_ALIGNMENT
                        },
                    )
                }

                is RewritePreferenceSpec.Checkbox -> {
                    val checkBox = JCheckBox(spec.label).apply {
                        name = "rewrite-preferences-option-${spec.key}"
                        alignmentX = LEFT_ALIGNMENT
                        addActionListener { handleControlEdited() }
                    }
                    controlsByKey[spec.key] = RewriteCheckboxPreferenceControl(spec.key, checkBox)
                    contentPanel.add(checkBox)
                }

                is RewritePreferenceSpec.TriState -> {
                    val combo = JComboBox(RewritePreferenceTriStateValue.entries.map { it.displayLabel }.toTypedArray()).apply {
                        name = "rewrite-preferences-option-${spec.key}"
                        addActionListener { handleControlEdited() }
                    }
                    controlsByKey[spec.key] = RewriteTriStatePreferenceControl(spec.key, combo)
                    contentPanel.add(
                        JPanel(BorderLayout(8, 0)).apply {
                            isOpaque = false
                            alignmentX = LEFT_ALIGNMENT
                            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                            add(
                                JLabel(spec.label).apply {
                                    name = "rewrite-preferences-option-label-${spec.key}"
                                },
                                BorderLayout.CENTER,
                            )
                            add(combo, BorderLayout.EAST)
                        },
                    )
                }
            }
        }
    }

    private fun buildFooterPanel(): JPanel {
        return JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(applyButton)
                    add(cancelButton)
                },
                BorderLayout.NORTH,
            )
            add(statusLabel, BorderLayout.CENTER)
            add(errorLabel, BorderLayout.SOUTH)
        }
    }

    private fun loadInitialValues() {
        suppressControlEvents = true
        try {
            val values = controller.gamePreferenceState()?.values.orEmpty()
            controlsByKey.forEach { (key, control) ->
                control.applyStoredValue(values[key])
                baselineValuesByKey[key] = control.readStoredValue()
            }
        } finally {
            suppressControlEvents = false
        }
    }

    private fun handleControlEdited() {
        if (suppressControlEvents || requestInFlight) {
            return
        }
        statusLabel.text = " "
        errorLabel.text = " "
        renderControlState()
    }

    private fun dirtyEntries(): List<Pair<String, String>> {
        return controlsByKey.entries.mapNotNull { (key, control) ->
            val currentValue = control.readStoredValue()
            if (baselineValuesByKey[key] != currentValue) {
                key to currentValue
            } else {
                null
            }
        }
    }

    private fun requestApply() {
        val dirtyEntries = dirtyEntries()
        if (dirtyEntries.isEmpty()) {
            return
        }
        requestInFlight = true
        statusLabel.text = "Saving preferences..."
        errorLabel.text = " "
        renderControlState()
        windowScope.launch {
            var savedCount = 0
            var failureMessage: String? = null
            for ((key, value) in dirtyEntries) {
                when (val result = controller.requestSetPreference(key, value)) {
                    is RewriteGameCommandResult.Success -> {
                        val confirmedValue = result.value.value
                        baselineValuesByKey[key] = confirmedValue
                        savedCount += 1
                    }

                    is RewriteGameCommandResult.Failure -> {
                        failureMessage = result.message
                        break
                    }
                }
            }
            SwingUtilities.invokeLater {
                requestInFlight = false
                statusLabel.text = when {
                    failureMessage == null && savedCount > 0 -> "Preferences saved."
                    failureMessage != null && savedCount > 0 -> "$savedCount preference(s) saved."
                    else -> " "
                }
                errorLabel.text = failureMessage ?: " "
                renderControlState()
            }
        }
    }

    private fun renderControlState() {
        val hasDirtyEntries = dirtyEntries().isNotEmpty()
        controlsByKey.values.forEach { control ->
            control.setEnabled(!requestInFlight)
        }
        applyButton.isEnabled = !requestInFlight && hasDirtyEntries
        cancelButton.isEnabled = !requestInFlight
    }
}

private sealed interface RewritePreferenceControl {
    val key: String

    fun readStoredValue(): String

    fun applyStoredValue(rawValue: String?)

    fun setEnabled(enabled: Boolean)
}

private class RewriteCheckboxPreferenceControl(
    override val key: String,
    private val checkBox: JCheckBox,
) : RewritePreferenceControl {
    override fun readStoredValue(): String = if (checkBox.isSelected) "true" else "false"

    override fun applyStoredValue(rawValue: String?) {
        checkBox.isSelected = legacyCheckboxPreferenceSelected(rawValue)
    }

    override fun setEnabled(enabled: Boolean) {
        checkBox.isEnabled = enabled
    }
}

private class RewriteTriStatePreferenceControl(
    override val key: String,
    private val comboBox: JComboBox<String>,
) : RewritePreferenceControl {
    override fun readStoredValue(): String {
        return RewritePreferenceTriStateValue.fromDisplay(comboBox.selectedItem?.toString()).persistedValue
    }

    override fun applyStoredValue(rawValue: String?) {
        comboBox.selectedItem = RewritePreferenceTriStateValue.fromPersisted(rawValue).displayLabel
    }

    override fun setEnabled(enabled: Boolean) {
        comboBox.isEnabled = enabled
    }
}
