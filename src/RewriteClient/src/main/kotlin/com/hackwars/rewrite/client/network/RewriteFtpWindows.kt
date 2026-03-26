package com.hackwars.rewrite.client.network

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserController
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserPanel
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientFtpTransferResponse
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientSellFileResponse
import com.hackwars.rewrite.protocol.ClientSetFtpPasswordResponse
import com.hackwars.rewrite.protocol.ClientStoredFile
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.JTextField
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

private const val MAX_REMOTE_FTP_PORT = 31

internal data class RewriteFtpPortOption(
    val portNumber: Int,
    val label: String,
    val defaultPort: Boolean,
) {
    override fun toString(): String = label
}

internal fun deriveFtpPortOptions(snapshot: ClientGameSnapshot?): List<RewriteFtpPortOption> {
    return snapshot?.ports.orEmpty()
        .filter(ClientPortState::enabled)
        .filterNot(ClientPortState::dummy)
        .filter { it.installedApplication?.kind == "FTP" }
        .sortedBy(ClientPortState::number)
        .map { port ->
            RewriteFtpPortOption(
                portNumber = port.number,
                label = "${port.number}: ${port.note.toLegacyFtpPortNote()}",
                defaultPort = port.defaultPort,
            )
        }
}

internal fun reconcileShopFtpPortSelection(
    options: List<RewriteFtpPortOption>,
    currentSelection: Int?,
): Int? {
    val availablePorts = options.map(RewriteFtpPortOption::portNumber).toSet()
    return when {
        currentSelection in availablePorts -> currentSelection
        else -> options.firstOrNull(RewriteFtpPortOption::defaultPort)?.portNumber
            ?: options.firstOrNull()?.portNumber
    }
}

internal fun defaultPublicFtpTargetPort(
    options: List<RewriteFtpPortOption>,
): Int {
    return options.firstOrNull(RewriteFtpPortOption::defaultPort)?.portNumber
        ?: options.firstOrNull()?.portNumber
        ?: 0
}

internal fun allowShopFtpSourceDirectory(directory: ClientDirectoryEntry): Boolean {
    return directory.path !in setOf(STORE_FTP_ROOT, PUBLIC_FTP_ROOT)
}

internal class RewriteShopFtpWindow(
    private val controller: RewriteRootController,
) : JInternalFrame("Shop FTP", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val localBrowserController = RewriteLocalDirectoryBrowserController(
        rootController = controller,
        directoryFilter = ::allowShopFtpSourceDirectory,
    )
    private val localBrowserPanel = RewriteLocalDirectoryBrowserPanel(
        browserController = localBrowserController,
        primaryActionLabel = "Open",
        primaryActionName = "rewrite-shop-ftp-open-local-button",
        onPrimaryAction = { openSelectedLocalDirectory() },
        canRunPrimaryAction = { state ->
            state.entries.firstOrNull { it.path == state.selectedPath }?.isDirectory == true
        },
    )
    private val portModel = DefaultComboBoxModel<RewriteFtpPortOption>()
    private val portCombo = JComboBox(portModel).apply {
        name = "rewrite-shop-ftp-port-combo"
        addActionListener {
            if (!updatingPortSelection) {
                bindStoreBrowser(reloadRoot = true)
                renderState()
            }
        }
    }
    private val sellButton = JButton("Sell").apply {
        name = "rewrite-shop-ftp-sell-button"
        addActionListener { openSellDialog() }
    }
    private val refreshButton = JButton("Refresh Store").apply {
        name = "rewrite-shop-ftp-refresh-store-button"
        addActionListener { bindStoreBrowser(reloadRoot = true) }
    }
    private val statusLabel = JLabel("Waiting for local FTP port data...").apply {
        name = "rewrite-shop-ftp-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-shop-ftp-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val storePanelHost = JPanel(BorderLayout()).apply {
        border = BorderFactory.createTitledBorder("Store")
    }

    private var updatingPortSelection: Boolean = false
    private var storeBrowserController: RewriteRemoteDirectoryBrowserController? = null
    private var storeBrowserPanel: RewriteRemoteDirectoryBrowserPanel? = null
    private var currentStorePort: Int? = null
    private var sellDialog: RewriteSellFileDialog? = null

    init {
        name = "rewrite-shell-window-shop_ftp"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(920, 500)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(JLabel("Store Port:"))
                    add(portCombo)
                    add(sellButton)
                    add(refreshButton)
                },
                BorderLayout.NORTH,
            )
            add(
                JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    JPanel(BorderLayout()).apply {
                        border = BorderFactory.createTitledBorder("Local Files")
                        add(localBrowserPanel, BorderLayout.CENTER)
                    },
                    storePanelHost,
                ).apply {
                    resizeWeight = 0.5
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(BorderLayout(0, 4)).apply {
                    isOpaque = false
                    add(statusLabel, BorderLayout.NORTH)
                    add(errorLabel, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH,
            )
        }
        showStorePlaceholder("Store listing unavailable until a local FTP port is active.")
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                sellDialog?.dispose()
                sellDialog = null
                localBrowserPanel.close()
                localBrowserController.close()
                closeStoreBrowser()
                windowScope.cancel()
            }
        })
        observeLocalBrowserState()
        observeShellState()
        localBrowserController.activate()
    }

    private fun observeLocalBrowserState() {
        windowScope.launch {
            localBrowserController.selector().collect {
                SwingUtilities.invokeLater {
                    if (!isDisplayable || isClosed) {
                        return@invokeLater
                    }
                    renderState()
                }
            }
        }
    }

    private fun observeShellState() {
        windowScope.launch {
            controller.gameShellStateSelector().collect { snapshot ->
                SwingUtilities.invokeLater {
                    if (!isDisplayable || isClosed) {
                        return@invokeLater
                    }
                    updatePortOptions(snapshot)
                }
            }
        }
    }

    private fun updatePortOptions(snapshot: ClientGameSnapshot?) {
        val options = deriveFtpPortOptions(snapshot)
        val currentSelection = selectedPortNumber()
        val nextSelection = reconcileShopFtpPortSelection(options, currentSelection)
        updatingPortSelection = true
        portModel.removeAllElements()
        options.forEach(portModel::addElement)
        if (nextSelection != null) {
            portModel.selectedItem = options.firstOrNull { it.portNumber == nextSelection }
        }
        updatingPortSelection = false

        if (nextSelection == null) {
            currentStorePort = null
            closeStoreBrowser()
            showStorePlaceholder("No eligible local FTP ports are available.")
        } else if (nextSelection != currentStorePort || storeBrowserController == null) {
            bindStoreBrowser(reloadRoot = false)
        }
        renderState()
    }

    private fun openSelectedLocalDirectory() {
        val entry = localBrowserController.selectedEntry() ?: return
        if (entry.isDirectory) {
            localBrowserController.openSelectedDirectory()
        }
    }

    private fun openSellDialog() {
        val selection = localBrowserController.chooseSelectedFile() ?: return
        val existingDialog = sellDialog
        if (existingDialog != null && existingDialog.isShowing) {
            existingDialog.toFront()
            existingDialog.requestFocus()
            return
        }
        val owner = SwingUtilities.getWindowAncestor(this)
        val dialog = RewriteSellFileDialog(
            owner = owner,
            controller = controller,
            fileSelection = selection,
            onSucceeded = { response ->
                handleSellSucceeded(response)
            },
            onClosed = { closedDialog ->
                if (sellDialog === closedDialog) {
                    sellDialog = null
                }
            },
        )
        sellDialog = dialog
        dialog.setLocationRelativeTo(owner)
        dialog.isVisible = true
    }

    private fun handleSellSucceeded(response: ClientSellFileResponse) {
        errorLabel.text = " "
        statusLabel.text = "Sold ${response.file.name} to /Store."
        bindStoreBrowser(reloadRoot = true)
    }

    private fun bindStoreBrowser(reloadRoot: Boolean) {
        val selectedPort = selectedPortNumber()
        val playerIp = controller.currentAuthenticatedPlayerIp()
        if (selectedPort == null || playerIp.isNullOrBlank()) {
            currentStorePort = null
            closeStoreBrowser()
            showStorePlaceholder("Store listing unavailable until a local FTP port is active.")
            renderState()
            return
        }
        if (storeBrowserController == null) {
            val remoteController = RewriteRemoteDirectoryBrowserController(
                rootController = controller,
                targetIp = playerIp,
                targetPort = selectedPort,
                entryRootPath = STORE_FTP_ROOT,
            )
            val remotePanel = RewriteRemoteDirectoryBrowserPanel(
                browserController = remoteController,
                primaryActionLabel = "Open",
                primaryActionName = "rewrite-shop-ftp-open-store-button",
                onPrimaryAction = { remoteController.openSelectedDirectory() },
                canRunPrimaryAction = { state ->
                    state.entries.firstOrNull { it.path == state.selectedPath }?.isDirectory == true
                },
                secondaryActionLabel = "Get",
                secondaryActionName = "rewrite-shop-ftp-get-button",
                onSecondaryAction = { openStoreGetDialog() },
                canRunSecondaryAction = { state ->
                    state.entries.firstOrNull { it.path == state.selectedPath }?.file != null
                },
            )
            storeBrowserController = remoteController
            storeBrowserPanel = remotePanel
            currentStorePort = selectedPort
            storePanelHost.removeAll()
            storePanelHost.add(remotePanel, BorderLayout.CENTER)
            storePanelHost.revalidate()
            storePanelHost.repaint()
            remoteController.activate()
        } else if (currentStorePort != selectedPort) {
            currentStorePort = selectedPort
            storeBrowserController?.rebindTarget(
                targetIp = playerIp,
                targetPort = selectedPort,
                initialPath = STORE_FTP_ROOT,
            )
        } else if (reloadRoot) {
            storeBrowserController?.navigateHome()
        }
        statusLabel.text = "Viewing /Store on port $selectedPort."
        errorLabel.text = " "
        renderState()
    }

    private fun closeStoreBrowser() {
        storeBrowserPanel?.close()
        storeBrowserController?.close()
        storeBrowserPanel = null
        storeBrowserController = null
        storePanelHost.removeAll()
        storePanelHost.revalidate()
        storePanelHost.repaint()
    }

    private fun showStorePlaceholder(message: String) {
        if (storeBrowserController != null) {
            return
        }
        storePanelHost.removeAll()
        storePanelHost.add(
            JPanel(BorderLayout()).apply {
                add(JLabel(message).apply {
                    name = "rewrite-shop-ftp-store-placeholder"
                    horizontalAlignment = JLabel.CENTER
                }, BorderLayout.CENTER)
            },
            BorderLayout.CENTER,
        )
        storePanelHost.revalidate()
        storePanelHost.repaint()
    }

    private fun selectedPortNumber(): Int? {
        return (portCombo.selectedItem as? RewriteFtpPortOption)?.portNumber
    }

    private fun openStoreGetDialog() {
        val selectedFile = storeBrowserController?.selectedEntry()?.file ?: return
        val displayedPath = storeBrowserController?.snapshot()?.listing?.path ?: STORE_FTP_ROOT
        openTransferDialog(
            title = "Get File",
            dialogName = "rewrite-shop-ftp-get-dialog",
            buttonName = "rewrite-shop-ftp-get-confirm-button",
            statusName = "rewrite-shop-ftp-get-status",
            errorName = "rewrite-shop-ftp-get-error",
            file = selectedFile,
            onSubmit = { quantity ->
            val selectedPort = selectedPortNumber()
                ?: return@openTransferDialog RewriteGameCommandResult.Failure("No eligible local FTP ports are available.")
            val playerIp = controller.currentAuthenticatedPlayerIp()
                ?: return@openTransferDialog RewriteGameCommandResult.Failure("Not connected to a rewrite game session.")
            controller.requestGetFile(
                targetIp = playerIp,
                portNumber = selectedPort,
                fileName = selectedFile.name,
                localPath = localBrowserController.snapshot().listing?.path ?: "/",
                remotePath = displayedPath,
                quantity = quantity,
            )
            },
            onSucceeded = { response ->
            handleTransferSucceeded(
                response = response,
                successMessage = "Retrieved ${response.file.name} from /Store.",
                refreshRemote = { storeBrowserController?.refresh() },
            )
            },
        )
    }

    private fun handleTransferSucceeded(
        response: ClientFtpTransferResponse,
        successMessage: String,
        refreshRemote: () -> Unit,
    ) {
        errorLabel.text = " "
        statusLabel.text = successMessage
        localBrowserController.activate(localBrowserController.snapshot().listing?.path ?: "/")
        refreshRemote()
    }

    private fun renderState() {
        val hasEligiblePort = selectedPortNumber() != null
        val selectedFile = localBrowserController.selectedEntry()?.file
        portCombo.isEnabled = portModel.size > 0
        sellButton.isEnabled = hasEligiblePort && selectedFile != null
        refreshButton.isEnabled = hasEligiblePort
        if (!hasEligiblePort) {
            statusLabel.text = "No eligible local FTP ports are available."
        } else if (statusLabel.text.isBlank()) {
            statusLabel.text = "Viewing /Store."
        }
    }
}

internal class RewritePublicFtpWindow(
    private val controller: RewriteRootController,
) : JInternalFrame("Public FTP", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val localBrowserController = RewriteLocalDirectoryBrowserController(
        rootController = controller,
    )
    private val localBrowserPanel = RewriteLocalDirectoryBrowserPanel(
        browserController = localBrowserController,
        primaryActionLabel = "Open",
        primaryActionName = "rewrite-public-ftp-open-local-button",
        onPrimaryAction = { openSelectedLocalDirectory() },
        canRunPrimaryAction = { state ->
            state.entries.firstOrNull { it.path == state.selectedPath }?.isDirectory == true
        },
        secondaryActionLabel = "Put",
        secondaryActionName = "rewrite-public-ftp-put-button",
        onSecondaryAction = { openPutDialog() },
        canRunSecondaryAction = { state ->
            state.entries.firstOrNull { it.path == state.selectedPath }?.file != null && browserController != null
        },
    )
    private val targetIpField = JTextField(controller.currentAuthenticatedPlayerIp().orEmpty(), 16).apply {
        name = "rewrite-public-ftp-target-ip-field"
    }
    private val passwordField = JPasswordField("", 12).apply {
        name = "rewrite-public-ftp-password-field"
    }
    private val targetPortSpinner = JSpinner(
        SpinnerNumberModel(0, 0, MAX_REMOTE_FTP_PORT, 1),
    ).apply {
        name = "rewrite-public-ftp-target-port-spinner"
    }
    private val connectButton = JButton("Connect").apply {
        name = "rewrite-public-ftp-connect-button"
        addActionListener { connect() }
    }
    private val statusLabel = JLabel("Enter a target IP and port to connect.").apply {
        name = "rewrite-public-ftp-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-public-ftp-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val browserHost = JPanel(BorderLayout()).apply {
        border = BorderFactory.createTitledBorder("Remote Files")
    }

    private var browserController: RewriteRemoteDirectoryBrowserController? = null
    private var browserPanel: RewriteRemoteDirectoryBrowserPanel? = null
    private var browserObserverStarted: Boolean = false
    private var currentTargetIp: String? = null
    private var currentTargetPort: Int? = null
    private var portValueInitialized: Boolean = false
    private var suppressPortChangeEvent: Boolean = false
    private var stickyStatusMessage: String? = null

    init {
        name = "rewrite-shell-window-public_ftp"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(920, 500)
        targetPortSpinner.addChangeListener {
            if (!suppressPortChangeEvent) {
                portValueInitialized = true
            }
        }
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(JLabel("Target IP:"))
                    add(targetIpField)
                    add(JLabel("Port:"))
                    add(targetPortSpinner)
                    add(JLabel("Password:"))
                    add(passwordField)
                    add(connectButton)
                },
                BorderLayout.NORTH,
            )
            add(
                JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    JPanel(BorderLayout()).apply {
                        border = BorderFactory.createTitledBorder("Local Files")
                        add(localBrowserPanel, BorderLayout.CENTER)
                    },
                    browserHost,
                ).apply {
                    resizeWeight = 0.5
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(BorderLayout(0, 4)).apply {
                    isOpaque = false
                    add(statusLabel, BorderLayout.NORTH)
                    add(errorLabel, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH,
            )
        }
        showBrowserPlaceholder("Connect to view /Public.")
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                localBrowserPanel.close()
                localBrowserController.close()
                browserPanel?.close()
                browserController?.close()
                windowScope.cancel()
            }
        })
        observeLocalBrowserState()
        observeShellState()
        localBrowserController.activate()
    }

    private fun observeLocalBrowserState() {
        windowScope.launch {
            localBrowserController.selector().collect {
                SwingUtilities.invokeLater {
                    if (!isDisplayable || isClosed) {
                        return@invokeLater
                    }
                    renderState()
                }
            }
        }
    }

    private fun observeShellState() {
        windowScope.launch {
            controller.gameShellStateSelector().collect { snapshot ->
                SwingUtilities.invokeLater {
                    if (!isDisplayable || isClosed) {
                        return@invokeLater
                    }
                    if (!portValueInitialized) {
                        suppressPortChangeEvent = true
                        targetPortSpinner.value = defaultPublicFtpTargetPort(deriveFtpPortOptions(snapshot))
                        suppressPortChangeEvent = false
                    }
                    if (targetIpField.text.isBlank()) {
                        targetIpField.text = controller.currentAuthenticatedPlayerIp().orEmpty()
                    }
                    renderState()
                }
            }
        }
    }

    private fun openSelectedLocalDirectory() {
        val entry = localBrowserController.selectedEntry() ?: return
        if (entry.isDirectory) {
            localBrowserController.openSelectedDirectory()
        }
    }

    private fun connect() {
        val targetIp = targetIpField.text.trim()
        if (targetIp.isBlank()) {
            errorLabel.text = "Target IP is required."
            statusLabel.text = "Enter a target IP and port to connect."
            return
        }
        val targetPort = targetPortSpinner.value as Int
        portValueInitialized = true
        stickyStatusMessage = null
        errorLabel.text = " "
        statusLabel.text = "Connecting..."
        if (browserController == null) {
            val remoteController = RewriteRemoteDirectoryBrowserController(
                rootController = controller,
                targetIp = targetIp,
                targetPort = targetPort,
                entryRootPath = PUBLIC_FTP_ROOT,
            )
            val remotePanel = RewriteRemoteDirectoryBrowserPanel(
                browserController = remoteController,
                primaryActionLabel = "Open",
                primaryActionName = "rewrite-public-ftp-open-button",
                onPrimaryAction = { remoteController.openSelectedDirectory() },
                canRunPrimaryAction = { state ->
                    state.entries.firstOrNull { it.path == state.selectedPath }?.isDirectory == true
                },
                secondaryActionLabel = "Get",
                secondaryActionName = "rewrite-public-ftp-get-button",
                onSecondaryAction = { openGetDialog() },
                canRunSecondaryAction = { state ->
                    state.entries.firstOrNull { it.path == state.selectedPath }?.file != null
                },
            )
            browserController = remoteController
            browserPanel = remotePanel
            browserHost.removeAll()
            browserHost.add(remotePanel, BorderLayout.CENTER)
            browserHost.revalidate()
            browserHost.repaint()
            observeBrowserState(remoteController)
            remoteController.activate()
        } else {
            browserController?.rebindTarget(
                targetIp = targetIp,
                targetPort = targetPort,
                initialPath = PUBLIC_FTP_ROOT,
            )
        }
        currentTargetIp = targetIp
        currentTargetPort = targetPort
        renderState()
    }

    private fun observeBrowserState(remoteController: RewriteRemoteDirectoryBrowserController) {
        if (browserObserverStarted) {
            return
        }
        browserObserverStarted = true
        windowScope.launch {
            remoteController.selector().collect { state ->
                SwingUtilities.invokeLater {
                    if (!isDisplayable || isClosed) {
                        return@invokeLater
                    }
                    errorLabel.text = state.inlineError ?: " "
                    if (state.inlineError != null) {
                        stickyStatusMessage = null
                    }
                    statusLabel.text = when {
                        stickyStatusMessage != null -> stickyStatusMessage
                        state.requestInFlight -> "Connecting..."
                        state.listing != null && currentTargetIp != null && currentTargetPort != null ->
                            "Connected to ${currentTargetIp}:${currentTargetPort}."
                        else -> "Enter a target IP and port to connect."
                    }
                    renderState()
                }
            }
        }
    }

    private fun openPutDialog() {
        val targetIp = currentTargetIp ?: return
        val targetPort = currentTargetPort ?: return
        val selection = localBrowserController.chooseSelectedFile() ?: return
        openTransferDialog(
            title = "Put File",
            dialogName = "rewrite-public-ftp-put-dialog",
            buttonName = "rewrite-public-ftp-put-confirm-button",
            statusName = "rewrite-public-ftp-put-status",
            errorName = "rewrite-public-ftp-put-error",
            file = selection.file,
            onSubmit = { quantity ->
                controller.requestPutFile(
                    targetIp = targetIp,
                    portNumber = targetPort,
                    fileName = selection.file.name,
                    localPath = selection.displayedPath,
                    remotePath = currentRemotePath(),
                    password = currentPassword(),
                    quantity = quantity,
                )
            },
            onSucceeded = { response ->
            handleTransferSucceeded(
                response = response,
                successMessage = "Uploaded ${response.file.name} to ${response.targetStateId}.",
            )
            },
        )
    }

    private fun openGetDialog() {
        val targetIp = currentTargetIp ?: return
        val targetPort = currentTargetPort ?: return
        val selectedFile = browserController?.selectedEntry()?.file ?: return
        openTransferDialog(
            title = "Get File",
            dialogName = "rewrite-public-ftp-get-dialog",
            buttonName = "rewrite-public-ftp-get-confirm-button",
            statusName = "rewrite-public-ftp-get-status",
            errorName = "rewrite-public-ftp-get-error",
            file = selectedFile,
            onSubmit = { quantity ->
                controller.requestGetFile(
                    targetIp = targetIp,
                    portNumber = targetPort,
                    fileName = selectedFile.name,
                    localPath = localBrowserController.snapshot().listing?.path ?: "/",
                    remotePath = currentRemotePath(),
                    password = currentPassword(),
                    quantity = quantity,
                )
            },
            onSucceeded = { response ->
            handleTransferSucceeded(
                response = response,
                successMessage = "Downloaded ${response.file.name} from ${response.targetStateId}.",
            )
            },
        )
    }

    private fun handleTransferSucceeded(
        response: ClientFtpTransferResponse,
        successMessage: String,
    ) {
        stickyStatusMessage = successMessage
        errorLabel.text = " "
        statusLabel.text = successMessage
        localBrowserController.activate(localBrowserController.snapshot().listing?.path ?: "/")
        browserController?.refresh()
    }

    private fun currentRemotePath(): String {
        return browserController?.snapshot()?.listing?.path ?: PUBLIC_FTP_ROOT
    }

    private fun currentPassword(): String {
        return String(passwordField.password)
    }

    private fun showBrowserPlaceholder(message: String) {
        browserHost.removeAll()
        browserHost.add(
            JPanel(BorderLayout()).apply {
                add(JLabel(message).apply {
                    name = "rewrite-public-ftp-placeholder"
                    horizontalAlignment = JLabel.CENTER
                }, BorderLayout.CENTER)
            },
            BorderLayout.CENTER,
        )
        browserHost.revalidate()
        browserHost.repaint()
    }

    private fun renderState() {
        val connected = browserController != null
        connectButton.isEnabled = true
        passwordField.isEnabled = true
        localBrowserPanel.secondaryButton?.isEnabled = connected &&
            localBrowserController.selectedEntry()?.file != null
    }
}

private fun openTransferDialog(
    title: String,
    dialogName: String,
    buttonName: String,
    statusName: String,
    errorName: String,
    file: ClientStoredFile,
    onSubmit: suspend (Int) -> RewriteGameCommandResult<ClientFtpTransferResponse>,
    onSucceeded: (ClientFtpTransferResponse) -> Unit,
) {
    val dialog = RewriteFtpTransferDialog(
        title = title,
        dialogName = dialogName,
        buttonName = buttonName,
        statusName = statusName,
        errorName = errorName,
        file = file,
        onSubmit = onSubmit,
        onSucceeded = onSucceeded,
    )
    dialog.isVisible = true
    dialog.toFront()
    dialog.requestFocus()
}

internal class RewriteSetPublicFtpPasswordWindow(
    private val controller: RewriteRootController,
) : JInternalFrame("Set Public FTP Password", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val passwordField = JPasswordField("", 18).apply {
        name = "rewrite-public-ftp-password-window-field"
    }
    private val statusLabel = JLabel("Enter a password or leave it blank to clear it.").apply {
        name = "rewrite-public-ftp-password-window-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-public-ftp-password-window-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val setButton = JButton("Set Password").apply {
        name = "rewrite-public-ftp-password-window-submit-button"
        addActionListener { submit() }
    }
    private val cancelButton = JButton("Cancel").apply {
        name = "rewrite-public-ftp-password-window-cancel-button"
        addActionListener { dispose() }
    }

    private var requestInFlight: Boolean = false

    init {
        name = "rewrite-shell-window-set_public_ftp_password"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(420, 160)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(
                JPanel(GridBagLayout()).apply {
                    isOpaque = false
                    addLabeledRow("Password:", passwordField, 0)
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
                            add(setButton)
                            add(cancelButton)
                        },
                        BorderLayout.CENTER,
                    )
                    add(errorLabel, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH,
            )
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                windowScope.cancel()
            }
        })
    }

    private fun submit() {
        if (requestInFlight) {
            return
        }
        requestInFlight = true
        statusLabel.text = "Saving password..."
        errorLabel.text = " "
        renderState()
        windowScope.launch {
            val result = controller.requestSetFtpPassword(
                String(passwordField.password),
            )
            SwingUtilities.invokeLater {
                if (!isDisplayable || isClosed) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> handleSuccess(result.value)
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

    private fun handleSuccess(response: ClientSetFtpPasswordResponse) {
        requestInFlight = false
        statusLabel.text = if (response.passwordSet) {
            "Public FTP password saved."
        } else {
            "Public FTP password cleared."
        }
        dispose()
    }

    private fun renderState() {
        passwordField.isEnabled = !requestInFlight
        setButton.isEnabled = !requestInFlight
        cancelButton.isEnabled = !requestInFlight
    }
}

private class RewriteFtpTransferDialog(
    title: String,
    dialogName: String,
    buttonName: String,
    statusName: String,
    errorName: String,
    private val file: ClientStoredFile,
    private val onSubmit: suspend (Int) -> RewriteGameCommandResult<ClientFtpTransferResponse>,
    private val onSucceeded: (ClientFtpTransferResponse) -> Unit,
) : JDialog(null as Window?, title, Dialog.ModalityType.MODELESS) {
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fileField = JTextField(file.name, 20).apply {
        name = "$dialogName-file-field"
        isEditable = false
    }
    private val quantitySpinner = JSpinner(
        SpinnerNumberModel(1, 1, file.quantity.coerceAtLeast(1), 1),
    ).apply {
        name = "$dialogName-quantity-spinner"
    }
    private val statusLabel = JLabel("Ready.").apply {
        name = statusName
    }
    private val errorLabel = JLabel(" ").apply {
        name = errorName
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val submitButton = JButton(title.substringBefore(' ')).apply {
        name = buttonName
        addActionListener { submit() }
    }
    private val cancelButton = JButton("Cancel").apply {
        name = "$dialogName-cancel-button"
        addActionListener { dispose() }
    }

    private var requestInFlight: Boolean = false

    init {
        name = dialogName
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(
                JPanel(GridBagLayout()).apply {
                    isOpaque = false
                    addLabeledRow("File:", fileField, 0)
                    addLabeledRow("Quantity:", quantitySpinner, 1)
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
            }
        })
        renderState()
    }

    private fun submit() {
        if (requestInFlight) {
            return
        }
        requestInFlight = true
        statusLabel.text = "Submitting transfer..."
        errorLabel.text = " "
        renderState()
        dialogScope.launch {
            val result = onSubmit(quantitySpinner.value as Int)
            SwingUtilities.invokeLater {
                if (!isDisplayable) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        onSucceeded(result.value)
                        dispose()
                    }
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

    private fun renderState() {
        quantitySpinner.isEnabled = !requestInFlight
        submitButton.isEnabled = !requestInFlight
        cancelButton.isEnabled = !requestInFlight
    }
}

private class RewriteSellFileDialog(
    owner: Window?,
    private val controller: RewriteRootController,
    private val fileSelection: com.hackwars.rewrite.client.files.RewriteLocalFileSelection,
    private val onSucceeded: (ClientSellFileResponse) -> Unit,
    private val onClosed: (RewriteSellFileDialog) -> Unit,
) : JDialog(owner, "Sell File", Dialog.ModalityType.MODELESS) {
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fileField = JTextField(fileSelection.file.name, 20).apply {
        name = "rewrite-shop-ftp-sell-file-field"
        isEditable = false
    }
    private val quantitySpinner = JSpinner(
        SpinnerNumberModel(1, 1, fileSelection.file.quantity.coerceAtLeast(1), 1),
    ).apply {
        name = "rewrite-shop-ftp-sell-quantity-spinner"
    }
    private val compileCostField = JTextField("", 12).apply {
        name = "rewrite-shop-ftp-sell-compile-cost-field"
    }
    private val statusLabel = JLabel("Ready.").apply {
        name = "rewrite-shop-ftp-sell-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-shop-ftp-sell-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val sellButton = JButton("Sell").apply {
        name = "rewrite-shop-ftp-sell-confirm-button"
        addActionListener { submit() }
    }
    private val cancelButton = JButton("Cancel").apply {
        name = "rewrite-shop-ftp-sell-cancel-button"
        addActionListener { dispose() }
    }

    private var requestInFlight: Boolean = false

    init {
        name = "rewrite-shop-ftp-sell-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(
                JPanel(GridBagLayout()).apply {
                    isOpaque = false
                    addLabeledRow("File:", fileField, 0)
                    addLabeledRow("Quantity:", quantitySpinner, 1)
                    addLabeledRow("Compile Cost:", compileCostField, 2)
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
                            add(sellButton)
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
                onClosed(this@RewriteSellFileDialog)
            }
        })
        renderState()
    }

    private fun submit() {
        if (requestInFlight) {
            return
        }
        val compileCost = compileCostField.text.trim().let { raw ->
            when {
                raw.isBlank() -> null
                else -> raw.toDoubleOrNull()
            }
        }
        if (compileCostField.text.trim().isNotBlank() && (compileCost == null || compileCost <= 0.0)) {
            errorLabel.text = "Compile cost must be a positive number or blank."
            statusLabel.text = " "
            return
        }
        requestInFlight = true
        errorLabel.text = " "
        statusLabel.text = "Selling file..."
        renderState()
        dialogScope.launch {
            val result = controller.sellFile(
                path = parentDirectoryPath(fileSelection.file.path),
                fileName = fileSelection.file.name,
                compileCost = compileCost,
                quantity = quantitySpinner.value as Int,
            )
            SwingUtilities.invokeLater {
                if (!isDisplayable) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        onSucceeded(result.value)
                        dispose()
                    }
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

    private fun renderState() {
        quantitySpinner.isEnabled = !requestInFlight
        compileCostField.isEnabled = !requestInFlight
        sellButton.isEnabled = !requestInFlight
        cancelButton.isEnabled = !requestInFlight
    }
}

private fun JPanel.addLabeledRow(
    label: String,
    component: java.awt.Component,
    row: Int,
) {
    add(
        JLabel(label),
        GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 8, 8)
        },
    )
    add(
        component,
        GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(0, 0, 8, 0)
        },
    )
}

private fun String.toLegacyFtpPortNote(): String {
    return if (length > 10) {
        take(7) + "..."
    } else {
        this
    }
}

private fun parentDirectoryPath(path: String): String {
    if (path == "/") {
        return "/"
    }
    val parent = path.substringBeforeLast('/', "")
    return if (parent.isBlank()) "/" else parent
}
