package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.files.RewriteLocalFileChooserWindow
import com.hackwars.rewrite.client.mvc.RewriteControllerBase
import com.hackwars.rewrite.client.mvc.RewriteFrameBinding
import com.hackwars.rewrite.protocol.ClientEquipmentSlot
import com.hackwars.rewrite.protocol.ClientStoredFile
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

internal fun createEquipmentManagerWindowBinding(
    controller: RewriteRootController,
    onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
): RewriteFrameBinding {
    val view = RewriteEquipmentManagerWindow()
    val binding = RewriteEquipmentManagerController(
        controller = controller,
        view = view,
        onOpenAuxiliaryWindow = onOpenAuxiliaryWindow,
        onFocusAuxiliaryWindow = onFocusAuxiliaryWindow,
    )
    return RewriteFrameBinding(
        frame = view,
        controller = binding,
    )
}

internal fun createFirewallManagerWindowBinding(
    controller: RewriteRootController,
    onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
): RewriteFrameBinding {
    val view = RewriteFirewallManagerWindow()
    val binding = RewriteFirewallManagerController(
        controller = controller,
        view = view,
        onOpenAuxiliaryWindow = onOpenAuxiliaryWindow,
        onFocusAuxiliaryWindow = onFocusAuxiliaryWindow,
    )
    return RewriteFrameBinding(
        frame = view,
        controller = binding,
    )
}

internal class RewriteEquipmentManagerController(
    private val controller: RewriteRootController,
    private val view: RewriteEquipmentManagerWindow,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
) : RewriteControllerBase() {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var requestInFlight: Boolean = false
    private var chooserWindow: JInternalFrame? = null
    private var chooserSlotName: String? = null
    private var statusText: String = "Waiting for equipment data..."
    private var errorText: String = " "
    private var suppressSelectionEvents: Boolean = false

    private val selectionListener = javax.swing.event.ListSelectionListener {
        if (!suppressSelectionEvents) {
            renderState()
        }
    }
    private val installListener = java.awt.event.ActionListener { openChooser() }
    private val frameCloseListener = object : InternalFrameAdapter() {
        override fun internalFrameClosed(event: InternalFrameEvent) {
            close()
        }
    }

    init {
        bindListeners()
        onClose {
            view.table.selectionModel.removeListSelectionListener(selectionListener)
            view.installButton.removeActionListener(installListener)
            view.removeInternalFrameListener(frameCloseListener)
            chooserWindow?.dispose()
            windowScope.cancel()
        }
        view.addInternalFrameListener(frameCloseListener)
        observeShellState()
        renderState()
    }

    private fun bindListeners() {
        view.table.selectionModel.addListSelectionListener(selectionListener)
        view.installButton.addActionListener(installListener)
    }

    private fun observeShellState() {
        windowScope.launch {
            controller.gameShellStateSelector().collect { snapshot ->
                SwingUtilities.invokeLater {
                    if (view.isClosed) {
                        return@invokeLater
                    }
                    val previousSelection = view.selectedSlotName()
                    view.tableModel.updateRows(snapshot?.let(::buildEquipmentManagerRows).orEmpty())
                    val nextSelection = when {
                        previousSelection != null && view.tableModel.rows.any { it.slot.name == previousSelection } -> previousSelection
                        else -> view.tableModel.rows.firstOrNull()?.slot?.name
                    }
                    applySelection(nextSelection)
                    renderState()
                }
            }
        }
    }

    private fun renderState() {
        val model = buildEquipmentManagerViewModel(
            rows = view.tableModel.rows,
            selectedSlot = view.selectedRow()?.slot,
            requestInFlight = requestInFlight,
            statusText = statusText,
            errorText = errorText,
        )
        if (SwingUtilities.isEventDispatchThread()) {
            view.render(model)
        } else {
            SwingUtilities.invokeLater { view.render(model) }
        }
    }

    private fun applySelection(slotName: String?) {
        suppressSelectionEvents = true
        try {
            view.selectSlot(slotName)
        } finally {
            suppressSelectionEvents = false
        }
    }

    private fun openChooser() {
        val selected = view.selectedRow() ?: return
        val existing = chooserWindow
        if (existing != null && existing.isDisplayable && !existing.isClosed) {
            if (chooserSlotName == selected.slot.name) {
                onFocusAuxiliaryWindow(existing)
                return
            }
            existing.dispose()
        }
        val chooser = RewriteLocalFileChooserWindow(
            controller = controller,
            title = "Choose Equipment",
            onFileSelected = { selection ->
                chooserWindow?.dispose()
                submitInstall(selected.slot, selection.displayedPath, selection.file)
            },
            fileFilter = { file -> allowEquipmentManagerFile(file, selected.slot) },
        ).apply {
            name = "rewrite-equipment-manager-chooser-window"
            addInternalFrameListener(object : InternalFrameAdapter() {
                override fun internalFrameClosed(event: InternalFrameEvent) {
                    if (chooserWindow === this@apply) {
                        chooserWindow = null
                        chooserSlotName = null
                    }
                }
            })
        }
        chooserWindow = chooser
        chooserSlotName = selected.slot.name
        onOpenAuxiliaryWindow(chooser)
    }

    private fun submitInstall(
        slot: ClientEquipmentSlot,
        directoryPath: String,
        file: ClientStoredFile,
    ) {
        val selected = view.tableModel.rows.firstOrNull { it.slot == slot } ?: return
        if (selected.installedEquipment != null) {
            val confirmed = JOptionPane.showConfirmDialog(
                view,
                "Replace ${selected.installedEquipment.name} in ${slot.name}?",
                "Replace Equipment",
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
            val result = controller.requestInstallEquipment(
                path = directoryPath,
                name = file.name,
                slot = slot,
            )
            SwingUtilities.invokeLater {
                if (view.isClosed) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        statusText = "Installed ${result.value.equipment.name} in ${slot.name}."
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

internal class RewriteFirewallManagerController(
    private val controller: RewriteRootController,
    private val view: RewriteFirewallManagerWindow,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
) : RewriteControllerBase() {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var requestInFlight: Boolean = false
    private var chooserWindow: JInternalFrame? = null
    private var statusText: String = "Waiting for firewall data..."
    private var errorText: String = " "
    private var suppressSelectionEvents: Boolean = false

    private val selectionListener = javax.swing.event.ListSelectionListener {
        if (!suppressSelectionEvents) {
            renderState()
        }
    }
    private val installListener = java.awt.event.ActionListener { openChooser() }
    private val frameCloseListener = object : InternalFrameAdapter() {
        override fun internalFrameClosed(event: InternalFrameEvent) {
            close()
        }
    }

    init {
        bindListeners()
        onClose {
            view.table.selectionModel.removeListSelectionListener(selectionListener)
            view.installButton.removeActionListener(installListener)
            view.removeInternalFrameListener(frameCloseListener)
            chooserWindow?.dispose()
            windowScope.cancel()
        }
        view.addInternalFrameListener(frameCloseListener)
        observeShellState()
        renderState()
    }

    private fun bindListeners() {
        view.table.selectionModel.addListSelectionListener(selectionListener)
        view.installButton.addActionListener(installListener)
    }

    private fun observeShellState() {
        windowScope.launch {
            controller.gameShellStateSelector().collect { snapshot ->
                SwingUtilities.invokeLater {
                    if (view.isClosed) {
                        return@invokeLater
                    }
                    val previousSelection = view.selectedPortNumber()
                    view.tableModel.updateRows(snapshot?.let(::buildFirewallManagerRows).orEmpty())
                    val nextSelection = when {
                        previousSelection != null && view.tableModel.rows.any { it.portNumber == previousSelection } -> previousSelection
                        else -> view.tableModel.rows.firstOrNull()?.portNumber
                    }
                    applySelection(nextSelection)
                    renderState()
                }
            }
        }
    }

    private fun renderState() {
        val model = buildFirewallManagerViewModel(
            rows = view.tableModel.rows,
            selectedPortNumber = view.selectedPortNumber(),
            requestInFlight = requestInFlight,
            statusText = statusText,
            errorText = errorText,
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

    private fun openChooser() {
        val selected = view.selectedRow() ?: return
        val existing = chooserWindow
        if (existing != null && existing.isDisplayable && !existing.isClosed) {
            onFocusAuxiliaryWindow(existing)
            return
        }
        val chooser = RewriteLocalFileChooserWindow(
            controller = controller,
            title = "Choose Firewall",
            onFileSelected = { selection ->
                chooserWindow?.dispose()
                submitInstall(selected.portNumber, selection.displayedPath, selection.file)
            },
            fileFilter = ::allowFirewallManagerFile,
        ).apply {
            name = "rewrite-firewall-manager-chooser-window"
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

    private fun submitInstall(
        portNumber: Int,
        directoryPath: String,
        file: ClientStoredFile,
    ) {
        val selected = view.tableModel.rows.firstOrNull { it.portNumber == portNumber } ?: return
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
