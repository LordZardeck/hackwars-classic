package com.hackwars.rewrite.client.economy

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.shell.RewritePreferredPortWindow
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientPortState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JFormattedTextField
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

internal data class RewriteBankPortOption(
    val portNumber: Int,
    val label: String,
) {
    override fun toString(): String = label
}

internal abstract class RewriteBankingWindow(
    protected val controller: RewriteRootController,
    private val command: RewriteShellCommand,
    title: String,
    preferredPort: Int? = null,
) : JInternalFrame(title, false, false, true, true), RewritePreferredPortWindow {
    protected val portComboBox = JComboBox<RewriteBankPortOption>().apply {
        name = "rewrite-economy-port-combo"
    }
    protected val amountField = JFormattedTextField(decimalFormatter()).apply {
        name = "rewrite-economy-amount-field"
        horizontalAlignment = JTextField.RIGHT
        columns = 8
        value = 0.0
    }
    protected val balanceValueLabel = JLabel(currencyFormatter.format(0.0)).apply {
        name = "rewrite-economy-balance-value"
    }
    protected val errorLabel = JLabel(" ").apply {
        name = "rewrite-economy-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }

    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var latestShellState: ClientGameSnapshot? = controller.gameShellState()
    private var preferredPortNumber: Int? = preferredPort
    private var selectedPortNumber: Int? = null
    private var requestInFlight: Boolean = false
    private var availableBankPorts: List<RewriteBankPortOption> = emptyList()

    init {
        name = "rewrite-economy-window-${command.stableId}"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        portComboBox.addActionListener {
            val option = portComboBox.selectedItem as? RewriteBankPortOption ?: return@addActionListener
            selectedPortNumber = option.portNumber
            preferredPortNumber = null
            updateActionState()
        }
        amountField.addActionListener { onPrimarySubmit() }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                windowScope.cancel()
            }
        })
    }

    override fun applyPreferredPort(preferredPort: Int?) {
        preferredPortNumber = preferredPort
        selectedPortNumber = preferredPort
        renderShellState(latestShellState)
    }

    protected fun currentSelectedPort(): Int? = selectedPortNumber

    protected fun currentShellState(): ClientGameSnapshot? = latestShellState

    protected fun currentEnteredAmount(): Double {
        return when (val value = amountField.value) {
            is Number -> value.toDouble()
            else -> amountField.text.toDoubleOrNull() ?: 0.0
        }
    }

    protected fun showError(message: String?) {
        errorLabel.text = message?.takeIf { it.isNotBlank() } ?: " "
    }

    protected fun finishWindowInitialization() {
        contentPane = buildWindowContent()
        minimumSize = preferredSize
        subscribeToShellState()
        renderShellState(latestShellState)
    }

    protected fun submitRequest(
        request: suspend () -> RewriteGameCommandResult<*>,
    ) {
        if (requestInFlight) {
            return
        }
        requestInFlight = true
        showError(null)
        updateActionState()
        windowScope.launch {
            val result = request()
            SwingUtilities.invokeLater {
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success<*> -> dispose()
                    is RewriteGameCommandResult.Failure -> showError(result.message)
                }
                updateActionState()
            }
        }
    }

    protected open fun onShellStateUpdated(shellState: ClientGameSnapshot?) {
        Unit
    }

    protected abstract fun balanceLabelText(): String

    protected abstract fun currentBalance(shellState: ClientGameSnapshot?): Double

    protected abstract fun addExtraRows(formPanel: JPanel, nextGridY: Int): Int

    protected abstract fun buildActionPanel(): JComponent

    protected abstract fun updateCustomActionState(enabled: Boolean)

    protected abstract fun onPrimarySubmit()

    private fun buildWindowContent(): JPanel {
        val formPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        var gridY = 0
        addRow(formPanel, gridY++, "${title} using port:", portComboBox)
        addRow(formPanel, gridY++, balanceLabelText(), balanceValueLabel)
        addRow(formPanel, gridY++, "Amount:", amountField)
        gridY = addExtraRows(formPanel, gridY)
        addFullWidth(formPanel, gridY++, errorLabel)
        addFullWidth(formPanel, gridY, buildActionPanel())

        return JPanel(BorderLayout()).apply {
            add(formPanel, BorderLayout.CENTER)
        }
    }

    private fun subscribeToShellState() {
        windowScope.launch {
            controller.gameShellStateSelector().collect { shellState ->
                SwingUtilities.invokeLater {
                    renderShellState(shellState)
                }
            }
        }
    }

    private fun renderShellState(shellState: ClientGameSnapshot?) {
        latestShellState = shellState
        availableBankPorts = shellState.deriveBankPortOptions()
        selectedPortNumber = reconcileSelection(
            options = availableBankPorts,
            selectedPortNumber = selectedPortNumber,
            preferredPortNumber = preferredPortNumber,
            defaultBankPort = shellState?.economy?.defaultBankPort,
        )
        portComboBox.model = DefaultComboBoxModel(availableBankPorts.toTypedArray())
        portComboBox.selectedItem = availableBankPorts.firstOrNull { it.portNumber == selectedPortNumber }
        balanceValueLabel.text = currencyFormatter.format(currentBalance(shellState))
        onShellStateUpdated(shellState)
        updateActionState()
    }

    private fun updateActionState() {
        val enabled = !requestInFlight && selectedPortNumber != null
        portComboBox.isEnabled = !requestInFlight && availableBankPorts.isNotEmpty()
        amountField.isEnabled = enabled
        updateCustomActionState(enabled)
    }

    private fun addRow(
        panel: JPanel,
        gridY: Int,
        label: String,
        field: JComponent,
    ) {
        panel.add(
            JLabel(label),
            GridBagConstraints().apply {
                gridx = 0
                gridy = gridY
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 0, 4, 8)
            },
        )
        panel.add(
            field,
            GridBagConstraints().apply {
                gridx = 1
                gridy = gridY
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(4, 0, 4, 0)
            },
        )
    }

    private fun addFullWidth(
        panel: JPanel,
        gridY: Int,
        field: JComponent,
    ) {
        panel.add(
            field,
            GridBagConstraints().apply {
                gridx = 0
                gridy = gridY
                gridwidth = 2
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(4, 0, 4, 0)
            },
        )
    }

    companion object {
        private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

        private fun decimalFormatter(): DecimalFormat {
            return DecimalFormat("0.##").apply {
                isParseBigDecimal = false
            }
        }
    }
}

internal class RewriteDepositWindow(
    controller: RewriteRootController,
    preferredPort: Int? = null,
) : RewriteBankingWindow(
    controller = controller,
    command = RewriteShellCommand.DEPOSIT,
    title = "Deposit",
    preferredPort = preferredPort,
) {
    private val depositButton = JButton("Deposit").apply {
        name = "rewrite-economy-submit"
        addActionListener { onPrimarySubmit() }
    }
    private val depositAllButton = JButton("Deposit All").apply {
        name = "rewrite-economy-submit-all"
        addActionListener { onDepositAll() }
    }

    init {
        size = Dimension(360, 180)
        finishWindowInitialization()
    }

    override fun balanceLabelText(): String = "Petty Cash:"

    override fun currentBalance(shellState: ClientGameSnapshot?): Double {
        return shellState?.economy?.pettyCash ?: 0.0
    }

    override fun addExtraRows(formPanel: JPanel, nextGridY: Int): Int = nextGridY

    override fun buildActionPanel(): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(depositButton)
            add(JPanel().apply { isOpaque = false; preferredSize = Dimension(8, 1) })
            add(depositAllButton)
        }
    }

    override fun updateCustomActionState(enabled: Boolean) {
        depositButton.isEnabled = enabled
        depositAllButton.isEnabled = enabled
    }

    override fun onPrimarySubmit() {
        val port = currentSelectedPort()
        if (port == null) {
            showError("An active banking port is required.")
            return
        }
        val amount = currentEnteredAmount()
        if (amount <= 0.0) {
            showError("Amount must be positive.")
            return
        }
        submitRequest { controller.requestDeposit(amount, port) }
    }

    private fun onDepositAll() {
        val port = currentSelectedPort()
        if (port == null) {
            showError("An active banking port is required.")
            return
        }
        val amount = currentShellState()?.economy?.pettyCash ?: 0.0
        if (amount <= 0.0) {
            showError("Amount must be positive.")
            return
        }
        submitRequest { controller.requestDeposit(amount, port) }
    }
}

internal class RewriteWithdrawWindow(
    controller: RewriteRootController,
    preferredPort: Int? = null,
) : RewriteBankingWindow(
    controller = controller,
    command = RewriteShellCommand.WITHDRAW,
    title = "Withdraw",
    preferredPort = preferredPort,
) {
    private val withdrawButton = JButton("Withdraw").apply {
        name = "rewrite-economy-submit"
        addActionListener { onPrimarySubmit() }
    }

    init {
        size = Dimension(340, 160)
        finishWindowInitialization()
    }

    override fun balanceLabelText(): String = "Bank Money:"

    override fun currentBalance(shellState: ClientGameSnapshot?): Double {
        return shellState?.economy?.bankMoney ?: 0.0
    }

    override fun addExtraRows(formPanel: JPanel, nextGridY: Int): Int = nextGridY

    override fun buildActionPanel(): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(withdrawButton)
        }
    }

    override fun updateCustomActionState(enabled: Boolean) {
        withdrawButton.isEnabled = enabled
    }

    override fun onPrimarySubmit() {
        val port = currentSelectedPort()
        if (port == null) {
            showError("An active banking port is required.")
            return
        }
        val amount = currentEnteredAmount()
        if (amount <= 0.0) {
            showError("Amount must be positive.")
            return
        }
        submitRequest { controller.requestWithdraw(amount, port) }
    }
}

internal class RewriteTransferWindow(
    controller: RewriteRootController,
    preferredPort: Int? = null,
) : RewriteBankingWindow(
    controller = controller,
    command = RewriteShellCommand.TRANSFER,
    title = "Transfer",
    preferredPort = preferredPort,
) {
    private val ipInput = RewriteSegmentedIpInput().apply {
        name = "rewrite-economy-ip-input"
    }
    private val transferButton = JButton("Transfer").apply {
        name = "rewrite-economy-submit"
        addActionListener { onPrimarySubmit() }
    }

    init {
        size = Dimension(390, 200)
        finishWindowInitialization()
    }

    override fun balanceLabelText(): String = "Petty Cash:"

    override fun currentBalance(shellState: ClientGameSnapshot?): Double {
        return shellState?.economy?.pettyCash ?: 0.0
    }

    override fun addExtraRows(formPanel: JPanel, nextGridY: Int): Int {
        formPanel.add(
            JLabel("IP to transfer to:"),
            GridBagConstraints().apply {
                gridx = 0
                gridy = nextGridY
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 0, 4, 8)
            },
        )
        formPanel.add(
            ipInput,
            GridBagConstraints().apply {
                gridx = 1
                gridy = nextGridY
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(4, 0, 4, 0)
            },
        )
        return nextGridY + 1
    }

    override fun buildActionPanel(): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(transferButton)
        }
    }

    override fun updateCustomActionState(enabled: Boolean) {
        transferButton.isEnabled = enabled
        ipInput.isEnabled = enabled
    }

    override fun onPrimarySubmit() {
        val port = currentSelectedPort()
        if (port == null) {
            showError("An active banking port is required.")
            return
        }
        val amount = currentEnteredAmount()
        if (amount <= 0.0) {
            showError("Amount must be positive.")
            return
        }
        val targetIp = ipInput.valueOrNull()
        if (targetIp == null) {
            showError("Target IP must be complete.")
            return
        }
        submitRequest { controller.requestTransfer(amount, targetIp, port) }
    }
}

internal class RewriteSegmentedIpInput : JPanel() {
    private val segments = List(4) { index ->
        JTextField(3).apply {
            name = "rewrite-economy-ip-segment-$index"
            horizontalAlignment = JTextField.CENTER
            font = font.deriveFont(Font.PLAIN)
        }
    }

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        segments.forEachIndexed { index, field ->
            add(field)
            if (index < segments.lastIndex) {
                add(JLabel("."))
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        segments.forEach { it.isEnabled = enabled }
    }

    fun valueOrNull(): String? {
        val parts = segments.map { it.text.trim() }
        if (parts.any { it.isBlank() }) {
            return null
        }
        val numbers = parts.map { part -> part.toIntOrNull() ?: return null }
        if (numbers.any { it !in 0..255 }) {
            return null
        }
        return numbers.joinToString(".")
    }

    fun setIp(ip: String) {
        val parts = ip.split('.')
        segments.forEachIndexed { index, field ->
            field.text = parts.getOrNull(index).orEmpty()
        }
    }
}

internal fun ClientGameSnapshot?.deriveBankPortOptions(): List<RewriteBankPortOption> {
    return this?.ports.orEmpty()
        .filter(ClientPortState::enabled)
        .filterNot(ClientPortState::dummy)
        .filter { it.installedApplication?.kind == "BANKING" }
        .map { port ->
            RewriteBankPortOption(
                portNumber = port.number,
                label = "${port.number}: ${port.note.toLegacyPortNote()}",
            )
        }
}

internal fun reconcileSelection(
    options: List<RewriteBankPortOption>,
    selectedPortNumber: Int?,
    preferredPortNumber: Int?,
    defaultBankPort: Int?,
): Int? {
    val availableNumbers = options.map { it.portNumber }.toSet()
    return when {
        selectedPortNumber in availableNumbers -> selectedPortNumber
        preferredPortNumber in availableNumbers -> preferredPortNumber
        defaultBankPort in availableNumbers -> defaultBankPort
        else -> options.firstOrNull()?.portNumber
    }
}

private fun String.toLegacyPortNote(): String {
    return if (length > 10) {
        take(7) + "..."
    } else {
        this
    }
}
