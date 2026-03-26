package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.shell.RewritePreferredPortWindow
import com.hackwars.rewrite.client.mvc.RewriteView
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

internal class RewritePortManagementWindow :
    JInternalFrame("Port Management", true, true, true, true),
    RewritePreferredPortWindow,
    RewriteView<RewritePortManagementViewModel> {
    internal val tableModel = RewritePortManagementTableModel()
    internal val table = JTable(tableModel).apply {
        name = "rewrite-port-management-table"
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        autoCreateRowSorter = false
        fillsViewportHeight = true
    }
    internal val statusLabel = JLabel("Waiting for port data...").apply {
        name = "rewrite-port-management-status"
    }
    internal val errorLabel = JLabel(" ").apply {
        name = "rewrite-port-management-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    internal val selectedPortLabel = JLabel("No port selected").apply {
        name = "rewrite-port-management-selected-port"
    }
    internal val enabledToggle = JCheckBox("Enabled").apply {
        name = "rewrite-port-management-enabled-toggle"
        isEnabled = false
    }
    internal val defaultToggle = JCheckBox("Default").apply {
        name = "rewrite-port-management-default-toggle"
        isEnabled = false
    }
    internal val dummyToggle = JCheckBox("Dummy").apply {
        name = "rewrite-port-management-dummy-toggle"
        isEnabled = false
    }
    internal val noteField = JTextField().apply {
        name = "rewrite-port-management-note-field"
        isEditable = false
        isEnabled = false
        columns = 18
    }
    internal val healButton = JButton("Heal").apply {
        name = "rewrite-port-management-heal-button"
    }
    internal val installProgramButton = JButton("Install/Replace Program").apply {
        name = "rewrite-port-management-install-program-button"
    }
    internal val installFirewallButton = JButton("Install/Replace Firewall").apply {
        name = "rewrite-port-management-install-firewall-button"
    }

    private var controllerDriver: RewritePortManagementDriver? = null

    init {
        name = "rewrite-port-management-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(900, 460)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(buildHeaderPanel(), BorderLayout.NORTH)
            add(JScrollPane(table), BorderLayout.CENTER)
            add(buildFooterPanel(), BorderLayout.SOUTH)
        }
    }

    internal fun bindController(driver: RewritePortManagementDriver) {
        controllerDriver = driver
    }

    override fun applyPreferredPort(preferredPort: Int?) {
        controllerDriver?.applyPreferredPort(preferredPort)
    }

    override fun render(model: RewritePortManagementViewModel) {
        tableModel.updateRows(model.rows)
        selectedPortLabel.text = model.selectedPortLabel
        enabledToggle.isSelected = model.selectedEnabled
        defaultToggle.isSelected = model.selectedDefault
        dummyToggle.isSelected = model.selectedDummy
        noteField.text = model.selectedNote
        statusLabel.text = model.statusText
        errorLabel.text = model.errorText
        table.isEnabled = model.tableEnabled
        healButton.isEnabled = model.healButtonEnabled
        installProgramButton.isEnabled = model.installProgramButtonEnabled
        installFirewallButton.isEnabled = model.installFirewallButtonEnabled
    }

    internal fun selectedPortNumber(): Int? {
        val selectedIndex = table.selectedRow
        if (selectedIndex < 0) {
            return null
        }
        return tableModel.rows.getOrNull(selectedIndex)?.number
    }

    internal fun selectPort(portNumber: Int?) {
        if (portNumber == null) {
            table.clearSelection()
            return
        }
        val rowIndex = tableModel.rows.indexOfFirst { it.number == portNumber }
        if (rowIndex < 0) {
            table.clearSelection()
            return
        }
        table.selectionModel.setSelectionInterval(rowIndex, rowIndex)
    }

    private fun buildHeaderPanel(): JPanel {
        return JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(selectedPortLabel, BorderLayout.WEST)
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(enabledToggle)
                    add(defaultToggle)
                    add(dummyToggle)
                    add(JLabel("Note:"))
                    add(noteField)
                },
                BorderLayout.EAST,
            )
        }
    }

    private fun buildFooterPanel(): JPanel {
        return JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(healButton)
                    add(installProgramButton)
                    add(installFirewallButton)
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

internal class RewritePortManagementTableModel : AbstractTableModel() {
    val rows = mutableListOf<RewritePortManagementRow>()
    private val columns = listOf(
        "Port",
        "Program",
        "Firewall",
        "CPU / Max",
        "Health",
        "Heals Left",
        "Default",
        "Dummy",
        "Enabled",
        "Note",
    )

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        0, 5 -> Int::class.java
        6, 7, 8 -> Boolean::class.java
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.number
            1 -> row.programLabel
            2 -> row.firewallLabel
            3 -> row.cpuDisplay
            4 -> row.healthDisplay
            5 -> row.healsLeft
            6 -> row.defaultPort
            7 -> row.dummy
            8 -> row.enabled
            9 -> row.note
            else -> ""
        }
    }

    fun updateRows(nextRows: List<RewritePortManagementRow>) {
        if (rows == nextRows) {
            return
        }
        rows.clear()
        rows.addAll(nextRows)
        fireTableDataChanged()
    }
}
