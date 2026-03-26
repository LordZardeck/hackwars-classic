package com.hackwars.rewrite.client.files

import com.hackwars.rewrite.protocol.ClientProgramScriptSlot
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.text.NumberFormat
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea

internal enum class RewriteLocalFileOpenTarget {
    SCRIPT_EDITOR,
    FILE_PROPERTIES,
}

internal data class RewriteReadOnlyEditorTab(
    val title: String,
    val content: String,
)

internal fun routeLocalFileTarget(file: ClientStoredFile): RewriteLocalFileOpenTarget {
    return when (file.kind) {
        ClientStoredFileKind.SCRIPT_SOURCE,
        ClientStoredFileKind.TEXT,
        ClientStoredFileKind.NOTE,
        -> RewriteLocalFileOpenTarget.SCRIPT_EDITOR

        else -> RewriteLocalFileOpenTarget.FILE_PROPERTIES
    }
}

internal fun buildReadOnlyEditorTabs(file: ClientStoredFile): List<RewriteReadOnlyEditorTab> {
    if (file.kind != ClientStoredFileKind.SCRIPT_SOURCE) {
        return listOf(
            RewriteReadOnlyEditorTab(
                title = "Content",
                content = file.contents,
            ),
        )
    }

    val scriptsBySlot = file.scriptBundle?.scriptsBySlot.orEmpty()
    val slotGroups = listOf(
        listOf(
            ClientProgramScriptSlot.DEPOSIT to "Deposit",
            ClientProgramScriptSlot.WITHDRAW to "Withdraw",
            ClientProgramScriptSlot.TRANSFER to "Transfer",
        ),
        listOf(
            ClientProgramScriptSlot.INITIALIZE to "Initialize",
            ClientProgramScriptSlot.FINALIZE to "Finalize",
            ClientProgramScriptSlot.CONTINUE to "Continue",
        ),
        listOf(
            ClientProgramScriptSlot.PUT to "Put",
            ClientProgramScriptSlot.GET to "Get",
        ),
        listOf(
            ClientProgramScriptSlot.ENTER to "Enter",
            ClientProgramScriptSlot.EXIT to "Exit",
            ClientProgramScriptSlot.SUBMIT to "Submit",
        ),
        listOf(
            ClientProgramScriptSlot.FIRE to "Fire",
        ),
    )

    slotGroups.forEach { group ->
        val tabs = group.mapNotNull { (slot, title) ->
            scriptsBySlot[slot]?.let { script ->
                RewriteReadOnlyEditorTab(title = title, content = script)
            }
        }
        if (tabs.isNotEmpty()) {
            return tabs
        }
    }

    val fallbackContent = when {
        file.contents.isNotBlank() -> file.contents
        scriptsBySlot.isNotEmpty() -> scriptsBySlot.entries
            .sortedBy { it.key.ordinal }
            .joinToString("\n\n") { (slot, script) -> "${slot.name}\n$script" }
        else -> ""
    }
    return listOf(
        RewriteReadOnlyEditorTab(
            title = "Content",
            content = fallbackContent,
        ),
    )
}

internal class RewriteFilePropertiesWindow(
    file: ClientStoredFile,
) : JInternalFrame("File Properties -- ${file.name}", true, true, true, true) {
    private val nameValueLabel = valueLabel("rewrite-file-properties-name-value")
    private val typeValueLabel = valueLabel("rewrite-file-properties-type-value")
    private val makerValueLabel = valueLabel("rewrite-file-properties-maker-value")
    private val priceValueLabel = valueLabel("rewrite-file-properties-price-value")
    private val cpuCostValueLabel = valueLabel("rewrite-file-properties-cpu-value")
    private val quantityValueLabel = valueLabel("rewrite-file-properties-quantity-value")
    private val descriptionArea = JTextArea().apply {
        name = "rewrite-file-properties-description-value"
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = background.brighter()
        border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
    }

    init {
        name = "rewrite-file-properties-window-${sanitizeWindowKey(file.path)}"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(420, 320)
        contentPane = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(buildFormPanel(), BorderLayout.NORTH)
            add(JScrollPane(descriptionArea).apply {
                name = "rewrite-file-properties-description-scroll"
                preferredSize = Dimension(360, 120)
            }, BorderLayout.CENTER)
        }
        updateFile(file)
    }

    fun updateFile(file: ClientStoredFile) {
        title = "File Properties -- ${file.name}"
        nameValueLabel.text = file.name
        typeValueLabel.text = humanizeFileKind(file.kind)
        makerValueLabel.text = file.maker.ifBlank { "-" }
        priceValueLabel.text = currencyFormatter.format(file.price)
        cpuCostValueLabel.text = decimalFormatter.format(file.cpuCost)
        quantityValueLabel.text = file.quantity.toString()
        descriptionArea.text = file.description.ifBlank { "-" }
        descriptionArea.caretPosition = 0
    }

    private fun buildFormPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            addRow(0, "Name:", nameValueLabel)
            addRow(1, "Type:", typeValueLabel)
            addRow(2, "Maker:", makerValueLabel)
            addRow(3, "Price:", priceValueLabel)
            addRow(4, "CPU Cost:", cpuCostValueLabel)
            addRow(5, "Quantity:", quantityValueLabel)
            add(
                JLabel("Description:"),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 6
                    anchor = GridBagConstraints.NORTHWEST
                    insets = Insets(4, 0, 4, 8)
                },
            )
        }
    }

    private fun JPanel.addRow(
        row: Int,
        label: String,
        value: JLabel,
    ) {
        add(
            JLabel(label),
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 0, 4, 8)
            },
        )
        add(
            value,
            GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 0, 4, 0)
            },
        )
    }

    companion object {
        private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)
        private val decimalFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }

        private fun valueLabel(name: String): JLabel {
            return JLabel("-").apply { this.name = name }
        }
    }
}

internal class RewriteScriptEditorWindow : JInternalFrame("Script Editor", true, true, true, true) {
    private val emptyStateLabel = JLabel("Open a file from Home to view it.").apply {
        name = "rewrite-script-editor-empty-state"
        horizontalAlignment = JLabel.CENTER
    }
    private val fileTabs = JTabbedPane().apply {
        name = "rewrite-script-editor-file-tabs"
    }
    private val openDocumentsByPath = linkedMapOf<String, RewriteEditorDocumentView>()

    init {
        name = "rewrite-script-editor-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(760, 520)
        contentPane = JPanel(BorderLayout()).apply {
            add(emptyStateLabel, BorderLayout.CENTER)
            add(fileTabs, BorderLayout.SOUTH)
        }
        fileTabs.removeAll()
        fileTabs.isVisible = false
        fileTabs.tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
        renderState()
    }

    fun openFile(file: ClientStoredFile) {
        val existing = openDocumentsByPath[file.path]
        if (existing != null) {
            existing.update(file)
            fileTabs.selectedComponent = existing.container
            renderState()
            return
        }

        val documentView = RewriteEditorDocumentView(file)
        openDocumentsByPath[file.path] = documentView
        fileTabs.addTab(file.name, documentView.container)
        fileTabs.selectedComponent = documentView.container
        renderState()
    }

    private fun renderState() {
        val hasDocuments = openDocumentsByPath.isNotEmpty()
        emptyStateLabel.isVisible = !hasDocuments
        fileTabs.isVisible = hasDocuments
        contentPane.removeAll()
        if (hasDocuments) {
            contentPane.add(fileTabs, BorderLayout.CENTER)
        } else {
            contentPane.add(emptyStateLabel, BorderLayout.CENTER)
        }
        contentPane.revalidate()
        contentPane.repaint()
    }
}

private class RewriteEditorDocumentView(
    file: ClientStoredFile,
) {
    val container = JPanel(BorderLayout())
    private val tabbedPane = JTabbedPane().apply {
        name = "rewrite-script-editor-document-tabs-${sanitizeWindowKey(file.path)}"
    }

    init {
        container.add(tabbedPane, BorderLayout.CENTER)
        update(file)
    }

    fun update(file: ClientStoredFile) {
        tabbedPane.removeAll()
        buildReadOnlyEditorTabs(file).forEach { tab ->
            val area = JTextArea(tab.content).apply {
                isEditable = false
                lineWrap = false
                wrapStyleWord = false
                font = Font(Font.MONOSPACED, Font.PLAIN, 13)
                name = "rewrite-script-editor-content-${sanitizeWindowKey(file.path)}-${sanitizeWindowKey(tab.title)}"
                caretPosition = 0
            }
            tabbedPane.addTab(
                tab.title,
                JScrollPane(area),
            )
        }
    }
}

private fun humanizeFileKind(kind: ClientStoredFileKind): String {
    return kind.name
        .lowercase()
        .split('_')
        .joinToString(" ") { token -> token.replaceFirstChar { it.titlecase() } }
}

internal fun sanitizeWindowKey(value: String): String {
    return value
        .lowercase()
        .map { character ->
            if (character.isLetterOrDigit()) {
                character
            } else {
                '-'
            }
        }
        .joinToString("")
        .trim('-')
        .ifBlank { "item" }
}
