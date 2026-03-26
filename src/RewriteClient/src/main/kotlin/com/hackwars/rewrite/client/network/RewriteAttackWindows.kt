package com.hackwars.rewrite.client.network

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.economy.RewriteSegmentedIpInput
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserEntry
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserController
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserPanel
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryEntryType
import com.hackwars.rewrite.client.shell.RewritePreferredPortWindow
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameUiNotice
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientAttackCancelResponse
import com.hackwars.rewrite.protocol.ClientAttackMessageUiEvent
import com.hackwars.rewrite.protocol.ClientAttackPaneType
import com.hackwars.rewrite.protocol.ClientAttackStartResponse
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientFloatHookValue
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientHookValue
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientProgramLifecycleStatus
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.ClientShowChoicesUiEvent
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.ClientStringHookValue
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val MAX_ATTACK_TARGET_PORT = 31
private const val MAX_ATTACK_SCRIPT_SLOTS = 4
private const val MAX_ATTACK_EXTRA_INFO_VALUES = 5
private const val MAX_ATTACK_PETTY_CASH_TARGET = 500_000_000.0

internal enum class RewriteAttackPaneMode(
    val command: RewriteShellCommand,
    val windowTitle: String,
    val primaryActionLabel: String,
    val sourceApplicationKind: String,
    val paneType: ClientAttackPaneType,
) {
    ATTACK(
        command = RewriteShellCommand.ATTACK_PORT,
        windowTitle = "Attack Port",
        primaryActionLabel = "Attack",
        sourceApplicationKind = "ATTACK",
        paneType = ClientAttackPaneType.ATTACK,
    ),
    REDIRECT(
        command = RewriteShellCommand.REDIRECT_PORT,
        windowTitle = "Redirect Port",
        primaryActionLabel = "Redirect",
        sourceApplicationKind = "REDIRECT",
        paneType = ClientAttackPaneType.REDIRECT,
    ),
    ;

    companion object {
        fun fromCommand(command: RewriteShellCommand): RewriteAttackPaneMode = when (command) {
            RewriteShellCommand.ATTACK_PORT -> ATTACK
            RewriteShellCommand.REDIRECT_PORT -> REDIRECT
            else -> error("Unsupported attack pane command ${command.name}")
        }
    }
}

internal data class RewriteAttackSourcePortOption(
    val portNumber: Int,
    val label: String,
) {
    override fun toString(): String = label
}

internal data class RewriteAttackBankingScriptSelection(
    val directoryPath: String,
    val fileName: String,
    val maliciousIp: String,
    val pettyCashTarget: Double,
)

internal fun deriveAttackSourcePortOptions(
    snapshot: ClientGameSnapshot?,
    mode: RewriteAttackPaneMode,
): List<RewriteAttackSourcePortOption> {
    return snapshot?.ports.orEmpty()
        .filter(ClientPortState::enabled)
        .filterNot(ClientPortState::dummy)
        .filter { port -> port.installedApplication?.kind == mode.sourceApplicationKind }
        .sortedBy(ClientPortState::number)
        .map { port ->
            RewriteAttackSourcePortOption(
                portNumber = port.number,
                label = "${port.number}: ${port.note.toLegacyAttackPortNote()}",
            )
        }
}

internal fun reconcileAttackSourcePortSelection(
    options: List<RewriteAttackSourcePortOption>,
    currentSelection: Int?,
    preferredPort: Int?,
    defaultRedirectPort: Int?,
): Int? {
    val available = options.map(RewriteAttackSourcePortOption::portNumber).toSet()
    return when {
        preferredPort in available -> preferredPort
        currentSelection in available -> currentSelection
        defaultRedirectPort in available -> defaultRedirectPort
        else -> options.firstOrNull()?.portNumber
    }
}

internal fun allowAttackChooserDirectory(directory: ClientDirectoryEntry): Boolean {
    return directory.path !in setOf("/Store", "/Public")
}

internal fun allowAttackChooserFile(file: ClientStoredFile): Boolean {
    return file.kind == ClientStoredFileKind.APPLICATION_BINARY &&
        file.compiledBinary?.applicationKind == ClientApplicationKind.BANKING
}

internal fun buildLegacyAttackScripts(
    bankingSelection: RewriteAttackBankingScriptSelection?,
): List<List<String?>> {
    val bankSlot = listOf(
        bankingSelection?.directoryPath,
        bankingSelection?.fileName,
    )
    val emptySlot = listOf<String?>(null, null)
    return buildList(MAX_ATTACK_SCRIPT_SLOTS) {
        add(bankSlot)
        add(emptySlot)
        add(emptySlot)
        add(emptySlot)
    }
}

internal fun buildLegacyAttackExtraInfo(
    bankingSelection: RewriteAttackBankingScriptSelection?,
    panePettyCashTarget: Double,
): List<ClientHookValue> {
    val bankMaliciousIp = bankingSelection?.maliciousIp.orEmpty()
    val bankPettyCashTarget = bankingSelection?.pettyCashTarget ?: 0.0
    return buildList(MAX_ATTACK_EXTRA_INFO_VALUES) {
        add(ClientStringHookValue(bankMaliciousIp))
        add(ClientFloatHookValue(bankPettyCashTarget))
        add(ClientStringHookValue(""))
        add(ClientFloatHookValue(panePettyCashTarget))
        add(ClientStringHookValue(""))
    }
}

internal class RewriteAttackWindow(
    private val controller: RewriteRootController,
    command: RewriteShellCommand,
    preferredPort: Int? = null,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
) : JInternalFrame(RewriteAttackPaneMode.fromCommand(command).windowTitle, true, true, true, true), RewritePreferredPortWindow {
    private val mode = RewriteAttackPaneMode.fromCommand(command)
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sourcePortModel = DefaultComboBoxModel<RewriteAttackSourcePortOption>()
    private val sourcePortCombo = JComboBox(sourcePortModel).apply {
        name = "rewrite-attack-source-port-combo"
        renderer = RewriteAttackSourcePortRenderer()
        addActionListener { renderState() }
    }
    private val targetIpInput = RewriteSegmentedIpInput().apply {
        name = "rewrite-attack-target-ip-input"
    }
    private val sourceIpField = JTextField(controller.currentAuthenticatedPlayerIp().orEmpty()).apply {
        name = "rewrite-attack-source-ip-field"
        isEditable = false
        columns = 14
    }
    private val targetPortSpinner = JSpinner(
        SpinnerNumberModel(0, 0, MAX_ATTACK_TARGET_PORT, 1),
    ).apply {
        name = "rewrite-attack-target-port-spinner"
    }
    private val statusLabel = JLabel("Waiting for local port data...").apply {
        name = "rewrite-attack-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-attack-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val transcriptArea = JTextArea().apply {
        name = "rewrite-attack-transcript"
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        isEditable = false
    }
    private val primaryButton = JButton(mode.primaryActionLabel).apply {
        name = "rewrite-attack-primary-button"
        addActionListener { submitPrimaryAction() }
    }
    private val scriptField = JTextField().apply {
        name = "rewrite-attack-script-field"
        isEditable = false
        columns = 18
    }
    private val browseButton = JButton("Browse").apply {
        name = "rewrite-attack-browse-button"
        addActionListener { openAttackChooser() }
    }
    private val secondaryPortsLabel = JLabel("None").apply {
        name = "rewrite-attack-secondary-ports-label"
    }
    private val secondaryPortsButton = JButton("Edit").apply {
        name = "rewrite-attack-secondary-ports-button"
        addActionListener { showSecondaryPortsDialog() }
    }
    private val pettyCashTargetSpinner = JSpinner(
        SpinnerNumberModel(0.0, 0.0, MAX_ATTACK_PETTY_CASH_TARGET, 1.0),
    ).apply {
        name = "rewrite-attack-petty-cash-spinner"
    }
    private val advancedPanel = buildAdvancedPanel()

    private var latestShellState: ClientGameSnapshot? = controller.gameShellState()
    private var pendingPreferredPort: Int? = preferredPort
    private var requestInFlight: Boolean = false
    private var activeProgramId: String? = null
    private var latestWindowHandle: Int? = null
    private var activeWindowHandle: Int? = null
    private var activeSourcePort: Int? = null
    private var bankingSelection: RewriteAttackBankingScriptSelection? = null
    private var selectedSecondaryPorts: List<Int> = emptyList()
    private var chooserWindow: JInternalFrame? = null
    private var choicesWindow: RewriteShowChoicesWindow? = null
    private val followupWindowsByKey = linkedMapOf<String, JInternalFrame>()
    private val dailyPayDialogsByKey = linkedMapOf<String, RewriteChangeDailyPayDialog>()
    private val processedUiNoticeKeys = mutableSetOf<String>()

    init {
        name = "rewrite-shell-window-${command.name.lowercase()}"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(760, if (mode == RewriteAttackPaneMode.ATTACK) 560 else 460)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(buildHeaderPanel(), BorderLayout.NORTH)
            add(buildBodyPanel(), BorderLayout.CENTER)
            add(buildFooterPanel(), BorderLayout.SOUTH)
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                disposeFollowupWindows()
                windowScope.cancel()
            }
        })
        controller.snapshot().game.decodedGame.uiNotices.forEach { notice ->
            processedUiNoticeKeys += noticeKey(notice)
        }
        observeShellState()
        observeProgramUpdates()
        observeUiNotices()
        refreshSourcePortOptions()
        renderState()
    }

    override fun applyPreferredPort(preferredPort: Int?) {
        pendingPreferredPort = preferredPort
        if (activeProgramId == null && !requestInFlight) {
            refreshSourcePortOptions()
        }
    }

    private fun buildHeaderPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(
                JLabel("Target IP:"),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 6, 8)
                },
            )
            add(
                targetIpInput,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 0
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(0, 0, 6, 16)
                },
            )
            add(
                JLabel("Target Port:"),
                GridBagConstraints().apply {
                    gridx = 2
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 6, 8)
                },
            )
            add(
                targetPortSpinner,
                GridBagConstraints().apply {
                    gridx = 3
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 6, 0)
                },
            )
            add(
                JLabel("Source IP:"),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 1
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 0, 8)
                },
            )
            add(
                sourceIpField,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 1
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(0, 0, 0, 16)
                },
            )
            add(
                JLabel("Source Port:"),
                GridBagConstraints().apply {
                    gridx = 2
                    gridy = 1
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 0, 8)
                },
            )
            add(
                sourcePortCombo,
                GridBagConstraints().apply {
                    gridx = 3
                    gridy = 1
                    anchor = GridBagConstraints.WEST
                    fill = GridBagConstraints.HORIZONTAL
                },
            )
        }
    }

    private fun buildAdvancedPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            name = "rewrite-attack-advanced-panel"
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0xCC, 0xCC, 0xCC)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
            )
            add(
                JLabel("installScript()"),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 6, 8)
                },
            )
            add(
                scriptField,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 0
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(0, 0, 6, 8)
                },
            )
            add(
                browseButton,
                GridBagConstraints().apply {
                    gridx = 2
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 6, 0)
                },
            )
            add(
                JLabel("switchAttack()"),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 1
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 6, 8)
                },
            )
            add(
                secondaryPortsLabel,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 1
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(0, 0, 6, 8)
                },
            )
            add(
                secondaryPortsButton,
                GridBagConstraints().apply {
                    gridx = 2
                    gridy = 1
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 6, 0)
                },
            )
            add(
                JLabel("checkPettyCashTarget()"),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 2
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 0, 8)
                },
            )
            add(
                pettyCashTargetSpinner,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 2
                    anchor = GridBagConstraints.WEST
                    fill = GridBagConstraints.HORIZONTAL
                },
            )
        }
    }

    private fun buildBodyPanel(): JPanel {
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            if (mode == RewriteAttackPaneMode.ATTACK) {
                add(advancedPanel)
                add(JPanel().apply {
                    isOpaque = false
                    preferredSize = Dimension(0, 8)
                    maximumSize = Dimension(Int.MAX_VALUE, 8)
                })
            }
            add(
                JScrollPane(transcriptArea).apply {
                    preferredSize = Dimension(640, 220)
                    border = BorderFactory.createTitledBorder("Battle")
                },
            )
        }
    }

    private fun buildFooterPanel(): JPanel {
        return JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(primaryButton)
                },
                BorderLayout.NORTH,
            )
            add(
                JPanel(BorderLayout(0, 2)).apply {
                    isOpaque = false
                    add(statusLabel, BorderLayout.NORTH)
                    add(errorLabel, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH,
            )
        }
    }

    private fun observeShellState() {
        windowScope.launch {
            controller.gameShellStateSelector().collect { snapshot ->
                SwingUtilities.invokeLater {
                    if (isClosed || !isDisplayable) {
                        return@invokeLater
                    }
                    latestShellState = snapshot
                    sourceIpField.text = controller.currentAuthenticatedPlayerIp().orEmpty()
                    refreshSourcePortOptions()
                    renderState()
                }
            }
        }
    }

    private fun observeProgramUpdates() {
        windowScope.launch {
            controller.gameProgramUpdatesSelector().collect { updates ->
                SwingUtilities.invokeLater {
                    if (isClosed || !isDisplayable) {
                        return@invokeLater
                    }
                    handleProgramUpdates(updates)
                }
            }
        }
    }

    private fun observeUiNotices() {
        windowScope.launch {
            controller.gameUiNoticesSelector().collect { notices ->
                SwingUtilities.invokeLater {
                    if (isClosed || !isDisplayable) {
                        return@invokeLater
                    }
                    notices.forEach { notice ->
                        val key = noticeKey(notice)
                        if (!processedUiNoticeKeys.add(key)) {
                            return@forEach
                        }
                        when (val event = notice.event) {
                            is ClientAttackMessageUiEvent -> {
                                val windowHandle = activeWindowHandle ?: return@forEach
                                if (event.windowHandle != windowHandle || event.paneType != mode.paneType) {
                                    return@forEach
                                }
                                appendTranscript(event.message)
                            }

                            is ClientShowChoicesUiEvent -> {
                                val windowHandle = latestWindowHandle ?: activeWindowHandle ?: return@forEach
                                if (event.windowHandle != windowHandle) {
                                    return@forEach
                                }
                                openOrFocusShowChoicesWindow(event)
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun refreshSourcePortOptions() {
        val options = deriveAttackSourcePortOptions(latestShellState, mode)
        val currentSelection = selectedSourcePortNumber()
        val preferredPort = pendingPreferredPort
        val nextSelection = reconcileAttackSourcePortSelection(
            options = options,
            currentSelection = currentSelection,
            preferredPort = preferredPort,
            defaultRedirectPort = latestShellState?.economy?.defaultRedirectPort
                ?.takeIf { mode == RewriteAttackPaneMode.REDIRECT },
        )
        pendingPreferredPort = preferredPort?.takeUnless { it == nextSelection }
        sourcePortModel.removeAllElements()
        options.forEach(sourcePortModel::addElement)
        setSelectedSourcePort(nextSelection)
    }

    private fun selectedSourcePortNumber(): Int? {
        return (sourcePortCombo.selectedItem as? RewriteAttackSourcePortOption)?.portNumber
    }

    private fun setSelectedSourcePort(portNumber: Int?) {
        val match = (0 until sourcePortModel.size)
            .map(sourcePortModel::getElementAt)
            .firstOrNull { it.portNumber == portNumber }
        sourcePortCombo.selectedItem = match
    }

    private fun submitPrimaryAction() {
        if (activeProgramId != null) {
            submitCancel()
        } else {
            submitAttack()
        }
    }

    private fun submitAttack() {
        val targetIp = targetIpInput.valueOrNull()
        if (targetIp == null) {
            showError("Target IP is required.")
            return
        }
        val sourcePort = selectedSourcePortNumber()
        if (sourcePort == null) {
            showError("A valid source port is required.")
            return
        }
        val targetPort = (targetPortSpinner.value as Number).toInt()
        val windowHandle = controller.allocateAttackWindowHandle()
        disposeFollowupWindows()
        requestInFlight = true
        latestWindowHandle = windowHandle
        activeWindowHandle = windowHandle
        clearError()
        transcriptArea.text = ""
        appendTranscript("${mode.primaryActionLabel} commencing on $targetIp at port $targetPort...")
        statusLabel.text = "${mode.primaryActionLabel} request in progress..."
        renderState()
        val scripts = if (mode == RewriteAttackPaneMode.ATTACK) {
            buildLegacyAttackScripts(bankingSelection)
        } else {
            emptyList()
        }
        val extraInfo = if (mode == RewriteAttackPaneMode.ATTACK) {
            buildLegacyAttackExtraInfo(
                bankingSelection = bankingSelection,
                panePettyCashTarget = (pettyCashTargetSpinner.value as Number).toDouble(),
            )
        } else {
            emptyList()
        }
        val secondaryPorts = if (mode == RewriteAttackPaneMode.ATTACK) {
            selectedSecondaryPorts
        } else {
            emptyList()
        }
        windowScope.launch {
            val result = controller.requestAttack(
                targetIp = targetIp,
                targetPort = targetPort,
                sourcePort = sourcePort,
                secondaryPorts = secondaryPorts,
                scripts = scripts,
                extraInfo = extraInfo,
                windowHandle = windowHandle,
            )
            SwingUtilities.invokeLater {
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                handleAttackStartResult(result)
            }
        }
    }

    private fun submitCancel() {
        val sourcePort = activeSourcePort ?: selectedSourcePortNumber()
        if (sourcePort == null) {
            showError("A valid source port is required.")
            return
        }
        requestInFlight = true
        clearError()
        statusLabel.text = "Cancelling ${mode.primaryActionLabel.lowercase()}..."
        renderState()
        windowScope.launch {
            val result = controller.requestCancelAttack(sourcePort)
            SwingUtilities.invokeLater {
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                handleAttackCancelResult(result)
            }
        }
    }

    private fun handleAttackStartResult(
        result: RewriteGameCommandResult<ClientAttackStartResponse>,
    ) {
        requestInFlight = false
        when (result) {
            is RewriteGameCommandResult.Success -> {
                val response = result.value
                val session = response.session
                if (response.accepted && session != null) {
                    activeProgramId = session.programId
                    activeWindowHandle = session.windowHandle.takeIf { it > 0 } ?: activeWindowHandle
                    latestWindowHandle = activeWindowHandle
                    activeSourcePort = response.sourcePort
                    clearError()
                    statusLabel.text = response.message.ifBlank { "${mode.primaryActionLabel} started." }
                } else {
                    latestWindowHandle = null
                    activeWindowHandle = null
                    activeSourcePort = null
                    activeProgramId = null
                    showError(response.message.ifBlank { "The rewrite game request failed." })
                }
            }

            is RewriteGameCommandResult.Failure -> {
                latestWindowHandle = null
                activeWindowHandle = null
                activeSourcePort = null
                activeProgramId = null
                showError(result.message)
            }
        }
        renderState()
    }

    private fun handleAttackCancelResult(
        result: RewriteGameCommandResult<ClientAttackCancelResponse>,
    ) {
        requestInFlight = false
        when (result) {
            is RewriteGameCommandResult.Success -> {
                val response = result.value
                if (response.accepted) {
                    clearActiveSession()
                    clearError()
                    statusLabel.text = response.message.ifBlank { "${mode.primaryActionLabel} cancelled." }
                    appendTranscript(response.message.ifBlank { "${mode.primaryActionLabel} cancelled." })
                } else {
                    showError(response.message.ifBlank { "The rewrite game request failed." })
                }
            }

            is RewriteGameCommandResult.Failure -> {
                showError(result.message)
            }
        }
        renderState()
    }

    private fun handleProgramUpdates(
        updates: Map<String, ClientProgramUpdate>,
    ) {
        val programId = activeProgramId ?: return
        val update = updates[programId] ?: return
        val progressMessage = update.progress.message.ifBlank {
            when (update.status) {
                ClientProgramLifecycleStatus.RUNNING -> "${mode.primaryActionLabel} running."
                ClientProgramLifecycleStatus.COMPLETED -> "${mode.primaryActionLabel} completed."
                ClientProgramLifecycleStatus.CANCELLED -> "${mode.primaryActionLabel} cancelled."
                ClientProgramLifecycleStatus.FAILED -> "${mode.primaryActionLabel} failed."
            }
        }
        statusLabel.text = progressMessage
        if (update.status != ClientProgramLifecycleStatus.RUNNING) {
            clearActiveSession()
            renderState()
        }
    }

    private fun openAttackChooser() {
        if (mode != RewriteAttackPaneMode.ATTACK) {
            return
        }
        val existing = chooserWindow
        if (existing != null && existing.isDisplayable && !existing.isClosed) {
            onFocusAuxiliaryWindow(existing)
            return
        }
        val chooser = RewriteAttackFileChooserWindow(
            controller = controller,
            initialMaliciousIp = bankingSelection?.maliciousIp ?: controller.currentAuthenticatedPlayerIp().orEmpty(),
            initialPettyCashTarget = bankingSelection?.pettyCashTarget ?: 0.0,
            onSelected = { selection ->
                bankingSelection = selection
                scriptField.text = selection.fileName
                clearError()
                statusLabel.text = "Loaded ${selection.fileName}."
                chooserWindow = null
                renderState()
            },
        ).apply {
            name = "rewrite-attack-file-chooser-window"
            addInternalFrameListener(object : InternalFrameAdapter() {
                override fun internalFrameClosed(event: InternalFrameEvent) {
                    if (chooserWindow === this@apply) {
                        chooserWindow = null
                    }
                }
            })
        }
        chooserWindow = chooser
        onOpenAuxiliaryWindow(chooser)
    }

    private fun showSecondaryPortsDialog() {
        if (mode != RewriteAttackPaneMode.ATTACK) {
            return
        }
        RewriteSecondaryPortsDialog(
            owner = SwingUtilities.getWindowAncestor(this),
            selectedPorts = selectedSecondaryPorts,
        ) { ports ->
            selectedSecondaryPorts = ports
            if (ports.isNotEmpty()) {
                targetPortSpinner.value = ports.first()
            }
            renderState()
        }
    }

    private fun openOrFocusShowChoicesWindow(
        event: ClientShowChoicesUiEvent,
    ) {
        val existing = choicesWindow
        if (existing != null && existing.isDisplayable && !existing.isClosed) {
            existing.updateChoice(event)
            onFocusAuxiliaryWindow(existing)
            return
        }
        val window = RewriteShowChoicesWindow(
            controller = controller,
            initialChoice = event,
            onPerformAction = ::handleShowChoicesAction,
            onClosed = { closedWindow ->
                if (choicesWindow === closedWindow) {
                    choicesWindow = null
                }
            },
        )
        choicesWindow = window
        onOpenAuxiliaryWindow(window)
    }

    private fun handleShowChoicesAction(
        event: ClientShowChoicesUiEvent,
        action: RewriteShowChoicesActionKind,
    ) {
        when (action) {
            RewriteShowChoicesActionKind.OPEN_PUBLIC_FTP,
            RewriteShowChoicesActionKind.OPEN_STORE_FTP -> openRemoteFollowupBrowser(event, action)
            RewriteShowChoicesActionKind.CHANGE_DAILY_PAY_TARGET -> openChangeDailyPayDialog(event)
        }
    }

    private fun openRemoteFollowupBrowser(
        event: ClientShowChoicesUiEvent,
        action: RewriteShowChoicesActionKind,
    ) {
        val key = followupWindowKey(event.windowHandle, action)
        val existing = followupWindowsByKey[key]
        if (existing != null && existing.isDisplayable && !existing.isClosed) {
            onFocusAuxiliaryWindow(existing)
            return
        }
        val window = RewriteRemoteDirectoryBrowserWindow(
            controller = controller,
            actionKind = action,
            targetIp = event.targetIp,
            targetPort = event.targetPort,
            onClosed = { closedWindow ->
                followupWindowsByKey.remove(key, closedWindow)
            },
        )
        followupWindowsByKey[key] = window
        onOpenAuxiliaryWindow(window)
    }

    private fun openChangeDailyPayDialog(
        event: ClientShowChoicesUiEvent,
    ) {
        val key = followupWindowKey(event.windowHandle, RewriteShowChoicesActionKind.CHANGE_DAILY_PAY_TARGET)
        val existing = dailyPayDialogsByKey[key]
        if (existing != null && existing.isDisplayable) {
            existing.toFront()
            existing.requestFocus()
            return
        }
        val dialog = RewriteChangeDailyPayDialog(
            owner = SwingUtilities.getWindowAncestor(this),
            controller = controller,
            targetIp = event.targetIp,
            targetPort = event.targetPort,
            attackPort = event.windowHandle,
            initialRevenueTargetIp = controller.currentAuthenticatedPlayerIp().orEmpty(),
            onSucceeded = { message ->
                clearError()
                statusLabel.text = message
                appendTranscript(message)
            },
            onClosed = { closedDialog ->
                dailyPayDialogsByKey.remove(key, closedDialog)
            },
        )
        dailyPayDialogsByKey[key] = dialog
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this))
        dialog.isVisible = true
        dialog.toFront()
        dialog.requestFocus()
    }

    private fun disposeFollowupWindows() {
        chooserWindow?.let { window ->
            chooserWindow = null
            runCatching { window.dispose() }
        }
        choicesWindow?.let { window ->
            choicesWindow = null
            runCatching { window.dispose() }
        }
        val windows = followupWindowsByKey.values.toList()
        followupWindowsByKey.clear()
        windows.forEach { window ->
            runCatching { window.dispose() }
        }
        val dialogs = dailyPayDialogsByKey.values.toList()
        dailyPayDialogsByKey.clear()
        dialogs.forEach { dialog ->
            runCatching { dialog.dispose() }
        }
    }

    private fun followupWindowKey(
        windowHandle: Int,
        action: RewriteShowChoicesActionKind,
    ): String = "$windowHandle|${action.name}"

    private fun clearActiveSession() {
        activeProgramId = null
        activeSourcePort = null
        activeWindowHandle = null
    }

    private fun appendTranscript(message: String) {
        if (message.isBlank()) {
            return
        }
        val rendered = if (message.endsWith('\n')) message else "$message\n"
        transcriptArea.append(rendered)
        transcriptArea.caretPosition = transcriptArea.document.length
    }

    private fun clearError() {
        errorLabel.text = " "
    }

    private fun showError(message: String) {
        errorLabel.text = message
        statusLabel.text = " "
    }

    private fun renderState() {
        val busy = requestInFlight || activeProgramId != null
        val hasSourcePort = selectedSourcePortNumber() != null
        val hasPorts = sourcePortModel.size > 0
        sourceIpField.text = controller.currentAuthenticatedPlayerIp().orEmpty()
        targetIpInput.isEnabled = !busy
        targetPortSpinner.isEnabled = !busy
        sourcePortCombo.isEnabled = !busy && hasPorts
        primaryButton.text = if (activeProgramId != null) "Cancel" else mode.primaryActionLabel
        primaryButton.isEnabled = when {
            requestInFlight -> false
            activeProgramId != null -> true
            else -> hasSourcePort
        }
        advancedPanel.isVisible = mode == RewriteAttackPaneMode.ATTACK
        scriptField.isEnabled = !busy
        browseButton.isEnabled = !busy
        secondaryPortsButton.isEnabled = !busy
        pettyCashTargetSpinner.isEnabled = !busy
        secondaryPortsLabel.text = if (selectedSecondaryPorts.isEmpty()) {
            "None"
        } else {
            selectedSecondaryPorts.joinToString(", ")
        }
        if (!requestInFlight && activeProgramId == null && sourcePortModel.size == 0) {
            statusLabel.text = "No ${mode.primaryActionLabel.lowercase()} ports available."
        } else if (!requestInFlight && activeProgramId == null && statusLabel.text.isBlank()) {
            statusLabel.text = "Ready."
        }
        isClosable = activeProgramId == null
        defaultCloseOperation = if (activeProgramId == null) DISPOSE_ON_CLOSE else DO_NOTHING_ON_CLOSE
    }

    private fun noticeKey(notice: RewriteDecodedGameUiNotice): String {
        val eventId = notice.metadata.eventId ?: "no-event-id"
        return buildString {
            append(eventId)
            append('|')
            append(notice.metadata.receivedAtEpochMillis ?: 0L)
            append('|')
            append(notice.metadata.eventType.orEmpty())
        }
    }
}

private class RewriteAttackSourcePortRenderer : JLabel(), ListCellRenderer<RewriteAttackSourcePortOption> {
    override fun getListCellRendererComponent(
        list: javax.swing.JList<out RewriteAttackSourcePortOption>?,
        value: RewriteAttackSourcePortOption?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        text = value?.label.orEmpty()
        isOpaque = true
        background = if (isSelected) list?.selectionBackground else list?.background
        foreground = if (isSelected) list?.selectionForeground else list?.foreground
        return this
    }
}

private class RewriteAttackFileChooserWindow(
    controller: RewriteRootController,
    initialMaliciousIp: String,
    initialPettyCashTarget: Double,
    private val onSelected: (RewriteAttackBankingScriptSelection) -> Unit,
) : JInternalFrame("Choose File", true, true, true, true) {
    private val browserController = RewriteLocalDirectoryBrowserController(
        rootController = controller,
        directoryFilter = ::allowAttackChooserDirectory,
        fileFilter = ::allowAttackChooserFile,
    )
    private val browserPanel = RewriteLocalDirectoryBrowserPanel(
        browserController = browserController,
        primaryActionLabel = "Open",
        primaryActionName = "rewrite-attack-chooser-open-button",
        onPrimaryAction = { openSelection() },
        canRunPrimaryAction = { state ->
            state.entries.any { it.path == state.selectedPath }
        },
    )
    private val maliciousIpInput = RewriteSegmentedIpInput().apply {
        name = "rewrite-attack-chooser-malicious-ip"
        setIp(initialMaliciousIp)
    }
    private val pettyCashTargetSpinner = JSpinner(
        SpinnerNumberModel(initialPettyCashTarget, 0.0, MAX_ATTACK_PETTY_CASH_TARGET, 1.0),
    ).apply {
        name = "rewrite-attack-chooser-petty-cash-spinner"
    }

    init {
        name = "rewrite-attack-file-chooser-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(580, 500)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(browserPanel, BorderLayout.CENTER)
            add(
                JPanel(GridBagLayout()).apply {
                    isOpaque = false
                    add(
                        JLabel("Malicious IP:"),
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 0
                            anchor = GridBagConstraints.WEST
                            insets = Insets(0, 0, 6, 8)
                        },
                    )
                    add(
                        maliciousIpInput,
                        GridBagConstraints().apply {
                            gridx = 1
                            gridy = 0
                            weightx = 1.0
                            fill = GridBagConstraints.HORIZONTAL
                            insets = Insets(0, 0, 6, 0)
                        },
                    )
                    add(
                        JLabel("Petty Cash Target:"),
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 1
                            anchor = GridBagConstraints.WEST
                            insets = Insets(0, 0, 6, 8)
                        },
                    )
                    add(
                        pettyCashTargetSpinner,
                        GridBagConstraints().apply {
                            gridx = 1
                            gridy = 1
                            anchor = GridBagConstraints.WEST
                            fill = GridBagConstraints.HORIZONTAL
                            insets = Insets(0, 0, 6, 0)
                        },
                    )
                    add(
                        JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                            isOpaque = false
                            add(
                                JButton("Choose").apply {
                                    name = "rewrite-attack-chooser-choose-button"
                                    addActionListener { submitSelection() }
                                },
                            )
                            add(
                                JButton("Cancel").apply {
                                    name = "rewrite-attack-chooser-cancel-button"
                                    addActionListener { dispose() }
                                },
                            )
                        },
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 2
                            gridwidth = 2
                            anchor = GridBagConstraints.EAST
                        },
                    )
                },
                BorderLayout.SOUTH,
            )
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                browserPanel.close()
                browserController.close()
            }
        })
        browserController.activate("/")
    }

    private fun openSelection() {
        val entry: RewriteLocalDirectoryBrowserEntry = browserController.selectedEntry() ?: return
        if (entry.type == RewriteLocalDirectoryEntryType.DIRECTORY) {
            browserController.openSelectedDirectory()
            return
        }
        submitSelection()
    }

    private fun submitSelection() {
        val selected = browserController.chooseSelectedFile()
        if (selected == null) {
            browserController.showInlineError("Choose a banking binary.")
            return
        }
        val maliciousIp = maliciousIpInput.valueOrNull()
        if (maliciousIp == null) {
            browserController.showInlineError("Malicious IP is required.")
            return
        }
        browserController.showInlineError(null)
        onSelected(
            RewriteAttackBankingScriptSelection(
                directoryPath = selected.displayedPath,
                fileName = selected.file.name,
                maliciousIp = maliciousIp,
                pettyCashTarget = (pettyCashTargetSpinner.value as Number).toDouble(),
            ),
        )
        dispose()
    }
}

private class RewriteSecondaryPortsDialog(
    owner: Window?,
    selectedPorts: List<Int>,
    onSubmit: (List<Int>) -> Unit,
) : JDialog(owner, "Set Selected Ports", Dialog.ModalityType.APPLICATION_MODAL) {
    init {
        name = "rewrite-attack-secondary-ports-dialog"
        val checkboxes = (0..MAX_ATTACK_TARGET_PORT).map { port ->
            javax.swing.JCheckBox("Port $port", port in selectedPorts).apply {
                name = "rewrite-attack-secondary-port-$port"
            }
        }
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JLabel("Select Secondary Ports:"), BorderLayout.NORTH)
            add(
                JPanel(GridBagLayout()).apply {
                    isOpaque = false
                    checkboxes.forEachIndexed { index, checkbox ->
                        add(
                            checkbox,
                            GridBagConstraints().apply {
                                gridx = index / 8
                                gridy = index % 8
                                anchor = GridBagConstraints.WEST
                                insets = Insets(2, 4, 2, 4)
                            },
                        )
                    }
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(
                        JButton("Okay").apply {
                            name = "rewrite-attack-secondary-ports-ok"
                            addActionListener {
                                val ports = checkboxes
                                    .filter { it.isSelected }
                                    .map { checkbox -> checkbox.text.removePrefix("Port ").toInt() }
                                    .sorted()
                                onSubmit(ports)
                                dispose()
                            }
                        },
                    )
                    add(
                        JButton("Cancel").apply {
                            name = "rewrite-attack-secondary-ports-cancel"
                            addActionListener { dispose() }
                        },
                    )
                },
                BorderLayout.SOUTH,
            )
        }
        isResizable = false
        pack()
        setLocationRelativeTo(owner)
        isVisible = true
    }
}

private fun String.toLegacyAttackPortNote(): String {
    return if (length > 10) {
        take(7) + "..."
    } else {
        this
    }
}
