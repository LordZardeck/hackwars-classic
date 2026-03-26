package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserController
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserEntry
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserState
import com.hackwars.rewrite.client.files.RewriteLocalDirectoryBrowserPanel
import com.hackwars.rewrite.client.files.RewriteLocalFileSelection
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledWatch
import com.hackwars.rewrite.protocol.ClientScriptFamily
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.ClientWatchKind
import com.hackwars.rewrite.protocol.ClientWatchListResponse
import com.hackwars.rewrite.protocol.ClientWatchManagerState
import com.hackwars.rewrite.protocol.ClientWatchMutationResponse
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFormattedTextField
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
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

private val WATCH_TYPE_LABELS: List<String> = listOf("Health", "Petty Cash", "Scan")
private val WATCH_FIREWALL_LABELS: List<String> = listOf(
    "None",
    "PortProtector",
    "PwnPreventer",
    "DataShield",
    "PacketBuster",
    "TrafficTender",
    "DigitalFortress",
    "ForceField",
    "RubyGuardian",
    "DiamondDefender",
    "ADNArmour",
)

internal data class RewriteWatchManagerRow(
    val index: Int,
    val type: ClientWatchKind,
    val enabled: Boolean,
    val portChoices: List<Int>,
    val installPort: Int,
    val cpuCost: Double,
    val note: String,
    val observedPorts: List<Int>,
    val searchFirewallType: Int,
    val quantityThreshold: Double,
    val installedWatch: ClientInstalledWatch,
) {
    val typeLabel: String = watchTypeLabel(type)
    val cpuCostDisplay: String = formatWatchMetric(cpuCost)
    val scanType: Boolean = type == ClientWatchKind.SCAN
}

internal fun buildWatchManagerRows(snapshot: ClientGameSnapshot): List<RewriteWatchManagerRow> {
    val availablePorts = snapshot.ports.map { it.number }.sorted()
    return snapshot.watches.watches.mapIndexed { index, watch ->
        val portChoices = when (watch.kind) {
            ClientWatchKind.SCAN -> emptyList()
            else -> (availablePorts + watch.installPort).distinct().sorted()
        }
        RewriteWatchManagerRow(
            index = index,
            type = watch.kind,
            enabled = watch.enabled,
            portChoices = portChoices,
            installPort = watch.installPort,
            cpuCost = watch.cpuCost,
            note = watch.note,
            observedPorts = watch.observedPorts.sorted(),
            searchFirewallType = watch.searchFirewallType,
            quantityThreshold = watch.quantityThreshold,
            installedWatch = watch,
        )
    }
}

internal fun allowWatchManagerFile(file: ClientStoredFile): Boolean {
    return file.kind == ClientStoredFileKind.APPLICATION_BINARY &&
        file.compiledBinary?.scriptFamily == ClientScriptFamily.WATCH
}

internal class RewriteWatchManagerWindow(
    private val controller: RewriteRootController,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
) : JInternalFrame("Watch Manager", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rowsPanel = JPanel().apply {
        name = "rewrite-watch-manager-rows"
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val statusLabel = JLabel("Loading watches...").apply {
        name = "rewrite-watch-manager-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-watch-manager-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val installMenuItem = JMenuItem("Install New Watch").apply {
        name = "rewrite-watch-manager-install-menu-item"
        addActionListener { openInstallChooser() }
    }
    private val exitMenuItem = JMenuItem("Exit").apply {
        name = "rewrite-watch-manager-exit-menu-item"
        addActionListener { dispose() }
    }

    private var latestSnapshot: ClientGameSnapshot? = controller.gameShellState()
    private var rowViews: List<RewriteWatchRowView> = emptyList()
    private var pendingRequests: Int = 0
    private var installChooserWindow: RewriteWatchInstallChooserWindow? = null

    init {
        name = "rewrite-watch-manager-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(1140, 520)
        jMenuBar = buildMenuBar()
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(buildHeaderPanel(), BorderLayout.NORTH)
            add(
                JScrollPane(rowsPanel).apply {
                    name = "rewrite-watch-manager-scroll-pane"
                    border = BorderFactory.createLineBorder(Color(0xCC, 0xCC, 0xCC))
                },
                BorderLayout.CENTER,
            )
            add(buildFooterPanel(), BorderLayout.SOUTH)
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                installChooserWindow?.dispose()
                windowScope.cancel()
            }
        })
        observeShellState()
        renderSnapshot(latestSnapshot)
        fetchInitialWatches()
    }

    private fun buildMenuBar(): JMenuBar {
        return JMenuBar().apply {
            add(
                JMenu("File").apply {
                    add(installMenuItem)
                    add(exitMenuItem)
                },
            )
        }
    }

    private fun buildHeaderPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(buildColumnHeaderRow(), BorderLayout.CENTER)
        }
    }

    private fun buildFooterPanel(): JPanel {
        return JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(statusLabel, BorderLayout.NORTH)
            add(errorLabel, BorderLayout.SOUTH)
        }
    }

    private fun buildColumnHeaderRow(): JPanel {
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 4, 4, 4)
            var column = 0
            addHeader(this, "#", column++, 32)
            addHeader(this, "On/Off", column++, 76)
            addHeader(this, "Port", column++, 76)
            addHeader(this, "Type", column++, 110)
            addHeader(this, "CPU Cost", column++, 76)
            addHeader(this, "Note", column++, 160, weightx = 0.4)
            addHeader(this, "Observed Ports", column++, 130, weightx = 0.25)
            addHeader(this, "Search FireWall", column++, 142, weightx = 0.15)
            addHeader(this, "Value", column++, 120, weightx = 0.2)
            addHeader(this, "Delete", column, 70)
        }
    }

    private fun addHeader(
        panel: JPanel,
        title: String,
        gridx: Int,
        width: Int,
        weightx: Double = 0.0,
    ) {
        panel.add(
            JLabel(title),
            GridBagConstraints().apply {
                this.gridx = gridx
                this.gridy = 0
                this.insets = Insets(0, 4, 0, 4)
                this.anchor = GridBagConstraints.WEST
                this.fill = GridBagConstraints.HORIZONTAL
                this.weightx = weightx
                this.ipadx = width
            },
        )
    }

    private fun observeShellState() {
        windowScope.launch {
            controller.gameShellStateSelector().collect { snapshot ->
                SwingUtilities.invokeLater {
                    if (isClosed || !isDisplayable) {
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
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                endRequest()
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        errorLabel.text = " "
                        statusLabel.text = "Loaded ${result.value.watches.size} watches."
                    }

                    is RewriteGameCommandResult.Failure -> {
                        errorLabel.text = result.message
                        statusLabel.text = "Unable to load watches."
                    }
                }
                renderSnapshot(latestSnapshot)
            }
        }
    }

    private fun renderSnapshot(snapshot: ClientGameSnapshot?) {
        val rows = snapshot?.let(::buildWatchManagerRows).orEmpty()
        reconcileRows(rows)
        rowViews.forEach { it.setInteractionEnabled(pendingRequests == 0) }
        installMenuItem.isEnabled = pendingRequests == 0
        if (pendingRequests == 0 && errorLabel.text.isBlank()) {
            errorLabel.text = " "
        }
        if (pendingRequests == 0 && rows.isEmpty() && errorLabel.text.isBlank()) {
            statusLabel.text = "No watches installed."
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    private fun reconcileRows(rows: List<RewriteWatchManagerRow>) {
        val canUpdateInPlace = rowViews.size == rows.size &&
            rowViews.indices.all { index -> rowViews[index].rowIndex == rows[index].index }
        if (!canUpdateInPlace) {
            rowsPanel.removeAll()
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
            rowViews.forEach { view ->
                rowsPanel.add(view.component)
                rowsPanel.add(Box.createVerticalStrut(6))
            }
        }
        rowViews.zip(rows).forEach { (view, row) ->
            view.update(row)
        }
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
        val owner = SwingUtilities.getWindowAncestor(this)
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
            this,
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
            if (isClosed || !isDisplayable) {
                return@invokeLater
            }
            endRequest()
            when (result) {
                is RewriteGameCommandResult.Success -> {
                    if (result.value.accepted) {
                        statusLabel.text = result.value.message
                        errorLabel.text = " "
                    } else {
                        statusLabel.text = "Watch update failed."
                        errorLabel.text = result.value.message
                    }
                }

                is RewriteGameCommandResult.Failure -> {
                    statusLabel.text = "Watch update failed."
                    errorLabel.text = result.message
                }
            }
            renderSnapshot(latestSnapshot)
        }
    }

    private fun beginRequest(status: String) {
        pendingRequests += 1
        statusLabel.text = status
        errorLabel.text = " "
        renderSnapshot(latestSnapshot)
    }

    private fun endRequest() {
        pendingRequests = (pendingRequests - 1).coerceAtLeast(0)
        renderSnapshot(latestSnapshot)
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
        private set

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
        addComponent(numberLabel, column++, 1.0, 0.0, fill = GridBagConstraints.NONE)
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
                if (updating || currentRow.type != ClientWatchKind.HEALTH) {
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
                if (updating || currentRow.type != ClientWatchKind.PETTY_CASH) {
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
        spinnerTextField().isEnabled = enabled && currentRow.type == ClientWatchKind.HEALTH
        healthSpinner.isEnabled = enabled && currentRow.type == ClientWatchKind.HEALTH
        pettyCashField.isEnabled = enabled && currentRow.type == ClientWatchKind.PETTY_CASH
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
            ClientWatchKind.HEALTH -> {
                val clamped = row.quantityThreshold.coerceIn(0.0, 100.0)
                if (!spinnerTextField().hasFocus()) {
                    healthSpinner.value = clamped
                }
                valueContainer.add(healthSpinner, BorderLayout.CENTER)
            }

            ClientWatchKind.PETTY_CASH -> {
                if (!pettyCashField.hasFocus()) {
                    pettyCashField.text = formatWatchMetric(row.quantityThreshold)
                }
                valueContainer.add(pettyCashField, BorderLayout.CENTER)
            }

            ClientWatchKind.SCAN -> {
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
                ClientWatchKind.HEALTH -> (healthSpinner.value as? Number)?.toDouble() ?: committedQuantity
                ClientWatchKind.PETTY_CASH -> pettyCashField.text.toDoubleOrNull() ?: committedQuantity
                ClientWatchKind.SCAN -> committedQuantity
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
        fill: Int = GridBagConstraints.HORIZONTAL,
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
                    insets = Insets(0, 0, 0, 8)
                },
            )
            add(
                typeCombo,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 0
                    fill = GridBagConstraints.HORIZONTAL
                    weightx = 0.5
                    insets = Insets(0, 0, 0, 16)
                },
            )
            add(
                JLabel("Port"),
                GridBagConstraints().apply {
                    gridx = 2
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 0, 8)
                },
            )
            add(
                portCombo,
                GridBagConstraints().apply {
                    gridx = 3
                    gridy = 0
                    fill = GridBagConstraints.HORIZONTAL
                    weightx = 0.5
                },
            )
        }
    }

    private fun openSelection() {
        val entry = browserController.selectedEntry() ?: return
        if (entry.isDirectory) {
            browserController.openSelectedDirectory()
        }
    }

    private fun submitSelection() {
        val selection = browserController.chooseSelectedFile()
        if (selection == null) {
            browserController.showInlineError("Choose a compiled watch file.")
            return
        }
        val portNumber = portCombo.selectedItem?.toString()?.toIntOrNull()
        if (portNumber == null) {
            browserController.showInlineError("Choose a target port.")
            return
        }
        onInstallSelected(
            RewriteWatchInstallSelection(
                displayedPath = selection.displayedPath,
                file = selection.file,
                typeIndex = typeCombo.selectedIndex.coerceAtLeast(0),
                portNumber = portNumber,
            ),
        )
    }
}

private data class RewriteWatchInstallSelection(
    val displayedPath: String,
    val file: ClientStoredFile,
    val typeIndex: Int,
    val portNumber: Int,
)

private fun allowWatchInstallDirectory(entry: ClientDirectoryEntry): Boolean {
    val rootDirectory = entry.path.count { it == '/' } == 1
    return !(rootDirectory && entry.name in setOf("Store", "Public", "Trash"))
}

private fun watchTypeLabel(type: ClientWatchKind): String = when (type) {
    ClientWatchKind.HEALTH -> WATCH_TYPE_LABELS[0]
    ClientWatchKind.PETTY_CASH -> WATCH_TYPE_LABELS[1]
    ClientWatchKind.SCAN -> WATCH_TYPE_LABELS[2]
}

private fun formatWatchMetric(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}
