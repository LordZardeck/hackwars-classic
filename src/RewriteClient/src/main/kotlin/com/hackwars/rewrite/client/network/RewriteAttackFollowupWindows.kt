package com.hackwars.rewrite.client.network

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.clientmodel.SelectorStore
import com.hackwars.rewrite.protocol.ClientChangeDailyPayOutcome
import com.hackwars.rewrite.protocol.ClientChangeDailyPayResponse
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientFinalizeCancelledResponse
import com.hackwars.rewrite.protocol.ClientSecondaryDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientShowChoicesType
import com.hackwars.rewrite.protocol.ClientShowChoicesUiEvent
import com.hackwars.rewrite.protocol.ClientStoredFile
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
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal const val PUBLIC_FTP_ROOT = "/Public"
internal const val STORE_FTP_ROOT = "/Store"

internal enum class RewriteShowChoicesActionKind(
    val label: String,
    val browserTitle: String? = null,
    val entryRootPath: String? = null,
    val windowNameSuffix: String,
) {
    OPEN_PUBLIC_FTP(
        label = "Open Public FTP",
        browserTitle = "Public FTP",
        entryRootPath = PUBLIC_FTP_ROOT,
        windowNameSuffix = "public_ftp",
    ),
    OPEN_STORE_FTP(
        label = "Open Store FTP",
        browserTitle = "Store FTP",
        entryRootPath = STORE_FTP_ROOT,
        windowNameSuffix = "store_ftp",
    ),
    CHANGE_DAILY_PAY_TARGET(
        label = "Change Daily Pay Target",
        windowNameSuffix = "change_daily_pay",
    ),
    ;

    override fun toString(): String = label
}

internal fun supportedShowChoicesActions(
    choiceType: ClientShowChoicesType,
): List<RewriteShowChoicesActionKind> = when (choiceType) {
    ClientShowChoicesType.FTP -> listOf(RewriteShowChoicesActionKind.OPEN_PUBLIC_FTP)
    ClientShowChoicesType.SHIPPING -> listOf(RewriteShowChoicesActionKind.OPEN_STORE_FTP)
    ClientShowChoicesType.HTTP -> listOf(RewriteShowChoicesActionKind.CHANGE_DAILY_PAY_TARGET)
    ClientShowChoicesType.BANK,
    ClientShowChoicesType.ATTACK -> emptyList()
}

internal fun clampRemoteFollowupPath(
    rawPath: String?,
    entryRootPath: String,
): String {
    val normalized = normalizeRemotePath(rawPath ?: entryRootPath)
    return if (normalized == entryRootPath || normalized.startsWith("$entryRootPath/")) {
        normalized
    } else {
        entryRootPath
    }
}

internal fun isSuccessfulChangeDailyPayOutcome(outcome: ClientChangeDailyPayOutcome): Boolean {
    return outcome == ClientChangeDailyPayOutcome.SUCCESS ||
        outcome == ClientChangeDailyPayOutcome.ALREADY_CONTROLLED
}

private fun normalizeRemotePath(rawPath: String?): String {
    val candidate = rawPath?.trim().takeUnless { it.isNullOrBlank() } ?: "/"
    val normalized = candidate.replace('\\', '/')
    val parts = normalized.split('/')
    val resolved = mutableListOf<String>()
    parts.forEach { part ->
        when {
            part.isBlank() || part == "." -> Unit
            part == ".." -> if (resolved.isNotEmpty()) {
                resolved.removeLast()
            }
            else -> resolved += part
        }
    }
    return if (resolved.isEmpty()) "/" else resolved.joinToString(prefix = "/", separator = "/")
}

private fun remoteParentDirectoryPath(path: String): String {
    val normalized = normalizeRemotePath(path)
    if (normalized == "/") {
        return "/"
    }
    val parent = normalized.substringBeforeLast('/', "")
    return if (parent.isBlank()) "/" else parent
}

private fun isWithinRemoteRoot(
    path: String,
    entryRootPath: String,
): Boolean = path == entryRootPath || path.startsWith("$entryRootPath/")

internal class RewriteShowChoicesWindow(
    private val controller: RewriteRootController,
    initialChoice: ClientShowChoicesUiEvent,
    private val onPerformAction: (ClientShowChoicesUiEvent, RewriteShowChoicesActionKind) -> Unit,
    private val onClosed: (RewriteShowChoicesWindow) -> Unit,
) : JInternalFrame("Choices - ${initialChoice.targetIp}:${initialChoice.targetPort}", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actionModel = DefaultComboBoxModel<RewriteShowChoicesActionKind>()
    private val actionCombo = JComboBox(actionModel).apply {
        name = "rewrite-show-choices-action-combo"
    }
    private val infoLabel = JLabel(" ").apply {
        name = "rewrite-show-choices-info"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-show-choices-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val goButton = JButton("Go").apply {
        name = "rewrite-show-choices-go-button"
        addActionListener { launchSelectedAction() }
    }
    private val cancelButton = JButton("Cancel").apply {
        name = "rewrite-show-choices-cancel-button"
        addActionListener { submitCancel() }
    }

    private var currentChoice: ClientShowChoicesUiEvent = initialChoice
    private var requestInFlight: Boolean = false

    init {
        name = "rewrite-show-choices-window"
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        size = Dimension(380, 190)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(
                JPanel(GridBagLayout()).apply {
                    isOpaque = false
                    add(
                        JLabel("Action:"),
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 0
                            anchor = GridBagConstraints.WEST
                            insets = Insets(0, 0, 8, 8)
                        },
                    )
                    add(
                        actionCombo,
                        GridBagConstraints().apply {
                            gridx = 1
                            gridy = 0
                            weightx = 1.0
                            fill = GridBagConstraints.HORIZONTAL
                            insets = Insets(0, 0, 8, 0)
                        },
                    )
                    add(
                        infoLabel,
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 1
                            gridwidth = 2
                            anchor = GridBagConstraints.WEST
                            fill = GridBagConstraints.HORIZONTAL
                            weightx = 1.0
                        },
                    )
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(BorderLayout(0, 6)).apply {
                    isOpaque = false
                    add(
                        JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                            isOpaque = false
                            add(goButton)
                            add(cancelButton)
                        },
                        BorderLayout.NORTH,
                    )
                    add(errorLabel, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH,
            )
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosing(event: InternalFrameEvent) {
                submitCancel()
            }

            override fun internalFrameClosed(event: InternalFrameEvent) {
                windowScope.cancel()
                onClosed(this@RewriteShowChoicesWindow)
            }
        })
        updateChoice(initialChoice)
    }

    fun updateChoice(choice: ClientShowChoicesUiEvent) {
        currentChoice = choice
        title = "Choices - ${choice.targetIp}:${choice.targetPort}"
        actionModel.removeAllElements()
        supportedShowChoicesActions(choice.choiceType).forEach(actionModel::addElement)
        renderState()
    }

    private fun launchSelectedAction() {
        val action = actionCombo.selectedItem as? RewriteShowChoicesActionKind ?: return
        onPerformAction(currentChoice, action)
        dispose()
    }

    private fun submitCancel() {
        if (requestInFlight) {
            return
        }
        requestInFlight = true
        errorLabel.text = " "
        infoLabel.text = "Finalizing cancelled attack..."
        renderState()
        windowScope.launch {
            val result = controller.requestFinalizeCancelled(
                targetIp = currentChoice.targetIp,
                targetPort = currentChoice.targetPort,
            )
            SwingUtilities.invokeLater {
                if (!isDisplayable || isClosed) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> handleFinalizeCancelled(result.value)
                    is RewriteGameCommandResult.Failure -> {
                        requestInFlight = false
                        errorLabel.text = result.message
                        infoLabel.text = " "
                        renderState()
                    }
                }
            }
        }
    }

    private fun handleFinalizeCancelled(response: ClientFinalizeCancelledResponse) {
        requestInFlight = false
        if (response.accepted) {
            dispose()
            return
        }
        errorLabel.text = response.message.ifBlank { "Unable to finalize the cancelled attack." }
        infoLabel.text = " "
        renderState()
    }

    private fun renderState() {
        val hasActions = actionModel.size > 0
        actionCombo.isEnabled = !requestInFlight && hasActions
        goButton.isEnabled = !requestInFlight && hasActions && actionCombo.selectedItem != null
        cancelButton.isEnabled = !requestInFlight
        if (!requestInFlight && !hasActions) {
            infoLabel.text = "No rewrite follow-up actions are available for this target yet."
        } else if (!requestInFlight && infoLabel.text.isBlank()) {
            infoLabel.text = "Select a rewrite follow-up action."
        }
    }
}

internal class RewriteRemoteDirectoryBrowserWindow(
    controller: RewriteRootController,
    actionKind: RewriteShowChoicesActionKind,
    targetIp: String,
    targetPort: Int,
    private val onClosed: (RewriteRemoteDirectoryBrowserWindow) -> Unit,
) : JInternalFrame("${actionKind.browserTitle} - $targetIp:$targetPort", true, true, true, true) {
    private val browserController = RewriteRemoteDirectoryBrowserController(
        rootController = controller,
        targetIp = targetIp,
        targetPort = targetPort,
        entryRootPath = requireNotNull(actionKind.entryRootPath),
    )
    private val browserPanel = RewriteRemoteDirectoryBrowserPanel(
        browserController = browserController,
        primaryActionLabel = "Open",
        primaryActionName = "rewrite-remote-files-open-button",
        onPrimaryAction = { browserController.openSelectedDirectory() },
        canRunPrimaryAction = { state ->
            state.entries.firstOrNull { it.path == state.selectedPath }?.isDirectory == true
        },
    )

    init {
        name = "rewrite-remote-directory-browser-window-${actionKind.windowNameSuffix}"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(560, 420)
        contentPane = browserPanel
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                browserPanel.close()
                browserController.close()
                onClosed(this@RewriteRemoteDirectoryBrowserWindow)
            }
        })
        browserController.activate()
    }
}

internal class RewriteChangeDailyPayDialog(
    owner: Window?,
    private val controller: RewriteRootController,
    private val targetIp: String,
    private val targetPort: Int,
    private val attackPort: Int,
    initialRevenueTargetIp: String,
    private val onSucceeded: (String) -> Unit,
    private val onClosed: (RewriteChangeDailyPayDialog) -> Unit,
) : JDialog(owner, "Change Daily Pay Target", Dialog.ModalityType.MODELESS) {
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val revenueTargetField = JTextField(initialRevenueTargetIp, 18).apply {
        name = "rewrite-change-daily-pay-ip-field"
    }
    private val statusLabel = JLabel("Ready.").apply {
        name = "rewrite-change-daily-pay-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-change-daily-pay-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val submitButton = JButton("Change").apply {
        name = "rewrite-change-daily-pay-submit-button"
        addActionListener { submit() }
    }
    private val cancelButton = JButton("Cancel").apply {
        name = "rewrite-change-daily-pay-cancel-button"
        addActionListener { dispose() }
    }

    private var requestInFlight: Boolean = false

    init {
        name = "rewrite-change-daily-pay-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(
                JPanel(GridBagLayout()).apply {
                    isOpaque = false
                    add(
                        JLabel("Revenue Target IP:"),
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 0
                            anchor = GridBagConstraints.WEST
                            insets = Insets(0, 0, 8, 8)
                        },
                    )
                    add(
                        revenueTargetField,
                        GridBagConstraints().apply {
                            gridx = 1
                            gridy = 0
                            weightx = 1.0
                            fill = GridBagConstraints.HORIZONTAL
                        },
                    )
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(BorderLayout(0, 6)).apply {
                    isOpaque = false
                    add(statusLabel, BorderLayout.NORTH)
                    add(
                        JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                            isOpaque = false
                            add(submitButton)
                            add(cancelButton)
                        },
                        BorderLayout.CENTER,
                    )
                    add(errorLabel, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH,
            )
        }
        pack()
        minimumSize = size
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) {
                dialogScope.cancel()
                onClosed(this@RewriteChangeDailyPayDialog)
            }
        })
        renderState()
    }

    private fun submit() {
        if (requestInFlight) {
            return
        }
        val revenueTargetIp = revenueTargetField.text.trim().takeIf { it.isNotBlank() }
        if (revenueTargetIp == null) {
            errorLabel.text = "Revenue target IP is required."
            statusLabel.text = " "
            return
        }
        requestInFlight = true
        errorLabel.text = " "
        statusLabel.text = "Changing daily pay target..."
        renderState()
        dialogScope.launch {
            val result = controller.requestChangeDailyPay(
                targetIp = targetIp,
                targetPort = targetPort,
                revenueTargetIp = revenueTargetIp,
                attackPort = attackPort,
            )
            SwingUtilities.invokeLater {
                if (!isDisplayable) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> handleChangeDailyPay(result.value)
                    is RewriteGameCommandResult.Failure -> {
                        requestInFlight = false
                        statusLabel.text = " "
                        errorLabel.text = result.message
                        renderState()
                    }
                }
            }
        }
    }

    private fun handleChangeDailyPay(response: ClientChangeDailyPayResponse) {
        requestInFlight = false
        if (response.accepted && isSuccessfulChangeDailyPayOutcome(response.outcome)) {
            onSucceeded(response.message.ifBlank { "Daily pay target updated." })
            dispose()
            return
        }
        statusLabel.text = " "
        errorLabel.text = response.message.ifBlank { "Unable to change the daily pay target." }
        renderState()
    }

    private fun renderState() {
        revenueTargetField.isEnabled = !requestInFlight
        submitButton.isEnabled = !requestInFlight
        cancelButton.isEnabled = !requestInFlight
    }
}

internal enum class RewriteRemoteDirectoryEntryType {
    DIRECTORY,
    FILE,
}

internal data class RewriteRemoteDirectoryBrowserEntry(
    val path: String,
    val name: String,
    val description: String,
    val type: RewriteRemoteDirectoryEntryType,
    val directory: ClientDirectoryEntry? = null,
    val file: ClientStoredFile? = null,
) {
    val isDirectory: Boolean
        get() = type == RewriteRemoteDirectoryEntryType.DIRECTORY

    override fun toString(): String = name
}

internal data class RewriteRemoteDirectoryBrowserState(
    val displayedPath: String,
    val listing: ClientSecondaryDirectoryListingResponse? = null,
    val entries: List<RewriteRemoteDirectoryBrowserEntry> = emptyList(),
    val selectedPath: String? = null,
    val requestInFlight: Boolean = false,
    val inlineError: String? = null,
)

internal class RewriteRemoteDirectoryBrowserController(
    private val rootController: RewriteRootController,
    targetIp: String,
    targetPort: Int,
    private val entryRootPath: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AutoCloseable {
    private val store = SelectorStore(
        RewriteRemoteDirectoryBrowserState(displayedPath = entryRootPath),
    )
    private var disposed: Boolean = false
    private var queuedRequest: String? = null
    private var targetIp: String = targetIp
    private var targetPort: Int = targetPort
    private var targetRevision: Long = 0L

    fun snapshot(): RewriteRemoteDirectoryBrowserState = store.snapshot()

    fun selector(): Flow<RewriteRemoteDirectoryBrowserState> = store.selector { it }

    fun activate() {
        requestDirectory(entryRootPath)
    }

    fun navigateHome() {
        requestDirectory(entryRootPath)
    }

    fun refresh(path: String? = null) {
        requestDirectory(path ?: currentDisplayedPath())
    }

    fun rebindTarget(
        targetIp: String,
        targetPort: Int,
        initialPath: String? = entryRootPath,
    ) {
        if (disposed) {
            return
        }
        val normalizedPath = clampRemoteFollowupPath(initialPath, entryRootPath)
        val targetChanged = this.targetIp != targetIp || this.targetPort != targetPort
        this.targetIp = targetIp
        this.targetPort = targetPort
        if (targetChanged) {
            targetRevision += 1
        }
        val currentState = snapshot()
        store.update { state ->
            state.copy(
                displayedPath = normalizedPath,
                listing = if (targetChanged) null else state.listing,
                entries = if (targetChanged) emptyList() else state.entries,
                selectedPath = if (targetChanged) null else state.selectedPath,
                inlineError = null,
            )
        }
        if (currentState.requestInFlight) {
            queuedRequest = normalizedPath
        } else {
            requestDirectory(normalizedPath)
        }
    }

    fun navigateUp() {
        if (!canNavigateUp()) {
            return
        }
        requestDirectory(
            clampRemoteFollowupPath(
                remoteParentDirectoryPath(currentDisplayedPath()),
                entryRootPath,
            ),
        )
    }

    fun canNavigateUp(): Boolean = currentDisplayedPath() != entryRootPath

    fun updateSelection(selectedPath: String?) {
        store.update { state -> state.copy(selectedPath = selectedPath) }
    }

    fun selectedEntry(): RewriteRemoteDirectoryBrowserEntry? {
        val state = snapshot()
        val selectedPath = state.selectedPath ?: return null
        return state.entries.firstOrNull { it.path == selectedPath }
    }

    fun openSelectedDirectory() {
        val entry = selectedEntry() ?: return
        if (!entry.isDirectory) {
            return
        }
        requestDirectory(entry.path)
    }

    override fun close() {
        disposed = true
        queuedRequest = null
        scope.cancel()
    }

    private fun currentDisplayedPath(): String = snapshot().listing?.path ?: snapshot().displayedPath

    private fun requestDirectory(path: String?) {
        if (disposed) {
            return
        }
        val normalizedPath = clampRemoteFollowupPath(path, entryRootPath)
        val state = snapshot()
        if (state.requestInFlight) {
            queuedRequest = normalizedPath
            return
        }
        store.update { current ->
            current.copy(
                requestInFlight = true,
                inlineError = null,
            )
        }
        scope.launch {
            val requestRevision = targetRevision
            val requestTargetIp = targetIp
            val requestTargetPort = targetPort
            val result = rootController.requestSecondaryDirectory(
                path = normalizedPath,
                targetIp = requestTargetIp,
                portNumber = requestTargetPort,
            )
            SwingUtilities.invokeLater {
                if (disposed) {
                    return@invokeLater
                }
                if (requestRevision != targetRevision) {
                    store.update { current ->
                        current.copy(requestInFlight = false)
                    }
                    val nextQueued = queuedRequest
                    queuedRequest = null
                    if (nextQueued != null) {
                        requestDirectory(nextQueued)
                    }
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> applyListing(result.value)
                    is RewriteGameCommandResult.Failure -> {
                        store.update { current ->
                            current.copy(
                                requestInFlight = false,
                                inlineError = result.message,
                            )
                        }
                    }
                }
                val nextQueued = queuedRequest
                queuedRequest = null
                if (nextQueued != null) {
                    requestDirectory(nextQueued)
                }
            }
        }
    }

    private fun applyListing(response: ClientSecondaryDirectoryListingResponse) {
        val previousSelection = snapshot().selectedPath
        val displayedPath = clampRemoteFollowupPath(response.path, entryRootPath)
        val entries = buildEntries(response)
        val nextSelection = entries.firstOrNull { it.path == previousSelection }?.path
        store.update { current ->
            current.copy(
                displayedPath = displayedPath,
                listing = response.copy(path = displayedPath),
                entries = entries,
                selectedPath = nextSelection,
                requestInFlight = false,
                inlineError = null,
            )
        }
    }

    private fun buildEntries(response: ClientSecondaryDirectoryListingResponse): List<RewriteRemoteDirectoryBrowserEntry> {
        val directories = response.directories
            .filter { directory -> isWithinRemoteRoot(directory.path, entryRootPath) }
            .map { directory ->
                RewriteRemoteDirectoryBrowserEntry(
                    path = clampRemoteFollowupPath(directory.path, entryRootPath),
                    name = directory.name,
                    description = directory.description,
                    type = RewriteRemoteDirectoryEntryType.DIRECTORY,
                    directory = directory,
                )
            }
        val files = response.files
            .filter { file -> isWithinRemoteRoot(file.path, entryRootPath) }
            .map { file ->
                RewriteRemoteDirectoryBrowserEntry(
                    path = clampRemoteFollowupPath(file.path, entryRootPath),
                    name = file.name,
                    description = file.description,
                    type = RewriteRemoteDirectoryEntryType.FILE,
                    file = file,
                )
            }
        return directories + files
    }
}

internal class RewriteRemoteDirectoryBrowserPanel(
    private val browserController: RewriteRemoteDirectoryBrowserController,
    private val primaryActionLabel: String,
    private val primaryActionName: String,
    private val onPrimaryAction: () -> Unit,
    private val canRunPrimaryAction: (RewriteRemoteDirectoryBrowserState) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : JPanel(BorderLayout()) {
    private val pathValueLabel = JLabel("/").apply {
        name = "rewrite-remote-files-path-label"
        font = font.deriveFont(Font.BOLD)
    }
    private val statusLabel = JLabel("Loading remote directory...").apply {
        name = "rewrite-remote-files-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-remote-files-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val listModel = javax.swing.DefaultListModel<RewriteRemoteDirectoryBrowserEntry>()
    val entryList = JList(listModel).apply {
        name = "rewrite-remote-files-entry-list"
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = RewriteRemoteDirectoryEntryRenderer()
    }
    val upButton = JButton("Up").apply {
        name = "rewrite-remote-files-up-button"
        addActionListener { browserController.navigateUp() }
    }
    val homeButton = JButton("Home").apply {
        name = "rewrite-remote-files-home-button"
        addActionListener { browserController.navigateHome() }
    }
    val primaryButton = JButton(primaryActionLabel).apply {
        name = primaryActionName
        addActionListener { onPrimaryAction() }
    }

    init {
        name = "rewrite-remote-files-browser-panel"
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        val header = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(JLabel("Path:"), BorderLayout.WEST)
            add(pathValueLabel, BorderLayout.CENTER)
        }
        val actionBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(upButton)
            add(homeButton)
            add(primaryButton)
        }
        val footer = JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(statusLabel, BorderLayout.NORTH)
            add(errorLabel, BorderLayout.SOUTH)
        }
        add(header, BorderLayout.NORTH)
        add(JScrollPane(entryList).apply {
            name = "rewrite-remote-files-scroll-pane"
            preferredSize = Dimension(440, 260)
        }, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout(0, 8)).apply {
                isOpaque = false
                add(actionBar, BorderLayout.NORTH)
                add(footer, BorderLayout.SOUTH)
            },
            BorderLayout.SOUTH,
        )

        entryList.addListSelectionListener {
            val selected = entryList.selectedValue
            browserController.updateSelection(selected?.path)
            render(browserController.snapshot())
        }
        entryList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(event: java.awt.event.MouseEvent) {
                if (event.clickCount >= 2 && entryList.selectedIndex >= 0) {
                    onPrimaryAction()
                }
            }
        })

        scope.launch {
            browserController.selector().collect { state ->
                SwingUtilities.invokeLater {
                    render(state)
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun render(state: RewriteRemoteDirectoryBrowserState) {
        pathValueLabel.text = state.listing?.path ?: state.displayedPath
        renderEntries(state)
        val selectedIndex = state.entries.indexOfFirst { it.path == state.selectedPath }
        if (selectedIndex >= 0) {
            entryList.selectedIndex = selectedIndex
            entryList.ensureIndexIsVisible(selectedIndex)
        } else {
            entryList.clearSelection()
        }
        val hasListing = state.listing != null
        statusLabel.text = when {
            state.requestInFlight && !hasListing -> "Loading remote directory..."
            hasListing && state.entries.isEmpty() -> "No files or folders in this directory."
            hasListing -> "Read-only remote directory."
            else -> " "
        }
        errorLabel.text = state.inlineError ?: " "
        primaryButton.isEnabled = !state.requestInFlight && canRunPrimaryAction(state)
        entryList.isEnabled = !state.requestInFlight
        homeButton.isEnabled = !state.requestInFlight
        upButton.isEnabled = !state.requestInFlight && browserController.canNavigateUp()
    }

    private fun renderEntries(state: RewriteRemoteDirectoryBrowserState) {
        val existingPaths = (0 until listModel.size()).map { index -> listModel.get(index).path }
        val nextPaths = state.entries.map { it.path }
        if (existingPaths == nextPaths) {
            for (index in state.entries.indices) {
                listModel.set(index, state.entries[index])
            }
            return
        }
        listModel.clear()
        state.entries.forEach { entry ->
            listModel.addElement(entry)
        }
    }
}

internal class RewriteRemoteDirectoryEntryRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
        val entry = value as? RewriteRemoteDirectoryBrowserEntry
        label.text = when (entry?.type) {
            RewriteRemoteDirectoryEntryType.DIRECTORY -> "[DIR] ${entry.name}"
            RewriteRemoteDirectoryEntryType.FILE -> "[FILE] ${entry.name}"
            null -> ""
        }
        return label
    }
}
