package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.mvc.RewriteView
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

internal class RewriteEquipmentManagerWindow(
    controller: Any? = null,
    private val onOpenAuxiliaryWindow: ((JInternalFrame) -> Unit)? = null,
    private val onFocusAuxiliaryWindow: ((JInternalFrame) -> Unit)? = null,
) :
    JInternalFrame("Equipment Manager", true, true, true, true),
    RewriteView<RewriteEquipmentManagerViewModel> {
    internal val tableModel = RewriteEquipmentManagerTableModel()
    internal val table = JTable(tableModel).apply {
        name = "rewrite-equipment-manager-table"
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        autoCreateRowSorter = false
        fillsViewportHeight = true
    }
    internal val selectedSlotLabel = JLabel("No slot selected").apply {
        name = "rewrite-equipment-manager-selected-slot"
    }
    internal val statusLabel = JLabel("Waiting for equipment data...").apply {
        name = "rewrite-equipment-manager-status"
    }
    internal val errorLabel = JLabel(" ").apply {
        name = "rewrite-equipment-manager-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    internal val installButton = JButton("Install/Replace").apply {
        name = "rewrite-equipment-manager-install-button"
    }

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
    }

    override fun render(model: RewriteEquipmentManagerViewModel) {
        tableModel.updateRows(model.rows)
        selectedSlotLabel.text = model.selectedSlotLabel
        statusLabel.text = model.statusText
        errorLabel.text = model.errorText
        table.isEnabled = model.tableEnabled
        installButton.isEnabled = model.installButtonEnabled
    }

    internal fun selectedRow(): RewriteEquipmentManagerRow? {
        val selectedIndex = table.selectedRow
        if (selectedIndex < 0) {
            return null
        }
        return tableModel.rows.getOrNull(selectedIndex)
    }

    internal fun selectedSlotName(): String? = selectedRow()?.slot?.name

    internal fun selectSlot(slotName: String?) {
        if (slotName == null) {
            table.clearSelection()
            return
        }
        val rowIndex = tableModel.rows.indexOfFirst { it.slot.name == slotName }
        if (rowIndex < 0) {
            table.clearSelection()
            return
        }
        table.selectionModel.setSelectionInterval(rowIndex, rowIndex)
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
}

internal class RewriteFirewallManagerWindow(
    controller: Any? = null,
    private val onOpenAuxiliaryWindow: ((JInternalFrame) -> Unit)? = null,
    private val onFocusAuxiliaryWindow: ((JInternalFrame) -> Unit)? = null,
) :
    JInternalFrame("Firewall Manager", true, true, true, true),
    RewriteView<RewriteFirewallManagerViewModel> {
    internal val tableModel = RewriteFirewallManagerTableModel()
    internal val table = JTable(tableModel).apply {
        name = "rewrite-firewall-manager-table"
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        autoCreateRowSorter = false
        fillsViewportHeight = true
    }
    internal val selectedPortLabel = JLabel("No port selected").apply {
        name = "rewrite-firewall-manager-selected-port"
    }
    internal val statusLabel = JLabel("Waiting for firewall data...").apply {
        name = "rewrite-firewall-manager-status"
    }
    internal val errorLabel = JLabel(" ").apply {
        name = "rewrite-firewall-manager-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    internal val installButton = JButton("Install/Replace").apply {
        name = "rewrite-firewall-manager-install-button"
    }

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
    }

    override fun render(model: RewriteFirewallManagerViewModel) {
        tableModel.updateRows(model.rows)
        selectedPortLabel.text = model.selectedPortLabel
        statusLabel.text = model.statusText
        errorLabel.text = model.errorText
        table.isEnabled = model.tableEnabled
        installButton.isEnabled = model.installButtonEnabled
    }

    internal fun selectedRow(): RewriteFirewallManagerRow? {
        val selectedIndex = table.selectedRow
        if (selectedIndex < 0) {
            return null
        }
        return tableModel.rows.getOrNull(selectedIndex)
    }

    internal fun selectedPortNumber(): Int? = selectedRow()?.portNumber

    internal fun selectPort(portNumber: Int?) {
        if (portNumber == null) {
            table.clearSelection()
            return
        }
        val rowIndex = tableModel.rows.indexOfFirst { it.portNumber == portNumber }
        if (rowIndex < 0) {
            table.clearSelection()
            return
        }
        table.selectionModel.setSelectionInterval(rowIndex, rowIndex)
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
}

internal class RewriteEquipmentManagerTableModel : AbstractTableModel() {
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
        if (rows == nextRows) {
            return
        }
        rows.clear()
        rows.addAll(nextRows)
        fireTableDataChanged()
    }
}

internal class RewriteFirewallManagerTableModel : AbstractTableModel() {
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
        if (rows == nextRows) {
            return
        }
        rows.clear()
        rows.addAll(nextRows)
        fireTableDataChanged()
    }
}
