package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.files.RewriteLocalFileChooserWindow
import com.hackwars.rewrite.protocol.ClientEquipmentSlot
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledEquipment
import com.hackwars.rewrite.protocol.ClientInstalledFirewall
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import javax.swing.table.AbstractTableModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val INSTALL_EQUIPMENT_PROMPT: String = "Click Here To Install Equipment"
private const val INSTALL_FIREWALL_PROMPT: String = "Click Here To Install Firewall"

internal data class RewriteEquipmentManagerRow(
    val slot: ClientEquipmentSlot,
    val equipmentLabel: String,
    val maker: String,
    val durability: Int,
    val cpuBoost: Double,
    val memoryBoost: Int,
    val storageBoost: Int,
    val watchCapacityBoost: Int,
    val healCostMultiplier: Double,
    val healModifierDelta: Int,
    val freezeImmune: Boolean,
    val destroyWatchesImmune: Boolean,
    val installedEquipment: ClientInstalledEquipment? = null,
) {
    val cpuBoostDisplay: String = formatSystemMetric(cpuBoost)
    val healCostMultiplierDisplay: String = formatSystemMetric(healCostMultiplier)
}

internal data class RewriteFirewallManagerRow(
    val portNumber: Int,
    val firewallLabel: String,
    val kind: String,
    val maker: String,
    val strength: Int,
    val cpuCost: Double,
    val enabled: Boolean,
    val defaultPort: Boolean,
    val dummy: Boolean,
    val note: String,
    val installedFirewall: ClientInstalledFirewall? = null,
) {
    val cpuCostDisplay: String = formatSystemMetric(cpuCost)
}

internal fun buildEquipmentManagerRows(snapshot: ClientGameSnapshot): List<RewriteEquipmentManagerRow> {
    return ClientEquipmentSlot.values().map { slot ->
        val installed = snapshot.hardware.equipmentSlots[slot.name]
        RewriteEquipmentManagerRow(
            slot = slot,
            equipmentLabel = installed?.name ?: INSTALL_EQUIPMENT_PROMPT,
            maker = installed?.maker?.ifBlank { "-" } ?: "-",
            durability = installed?.durability ?: 0,
            cpuBoost = installed?.cpuBoost ?: 0.0,
            memoryBoost = installed?.memoryBoost ?: 0,
            storageBoost = installed?.storageBoost ?: 0,
            watchCapacityBoost = installed?.watchCapacityBoost ?: 0,
            healCostMultiplier = installed?.healCostMultiplier ?: 1.0,
            healModifierDelta = installed?.healModifierDelta ?: 0,
            freezeImmune = installed?.freezeImmune ?: false,
            destroyWatchesImmune = installed?.destroyWatchesImmune ?: false,
            installedEquipment = installed,
        )
    }
}

internal fun buildFirewallManagerRows(snapshot: ClientGameSnapshot): List<RewriteFirewallManagerRow> {
    return snapshot.ports
        .sortedBy(ClientPortState::number)
        .map { port ->
            val installed = port.installedFirewall
            RewriteFirewallManagerRow(
                portNumber = port.number,
                firewallLabel = installed?.name ?: INSTALL_FIREWALL_PROMPT,
                kind = installed?.kind?.ifBlank { "-" } ?: "-",
                maker = installed?.maker?.ifBlank { "-" } ?: "-",
                strength = installed?.strength ?: 0,
                cpuCost = installed?.cpuCost ?: 0.0,
                enabled = port.enabled,
                defaultPort = port.defaultPort,
                dummy = port.dummy,
                note = port.note,
                installedFirewall = installed,
            )
        }
}

internal fun allowEquipmentManagerFile(
    file: ClientStoredFile,
    slot: ClientEquipmentSlot,
): Boolean {
    return file.kind == ClientStoredFileKind.EQUIPMENT_BINARY &&
        file.compiledBinary?.equipmentSlot == slot
}

internal fun allowFirewallManagerFile(file: ClientStoredFile): Boolean {
    return allowPortManagementFirewallFile(file)
}

internal class RewriteEquipmentManagerWindow(
    private val controller: RewriteRootController,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
) : JInternalFrame("Equipment Manager", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tableModel = RewriteEquipmentManagerTableModel()
    private val table = JTable(tableModel).apply {
        name = "rewrite-equipment-manager-table"
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        autoCreateRowSorter = false
        fillsViewportHeight = true
    }
    private val selectedSlotLabel = JLabel("No slot selected").apply {
        name = "rewrite-equipment-manager-selected-slot"
    }
    private val statusLabel = JLabel("Waiting for equipment data...").apply {
        name = "rewrite-equipment-manager-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-equipment-manager-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val installButton = JButton("Install/Replace").apply {
        name = "rewrite-equipment-manager-install-button"
        addActionListener { openChooser() }
    }

    private var requestInFlight: Boolean = false
    private var chooserWindow: JInternalFrame? = null
    private var chooserSlot: ClientEquipmentSlot? = null

    init {
        name = "rewrite-equipment-manager-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(980, 420)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(buildHeaderPanel(), BorderLayout.NORTH)
            add(JScrollPane(table), BorderLayout.CENTER)
            add(buildFooterPanel(), BorderLayout.SOUTH)
        }
        table.selectionModel.addListSelectionListener { renderSelection() }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                chooserWindow?.dispose()
                windowScope.cancel()
            }
        })
        observeShellState()
        renderState()
    }

    private fun buildHeaderPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(selectedSlotLabel, BorderLayout.WEST)
        }
    }

    private fun buildFooterPanel(): JPanel {
        return JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(installButton)
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
                    val previousSelection = selectedSlot()
                    tableModel.updateRows(snapshot?.let(::buildEquipmentManagerRows).orEmpty())
                    selectSlot(previousSelection ?: tableModel.rows.firstOrNull()?.slot)
                    renderState()
                }
            }
        }
    }

    private fun renderSelection() {
        val selected = selectedRow()
        selectedSlotLabel.text = if (selected == null) {
            "No slot selected"
        } else {
            "Selected Slot: ${selected.slot.name}"
        }
    }

    private fun renderState() {
        renderSelection()
        table.isEnabled = !requestInFlight
        installButton.isEnabled = !requestInFlight && selectedRow() != null
        statusLabel.text = when {
            requestInFlight -> "Installing equipment..."
            tableModel.rows.isEmpty() -> "No equipment slots available."
            statusLabel.text.isBlank() -> " "
            else -> statusLabel.text
        }
    }

    private fun selectedSlot(): ClientEquipmentSlot? {
        return selectedRow()?.slot
    }

    private fun selectedRow(): RewriteEquipmentManagerRow? {
        val selectedIndex = table.selectedRow
        if (selectedIndex < 0) {
            return null
        }
        return tableModel.rows.getOrNull(selectedIndex)
    }

    private fun selectSlot(slot: ClientEquipmentSlot?) {
        if (slot == null) {
            table.clearSelection()
            renderSelection()
            return
        }
        val rowIndex = tableModel.rows.indexOfFirst { it.slot == slot }
        if (rowIndex < 0) {
            table.clearSelection()
            renderSelection()
            return
        }
        table.selectionModel.setSelectionInterval(rowIndex, rowIndex)
    }

    private fun openChooser() {
        val selected = selectedRow() ?: return
        val existing = chooserWindow
        if (existing != null && existing.isDisplayable && !existing.isClosed) {
            if (chooserSlot == selected.slot) {
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
                        chooserSlot = null
                    }
                }
            })
        }
        chooserWindow = chooser
        chooserSlot = selected.slot
        onOpenAuxiliaryWindow(chooser)
    }

    private fun submitInstall(
        slot: ClientEquipmentSlot,
        directoryPath: String,
        file: ClientStoredFile,
    ) {
        val selected = tableModel.rows.firstOrNull { it.slot == slot } ?: return
        if (selected.installedEquipment != null) {
            val confirmed = JOptionPane.showConfirmDialog(
                this,
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
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        statusLabel.text = "Installed ${result.value.equipment.name} in ${slot.name}."
                        errorLabel.text = " "
                    }

                    is RewriteGameCommandResult.Failure -> {
                        statusLabel.text = " "
                        errorLabel.text = result.message
                    }
                }
                renderState()
            }
        }
    }

    private fun clearMessages() {
        statusLabel.text = " "
        errorLabel.text = " "
    }
}

internal class RewriteFirewallManagerWindow(
    private val controller: RewriteRootController,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
) : JInternalFrame("Firewall Manager", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tableModel = RewriteFirewallManagerTableModel()
    private val table = JTable(tableModel).apply {
        name = "rewrite-firewall-manager-table"
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        autoCreateRowSorter = false
        fillsViewportHeight = true
    }
    private val selectedPortLabel = JLabel("No port selected").apply {
        name = "rewrite-firewall-manager-selected-port"
    }
    private val statusLabel = JLabel("Waiting for firewall data...").apply {
        name = "rewrite-firewall-manager-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-firewall-manager-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val installButton = JButton("Install/Replace").apply {
        name = "rewrite-firewall-manager-install-button"
        addActionListener { openChooser() }
    }

    private var requestInFlight: Boolean = false
    private var chooserWindow: JInternalFrame? = null

    init {
        name = "rewrite-firewall-manager-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(920, 420)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(buildHeaderPanel(), BorderLayout.NORTH)
            add(JScrollPane(table), BorderLayout.CENTER)
            add(buildFooterPanel(), BorderLayout.SOUTH)
        }
        table.selectionModel.addListSelectionListener { renderSelection() }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                chooserWindow?.dispose()
                windowScope.cancel()
            }
        })
        observeShellState()
        renderState()
    }

    private fun buildHeaderPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(selectedPortLabel, BorderLayout.WEST)
        }
    }

    private fun buildFooterPanel(): JPanel {
        return JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(installButton)
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
                    val previousSelection = selectedPortNumber()
                    tableModel.updateRows(snapshot?.let(::buildFirewallManagerRows).orEmpty())
                    selectPort(previousSelection ?: tableModel.rows.firstOrNull()?.portNumber)
                    renderState()
                }
            }
        }
    }

    private fun renderSelection() {
        val selected = selectedRow()
        selectedPortLabel.text = if (selected == null) {
            "No port selected"
        } else {
            "Selected Port: ${selected.portNumber}"
        }
    }

    private fun renderState() {
        renderSelection()
        table.isEnabled = !requestInFlight
        installButton.isEnabled = !requestInFlight && selectedRow() != null
        statusLabel.text = when {
            requestInFlight -> "Installing firewall..."
            tableModel.rows.isEmpty() -> "No firewall ports available."
            statusLabel.text.isBlank() -> " "
            else -> statusLabel.text
        }
    }

    private fun selectedPortNumber(): Int? {
        return selectedRow()?.portNumber
    }

    private fun selectedRow(): RewriteFirewallManagerRow? {
        val selectedIndex = table.selectedRow
        if (selectedIndex < 0) {
            return null
        }
        return tableModel.rows.getOrNull(selectedIndex)
    }

    private fun selectPort(portNumber: Int?) {
        if (portNumber == null) {
            table.clearSelection()
            renderSelection()
            return
        }
        val rowIndex = tableModel.rows.indexOfFirst { it.portNumber == portNumber }
        if (rowIndex < 0) {
            table.clearSelection()
            renderSelection()
            return
        }
        table.selectionModel.setSelectionInterval(rowIndex, rowIndex)
    }

    private fun openChooser() {
        val selected = selectedRow() ?: return
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
        val selected = tableModel.rows.firstOrNull { it.portNumber == portNumber } ?: return
        if (selected.installedFirewall != null) {
            val confirmed = JOptionPane.showConfirmDialog(
                this,
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
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        statusLabel.text = "Installed ${result.value.installedFirewall.name} on port $portNumber."
                        errorLabel.text = " "
                    }

                    is RewriteGameCommandResult.Failure -> {
                        statusLabel.text = " "
                        errorLabel.text = result.message
                    }
                }
                renderState()
            }
        }
    }

    private fun clearMessages() {
        statusLabel.text = " "
        errorLabel.text = " "
    }
}

private class RewriteEquipmentManagerTableModel : AbstractTableModel() {
    val rows = mutableListOf<RewriteEquipmentManagerRow>()
    private val columns = listOf(
        "Slot",
        "Equipment",
        "Maker",
        "Durability",
        "CPU Boost",
        "Memory Boost",
        "Storage Boost",
        "Watch Cap",
        "Heal Cost",
        "Heal Delta",
        "Freeze Immune",
        "Destroy Watches Immune",
    )

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        3, 5, 6, 7, 9 -> Int::class.java
        10, 11 -> Boolean::class.java
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.slot.name
            1 -> row.equipmentLabel
            2 -> row.maker
            3 -> row.durability
            4 -> row.cpuBoostDisplay
            5 -> row.memoryBoost
            6 -> row.storageBoost
            7 -> row.watchCapacityBoost
            8 -> row.healCostMultiplierDisplay
            9 -> row.healModifierDelta
            10 -> row.freezeImmune
            11 -> row.destroyWatchesImmune
            else -> ""
        }
    }

    fun updateRows(nextRows: List<RewriteEquipmentManagerRow>) {
        rows.clear()
        rows.addAll(nextRows)
        fireTableDataChanged()
    }
}

private class RewriteFirewallManagerTableModel : AbstractTableModel() {
    val rows = mutableListOf<RewriteFirewallManagerRow>()
    private val columns = listOf(
        "Port",
        "Firewall",
        "Kind",
        "Maker",
        "Strength",
        "CPU Cost",
        "Enabled",
        "Default",
        "Dummy",
        "Note",
    )

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        0, 4 -> Int::class.java
        6, 7, 8 -> Boolean::class.java
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.portNumber
            1 -> row.firewallLabel
            2 -> row.kind
            3 -> row.maker
            4 -> row.strength
            5 -> row.cpuCostDisplay
            6 -> row.enabled
            7 -> row.defaultPort
            8 -> row.dummy
            9 -> row.note
            else -> ""
        }
    }

    fun updateRows(nextRows: List<RewriteFirewallManagerRow>) {
        rows.clear()
        rows.addAll(nextRows)
        fireTableDataChanged()
    }
}

private fun formatSystemMetric(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}
