package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.files.RewriteLocalFileChooserWindow
import com.hackwars.rewrite.client.mvc.RewriteControllerBase
import com.hackwars.rewrite.client.mvc.RewriteFrameBinding
import javax.swing.JInternalFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal interface RewritePortManagementDriver {
    fun applyPreferredPort(preferredPort: Int?)
}

internal fun createPortManagementWindowBinding(
    controller: RewriteRootController,
    preferredPort: Int? = null,
    onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
): RewriteFrameBinding {
    val view = RewritePortManagementWindow()
    val binding = RewritePortManagementController(
        controller = controller,
        view = view,
        onOpenAuxiliaryWindow = onOpenAuxiliaryWindow,
        onFocusAuxiliaryWindow = onFocusAuxiliaryWindow,
    )
    view.bindController(binding)
    if (preferredPort != null) {
        binding.applyPreferredPort(preferredPort)
    }
    return RewriteFrameBinding(
        frame = view,
        controller = binding,
    )
}

internal class RewritePortManagementController(
    private val controller: RewriteRootController,
    private val view: RewritePortManagementWindow,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
) : RewriteControllerBase(),
    RewritePortManagementDriver {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var requestInFlight: Boolean = false
    private var pendingPreferredPort: Int? = null
    private var applicationChooserWindow: JInternalFrame? = null
    private var firewallChooserWindow: JInternalFrame? = null
    private var statusText: String = "Waiting for port data..."
    private var errorText: String = " "
    private var suppressSelectionEvents: Boolean = false

    private val selectionListener = javax.swing.event.ListSelectionListener {
        if (!suppressSelectionEvents) {
            renderState()
        }
    }
    private val healListener = java.awt.event.ActionListener { submitHeal() }
    private val installProgramListener = java.awt.event.ActionListener { openProgramChooser() }
    private val installFirewallListener = java.awt.event.ActionListener { openFirewallChooser() }

    init {
        bindListeners()
        onClose {
            view.table.selectionModel.removeListSelectionListener(selectionListener)
            view.healButton.removeActionListener(healListener)
            view.installProgramButton.removeActionListener(installProgramListener)
            view.installFirewallButton.removeActionListener(installFirewallListener)
            applicationChooserWindow?.dispose()
            firewallChooserWindow?.dispose()
            windowScope.cancel()
        }
        view.addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                close()
            }
        })
        observeShellState()
        renderState()
    }

    override fun applyPreferredPort(preferredPort: Int?) {
        pendingPreferredPort = preferredPort
        applySelection(preferredPort)
        renderState()
    }

    private fun bindListeners() {
        view.table.selectionModel.addListSelectionListener(selectionListener)
        view.healButton.addActionListener(healListener)
        view.installProgramButton.addActionListener(installProgramListener)
        view.installFirewallButton.addActionListener(installFirewallListener)
    }

    private fun observeShellState() {
        windowScope.launch {
            controller.gameShellStateSelector().collect { snapshot ->
                SwingUtilities.invokeLater {
                    if (view.isClosed) {
                        return@invokeLater
                    }
                    val previousSelection = view.selectedPortNumber()
                    view.tableModel.updateRows(snapshot?.let(::buildPortManagementRows).orEmpty())
                    val nextSelection = when {
                        pendingPreferredPort != null && view.tableModel.rows.any { it.number == pendingPreferredPort } -> {
                            pendingPreferredPort.also { pendingPreferredPort = null }
                        }

                        previousSelection != null && view.tableModel.rows.any { it.number == previousSelection } -> previousSelection
                        else -> view.tableModel.rows.firstOrNull()?.number
                    }
                    applySelection(nextSelection)
                    renderState()
                }
            }
        }
    }

    private fun renderState() {
        val selectedPort = view.selectedPortNumber()
        val effectiveStatus = when {
            statusText.isNotBlank() && statusText != " " -> statusText
            view.tableModel.rows.isEmpty() && !requestInFlight -> "No ports available."
            else -> " "
        }
        val model = buildPortManagementViewModel(
            rows = view.tableModel.rows,
            selectedPortNumber = selectedPort,
            requestInFlight = requestInFlight,
            statusText = effectiveStatus,
            errorText = errorText.takeIf { it.isNotBlank() } ?: " ",
        )
        if (SwingUtilities.isEventDispatchThread()) {
            view.render(model)
        } else {
            SwingUtilities.invokeLater { view.render(model) }
        }
    }

    private fun applySelection(portNumber: Int?) {
        suppressSelectionEvents = true
        try {
            view.selectPort(portNumber)
        } finally {
            suppressSelectionEvents = false
        }
    }

    private fun selectedRow(): RewritePortManagementRow? {
        val selectedIndex = view.table.selectedRow
        if (selectedIndex < 0) {
            return null
        }
        return view.tableModel.rows.getOrNull(selectedIndex)
    }

    private fun openProgramChooser() {
        val selected = selectedRow() ?: return
        val existing = applicationChooserWindow
        if (existing != null && existing.isDisplayable && !existing.isClosed) {
            onFocusAuxiliaryWindow(existing)
            return
        }
        val chooser = RewriteLocalFileChooserWindow(
            controller = controller,
            title = "Choose Program",
            onFileSelected = { selection ->
                applicationChooserWindow?.dispose()
                submitInstallApplication(selected.number, selection.displayedPath, selection.file)
            },
            fileFilter = ::allowPortManagementApplicationFile,
        ).apply {
            name = "rewrite-port-management-program-chooser-window"
            addInternalFrameListener(object : InternalFrameAdapter() {
                override fun internalFrameClosed(event: InternalFrameEvent) {
                    if (applicationChooserWindow === this@apply) {
                        applicationChooserWindow = null
                    }
                }
            })
        }
        applicationChooserWindow = chooser
        onOpenAuxiliaryWindow(chooser)
    }

    private fun openFirewallChooser() {
        val selected = selectedRow() ?: return
        val existing = firewallChooserWindow
        if (existing != null && existing.isDisplayable && !existing.isClosed) {
            onFocusAuxiliaryWindow(existing)
            return
        }
        val chooser = RewriteLocalFileChooserWindow(
            controller = controller,
            title = "Choose Firewall",
            onFileSelected = { selection ->
                firewallChooserWindow?.dispose()
                submitInstallFirewall(selected.number, selection.displayedPath, selection.file)
            },
            fileFilter = ::allowPortManagementFirewallFile,
        ).apply {
            name = "rewrite-port-management-firewall-chooser-window"
            addInternalFrameListener(object : InternalFrameAdapter() {
                override fun internalFrameClosed(event: InternalFrameEvent) {
                    if (firewallChooserWindow === this@apply) {
                        firewallChooserWindow = null
                    }
                }
            })
        }
        firewallChooserWindow = chooser
        onOpenAuxiliaryWindow(chooser)
    }

    private fun submitHeal() {
        val selected = selectedRow() ?: return
        clearMessages()
        requestInFlight = true
        renderState()
        windowScope.launch {
            val result = controller.requestHealPort(selected.number)
            SwingUtilities.invokeLater {
                if (view.isClosed) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        if (result.value.accepted) {
                            statusText = "Healed port ${selected.number}."
                            errorText = " "
                        } else {
                            statusText = " "
                            errorText = result.value.message
                        }
                    }

                    is RewriteGameCommandResult.Failure -> {
                        statusText = " "
                        errorText = result.message
                    }
                }
                renderState()
            }
        }
    }

    private fun submitInstallApplication(
        portNumber: Int,
        directoryPath: String,
        file: com.hackwars.rewrite.protocol.ClientStoredFile,
    ) {
        val selected = view.tableModel.rows.firstOrNull { it.number == portNumber } ?: return
        if (selected.installedApplication != null) {
            val confirmed = JOptionPane.showConfirmDialog(
                view,
                "Replace ${selected.installedApplication.name} on port $portNumber?",
                "Replace Program",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
            )
            if (confirmed != JOptionPane.YES_OPTION) {
                return
            }
        }
        clearMessages()
        requestInFlight = true
        renderState()
        windowScope.launch {
            val result = controller.requestInstallApplication(
                path = directoryPath,
                name = file.name,
                portNumber = portNumber,
            )
            SwingUtilities.invokeLater {
                if (view.isClosed) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        statusText = "Installed ${result.value.installedApplication.name} on port $portNumber."
                        errorText = " "
                    }

                    is RewriteGameCommandResult.Failure -> {
                        statusText = " "
                        errorText = result.message
                    }
                }
                renderState()
            }
        }
    }

    private fun submitInstallFirewall(
        portNumber: Int,
        directoryPath: String,
        file: com.hackwars.rewrite.protocol.ClientStoredFile,
    ) {
        val selected = view.tableModel.rows.firstOrNull { it.number == portNumber } ?: return
        if (selected.installedFirewall != null) {
            val confirmed = JOptionPane.showConfirmDialog(
                view,
                "Replace ${selected.installedFirewall.name} on port $portNumber?",
                "Replace Firewall",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
            )
            if (confirmed != JOptionPane.YES_OPTION) {
                return
            }
        }
        clearMessages()
        requestInFlight = true
        renderState()
        windowScope.launch {
            val result = controller.requestInstallFirewall(
                path = directoryPath,
                name = file.name,
                portNumber = portNumber,
            )
            SwingUtilities.invokeLater {
                if (view.isClosed) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        statusText = "Installed ${result.value.installedFirewall.name} on port $portNumber."
                        errorText = " "
                    }

                    is RewriteGameCommandResult.Failure -> {
                        statusText = " "
                        errorText = result.message
                    }
                }
                renderState()
            }
        }
    }

    private fun clearMessages() {
        statusText = " "
        errorText = " "
    }
}
