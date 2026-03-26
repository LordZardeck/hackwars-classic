package com.hackwars.rewrite.client.network

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.economy.RewriteSegmentedIpInput
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameUiNotice
import com.hackwars.rewrite.protocol.ClientFloatHookValue
import com.hackwars.rewrite.protocol.ClientGameUiEvent
import com.hackwars.rewrite.protocol.ClientHookValue
import com.hackwars.rewrite.protocol.ClientProgramLifecycleStatus
import com.hackwars.rewrite.protocol.ClientProgramUpdate
import com.hackwars.rewrite.protocol.ClientStringHookValue
import com.hackwars.rewrite.protocol.ClientZombieAttackCancelResponse
import com.hackwars.rewrite.protocol.ClientZombieAttackStartResponse
import com.hackwars.rewrite.protocol.ClientZombieAttackUiEvent
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.SpinnerNumberModel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val MAX_ZOMBIE_TARGET_PORT = 31
private const val MAX_ZOMBIE_EXTRA_INFO_VALUES = 5
private const val MAX_ZOMBIE_PETTY_CASH_TARGET = 50_000_000.0

internal fun buildZombieAttackExtraInfo(
    pettyCashTarget: Double,
): List<ClientHookValue> {
    return buildList(MAX_ZOMBIE_EXTRA_INFO_VALUES) {
        add(ClientStringHookValue(""))
        add(ClientFloatHookValue(0.0))
        add(ClientStringHookValue(""))
        add(ClientFloatHookValue(pettyCashTarget))
        add(ClientStringHookValue(""))
    }
}

internal class RewriteZombieAttackDialog(
    owner: Window?,
    private val controller: RewriteRootController,
) : JDialog(owner, "Start Zombie Attack", Dialog.ModalityType.APPLICATION_MODAL) {
    private val zombieIpInput = RewriteSegmentedIpInput().apply {
        name = "rewrite-zombie-launch-ip-input"
    }
    private val zombiePortSpinner = JSpinner(
        SpinnerNumberModel(0, 0, MAX_ZOMBIE_TARGET_PORT, 1),
    ).apply {
        name = "rewrite-zombie-launch-port-spinner"
    }
    private val continueButton = JButton("Continue").apply {
        name = "rewrite-zombie-launch-continue"
        addActionListener { submitContinue() }
    }
    private val cancelButton = JButton("Cancel").apply {
        name = "rewrite-zombie-launch-cancel"
        addActionListener { dispose() }
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-zombie-launch-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }

    init {
        name = "rewrite-zombie-launch-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane = buildContent()
        isResizable = false
        pack()
    }

    private fun buildContent(): JComponent {
        return JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(
                JPanel(GridBagLayout()).apply {
                    isOpaque = false
                    add(
                        JLabel("Zombie IP:"),
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 0
                            anchor = GridBagConstraints.WEST
                            insets = Insets(0, 0, 8, 8)
                        },
                    )
                    add(
                        zombieIpInput,
                        GridBagConstraints().apply {
                            gridx = 1
                            gridy = 0
                            weightx = 1.0
                            fill = GridBagConstraints.HORIZONTAL
                            insets = Insets(0, 0, 8, 0)
                        },
                    )
                    add(
                        JLabel("Port:"),
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 1
                            anchor = GridBagConstraints.WEST
                            insets = Insets(0, 0, 8, 8)
                        },
                    )
                    add(
                        zombiePortSpinner,
                        GridBagConstraints().apply {
                            gridx = 1
                            gridy = 1
                            anchor = GridBagConstraints.WEST
                            fill = GridBagConstraints.HORIZONTAL
                            insets = Insets(0, 0, 8, 0)
                        },
                    )
                    add(
                        errorLabel,
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 2
                            gridwidth = 2
                            anchor = GridBagConstraints.WEST
                        },
                    )
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(continueButton)
                    add(cancelButton)
                },
                BorderLayout.SOUTH,
            )
        }
    }

    private fun submitContinue() {
        val zombieIp = zombieIpInput.valueOrNull()
        if (zombieIp == null) {
            errorLabel.text = "Zombie IP is required."
            return
        }
        errorLabel.text = " "
        controller.openZombieAttackPane(
            zombieIp = zombieIp,
            zombiePort = (zombiePortSpinner.value as Number).toInt(),
        )
        dispose()
    }
}

internal class RewriteZombieAttackWindow(
    private val controller: RewriteRootController,
    private val zombieIp: String,
    private val zombiePort: Int,
) : JInternalFrame("Zombie Attack -- $zombieIp:$zombiePort", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val targetIpInput = RewriteSegmentedIpInput().apply {
        name = "rewrite-zombie-target-ip-input"
    }
    private val targetPortSpinner = JSpinner(
        SpinnerNumberModel(0, 0, MAX_ZOMBIE_TARGET_PORT, 1),
    ).apply {
        name = "rewrite-zombie-target-port-spinner"
    }
    private val zombieIpLabel = JLabel(zombieIp).apply {
        name = "rewrite-zombie-source-ip"
    }
    private val zombiePortLabel = JLabel(zombiePort.toString()).apply {
        name = "rewrite-zombie-source-port"
    }
    private val transcriptArea = JTextArea().apply {
        name = "rewrite-zombie-transcript"
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val statusLabel = JLabel("Ready.").apply {
        name = "rewrite-zombie-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-zombie-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val primaryButton = JButton("Attack").apply {
        name = "rewrite-zombie-primary-button"
        addActionListener { submitPrimaryAction() }
    }
    private val cancelAttackButton = JButton("Cancel Attack").apply {
        name = "rewrite-zombie-cancel-button"
        addActionListener { submitCancel() }
    }
    private val secondaryPortsLabel = JLabel("None").apply {
        name = "rewrite-zombie-secondary-ports-label"
    }
    private val secondaryPortsButton = JButton("Edit").apply {
        name = "rewrite-zombie-secondary-ports-button"
        addActionListener { showSecondaryPortsDialog() }
    }
    private val pettyCashTargetSpinner = JSpinner(
        SpinnerNumberModel(0.0, 0.0, MAX_ZOMBIE_PETTY_CASH_TARGET, 1.0),
    ).apply {
        name = "rewrite-zombie-petty-cash-spinner"
    }
    private val tabs = JTabbedPane().apply {
        name = "rewrite-zombie-tabs"
    }

    private var requestInFlight: Boolean = false
    private var activeProgramId: String? = null
    private var activeTargetIp: String? = null
    private var activeTargetPort: Int? = null
    private var selectedSecondaryPorts: List<Int> = emptyList()
    private val processedUiNoticeKeys = mutableSetOf<String>()

    init {
        name = "rewrite-zombie-window-${zombieIp.replace('.', '_')}-$zombiePort"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(700, 500)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(buildHeaderPanel(), BorderLayout.NORTH)
            add(buildBodyPanel(), BorderLayout.CENTER)
            add(buildFooterPanel(), BorderLayout.SOUTH)
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                windowScope.cancel()
            }
        })
        controller.snapshot().game.decodedGame.uiNotices.forEach { notice ->
            processedUiNoticeKeys += zombieNoticeKey(notice)
        }
        observeProgramUpdates()
        observeUiNotices()
        renderState()
    }

    private fun buildHeaderPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(
                JLabel("Target IP:"),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 6, 8)
                },
            )
            add(
                targetIpInput,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 0
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(0, 0, 6, 16)
                },
            )
            add(
                JLabel("Target Port:"),
                GridBagConstraints().apply {
                    gridx = 2
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 6, 8)
                },
            )
            add(
                targetPortSpinner,
                GridBagConstraints().apply {
                    gridx = 3
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                },
            )
            add(
                JLabel("Zombie IP:"),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 1
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 0, 8)
                },
            )
            add(
                zombieIpLabel,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 1
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 0, 16)
                },
            )
            add(
                JLabel("Zombie Port:"),
                GridBagConstraints().apply {
                    gridx = 2
                    gridy = 1
                    anchor = GridBagConstraints.WEST
                    insets = Insets(0, 0, 0, 8)
                },
            )
            add(
                zombiePortLabel,
                GridBagConstraints().apply {
                    gridx = 3
                    gridy = 1
                    anchor = GridBagConstraints.WEST
                },
            )
        }
    }

    private fun buildBodyPanel(): JComponent {
        tabs.addTab(
            "Terminal",
            JScrollPane(transcriptArea).apply {
                border = BorderFactory.createEmptyBorder()
            },
        )
        tabs.addTab("Options", buildOptionsPanel())
        return tabs
    }

    private fun buildOptionsPanel(): JComponent {
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    add(cancelAttackButton)
                    alignmentX = LEFT_ALIGNMENT
                },
            )
            add(spacer())
            add(
                JPanel(GridBagLayout()).apply {
                    isOpaque = false
                    alignmentX = LEFT_ALIGNMENT
                    add(
                        JLabel("Selected Ports:"),
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 0
                            anchor = GridBagConstraints.WEST
                            insets = Insets(0, 0, 8, 8)
                        },
                    )
                    add(
                        secondaryPortsLabel,
                        GridBagConstraints().apply {
                            gridx = 1
                            gridy = 0
                            weightx = 1.0
                            anchor = GridBagConstraints.WEST
                            fill = GridBagConstraints.HORIZONTAL
                            insets = Insets(0, 0, 8, 8)
                        },
                    )
                    add(
                        secondaryPortsButton,
                        GridBagConstraints().apply {
                            gridx = 2
                            gridy = 0
                            anchor = GridBagConstraints.WEST
                            insets = Insets(0, 0, 8, 0)
                        },
                    )
                    add(
                        JLabel("Petty Cash Target:"),
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = 1
                            anchor = GridBagConstraints.WEST
                            insets = Insets(0, 0, 0, 8)
                        },
                    )
                    add(
                        pettyCashTargetSpinner,
                        GridBagConstraints().apply {
                            gridx = 1
                            gridy = 1
                            anchor = GridBagConstraints.WEST
                            fill = GridBagConstraints.HORIZONTAL
                        },
                    )
                },
            )
        }
    }

    private fun buildFooterPanel(): JPanel {
        return JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(primaryButton)
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

    private fun observeProgramUpdates() {
        windowScope.launch {
            controller.gameProgramUpdatesSelector().collect { updates ->
                SwingUtilities.invokeLater {
                    if (isClosed || !isDisplayable) {
                        return@invokeLater
                    }
                    handleProgramUpdates(updates)
                }
            }
        }
    }

    private fun observeUiNotices() {
        windowScope.launch {
            controller.gameUiNoticesSelector().collect { notices ->
                SwingUtilities.invokeLater {
                    if (isClosed || !isDisplayable) {
                        return@invokeLater
                    }
                    notices.forEach { notice ->
                        val key = zombieNoticeKey(notice)
                        if (!processedUiNoticeKeys.add(key)) {
                            return@forEach
                        }
                        val event = notice.event as? ClientZombieAttackUiEvent ?: return@forEach
                        if (event.zombieIp == zombieIp && event.sourcePort == zombiePort) {
                            appendTranscript(event.message)
                        }
                    }
                }
            }
        }
    }

    private fun submitPrimaryAction() {
        if (activeProgramId != null) {
            submitCancel()
        } else {
            submitAttack()
        }
    }

    private fun submitAttack() {
        val targetIp = targetIpInput.valueOrNull()
        if (targetIp == null) {
            showError("Target IP is required.")
            return
        }
        val targetPort = (targetPortSpinner.value as Number).toInt()
        requestInFlight = true
        clearError()
        statusLabel.text = "Attack request in progress..."
        appendTranscript("Attack commencing on $targetIp at port $targetPort...")
        renderState()
        windowScope.launch {
            val result = controller.requestZombieAttack(
                targetIp = targetIp,
                targetPort = targetPort,
                zombieIp = zombieIp,
                zombiePort = zombiePort,
                secondaryPorts = selectedSecondaryPorts,
                extraInfo = buildZombieAttackExtraInfo((pettyCashTargetSpinner.value as Number).toDouble()),
            )
            SwingUtilities.invokeLater {
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                handleAttackStartResult(result)
            }
        }
    }

    private fun submitCancel() {
        requestInFlight = true
        clearError()
        statusLabel.text = "Cancelling attack..."
        renderState()
        windowScope.launch {
            val result = controller.requestZombieCancelAttack(
                zombieIp = zombieIp,
                zombiePort = zombiePort,
            )
            SwingUtilities.invokeLater {
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                handleAttackCancelResult(result)
            }
        }
    }

    private fun handleAttackStartResult(
        result: RewriteGameCommandResult<ClientZombieAttackStartResponse>,
    ) {
        requestInFlight = false
        when (result) {
            is RewriteGameCommandResult.Success -> {
                val response = result.value
                val session = response.session
                if (response.accepted && session != null) {
                    activeProgramId = session.programId
                    activeTargetIp = response.targetStateId
                    activeTargetPort = response.targetPort
                    clearError()
                    statusLabel.text = response.message.ifBlank { "Attack started." }
                } else {
                    clearActiveSession()
                    showError(response.message.ifBlank { "The rewrite game request failed." })
                }
            }

            is RewriteGameCommandResult.Failure -> {
                clearActiveSession()
                showError(result.message)
            }
        }
        renderState()
    }

    private fun handleAttackCancelResult(
        result: RewriteGameCommandResult<ClientZombieAttackCancelResponse>,
    ) {
        requestInFlight = false
        when (result) {
            is RewriteGameCommandResult.Success -> {
                val response = result.value
                if (response.accepted) {
                    clearActiveSession()
                    clearError()
                    val message = response.message.ifBlank { "Attack cancelled." }
                    statusLabel.text = message
                    appendTranscript(message)
                } else {
                    showError(response.message.ifBlank { "The rewrite game request failed." })
                }
            }

            is RewriteGameCommandResult.Failure -> {
                showError(result.message)
            }
        }
        renderState()
    }

    private fun handleProgramUpdates(
        updates: Map<String, ClientProgramUpdate>,
    ) {
        val programId = activeProgramId ?: return
        val update = updates[programId] ?: return
        val progressMessage = update.progress.message.ifBlank {
            when (update.status) {
                ClientProgramLifecycleStatus.RUNNING -> "Attack running."
                ClientProgramLifecycleStatus.COMPLETED -> "Attack completed."
                ClientProgramLifecycleStatus.CANCELLED -> "Attack cancelled."
                ClientProgramLifecycleStatus.FAILED -> "Attack failed."
            }
        }
        statusLabel.text = progressMessage
        if (update.status != ClientProgramLifecycleStatus.RUNNING) {
            appendTranscript(progressMessage)
            clearActiveSession()
            renderState()
        }
    }

    private fun showSecondaryPortsDialog() {
        RewriteZombieSelectedPortsDialog(
            owner = SwingUtilities.getWindowAncestor(this),
            selectedPorts = selectedSecondaryPorts,
        ) { ports ->
            selectedSecondaryPorts = ports
            if (ports.isNotEmpty()) {
                targetPortSpinner.value = ports.first()
            }
            renderState()
        }
    }

    private fun clearActiveSession() {
        activeProgramId = null
        activeTargetIp = null
        activeTargetPort = null
    }

    private fun appendTranscript(message: String) {
        if (message.isBlank()) {
            return
        }
        transcriptArea.append(if (message.endsWith('\n')) message else "$message\n")
        transcriptArea.caretPosition = transcriptArea.document.length
    }

    private fun clearError() {
        errorLabel.text = " "
    }

    private fun showError(message: String) {
        errorLabel.text = message
        statusLabel.text = " "
    }

    private fun renderState() {
        val busy = requestInFlight || activeProgramId != null
        targetIpInput.isEnabled = !busy
        targetPortSpinner.isEnabled = !busy
        pettyCashTargetSpinner.isEnabled = !busy
        secondaryPortsButton.isEnabled = !busy
        cancelAttackButton.isEnabled = !requestInFlight && activeProgramId != null
        primaryButton.text = if (activeProgramId != null) "Cancel" else "Attack"
        primaryButton.isEnabled = !requestInFlight
        secondaryPortsLabel.text = if (selectedSecondaryPorts.isEmpty()) {
            "None"
        } else {
            selectedSecondaryPorts.joinToString(", ")
        }
        if (!requestInFlight && activeProgramId == null && statusLabel.text.isBlank()) {
            statusLabel.text = "Ready."
        }
        isClosable = activeProgramId == null
        defaultCloseOperation = if (activeProgramId == null) DISPOSE_ON_CLOSE else DO_NOTHING_ON_CLOSE
        toolTipText = activeTargetIp?.let { target ->
            val port = activeTargetPort ?: return@let null
            "Active target: $target:$port"
        }
    }
}

private class RewriteZombieSelectedPortsDialog(
    owner: Window?,
    selectedPorts: List<Int>,
    onSubmit: (List<Int>) -> Unit,
) : JDialog(owner, "Set Selected Ports", Dialog.ModalityType.APPLICATION_MODAL) {
    init {
        name = "rewrite-zombie-secondary-ports-dialog"
        val checkboxes = (0..MAX_ZOMBIE_TARGET_PORT).map { port ->
            javax.swing.JCheckBox("Port $port", port in selectedPorts).apply {
                name = "rewrite-zombie-secondary-port-$port"
            }
        }
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JLabel("Select Secondary Ports:"), BorderLayout.NORTH)
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
                            name = "rewrite-zombie-secondary-ports-ok"
                            addActionListener {
                                val ports = checkboxes
                                    .filter { it.isSelected }
                                    .map { it.text.removePrefix("Port ").toInt() }
                                    .sorted()
                                onSubmit(ports)
                                dispose()
                            }
                        },
                    )
                    add(
                        JButton("Cancel").apply {
                            name = "rewrite-zombie-secondary-ports-cancel"
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

private fun spacer(): JComponent {
    return JPanel().apply {
        isOpaque = false
        preferredSize = Dimension(0, 8)
        maximumSize = Dimension(Int.MAX_VALUE, 8)
    }
}

private fun zombieNoticeKey(notice: RewriteDecodedGameUiNotice): String {
    val eventId = notice.metadata.eventId ?: "no-event-id"
    return buildString {
        append(eventId)
        append('|')
        append(notice.metadata.receivedAtEpochMillis ?: 0L)
        append('|')
        append(notice.metadata.eventType.orEmpty())
    }
}
