package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.files.RewriteLocalFileChooserWindow
import com.hackwars.rewrite.client.shell.RewritePreferredPortWindow
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledApplication
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
import javax.swing.JCheckBox
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import javax.swing.table.AbstractTableModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

private const val MAX_PORT_HEALS: Int = 10
private const val INSTALL_PROGRAM_PROMPT: String = "Click Here To Install Script"
private const val INSTALL_FIREWALL_PROMPT: String = "Click Here To Install Firewall"

internal data class RewritePortManagementRow(
    val number: Int,
    val programLabel: String,
    val firewallLabel: String,
    val currentCpuCost: Double,
    val maxCpuCost: Double,
    val health: Double,
    val healsLeft: Int,
    val defaultPort: Boolean,
    val dummy: Boolean,
    val enabled: Boolean,
    val note: String,
    val installedApplication: ClientInstalledApplication? = null,
    val installedFirewall: ClientInstalledFirewall? = null,
) {
    val cpuDisplay: String = "${formatPortMetric(currentCpuCost)}/${formatPortMetric(maxCpuCost)}"
    val healthDisplay: String = formatPortMetric(health)
}

internal fun buildPortManagementRows(snapshot: ClientGameSnapshot): List<RewritePortManagementRow> {
    return snapshot.ports
        .sortedBy(ClientPortState::number)
        .map { port ->
            val program = port.installedApplication
            val firewall = port.installedFirewall
            RewritePortManagementRow(
                number = port.number,
                programLabel = program?.name ?: INSTALL_PROGRAM_PROMPT,
                firewallLabel = firewall?.name ?: INSTALL_FIREWALL_PROMPT,
                currentCpuCost = (program?.cpuCost ?: 0.0) + (firewall?.cpuCost ?: 0.0),
                maxCpuCost = port.maxCpuCost,
                health = port.health,
                healsLeft = (MAX_PORT_HEALS - port.healCount).coerceAtLeast(0),
                defaultPort = port.defaultPort,
                dummy = port.dummy,
                enabled = port.enabled,
                note = port.note,
                installedApplication = program,
                installedFirewall = firewall,
            )
        }
}

internal fun allowPortManagementApplicationFile(file: ClientStoredFile): Boolean {
    return file.kind == ClientStoredFileKind.APPLICATION_BINARY && file.compiledBinary?.applicationKind != null
}

internal fun allowPortManagementFirewallFile(file: ClientStoredFile): Boolean {
    return file.kind == ClientStoredFileKind.FIREWALL_BINARY && file.compiledBinary != null
}

internal class RewritePortManagementWindow(
    private val controller: RewriteRootController,
    preferredPort: Int? = null,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
) : JInternalFrame("Port Management", true, true, true, true), RewritePreferredPortWindow {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tableModel = RewritePortManagementTableModel()
    private val table = JTable(tableModel).apply {
        name = "rewrite-port-management-table"
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        autoCreateRowSorter = false
        fillsViewportHeight = true
    }
    private val statusLabel = JLabel("Waiting for port data...").apply {
        name = "rewrite-port-management-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-port-management-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val selectedPortLabel = JLabel("No port selected").apply {
        name = "rewrite-port-management-selected-port"
    }
    private val enabledToggle = JCheckBox("Enabled").apply {
        name = "rewrite-port-management-enabled-toggle"
        isEnabled = false
    }
    private val defaultToggle = JCheckBox("Default").apply {
        name = "rewrite-port-management-default-toggle"
        isEnabled = false
    }
    private val dummyToggle = JCheckBox("Dummy").apply {
        name = "rewrite-port-management-dummy-toggle"
        isEnabled = false
    }
    private val noteField = JTextField().apply {
        name = "rewrite-port-management-note-field"
        isEditable = false
        isEnabled = false
        columns = 18
    }
    private val healButton = JButton("Heal").apply {
        name = "rewrite-port-management-heal-button"
        addActionListener { submitHeal() }
    }
    private val installProgramButton = JButton("Install/Replace Program").apply {
        name = "rewrite-port-management-install-program-button"
        addActionListener { openProgramChooser() }
    }
    private val installFirewallButton = JButton("Install/Replace Firewall").apply {
        name = "rewrite-port-management-install-firewall-button"
        addActionListener { openFirewallChooser() }
    }

    private var requestInFlight: Boolean = false
    private var pendingPreferredPort: Int? = preferredPort
    private var applicationChooserWindow: JInternalFrame? = null
    private var firewallChooserWindow: JInternalFrame? = null

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
        table.selectionModel.addListSelectionListener {
            renderSelection()
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                applicationChooserWindow?.dispose()
                firewallChooserWindow?.dispose()
                windowScope.cancel()
            }
        })
        observeShellState()
        renderState()
    }

    override fun applyPreferredPort(preferredPort: Int?) {
        pendingPreferredPort = preferredPort
        selectPort(preferredPort)
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

    private fun observeShellState() {
        windowScope.launch {
            controller.gameShellStateSelector().collect { snapshot ->
                SwingUtilities.invokeLater {
                    if (isClosed || !isDisplayable) {
                        return@invokeLater
                    }
                    val previousSelection = selectedPortNumber()
                    tableModel.updateRows(snapshot?.let(::buildPortManagementRows).orEmpty())
                    val nextSelection = when {
                        pendingPreferredPort != null && tableModel.rows.any { it.number == pendingPreferredPort } -> {
                            pendingPreferredPort.also { pendingPreferredPort = null }
                        }

                        previousSelection != null && tableModel.rows.any { it.number == previousSelection } -> previousSelection
                        else -> tableModel.rows.firstOrNull()?.number
                    }
                    selectPort(nextSelection)
                    renderState()
                }
            }
        }
    }

    private fun renderState() {
        renderSelection()
        table.isEnabled = !requestInFlight
        val selected = selectedRow()
        healButton.isEnabled = !requestInFlight && selected != null && selected.enabled && !selected.dummy
        installProgramButton.isEnabled = !requestInFlight && selected != null
        installFirewallButton.isEnabled = !requestInFlight && selected != null
        if (tableModel.rows.isEmpty() && !requestInFlight) {
            statusLabel.text = "No ports available."
        } else if (!requestInFlight && statusLabel.text.isBlank()) {
            statusLabel.text = " "
        }
    }

    private fun renderSelection() {
        val selected = selectedRow()
        if (selected == null) {
            selectedPortLabel.text = "No port selected"
            enabledToggle.isSelected = false
            defaultToggle.isSelected = false
            dummyToggle.isSelected = false
            noteField.text = ""
            return
        }
        selectedPortLabel.text = "Selected Port: ${selected.number}"
        enabledToggle.isSelected = selected.enabled
        defaultToggle.isSelected = selected.defaultPort
        dummyToggle.isSelected = selected.dummy
        noteField.text = selected.note
    }

    private fun selectedPortNumber(): Int? {
        val selectedIndex = table.selectedRow
        if (selectedIndex < 0) {
            return null
        }
        return tableModel.rows.getOrNull(selectedIndex)?.number
    }

    private fun selectedRow(): RewritePortManagementRow? {
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
        val rowIndex = tableModel.rows.indexOfFirst { it.number == portNumber }
        if (rowIndex < 0) {
            table.clearSelection()
            renderSelection()
            return
        }
        table.selectionModel.setSelectionInterval(rowIndex, rowIndex)
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
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        if (result.value.accepted) {
                            statusLabel.text = "Healed port ${selected.number}."
                            errorLabel.text = " "
                        } else {
                            statusLabel.text = " "
                            errorLabel.text = result.value.message
                        }
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

    private fun submitInstallApplication(
        portNumber: Int,
        directoryPath: String,
        file: ClientStoredFile,
    ) {
        val selected = tableModel.rows.firstOrNull { it.number == portNumber } ?: return
        if (selected.installedApplication != null) {
            val confirmed = JOptionPane.showConfirmDialog(
                this,
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
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        statusLabel.text = "Installed ${result.value.installedApplication.name} on port $portNumber."
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

    private fun submitInstallFirewall(
        portNumber: Int,
        directoryPath: String,
        file: ClientStoredFile,
    ) {
        val selected = tableModel.rows.firstOrNull { it.number == portNumber } ?: return
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

private class RewritePortManagementTableModel : AbstractTableModel() {
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
        rows.clear()
        rows.addAll(nextRows)
        fireTableDataChanged()
    }
}

private fun formatPortMetric(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}
