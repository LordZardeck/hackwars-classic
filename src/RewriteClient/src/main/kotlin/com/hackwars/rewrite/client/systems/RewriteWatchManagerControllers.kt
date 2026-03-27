package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserController
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserPanel
import com.hackwars.rewrite.client.files.RewriteLocalFileSelection
import com.hackwars.rewrite.client.mvc.RewriteControllerBase
import com.hackwars.rewrite.client.mvc.RewriteFrameBinding
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientWatchListResponse
import com.hackwars.rewrite.protocol.ClientWatchManagerState
import com.hackwars.rewrite.protocol.ClientWatchMutationResponse
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Dimension
import java.awt.Insets
import java.awt.Window
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFormattedTextField
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal fun createWatchManagerWindowBinding(
    controller: RewriteRootController,
    onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
): RewriteFrameBinding {
    val view = RewriteWatchManagerWindow()
    val binding = RewriteWatchManagerController(
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

internal class RewriteWatchManagerController(
    private val controller: RewriteRootController,
    private val view: RewriteWatchManagerWindow,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
) : RewriteControllerBase() {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var latestSnapshot: ClientGameSnapshot? = controller.gameShellState()
    private var rowViews: List<RewriteWatchRowView> = emptyList()
    private var pendingRequests: Int = 0
    private var statusText: String = "Loading watches..."
    private var errorText: String = " "
    private var installChooserWindow: RewriteWatchInstallChooserWindow? = null
    private var suppressRender: Boolean = false
    private val installListener = java.awt.event.ActionListener { openInstallChooser() }
    private val exitListener = java.awt.event.ActionListener { view.dispose() }

    init {
        view.installMenuItem.addActionListener(installListener)
        view.exitMenuItem.addActionListener(exitListener)
        onClose {
            view.installMenuItem.removeActionListener(installListener)
            view.exitMenuItem.removeActionListener(exitListener)
            installChooserWindow?.dispose()
            windowScope.cancel()
        }
        view.addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                close()
            }
        })
        observeShellState()
        renderSnapshot(latestSnapshot)
        fetchInitialWatches()
    }

    private fun observeShellState() {
        windowScope.launch {
            controller.gameShellStateSelector().collect { snapshot ->
                SwingUtilities.invokeLater {
                    if (view.isClosed || !view.isDisplayable) {
                        return@invokeLater
                    }
                    latestSnapshot = snapshot
                    installChooserWindow?.updatePortChoices(snapshot?.ports.orEmpty().map { it.number }.sorted())
                    renderSnapshot(snapshot)
                }
            }
        }
    }

    private fun fetchInitialWatches() {
        beginRequest("Loading watches...")
        windowScope.launch {
            val result = controller.requestFetchWatches()
            SwingUtilities.invokeLater {
                if (view.isClosed) {
                    return@invokeLater
                }
                endRequest()
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        statusText = "Loaded ${result.value.watches.size} watches."
                        errorText = " "
                    }

                    is RewriteGameCommandResult.Failure -> {
                        statusText = "Unable to load watches."
                        errorText = result.message
                    }
                }
                renderSnapshot(latestSnapshot)
            }
        }
    }

    private fun renderSnapshot(snapshot: ClientGameSnapshot?) {
        if (suppressRender) {
            return
        }
        val rows = snapshot?.let(::buildWatchManagerRows).orEmpty()
        reconcileRows(rows)
        rowViews.forEach { it.setInteractionEnabled(pendingRequests == 0) }
        renderState(rows)
    }

    private fun reconcileRows(rows: List<RewriteWatchManagerRow>) {
        val canUpdateInPlace = rowViews.size == rows.size &&
            rowViews.indices.all { index -> rowViews[index].rowIndex == rows[index].index }
        if (!canUpdateInPlace) {
            rowViews = rows.map { row ->
                RewriteWatchRowView(
                    initialRow = row,
                    onToggle = { handleToggle(rowIndex = it.rowIndex, enabled = it.enabled) },
                    onPortChanged = { handlePortChanged(it.rowIndex, it.selectedPort) },
                    onTypeChanged = { handleTypeChanged(it.rowIndex, it.selectedTypeIndex) },
                    onNoteChanged = { handleNoteChanged(it.rowIndex, it.noteText) },
                    onObservedPortsRequested = { handleObservedPortsRequested(it.rowIndex, it.portChoices, it.observedPorts) },
                    onSearchFirewallChanged = { handleSearchFirewallChanged(it.rowIndex, it.selectedSearchFirewall) },
                    onQuantityChanged = { handleQuantityChanged(it.rowIndex, it.quantityValue) },
                    onDelete = { handleDelete(it.rowIndex) },
                )
            }
            view.setRowComponents(rowViews.map { it.component })
        }
        rowViews.zip(rows).forEach { (viewRow, row) -> viewRow.update(row) }
    }

    private fun openInstallChooser() {
        val currentHostChooser = installChooserWindow
        if (currentHostChooser != null && !currentHostChooser.isClosed) {
            onFocusAuxiliaryWindow(currentHostChooser)
            return
        }
        val chooser = RewriteWatchInstallChooserWindow(
            controller = controller,
            initialPath = controller.gameFilesystemState()?.currentPath,
            portChoices = latestSnapshot?.ports.orEmpty().map { it.number }.sorted(),
            onInstallSelected = { selection ->
                submitInstallWatch(
                    path = selection.displayedPath,
                    name = selection.file.name,
                    type = selection.typeIndex,
                    port = selection.portNumber,
                )
            },
        )
        chooser.addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                installChooserWindow = null
            }
        })
        installChooserWindow = chooser
        onOpenAuxiliaryWindow(chooser)
    }

    private fun handleToggle(rowIndex: Int, enabled: Boolean) {
        beginRequest("Updating watch state...")
        windowScope.launch {
            val result = controller.requestSetWatchOnOff(
                watchId = rowIndex,
                state = !enabled,
            )
            completeMutation(result)
        }
    }

    private fun handlePortChanged(rowIndex: Int, selectedPort: Int) {
        beginRequest("Changing watch port...")
        windowScope.launch {
            val result = controller.requestChangeWatchPort(
                watchId = rowIndex,
                portId = selectedPort,
            )
            completeMutation(result)
        }
    }

    private fun handleTypeChanged(rowIndex: Int, selectedTypeIndex: Int) {
        beginRequest("Changing watch type...")
        windowScope.launch {
            val result = controller.requestChangeWatchType(
                watchId = rowIndex,
                newType = selectedTypeIndex,
            )
            completeMutation(result)
        }
    }

    private fun handleNoteChanged(rowIndex: Int, note: String) {
        beginRequest("Saving watch note...")
        windowScope.launch {
            val result = controller.requestSetWatchNote(
                watchId = rowIndex,
                note = note,
            )
            completeMutation(result)
        }
    }

    private fun handleObservedPortsRequested(
        rowIndex: Int,
        portChoices: List<Int>,
        selectedPorts: List<Int>,
    ) {
        val owner = SwingUtilities.getWindowAncestor(view)
        RewriteObservedPortsDialog(
            owner = owner,
            portChoices = portChoices,
            selectedPorts = selectedPorts,
        ) { nextPorts ->
            beginRequest("Updating observed ports...")
            windowScope.launch {
                val result = controller.requestSetWatchObservedPorts(
                    watchId = rowIndex,
                    observedPorts = nextPorts,
                )
                completeMutation(result)
            }
        }
    }

    private fun handleSearchFirewallChanged(rowIndex: Int, selectedSearchFirewall: Int) {
        beginRequest("Updating watch firewall search...")
        windowScope.launch {
            val result = controller.requestSetWatchSearchFirewall(
                watchId = rowIndex,
                searchFirewall = selectedSearchFirewall,
            )
            completeMutation(result)
        }
    }

    private fun handleQuantityChanged(rowIndex: Int, quantityValue: Double) {
        beginRequest("Updating watch value...")
        windowScope.launch {
            val result = controller.requestSetWatchQuantity(
                watchId = rowIndex,
                quantity = quantityValue,
            )
            completeMutation(result)
        }
    }

    private fun handleDelete(rowIndex: Int) {
        val confirmed = JOptionPane.showConfirmDialog(
            view,
            "Are you sure?\nThis will permanently remove this watch from your computer.",
            "Delete Watch",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
        )
        if (confirmed != JOptionPane.YES_OPTION) {
            return
        }
        beginRequest("Deleting watch...")
        windowScope.launch {
            val result = controller.requestDeleteWatch(watchId = rowIndex)
            completeMutation(result)
        }
    }

    private fun submitInstallWatch(
        path: String,
        name: String,
        type: Int,
        port: Int,
    ) {
        beginRequest("Installing watch...")
        windowScope.launch {
            val result = controller.requestInstallWatch(
                path = path,
                name = name,
                type = type,
                portNumber = port,
            )
            SwingUtilities.invokeLater {
                installChooserWindow?.dispose()
            }
            completeMutation(result)
        }
    }

    private fun completeMutation(result: RewriteGameCommandResult<ClientWatchMutationResponse>) {
        SwingUtilities.invokeLater {
            if (view.isClosed) {
                return@invokeLater
            }
            endRequest()
            when (result) {
                is RewriteGameCommandResult.Success -> {
                    if (result.value.accepted) {
                        statusText = result.value.message
                        errorText = " "
                    } else {
                        statusText = "Watch update failed."
                        errorText = result.value.message
                    }
                }

                is RewriteGameCommandResult.Failure -> {
                    statusText = "Watch update failed."
                    errorText = result.message
                }
            }
            renderSnapshot(latestSnapshot)
        }
    }

    private fun beginRequest(status: String) {
        pendingRequests += 1
        statusText = status
        errorText = " "
        renderState(latestSnapshot?.let(::buildWatchManagerRows).orEmpty())
    }

    private fun endRequest() {
        pendingRequests = (pendingRequests - 1).coerceAtLeast(0)
    }

    private fun renderState(rows: List<RewriteWatchManagerRow>) {
        val effectiveStatus = when {
            pendingRequests > 0 -> statusText.ifBlank { "Loading watches..." }
            statusText.isNotBlank() && statusText != " " -> statusText
            rows.isEmpty() -> "No watches installed."
            else -> " "
        }
        val effectiveError = if (errorText.isBlank()) " " else errorText
        view.render(
            RewriteWatchManagerViewModel(
                rows = rows,
                statusText = effectiveStatus,
                errorText = effectiveError,
                installMenuEnabled = pendingRequests == 0,
            ),
        )
    }
}

private class RewriteWatchRowView(
    initialRow: RewriteWatchManagerRow,
    private val onToggle: (RewriteWatchRowInteraction) -> Unit,
    private val onPortChanged: (RewriteWatchRowInteraction) -> Unit,
    private val onTypeChanged: (RewriteWatchRowInteraction) -> Unit,
    private val onNoteChanged: (RewriteWatchRowInteraction) -> Unit,
    private val onObservedPortsRequested: (RewriteWatchRowInteraction) -> Unit,
    private val onSearchFirewallChanged: (RewriteWatchRowInteraction) -> Unit,
    private val onQuantityChanged: (RewriteWatchRowInteraction) -> Unit,
    private val onDelete: (RewriteWatchRowInteraction) -> Unit,
) {
    val component = JPanel(GridBagLayout()).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0xDD, 0xDD, 0xDD)),
            BorderFactory.createEmptyBorder(6, 6, 6, 6),
        )
        isOpaque = true
        background = Color.WHITE
    }

    var rowIndex: Int = initialRow.index
        internal set

    private val numberLabel = JLabel()
    private val onOffButton = JButton()
    private val portCombo = JComboBox<String>()
    private val typeCombo = JComboBox(WATCH_TYPE_LABELS.toTypedArray())
    private val cpuCostLabel = JLabel()
    private val noteField = JTextField()
    private val observedPortsCombo = JComboBox<String>()
    private val editObservedPortsButton = JButton("Edit")
    private val searchFirewallCombo = JComboBox(WATCH_FIREWALL_LABELS.toTypedArray())
    private val valueContainer = JPanel(BorderLayout())
    private val healthSpinner = JSpinner(SpinnerNumberModel(0.0, 0.0, 100.0, 1.0))
    private val pettyCashField = JTextField()
    private val scanValueLabel = JLabel("When Scanned")
    private val deleteButton = JButton("Delete")

    private var updating: Boolean = false
    private var currentRow: RewriteWatchManagerRow = initialRow
    private var committedNote: String = initialRow.note
    private var committedQuantity: Double = initialRow.quantityThreshold

    init {
        onOffButton.name = "rewrite-watch-row-onoff-${initialRow.index}"
        portCombo.name = "rewrite-watch-row-port-${initialRow.index}"
        typeCombo.name = "rewrite-watch-row-type-${initialRow.index}"
        noteField.name = "rewrite-watch-row-note-${initialRow.index}"
        observedPortsCombo.name = "rewrite-watch-row-observed-${initialRow.index}"
        editObservedPortsButton.name = "rewrite-watch-row-edit-observed-${initialRow.index}"
        searchFirewallCombo.name = "rewrite-watch-row-search-firewall-${initialRow.index}"
        healthSpinner.name = "rewrite-watch-row-health-${initialRow.index}"
        pettyCashField.name = "rewrite-watch-row-petty-cash-${initialRow.index}"
        deleteButton.name = "rewrite-watch-row-delete-${initialRow.index}"
        pettyCashField.horizontalAlignment = SwingConstants.RIGHT
        scanValueLabel.name = "rewrite-watch-row-scan-value-${initialRow.index}"
        observedPortsCombo.isEnabled = false

        var column = 0
        addComponent(numberLabel, column++, 1.0, 0.0, fill = java.awt.GridBagConstraints.NONE)
        addComponent(onOffButton, column++, 1.0, 0.0)
        addComponent(portCombo, column++, 1.0, 0.0)
        addComponent(typeCombo, column++, 1.0, 0.0)
        addComponent(cpuCostLabel, column++, 1.0, 0.0)
        addComponent(noteField, column++, 1.0, 0.4)
        addComponent(
            JPanel(BorderLayout(4, 0)).apply {
                isOpaque = false
                add(observedPortsCombo, BorderLayout.CENTER)
                add(editObservedPortsButton, BorderLayout.EAST)
            },
            column++,
            1.0,
            0.2,
        )
        addComponent(searchFirewallCombo, column++, 1.0, 0.15)
        addComponent(valueContainer, column++, 1.0, 0.25)
        addComponent(deleteButton, column, 1.0, 0.0)

        onOffButton.addActionListener {
            if (!updating) {
                onToggle(interaction())
            }
        }
        portCombo.addActionListener {
            if (!updating && currentRow.scanType.not()) {
                onPortChanged(interaction())
            }
        }
        typeCombo.addActionListener {
            if (!updating) {
                onTypeChanged(interaction())
            }
        }
        noteField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(event: FocusEvent) {
                if (updating) {
                    return
                }
                val nextNote = noteField.text
                if (nextNote != committedNote) {
                    onNoteChanged(interaction())
                }
            }
        })
        editObservedPortsButton.addActionListener {
            if (!updating && currentRow.scanType.not()) {
                onObservedPortsRequested(interaction())
            }
        }
        searchFirewallCombo.addActionListener {
            if (!updating) {
                onSearchFirewallChanged(interaction())
            }
        }
        spinnerTextField().addFocusListener(object : FocusAdapter() {
            override fun focusLost(event: FocusEvent) {
                if (updating || currentRow.type != com.hackwars.rewrite.protocol.ClientWatchKind.HEALTH) {
                    return
                }
                val value = (healthSpinner.value as? Number)?.toDouble() ?: committedQuantity
                val clamped = value.coerceIn(0.0, 100.0)
                healthSpinner.value = clamped
                if (clamped != committedQuantity) {
                    onQuantityChanged(interaction())
                }
            }
        })
        pettyCashField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(event: FocusEvent) {
                if (updating || currentRow.type != com.hackwars.rewrite.protocol.ClientWatchKind.PETTY_CASH) {
                    return
                }
                val parsed = pettyCashField.text.toDoubleOrNull() ?: committedQuantity
                pettyCashField.text = formatWatchMetric(parsed)
                if (parsed != committedQuantity) {
                    onQuantityChanged(interaction(quantityOverride = parsed))
                }
            }
        })
        deleteButton.addActionListener {
            if (!updating) {
                onDelete(interaction())
            }
        }

        update(initialRow)
    }

    fun update(row: RewriteWatchManagerRow) {
        updating = true
        currentRow = row
        rowIndex = row.index
        committedNote = row.note
        committedQuantity = row.quantityThreshold
        numberLabel.text = row.index.toString()
        onOffButton.text = if (row.enabled) "On" else "Off"
        updatePortCombo(row)
        typeCombo.selectedIndex = row.type.ordinal
        cpuCostLabel.text = row.cpuCostDisplay
        if (!noteField.hasFocus()) {
            noteField.text = row.note
        }
        updateObservedPorts(row)
        searchFirewallCombo.selectedIndex = row.searchFirewallType.coerceIn(0, WATCH_FIREWALL_LABELS.lastIndex)
        updateValueWidget(row)
        renameComponents(row.index)
        updating = false
    }

    fun setInteractionEnabled(enabled: Boolean) {
        onOffButton.isEnabled = enabled
        portCombo.isEnabled = enabled && !currentRow.scanType
        typeCombo.isEnabled = enabled
        noteField.isEnabled = enabled
        observedPortsCombo.isEnabled = enabled && !currentRow.scanType
        editObservedPortsButton.isEnabled = enabled && !currentRow.scanType
        searchFirewallCombo.isEnabled = enabled
        spinnerTextField().isEnabled = enabled && currentRow.type == com.hackwars.rewrite.protocol.ClientWatchKind.HEALTH
        healthSpinner.isEnabled = enabled && currentRow.type == com.hackwars.rewrite.protocol.ClientWatchKind.HEALTH
        pettyCashField.isEnabled = enabled && currentRow.type == com.hackwars.rewrite.protocol.ClientWatchKind.PETTY_CASH
        deleteButton.isEnabled = enabled
    }

    private fun renameComponents(index: Int) {
        onOffButton.name = "rewrite-watch-row-onoff-$index"
        portCombo.name = "rewrite-watch-row-port-$index"
        typeCombo.name = "rewrite-watch-row-type-$index"
        noteField.name = "rewrite-watch-row-note-$index"
        observedPortsCombo.name = "rewrite-watch-row-observed-$index"
        editObservedPortsButton.name = "rewrite-watch-row-edit-observed-$index"
        searchFirewallCombo.name = "rewrite-watch-row-search-firewall-$index"
        healthSpinner.name = "rewrite-watch-row-health-$index"
        pettyCashField.name = "rewrite-watch-row-petty-cash-$index"
        deleteButton.name = "rewrite-watch-row-delete-$index"
        scanValueLabel.name = "rewrite-watch-row-scan-value-$index"
    }

    private fun updatePortCombo(row: RewriteWatchManagerRow) {
        portCombo.removeAllItems()
        if (row.scanType) {
            portCombo.addItem("N/A")
            portCombo.selectedIndex = 0
            return
        }
        row.portChoices.forEach { portCombo.addItem(it.toString()) }
        val selectedIndex = row.portChoices.indexOf(row.installPort).coerceAtLeast(0)
        portCombo.selectedIndex = selectedIndex
    }

    private fun updateObservedPorts(row: RewriteWatchManagerRow) {
        observedPortsCombo.removeAllItems()
        if (row.scanType) {
            observedPortsCombo.addItem("N/A")
            observedPortsCombo.selectedIndex = 0
            return
        }
        val displayedValues = if (row.observedPorts.isEmpty()) listOf("-") else row.observedPorts.map(Int::toString)
        displayedValues.forEach(observedPortsCombo::addItem)
        observedPortsCombo.selectedIndex = 0
    }

    private fun updateValueWidget(row: RewriteWatchManagerRow) {
        valueContainer.removeAll()
        when (row.type) {
            com.hackwars.rewrite.protocol.ClientWatchKind.HEALTH -> {
                val clamped = row.quantityThreshold.coerceIn(0.0, 100.0)
                if (!spinnerTextField().hasFocus()) {
                    healthSpinner.value = clamped
                }
                valueContainer.add(healthSpinner, BorderLayout.CENTER)
            }

            com.hackwars.rewrite.protocol.ClientWatchKind.PETTY_CASH -> {
                if (!pettyCashField.hasFocus()) {
                    pettyCashField.text = formatWatchMetric(row.quantityThreshold)
                }
                valueContainer.add(pettyCashField, BorderLayout.CENTER)
            }

            com.hackwars.rewrite.protocol.ClientWatchKind.SCAN -> {
                valueContainer.add(scanValueLabel, BorderLayout.CENTER)
            }
        }
        valueContainer.revalidate()
        valueContainer.repaint()
    }

    private fun interaction(quantityOverride: Double? = null): RewriteWatchRowInteraction {
        return RewriteWatchRowInteraction(
            rowIndex = rowIndex,
            enabled = currentRow.enabled,
            selectedPort = portCombo.selectedItem?.toString()?.toIntOrNull() ?: currentRow.installPort,
            selectedTypeIndex = typeCombo.selectedIndex.coerceAtLeast(0),
            noteText = noteField.text,
            portChoices = currentRow.portChoices,
            observedPorts = currentRow.observedPorts,
            selectedSearchFirewall = searchFirewallCombo.selectedIndex.coerceAtLeast(0),
            quantityValue = quantityOverride ?: when (currentRow.type) {
                com.hackwars.rewrite.protocol.ClientWatchKind.HEALTH -> (healthSpinner.value as? Number)?.toDouble() ?: committedQuantity
                com.hackwars.rewrite.protocol.ClientWatchKind.PETTY_CASH -> pettyCashField.text.toDoubleOrNull() ?: committedQuantity
                com.hackwars.rewrite.protocol.ClientWatchKind.SCAN -> committedQuantity
            },
        )
    }

    private fun spinnerTextField(): JFormattedTextField {
        return healthSpinner.editor.components.filterIsInstance<JFormattedTextField>().first()
    }

    private fun addComponent(
        child: java.awt.Component,
        gridx: Int,
        ipadx: Double,
        weightx: Double,
        fill: Int = java.awt.GridBagConstraints.HORIZONTAL,
    ) {
        component.add(
            child,
            GridBagConstraints().apply {
                this.gridx = gridx
                this.gridy = 0
                this.insets = Insets(0, 4, 0, 4)
                this.fill = fill
                this.weightx = weightx
                this.anchor = GridBagConstraints.WEST
                this.ipadx = ipadx.toInt()
            },
        )
    }
}

private data class RewriteWatchRowInteraction(
    val rowIndex: Int,
    val enabled: Boolean,
    val selectedPort: Int,
    val selectedTypeIndex: Int,
    val noteText: String,
    val portChoices: List<Int>,
    val observedPorts: List<Int>,
    val selectedSearchFirewall: Int,
    val quantityValue: Double,
)

private class RewriteObservedPortsDialog(
    owner: Window?,
    portChoices: List<Int>,
    selectedPorts: List<Int>,
    onSubmit: (List<Int>) -> Unit,
) : JDialog(owner, "Set Observed Ports", Dialog.ModalityType.APPLICATION_MODAL) {
    init {
        name = "rewrite-watch-observed-ports-dialog"
        val checkboxes = portChoices.map { port ->
            JCheckBox("Port $port", port in selectedPorts).apply {
                name = "rewrite-watch-observed-port-$port"
            }
        }
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JLabel("Select Observed Ports:"), BorderLayout.NORTH)
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
                            name = "rewrite-watch-observed-ports-ok"
                            addActionListener {
                                val chosen = checkboxes
                                    .filter(JCheckBox::isSelected)
                                    .map { checkbox -> checkbox.text.removePrefix("Port ").toInt() }
                                    .sorted()
                                onSubmit(chosen)
                                dispose()
                            }
                        },
                    )
                    add(
                        JButton("Cancel").apply {
                            name = "rewrite-watch-observed-ports-cancel"
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

private class RewriteWatchInstallChooserWindow(
    controller: RewriteRootController,
    initialPath: String?,
    portChoices: List<Int>,
    private val onInstallSelected: (RewriteWatchInstallSelection) -> Unit,
) : JInternalFrame("Choose File", true, true, true, true) {
    private val browserController = RewriteLocalDirectoryBrowserController(
        rootController = controller,
        directoryFilter = ::allowWatchInstallDirectory,
        fileFilter = ::allowWatchManagerFile,
    )
    private val browserPanel = RewriteLocalDirectoryBrowserPanel(
        browserController = browserController,
        primaryActionLabel = "Open",
        primaryActionName = "rewrite-watch-install-open-button",
        onPrimaryAction = { openSelection() },
        canRunPrimaryAction = { state ->
            state.entries.any { it.path == state.selectedPath }
        },
    )
    private val typeCombo = JComboBox(WATCH_TYPE_LABELS.toTypedArray()).apply {
        name = "rewrite-watch-install-type-combo"
    }
    private val portCombo = JComboBox<String>().apply {
        name = "rewrite-watch-install-port-combo"
    }
    private val installButton = JButton("Install").apply {
        name = "rewrite-watch-install-button"
        addActionListener { submitSelection() }
    }
    private val cancelButton = JButton("Cancel").apply {
        name = "rewrite-watch-install-cancel-button"
        addActionListener { dispose() }
    }

    init {
        name = "rewrite-watch-install-chooser-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(620, 480)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(browserPanel, BorderLayout.CENTER)
            add(
                JPanel(BorderLayout(0, 8)).apply {
                    isOpaque = false
                    add(buildControlsPanel(), BorderLayout.NORTH)
                    add(
                        JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                            isOpaque = false
                            add(installButton)
                            add(cancelButton)
                        },
                        BorderLayout.SOUTH,
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
        updatePortChoices(portChoices)
        browserController.activate(initialPath ?: "/")
    }

    fun updatePortChoices(nextPorts: List<Int>) {
        val selectedPort = portCombo.selectedItem?.toString()?.toIntOrNull()
        portCombo.removeAllItems()
        nextPorts.sorted().forEach { portCombo.addItem(it.toString()) }
        val selectedIndex = nextPorts.indexOf(selectedPort).takeIf { it >= 0 } ?: 0
        if (portCombo.itemCount > 0) {
            portCombo.selectedIndex = selectedIndex
        }
        installButton.isEnabled = portCombo.itemCount > 0
    }

    private fun buildControlsPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(
                JLabel("Type"),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = Insets(2, 4, 2, 4)
                },
            )
            add(
                typeCombo,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 0
                    fill = GridBagConstraints.HORIZONTAL
                    weightx = 0.5
                    insets = Insets(2, 4, 2, 8)
                },
            )
            add(
                JLabel("Port"),
                GridBagConstraints().apply {
                    gridx = 2
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = Insets(2, 4, 2, 4)
                },
            )
            add(
                portCombo,
                GridBagConstraints().apply {
                    gridx = 3
                    gridy = 0
                    fill = GridBagConstraints.HORIZONTAL
                    weightx = 0.5
                    insets = Insets(2, 4, 2, 4)
                },
            )
        }
    }

    private fun openSelection() {
        val selectedEntry = browserController.selectedEntry() ?: return
        if (!selectedEntry.isDirectory) {
            submitSelection()
            return
        }
        browserController.activate(selectedEntry.path)
    }

    private fun submitSelection() {
        val selectedEntry = browserController.selectedEntry() ?: return
        val selectedPort = portCombo.selectedItem?.toString()?.toIntOrNull() ?: return
        val selectedType = typeCombo.selectedIndex.coerceAtLeast(0)
        onInstallSelected(
            RewriteWatchInstallSelection(
                displayedPath = browserController.snapshot().listing?.path ?: browserController.snapshot().displayedPath,
                file = selectedEntry.file ?: return,
                typeIndex = selectedType,
                portNumber = selectedPort,
            ),
        )
    }
}

private data class RewriteWatchInstallSelection(
    val displayedPath: String,
    val file: com.hackwars.rewrite.protocol.ClientStoredFile,
    val typeIndex: Int,
    val portNumber: Int,
)

private fun allowWatchInstallDirectory(entry: ClientDirectoryEntry): Boolean {
    val rootDirectory = entry.path.count { it == '/' } == 1
    return !(rootDirectory && entry.name in setOf("Store", "Public", "Trash"))
}
