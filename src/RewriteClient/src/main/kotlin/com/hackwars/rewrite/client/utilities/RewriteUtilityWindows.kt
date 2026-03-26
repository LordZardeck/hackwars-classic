package com.hackwars.rewrite.client.utilities

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.shell.shellImageIcon
import com.hackwars.rewrite.client.ui.hackWarsImageIcon
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.protocol.ClientHardwareState
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientLogState
import com.hackwars.rewrite.protocol.ClientPersonalSettingsResponse
import com.hackwars.rewrite.protocol.ClientPlayerProfileView
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Image
import java.awt.Insets
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.ImageIcon
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
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.JTextField
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

private val PERSONAL_SETTINGS_IMAGE_PATHS = listOf(
    "images/nopic.png",
    "images/Snow_001.png",
    "images/Bill_001.png",
    "images/Necro_001.png",
    "images/Johan_001.png",
    "images/Butch_001.png",
    "images/Jansen_001.png",
    "images/Gunner001.png",
    "images/N00b001.png",
    "images/GothGirl_001.png",
    "images/Rep_001.png",
)

internal class RewritePersonalSettingsWindow(
    private val controller: RewriteRootController,
) : JInternalFrame("Personal Settings", true, false, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tabs = JTabbedPane().apply {
        name = "rewrite-personal-settings-tabs"
    }
    private val profilePanel = JPanel(null).apply {
        name = "rewrite-personal-settings-profile-panel"
        preferredSize = Dimension(470, 420)
    }
    private val portraitLabel = JLabel().apply {
        name = "rewrite-personal-settings-portrait"
        horizontalAlignment = JLabel.CENTER
        verticalAlignment = JLabel.CENTER
        border = BorderFactory.createLineBorder(Color(0x55, 0x55, 0x55))
        bounds = java.awt.Rectangle(5, 5, 160, 160)
        horizontalTextPosition = JLabel.CENTER
        verticalTextPosition = JLabel.BOTTOM
    }
    private val descriptionLabel = JLabel("Description:").apply {
        name = "rewrite-personal-settings-description-label"
        bounds = java.awt.Rectangle(180, 5, 120, 16)
    }
    private val descriptionArea = JTextArea().apply {
        name = "rewrite-personal-settings-description"
        lineWrap = true
        wrapStyleWord = true
        border = JTextField().border
    }
    private val locationLabel = JLabel("Location:").apply {
        name = "rewrite-personal-settings-location-label"
        bounds = java.awt.Rectangle(180, 120, 120, 16)
    }
    private val locationField = JTextField().apply {
        name = "rewrite-personal-settings-location"
    }
    private val statusLabel = JLabel("Loading profile...").apply {
        name = "rewrite-personal-settings-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-personal-settings-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val hardwarePanel = JPanel(null).apply {
        name = "rewrite-personal-settings-hardware-panel"
        bounds = java.awt.Rectangle(180, 175, 260, 170)
    }
    private val hardwareTitle = JLabel("Hardware").apply {
        name = "rewrite-personal-settings-hardware-title"
        font = font.deriveFont(Font.BOLD, 16f)
        bounds = java.awt.Rectangle(0, 0, 120, 20)
    }
    private val prevButton = JButton(shellImageIcon("left.png")).apply {
        name = "rewrite-personal-settings-prev-button"
        isBorderPainted = false
        isContentAreaFilled = false
        actionCommand = "prev"
        addActionListener { showPreviousImage() }
    }
    private val nextButton = JButton(shellImageIcon("right.png")).apply {
        name = "rewrite-personal-settings-next-button"
        isBorderPainted = false
        isContentAreaFilled = false
        actionCommand = "next"
        addActionListener { showNextImage() }
    }
    private val saveImageButton = JButton(shellImageIcon("save.png")).apply {
        name = "rewrite-personal-settings-save-image-button"
        isBorderPainted = false
        isContentAreaFilled = false
        actionCommand = "saveImage"
        addActionListener { submitProfileSave("Image") }
    }
    private val saveDescriptionButton = JButton(shellImageIcon("save.png")).apply {
        name = "rewrite-personal-settings-save-description-button"
        isBorderPainted = false
        isContentAreaFilled = false
        actionCommand = "saveDescription"
        addActionListener { submitProfileSave("Description") }
    }
    private val saveLocationButton = JButton(shellImageIcon("save.png")).apply {
        name = "rewrite-personal-settings-save-location-button"
        isBorderPainted = false
        isContentAreaFilled = false
        actionCommand = "saveLocation"
        addActionListener { submitProfileSave("Location") }
    }

    private var currentImageIndex: Int = 0
    private var currentStateId: String? = null
    private var selfProfile: Boolean = false
    private var requestInFlight: Boolean = false
    private var currentImagePath: String = PERSONAL_SETTINGS_IMAGE_PATHS.first()
    private var loadingError: String? = null

    init {
        name = "rewrite-personal-settings-window"
        defaultCloseOperation = HIDE_ON_CLOSE
        setBounds(50, 50, 500, 500)
        isResizable = false
        isMaximizable = false
        isIconifiable = true
        title = "Personal Settings"
        tabs.addTab("Profile", profilePanel)
        tabs.setBounds(2, 0, 485, 450)
        contentPane = JPanel(null).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            add(tabs)
            add(statusLabel)
            add(errorLabel)
        }
        statusLabel.bounds = java.awt.Rectangle(8, 452, 320, 18)
        errorLabel.bounds = java.awt.Rectangle(8, 468, 460, 18)
        buildProfilePanel()
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosing(event: InternalFrameEvent) {
                windowScope.cancel()
            }

            override fun internalFrameClosed(event: InternalFrameEvent) {
                windowScope.cancel()
            }
        })
        loadProfile()
    }

    private fun buildProfilePanel() {
        profilePanel.add(portraitLabel)
        profilePanel.add(descriptionLabel)
        profilePanel.add(descriptionArea)
        profilePanel.add(locationLabel)
        profilePanel.add(locationField)
        profilePanel.add(prevButton)
        profilePanel.add(nextButton)
        profilePanel.add(saveImageButton)
        profilePanel.add(saveDescriptionButton)
        profilePanel.add(saveLocationButton)
        profilePanel.add(hardwarePanel)

        portraitLabel.bounds = java.awt.Rectangle(5, 5, 160, 160)
        descriptionArea.bounds = java.awt.Rectangle(180, 5, 205, 100)
        locationField.bounds = java.awt.Rectangle(180, 120, 205, 20)
        prevButton.bounds = java.awt.Rectangle(5, 170, 16, 16)
        saveImageButton.bounds = java.awt.Rectangle(75, 170, 16, 16)
        nextButton.bounds = java.awt.Rectangle(149, 170, 16, 16)
        saveDescriptionButton.bounds = java.awt.Rectangle(390, 15, 16, 16)
        saveLocationButton.bounds = java.awt.Rectangle(390, 120, 16, 16)

        hardwarePanel.add(hardwareTitle)
        hardwareTitle.bounds = java.awt.Rectangle(0, 0, 120, 20)
    }

    private fun loadProfile() {
        requestInFlight = true
        statusLabel.text = "Loading profile..."
        errorLabel.text = " "
        renderActionState()
        windowScope.launch {
            val result = controller.requestPersonalSettings()
            SwingUtilities.invokeLater {
                if (!isDisplayable) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> applyProfile(result.value)
                    is RewriteGameCommandResult.Failure -> {
                        statusLabel.text = " "
                        errorLabel.text = result.message
                        renderActionState()
                    }
                }
            }
        }
    }

    private fun applyProfile(response: ClientPersonalSettingsResponse) {
        currentStateId = response.stateId
        val profile = response.profile
        title = "Personal Settings - ${profile.displayName.ifBlank { response.stateId }}"
        currentImagePath = profile.imagePath.ifBlank { PERSONAL_SETTINGS_IMAGE_PATHS.first() }
        currentImageIndex = PERSONAL_SETTINGS_IMAGE_PATHS.indexOf(currentImagePath).takeIf { it >= 0 } ?: 0
        descriptionArea.text = profile.description
        locationField.text = profile.location
        selfProfile = controller.currentAuthenticatedPlayerIp() == response.stateId
        renderProfileImage()
        renderHardwareSummary()
        renderActionState()
        statusLabel.text = "Profile loaded."
        errorLabel.text = " "
    }

    private fun renderProfileImage() {
        portraitLabel.icon = selectedProfileImageIcon()
        portraitLabel.text = if (portraitLabel.icon == null) currentImagePath.substringAfterLast('/') else ""
    }

    private fun renderHardwareSummary() {
        hardwarePanel.isVisible = selfProfile
        val hardware = controller.gameShellState()?.hardware
        hardwarePanel.removeAll()
        hardwarePanel.add(hardwareTitle)
        if (!selfProfile) {
            hardwarePanel.revalidate()
            hardwarePanel.repaint()
            return
        }
        val rows = buildList {
            add(hardwareRow(
                icon = shellImageIcon("cpu.png"),
                text = "CPU Max: ${hardware?.cpuMax?.toInt() ?: 0}",
                y = 30,
            ))
            add(hardwareRow(
                icon = shellImageIcon("hd.png"),
                text = "HD: ${hardware?.hdQuantity ?: 0} / ${hardware?.hdMaximum ?: 0}",
                y = 60,
            ))
            add(hardwareRow(
                icon = shellImageIcon("memory.png"),
                text = "Memory Type: ${hardware?.memoryType ?: 0}",
                y = 90,
            ))
        }
        rows.forEach(hardwarePanel::add)
        hardwarePanel.revalidate()
        hardwarePanel.repaint()
    }

    private fun hardwareRow(
        icon: ImageIcon?,
        text: String,
        y: Int,
    ): JLabel {
        return JLabel(text, icon, JLabel.LEADING).apply {
            bounds = java.awt.Rectangle(0, y, 240, 20)
        }
    }

    private fun showPreviousImage() {
        if (!selfProfile || requestInFlight || currentImageIndex <= 0) {
            return
        }
        currentImageIndex -= 1
        currentImagePath = PERSONAL_SETTINGS_IMAGE_PATHS[currentImageIndex]
        renderProfileImage()
        renderActionState()
    }

    private fun showNextImage() {
        if (!selfProfile || requestInFlight || currentImageIndex >= PERSONAL_SETTINGS_IMAGE_PATHS.lastIndex) {
            return
        }
        currentImageIndex += 1
        currentImagePath = PERSONAL_SETTINGS_IMAGE_PATHS[currentImageIndex]
        renderProfileImage()
        renderActionState()
    }

    private fun submitProfileSave(actionLabel: String) {
        if (!selfProfile || requestInFlight) {
            return
        }
        requestInFlight = true
        statusLabel.text = "Saving profile..."
        errorLabel.text = " "
        renderActionState()
        val imagePath = currentImagePath
        val description = descriptionArea.text
        val location = locationField.text
        windowScope.launch {
            val result = controller.savePersonalSettings(
                imagePath = imagePath,
                description = description,
                location = location,
            )
            SwingUtilities.invokeLater {
                if (!isDisplayable) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        applyProfile(result.value)
                        statusLabel.text = "${actionLabel} saved."
                    }
                    is RewriteGameCommandResult.Failure -> {
                        statusLabel.text = " "
                        errorLabel.text = result.message
                    }
                }
                renderActionState()
            }
        }
    }

    private fun renderActionState() {
        val enabled = selfProfile && !requestInFlight
        prevButton.isVisible = selfProfile
        nextButton.isVisible = selfProfile
        saveImageButton.isVisible = selfProfile
        saveDescriptionButton.isVisible = selfProfile
        saveLocationButton.isVisible = selfProfile
        prevButton.isEnabled = enabled && currentImageIndex > 0
        nextButton.isEnabled = enabled && currentImageIndex < PERSONAL_SETTINGS_IMAGE_PATHS.lastIndex
        saveImageButton.isEnabled = enabled
        saveDescriptionButton.isEnabled = enabled
        saveLocationButton.isEnabled = enabled
        descriptionArea.isEditable = enabled
        locationField.isEditable = enabled
    }

    private fun selectedProfileImageIcon(): ImageIcon? {
        return hackWarsImageIcon(currentImagePath, "images/legacy/image.png")
            ?: shellImageIcon("image.png")
    }
}
