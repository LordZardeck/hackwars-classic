package com.hackwars.rewrite.client.network

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.economy.RewriteSegmentedIpInput
import com.hackwars.rewrite.protocol.ClientDefaultPortVisibility
import com.hackwars.rewrite.protocol.ClientFirewallView
import com.hackwars.rewrite.protocol.ClientNetworkState
import com.hackwars.rewrite.protocol.ClientNpcDirectoryEntry
import com.hackwars.rewrite.protocol.ClientScannedPortView
import com.hackwars.rewrite.protocol.ClientScanResponse
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
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

private val KNOWN_NETWORK_MAP_NODES: List<RewriteNetworkMapNodeSpec> = listOf(
    RewriteNetworkMapNodeSpec("UGOPNet", 1, 0, Color(0x1E, 0x5A, 0xD7)),
    RewriteNetworkMapNodeSpec("ProgNet", 0, 1, Color(0x16, 0x7A, 0x52)),
    RewriteNetworkMapNodeSpec("JuniperPenetentiary", 2, 1, Color(0x89, 0x24, 0x24)),
)

internal data class RewriteNetworkDirectoryView(
    val currentNetworkName: String,
    val allowedNetworks: List<String>,
    val regularNpcs: List<String>,
    val questNpcs: List<String>,
    val miningNpcs: List<String>,
    val storeNpcs: List<String>,
)

internal fun buildNetworkDirectoryView(state: ClientNetworkState): RewriteNetworkDirectoryView {
    return RewriteNetworkDirectoryView(
        currentNetworkName = state.currentNetworkName.ifBlank { "Unknown" },
        allowedNetworks = state.allowedNetworks.toList().sorted(),
        regularNpcs = state.regularNpcs.map(::formatNpcDirectoryEntry),
        questNpcs = state.questNpcs.map(::formatNpcDirectoryEntry),
        miningNpcs = state.miningNpcs.map(::formatNpcDirectoryEntry),
        storeNpcs = state.storeNpcs.map(::formatNpcDirectoryEntry),
    )
}

internal data class RewriteNetworkMapNodeSpec(
    val networkName: String,
    val gridx: Int,
    val gridy: Int,
    val baseColor: Color,
)

internal fun buildNetworkMapNodes(state: ClientNetworkState?): List<RewriteNetworkMapNodeSpec> {
    val names = linkedSetOf<String>()
    KNOWN_NETWORK_MAP_NODES.forEach { names += it.networkName }
    state?.currentNetworkName?.takeIf(String::isNotBlank)?.let(names::add)
    state?.allowedNetworks.orEmpty().sorted().forEach(names::add)

    val knownByName = KNOWN_NETWORK_MAP_NODES.associateBy { it.networkName }
    val dynamicNodes = mutableListOf<RewriteNetworkMapNodeSpec>()
    var fallbackIndex = 0
    names.forEach { networkName ->
        val known = knownByName[networkName]
        if (known != null) {
            dynamicNodes += known
        } else {
            dynamicNodes += RewriteNetworkMapNodeSpec(
                networkName = networkName,
                gridx = fallbackIndex % 3,
                gridy = 2 + (fallbackIndex / 3),
                baseColor = Color(0x46, 0x46, 0x46),
            )
            fallbackIndex += 1
        }
    }
    return dynamicNodes
}

internal data class RewriteScannedPortRow(
    val number: Int,
    val type: String,
    val firewallLabel: String,
    val defaultVisibility: String,
    val enabled: Boolean,
    val dummy: Boolean,
    val attacking: Boolean,
    val cpuDisplay: String,
    val healthDisplay: String,
    val note: String,
)

internal fun buildPortScanRows(response: ClientScanResponse): List<RewriteScannedPortRow> {
    return response.ports.map { port ->
        RewriteScannedPortRow(
            number = port.number,
            type = port.type,
            firewallLabel = formatFirewallLabel(port.firewall),
            defaultVisibility = formatDefaultPortVisibility(port.defaultVisibility),
            enabled = port.enabled,
            dummy = port.dummy,
            attacking = port.attacking,
            cpuDisplay = "${formatScanMetric(port.cpuCost)}/${formatScanMetric(port.maxCpuCost)}",
            healthDisplay = formatScanMetric(port.health),
            note = port.note,
        )
    }
}

internal class RewriteNetworkWindow(
    controller: RewriteRootController,
) : JInternalFrame("Network", true, true, true, true) {
    private val view = RewriteNetworkWindowView()
    private val windowController = RewriteNetworkWindowController(
        controller = controller,
        view = view,
    )

    init {
        name = "rewrite-shell-window-network"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(980, 600)
        contentPane = view
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                windowController.dispose()
            }
        })
    }
}

internal class RewriteNetworkWindowView : JPanel(BorderLayout(0, 8)) {
    private val currentNetworkLabel = JLabel("Unknown").apply {
        name = "rewrite-network-current-name"
    }
    private val allowedNetworksLabel = JLabel("None").apply {
        name = "rewrite-network-allowed-networks"
    }
    private val statusLabel = JLabel("Waiting for network data...").apply {
        name = "rewrite-network-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-network-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val regularNpcList = JList<String>().apply { name = "rewrite-network-regular-list" }
    private val questNpcList = JList<String>().apply { name = "rewrite-network-quest-list" }
    private val miningNpcList = JList<String>().apply { name = "rewrite-network-mining-list" }
    private val storeNpcList = JList<String>().apply { name = "rewrite-network-store-list" }
    private val mapPanel = JPanel(GridBagLayout()).apply {
        name = "rewrite-network-map-panel"
        background = Color(0x11, 0x15, 0x1B)
        border = BorderFactory.createEmptyBorder(18, 18, 18, 18)
    }
    private val tabs = JTabbedPane().apply {
        name = "rewrite-network-tabs"
    }

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        add(buildHeaderPanel(), BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
        add(buildFooterPanel(), BorderLayout.SOUTH)
        tabs.addTab("Network", buildDirectoryPanel())
        tabs.addTab("Map", JScrollPane(mapPanel).apply {
            border = BorderFactory.createLineBorder(Color(0x33, 0x33, 0x33))
        })
    }

    fun renderDirectory(viewModel: RewriteNetworkDirectoryView?) {
        currentNetworkLabel.text = viewModel?.currentNetworkName ?: "Unknown"
        allowedNetworksLabel.text = viewModel?.allowedNetworks?.joinToString(", ").takeUnless { it.isNullOrBlank() } ?: "None"
        regularNpcList.setListData(viewModel?.regularNpcs?.toTypedArray() ?: emptyArray<String>())
        questNpcList.setListData(viewModel?.questNpcs?.toTypedArray() ?: emptyArray<String>())
        miningNpcList.setListData(viewModel?.miningNpcs?.toTypedArray() ?: emptyArray<String>())
        storeNpcList.setListData(viewModel?.storeNpcs?.toTypedArray() ?: emptyArray<String>())
    }

    fun renderMapNodes(
        state: ClientNetworkState?,
        requestInFlight: Boolean,
    ): Map<String, JButton> {
        val nodeSpecs = buildNetworkMapNodes(state)
        mapPanel.removeAll()
        val buttons = nodeSpecs.associate { spec ->
            val button = JButton(spec.networkName).apply {
                name = "rewrite-network-map-node-${sanitizeNetworkName(spec.networkName)}"
                background = spec.baseColor
                foreground = Color.WHITE
                isFocusPainted = false
                isEnabled = !requestInFlight && state != null
                border = if (spec.networkName == state?.currentNetworkName) {
                    BorderFactory.createLineBorder(Color(0xFF, 0xEE, 0x88), 3)
                } else {
                    BorderFactory.createLineBorder(Color(0x22, 0x22, 0x22), 1)
                }
            }
            mapPanel.add(
                button,
                GridBagConstraints().apply {
                    gridx = spec.gridx
                    gridy = spec.gridy
                    weightx = 1.0
                    weighty = 1.0
                    fill = GridBagConstraints.NONE
                    anchor = GridBagConstraints.CENTER
                    insets = Insets(12, 12, 12, 12)
                },
            )
            spec.networkName to button
        }
        mapPanel.revalidate()
        mapPanel.repaint()
        return buttons
    }

    fun setStatus(text: String) {
        statusLabel.text = text
    }

    fun currentStatusText(): String = statusLabel.text

    fun setError(text: String) {
        errorLabel.text = text
    }

    private fun buildHeaderPanel(): JPanel {
        return JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(JLabel("Current Network:"))
                    add(currentNetworkLabel)
                },
                BorderLayout.WEST,
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(JLabel("Allowed Networks:"))
                    add(allowedNetworksLabel)
                },
                BorderLayout.EAST,
            )
        }
    }

    private fun buildDirectoryPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            val groups = listOf(
                "Regular NPCs" to regularNpcList,
                "Quest NPCs" to questNpcList,
                "Mining NPCs" to miningNpcList,
                "Store NPCs" to storeNpcList,
            )
            groups.forEachIndexed { index, (title, list) ->
                add(
                    buildNpcGroup(title, list),
                    GridBagConstraints().apply {
                        gridx = index % 2
                        gridy = index / 2
                        weightx = 0.5
                        weighty = 0.5
                        fill = GridBagConstraints.BOTH
                        insets = Insets(6, 6, 6, 6)
                    },
                )
            }
        }
    }

    private fun buildNpcGroup(
        title: String,
        list: JList<String>,
    ): JPanel {
        return JPanel(BorderLayout(0, 6)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0xCC, 0xCC, 0xCC)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8),
            )
            add(JLabel(title), BorderLayout.NORTH)
            add(JScrollPane(list), BorderLayout.CENTER)
        }
    }

    private fun buildFooterPanel(): JPanel {
        return JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(statusLabel, BorderLayout.NORTH)
            add(errorLabel, BorderLayout.SOUTH)
        }
    }
}

internal class RewriteNetworkWindowController(
    private val controller: RewriteRootController,
    private val view: RewriteNetworkWindowView,
) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var latestState: ClientNetworkState? = controller.gameNetworkState()
    private var requestInFlight: Boolean = false
    private var mapButtonsByNetwork: Map<String, JButton> = emptyMap()

    init {
        observeNetworkState()
        renderState(latestState)
    }

    fun dispose() {
        windowScope.cancel()
    }

    private fun observeNetworkState() {
        windowScope.launch {
            controller.gameNetworkStateSelector().collect { state ->
                SwingUtilities.invokeLater {
                    latestState = state
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: ClientNetworkState?) {
        val viewModel = state?.let(::buildNetworkDirectoryView)
        view.renderDirectory(viewModel)
        mapButtonsByNetwork = view.renderMapNodes(state, requestInFlight)
        bindMapButtons()
        if (!requestInFlight && state != null && view.currentStatusText() == "Waiting for network data...") {
            view.setStatus("Viewing ${viewModel?.currentNetworkName ?: "Unknown"}.")
        }
    }

    private fun bindMapButtons() {
        mapButtonsByNetwork.forEach { (networkName, button) ->
            button.actionListeners.forEach(button::removeActionListener)
            button.addActionListener { submitNetworkSwitch(networkName) }
        }
    }

    private fun submitNetworkSwitch(targetNetwork: String) {
        requestInFlight = true
        view.setStatus("Switching to $targetNetwork...")
        view.setError(" ")
        renderState(latestState)
        windowScope.launch {
            val result = controller.requestChangeNetwork(targetNetwork)
            SwingUtilities.invokeLater {
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        if (result.value.accepted) {
                            view.setStatus(result.value.message)
                            view.setError(" ")
                        } else {
                            view.setStatus("Viewing ${latestState?.currentNetworkName ?: "Unknown"}.")
                            view.setError(result.value.message)
                        }
                    }

                    is RewriteGameCommandResult.Failure -> {
                        view.setStatus("Viewing ${latestState?.currentNetworkName ?: "Unknown"}.")
                        view.setError(result.message)
                    }
                }
                renderState(latestState)
            }
        }
    }
}

internal class RewritePortScanWindow(
    controller: RewriteRootController,
) : JInternalFrame("Port Scan", true, true, true, true) {
    private val view = RewritePortScanWindowView()
    private val windowController = RewritePortScanWindowController(
        controller = controller,
        view = view,
    )

    init {
        name = "rewrite-shell-window-port_scan"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(920, 520)
        contentPane = view
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                windowController.dispose()
            }
        })
    }
}

internal class RewritePortScanWindowView : JPanel(BorderLayout(0, 8)) {
    val ipInput = RewriteSegmentedIpInput().apply {
        name = "rewrite-port-scan-ip-input"
    }
    val scanButton = JButton("Scan").apply {
        name = "rewrite-port-scan-scan-button"
    }
    private val tableModel = RewritePortScanTableModel()
    val table = JTable(tableModel).apply {
        name = "rewrite-port-scan-table"
        fillsViewportHeight = true
        autoCreateRowSorter = false
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }
    private val statusLabel = JLabel("Enter a target IP to scan.").apply {
        name = "rewrite-port-scan-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-port-scan-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        add(buildHeaderPanel(), BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)
        add(buildFooterPanel(), BorderLayout.SOUTH)
    }

    fun setRequestInFlight(requestInFlight: Boolean) {
        scanButton.isEnabled = !requestInFlight
        ipInput.isEnabled = !requestInFlight
    }

    fun setStatus(text: String) {
        statusLabel.text = text
    }

    fun setError(text: String) {
        errorLabel.text = text
    }

    fun updateRows(rows: List<RewriteScannedPortRow>) {
        tableModel.updateRows(rows)
    }

    fun currentStatusText(): String = statusLabel.text

    private fun buildHeaderPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(JLabel("Target IP:"))
            add(ipInput)
            add(scanButton)
        }
    }

    private fun buildFooterPanel(): JPanel {
        return JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(statusLabel, BorderLayout.NORTH)
            add(errorLabel, BorderLayout.SOUTH)
        }
    }
}

internal class RewritePortScanWindowController(
    private val controller: RewriteRootController,
    private val view: RewritePortScanWindowView,
) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var requestInFlight: Boolean = false

    init {
        view.scanButton.addActionListener { submitScan() }
        renderState()
    }

    fun dispose() {
        windowScope.cancel()
    }

    private fun renderState() {
        view.setRequestInFlight(requestInFlight)
    }

    private fun submitScan() {
        val targetIp = view.ipInput.valueOrNull()
        if (targetIp == null) {
            view.setError("Enter a complete target IP.")
            return
        }
        requestInFlight = true
        view.setStatus("Scanning $targetIp...")
        view.setError(" ")
        renderState()
        windowScope.launch {
            val result = controller.requestScan(targetIp)
            SwingUtilities.invokeLater {
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> renderScanResponse(targetIp, result.value)
                    is RewriteGameCommandResult.Failure -> {
                        view.updateRows(emptyList())
                        view.setError(result.message)
                    }
                }
                renderState()
            }
        }
    }

    private fun renderScanResponse(
        targetIp: String,
        response: ClientScanResponse,
    ) {
        if (!response.accepted) {
            view.updateRows(emptyList())
            view.setStatus("Enter a target IP to scan.")
            view.setError(response.failureMessage ?: "Scan failed.")
            return
        }
        view.updateRows(buildPortScanRows(response))
        view.setStatus("Scanned $targetIp.")
        view.setError(" ")
    }
}

private class RewritePortScanTableModel : AbstractTableModel() {
    private val columns = listOf(
        "Port",
        "Type",
        "Firewall",
        "Default",
        "Enabled",
        "Dummy",
        "Attacking",
        "CPU",
        "Health",
        "Note",
    )

    var rows: List<RewriteScannedPortRow> = emptyList()
        private set

    fun updateRows(nextRows: List<RewriteScannedPortRow>) {
        rows = nextRows
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.number
            1 -> row.type
            2 -> row.firewallLabel
            3 -> row.defaultVisibility
            4 -> row.enabled
            5 -> row.dummy
            6 -> row.attacking
            7 -> row.cpuDisplay
            8 -> row.healthDisplay
            9 -> row.note
            else -> ""
        }
    }
}

private fun formatNpcDirectoryEntry(entry: ClientNpcDirectoryEntry): String {
    val title = entry.title.takeIf(String::isNotBlank)?.let { " - $it" }.orEmpty()
    val commodity = entry.commodity?.takeIf(String::isNotBlank)?.let { " [$it]" }.orEmpty()
    return "${entry.displayName}$title$commodity"
}

private fun formatFirewallLabel(firewall: ClientFirewallView?): String {
    return firewall?.name?.takeIf(String::isNotBlank) ?: "None"
}

private fun formatDefaultPortVisibility(visibility: ClientDefaultPortVisibility): String = when (visibility) {
    ClientDefaultPortVisibility.UNKNOWN -> "Unknown"
    ClientDefaultPortVisibility.NO -> "No"
    ClientDefaultPortVisibility.YES -> "Yes"
}

private fun formatScanMetric(value: Double): String {
    val rounded = value.toInt()
    return if (value == rounded.toDouble()) {
        rounded.toString()
    } else {
        "%.1f".format(value)
    }
}

private fun sanitizeNetworkName(networkName: String): String {
    return networkName
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}
