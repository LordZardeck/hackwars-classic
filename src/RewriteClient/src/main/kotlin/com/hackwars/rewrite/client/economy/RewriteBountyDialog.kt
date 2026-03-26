package com.hackwars.rewrite.client.economy

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.files.RewriteLocalFileChooserWindow
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.SpinnerNumberModel
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import javax.swing.JInternalFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal data class RewriteBountyTypeOption(
    val typeIndex: Int,
    val label: String,
) {
    override fun toString(): String = label
}

internal val rewriteBountyTypeOptions: List<RewriteBountyTypeOption> = listOf(
    RewriteBountyTypeOption(typeIndex = 0, label = "Scan"),
    RewriteBountyTypeOption(typeIndex = 1, label = "Attack"),
    RewriteBountyTypeOption(typeIndex = 2, label = "Install Script"),
    RewriteBountyTypeOption(typeIndex = 3, label = "Vote"),
    RewriteBountyTypeOption(typeIndex = 4, label = "Change HTTP Target"),
    RewriteBountyTypeOption(typeIndex = 5, label = "Destroy Watches"),
)

internal fun allowBountyChooserDirectory(directory: ClientDirectoryEntry): Boolean {
    return directory.name !in setOf("Store", "Public")
}

internal fun allowBountyChooserFile(file: ClientStoredFile): Boolean {
    if (file.kind != ClientStoredFileKind.APPLICATION_BINARY) {
        return false
    }
    return file.compiledBinary?.applicationKind in setOf(
        ClientApplicationKind.BANKING,
        ClientApplicationKind.ATTACK,
        ClientApplicationKind.FTP,
        ClientApplicationKind.HTTP,
    )
}

internal class RewriteCreateBountyDialog(
    owner: Window?,
    private val controller: RewriteRootController,
    private val onOpenChooser: (JInternalFrame) -> Unit,
    private val onFocusChooser: (JInternalFrame) -> Unit,
) : JDialog(owner, "Create Bounty", Dialog.ModalityType.MODELESS) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val anonymousCheckBox = JCheckBox().apply {
        name = "rewrite-bounty-anonymous"
    }
    private val anyPlayerCheckBox = JCheckBox("Any Player").apply {
        name = "rewrite-bounty-any-player"
        addActionListener { updateControlState() }
    }
    private val ipInput = RewriteSegmentedIpInput().apply {
        name = "rewrite-bounty-ip-input"
    }
    private val typeComboBox = JComboBox(rewriteBountyTypeOptions.toTypedArray()).apply {
        name = "rewrite-bounty-type"
        addActionListener { updateTypeState() }
    }
    private val fileField = JTextField(14).apply {
        name = "rewrite-bounty-file-field"
        isEditable = false
    }
    private val browseButton = JButton("Browse").apply {
        name = "rewrite-bounty-browse"
        addActionListener { openChooser() }
    }
    private val iterationsSpinner = JSpinner(SpinnerNumberModel(1, 1, 1_000, 1)).apply {
        name = "rewrite-bounty-iterations"
    }
    private val rewardSpinner = JSpinner(SpinnerNumberModel(0.0, 0.0, 50_000_000.0, 1.0)).apply {
        name = "rewrite-bounty-reward"
    }
    private val createButton = JButton("Create").apply {
        name = "rewrite-bounty-create"
        addActionListener { submitCreateBounty() }
    }
    private val cancelButton = JButton("Cancel").apply {
        name = "rewrite-bounty-cancel"
        addActionListener { dispose() }
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-bounty-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }

    private val enabledFileBackground = UIManager.getColor("TextField.background") ?: Color.WHITE
    private val disabledFileBackground = UIManager.getColor("Panel.background") ?: Color(0xEE, 0xEE, 0xEE)
    private var requestInFlight: Boolean = false
    private var selectedFolder: String? = null
    private var chooserWindow: JInternalFrame? = null

    init {
        name = "rewrite-bounty-dialog"
        isModal = false
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane = buildContent()
        minimumSize = preferredSize
        pack()
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(event: java.awt.event.WindowEvent) {
                chooserWindow?.dispose()
                chooserWindow = null
                windowScope.cancel()
            }
        })
        updateTypeState()
        updateControlState()
    }

    private fun buildContent(): JComponent {
        val formPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        var row = 0
        addRow(formPanel, row++, "Anonymous:", anonymousCheckBox)
        addRow(formPanel, row++, "Target:", buildTargetField())
        addRow(formPanel, row++, "Type:", typeComboBox)
        addRow(formPanel, row++, "File:", buildFileRow())
        addRow(formPanel, row++, "# of iterations:", iterationsSpinner)
        addRow(formPanel, row++, "Reward:", rewardSpinner)
        addFullWidth(formPanel, row++, errorLabel)
        addFullWidth(formPanel, row, buildActionRow())
        return JPanel(BorderLayout()).apply {
            add(formPanel, BorderLayout.CENTER)
        }
    }

    private fun buildTargetField(): JComponent {
        return JPanel().apply {
            isOpaque = false
            layout = GridBagLayout()
            add(
                ipInput,
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(0, 0, 0, 8)
                },
            )
            add(
                anyPlayerCheckBox,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                },
            )
        }
    }

    private fun buildFileRow(): JComponent {
        return JPanel().apply {
            isOpaque = false
            layout = GridBagLayout()
            add(
                fileField,
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(0, 0, 0, 8)
                },
            )
            add(
                browseButton,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 0
                },
            )
        }
    }

    private fun buildActionRow(): JComponent {
        return JPanel().apply {
            isOpaque = false
            add(createButton)
            add(cancelButton)
        }
    }

    private fun addRow(
        panel: JPanel,
        row: Int,
        label: String,
        field: JComponent,
    ) {
        panel.add(
            JLabel(label),
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 0, 4, 8)
            },
        )
        panel.add(
            field,
            GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(4, 0, 4, 0)
            },
        )
    }

    private fun addFullWidth(
        panel: JPanel,
        row: Int,
        field: JComponent,
    ) {
        panel.add(
            field,
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                gridwidth = 2
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(4, 0, 4, 0)
            },
        )
    }

    private fun updateTypeState() {
        val installSelected = selectedTypeOption().typeIndex == 2
        browseButton.isEnabled = installSelected && !requestInFlight
        fileField.background = if (installSelected) enabledFileBackground else disabledFileBackground
    }

    private fun updateControlState() {
        val inputsEnabled = !requestInFlight
        anonymousCheckBox.isEnabled = inputsEnabled
        anyPlayerCheckBox.isEnabled = inputsEnabled
        typeComboBox.isEnabled = inputsEnabled
        iterationsSpinner.isEnabled = inputsEnabled
        rewardSpinner.isEnabled = inputsEnabled
        cancelButton.isEnabled = inputsEnabled
        createButton.isEnabled = inputsEnabled
        ipInput.isEnabled = inputsEnabled && !anyPlayerCheckBox.isSelected
        updateTypeState()
    }

    private fun selectedTypeOption(): RewriteBountyTypeOption {
        return typeComboBox.selectedItem as? RewriteBountyTypeOption ?: rewriteBountyTypeOptions.first()
    }

    private fun openChooser() {
        val existingChooser = chooserWindow
        if (existingChooser != null && existingChooser.isDisplayable && !existingChooser.isClosed) {
            onFocusChooser(existingChooser)
            return
        }
        val chooser = RewriteLocalFileChooserWindow(
            controller = controller,
            title = "Choose File",
            onFileSelected = { selection ->
                fileField.text = selection.file.name
                selectedFolder = selection.displayedPath
                chooserWindow?.dispose()
                toFront()
                requestFocus()
            },
            directoryFilter = ::allowBountyChooserDirectory,
            fileFilter = ::allowBountyChooserFile,
        ).apply {
            name = "rewrite-bounty-file-chooser-window"
            addInternalFrameListener(object : InternalFrameAdapter() {
                override fun internalFrameClosed(event: InternalFrameEvent) {
                    if (chooserWindow === this@apply) {
                        chooserWindow = null
                    }
                }
            })
        }
        chooserWindow = chooser
        onOpenChooser(chooser)
    }

    private fun submitCreateBounty() {
        if (requestInFlight) {
            return
        }

        val target = if (anyPlayerCheckBox.isSelected) {
            "*"
        } else {
            ipInput.valueOrNull()
        }
        if (target == null) {
            showError("Target IP must be complete.")
            return
        }

        val reward = (rewardSpinner.value as? Number)?.toDouble() ?: 0.0
        if (reward <= 0.0) {
            showError("Reward must be positive.")
            return
        }

        val iterations = (iterationsSpinner.value as? Number)?.toInt() ?: 0
        if (iterations <= 0) {
            showError("Iterations must be positive.")
            return
        }

        val type = selectedTypeOption()
        val fileName = fileField.text.trim().takeIf { it.isNotBlank() }
        val folder = selectedFolder
        if (type.typeIndex == 2 && (fileName == null || folder.isNullOrBlank())) {
            showError("Install Script requires a selected file.")
            return
        }

        requestInFlight = true
        showError(null)
        updateControlState()
        windowScope.launch {
            val result = controller.requestMakeBounty(
                anonymous = anonymousCheckBox.isSelected,
                target = target,
                type = type.typeIndex,
                fileName = fileName,
                folder = folder,
                iterations = iterations,
                reward = reward,
            )
            SwingUtilities.invokeLater {
                requestInFlight = false
                if (!isDisplayable) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> dispose()
                    is RewriteGameCommandResult.Failure -> {
                        showError(result.message)
                        updateControlState()
                    }
                }
            }
        }
    }

    private fun showError(message: String?) {
        errorLabel.text = message?.takeIf { it.isNotBlank() } ?: " "
    }
}
