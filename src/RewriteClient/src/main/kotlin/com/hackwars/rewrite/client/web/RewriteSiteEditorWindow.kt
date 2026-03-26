package com.hackwars.rewrite.client.web

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.protocol.ClientPageEditorResponse
import com.hackwars.rewrite.protocol.ClientSavePageResponse
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Window
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import javax.swing.undo.CannotRedoException
import javax.swing.undo.CannotUndoException
import javax.swing.undo.UndoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal data class RewriteSiteEditorTextEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

internal fun wrapSiteEditorSelection(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    prefix: String,
    suffix: String,
): RewriteSiteEditorTextEdit {
    val safeStart = selectionStart.coerceIn(0, text.length)
    val safeEnd = selectionEnd.coerceIn(safeStart, text.length)
    val selected = text.substring(safeStart, safeEnd)
    val wrapped = buildString {
        append(text.substring(0, safeStart))
        append(prefix)
        append(selected)
        append(suffix)
        append(text.substring(safeEnd))
    }
    return if (selected.isEmpty()) {
        val caret = safeStart + prefix.length
        RewriteSiteEditorTextEdit(
            text = wrapped,
            selectionStart = caret,
            selectionEnd = caret,
        )
    } else {
        RewriteSiteEditorTextEdit(
            text = wrapped,
            selectionStart = safeStart,
            selectionEnd = safeStart + prefix.length + selected.length + suffix.length,
        )
    }
}

internal fun insertSiteEditorMarkup(
    text: String,
    caretPosition: Int,
    insertion: String,
): RewriteSiteEditorTextEdit {
    val safeCaret = caretPosition.coerceIn(0, text.length)
    val updated = buildString {
        append(text.substring(0, safeCaret))
        append(insertion)
        append(text.substring(safeCaret))
    }
    val newCaret = safeCaret + insertion.length
    return RewriteSiteEditorTextEdit(
        text = updated,
        selectionStart = newCaret,
        selectionEnd = newCaret,
    )
}

internal fun buildSiteEditorPreviewHtml(
    title: String,
    body: String,
): String {
    val trimmedBody = body.trimStart()
    if (trimmedBody.startsWith("<html", ignoreCase = true)) {
        return body
    }
    return """
        <html>
          <head>
            <title>$title</title>
          </head>
          <body>
            $body
          </body>
        </html>
    """.trimIndent()
}

internal class RewriteSiteEditorWindow(
    private val controller: RewriteRootController,
) : JInternalFrame("Site Editor", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val htmlView = RewriteHtmlView(
        paneName = "rewrite-site-editor-preview-pane",
        scrollPaneName = "rewrite-site-editor-preview-scroll",
    )
    private val undoManager = UndoManager()
    private val sourceArea = JTextArea().apply {
        name = "rewrite-site-editor-source-area"
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        lineWrap = false
        wrapStyleWord = false
        document.addUndoableEditListener { event ->
            if (!suppressDocumentEvents) {
                undoManager.addEdit(event.edit)
            }
        }
        document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent?) = handleDocumentEdited()
                override fun removeUpdate(event: DocumentEvent?) = handleDocumentEdited()
                override fun changedUpdate(event: DocumentEvent?) = handleDocumentEdited()
            },
        )
    }
    private val tabs = JTabbedPane().apply {
        name = "rewrite-site-editor-tabs"
        addChangeListener(::handleTabChanged)
    }
    private val statusLabel = JLabel(" ").apply {
        name = "rewrite-site-editor-status"
        foreground = Color(0x1F, 0x4D, 0x24)
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-site-editor-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }

    private var loadedTitle: String = ""
    private var loadedBody: String = ""
    private var currentTitle: String = ""
    private var loadedVersion: Long = 0L
    private var pageLoaded: Boolean = false
    private var requestInFlight: Boolean = false
    private var suppressDocumentEvents: Boolean = false
    private var insertLinkDialog: RewriteInsertLinkDialog? = null

    private val saveAction = object : AbstractAction("Save") {
        override fun actionPerformed(event: java.awt.event.ActionEvent?) {
            requestSave()
        }
    }
    private val setTitleAction = object : AbstractAction("Set Title") {
        override fun actionPerformed(event: java.awt.event.ActionEvent?) {
            promptForTitleChange()
        }
    }
    private val undoAction = object : AbstractAction("Undo") {
        override fun actionPerformed(event: java.awt.event.ActionEvent?) {
            runCatching { undoManager.undo() }.recoverCatching {
                if (it !is CannotUndoException) throw it
            }
            renderState()
        }
    }
    private val redoAction = object : AbstractAction("Redo") {
        override fun actionPerformed(event: java.awt.event.ActionEvent?) {
            runCatching { undoManager.redo() }.recoverCatching {
                if (it !is CannotRedoException) throw it
            }
            renderState()
        }
    }
    private val copyAction = object : AbstractAction("Copy") {
        override fun actionPerformed(event: java.awt.event.ActionEvent?) {
            sourceArea.copy()
        }
    }
    private val cutAction = object : AbstractAction("Cut") {
        override fun actionPerformed(event: java.awt.event.ActionEvent?) {
            sourceArea.cut()
        }
    }
    private val pasteAction = object : AbstractAction("Paste") {
        override fun actionPerformed(event: java.awt.event.ActionEvent?) {
            sourceArea.paste()
        }
    }
    private val boldAction = formattingAction(
        label = "Bold",
        prefix = "<b>",
        suffix = "</b>",
    )
    private val italicsAction = formattingAction(
        label = "Italics",
        prefix = "<i>",
        suffix = "</i>",
    )
    private val underlineAction = formattingAction(
        label = "Underline",
        prefix = "<u>",
        suffix = "</u>",
    )
    private val insertLinkAction = object : AbstractAction("Insert Link") {
        override fun actionPerformed(event: java.awt.event.ActionEvent?) {
            openInsertLinkDialog()
        }
    }
    private val newLineAction = object : AbstractAction("New Line") {
        override fun actionPerformed(event: java.awt.event.ActionEvent?) {
            applyTextEdit(
                insertSiteEditorMarkup(
                    text = sourceArea.text,
                    caretPosition = sourceArea.caretPosition,
                    insertion = "<br />\n",
                ),
            )
        }
    }
    private val pageHeaderAction = formattingAction(
        label = "Page Header",
        prefix = "<h1>",
        suffix = "</h1>",
    )
    private val centreTextAction = formattingAction(
        label = "Centre Text",
        prefix = "<center>",
        suffix = "</center>",
    )
    private val leftJustifyAction = formattingAction(
        label = "Left Justify Text",
        prefix = "<div align=\"left\">",
        suffix = "</div>",
    )
    private val rightJustifyAction = formattingAction(
        label = "Right Justify Text",
        prefix = "<div align=\"right\">",
        suffix = "</div>",
    )
    private val exitAction = object : AbstractAction("Exit") {
        override fun actionPerformed(event: java.awt.event.ActionEvent?) {
            attemptClose()
        }
    }

    init {
        name = "rewrite-site-editor-window"
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        size = Dimension(860, 620)
        jMenuBar = buildMenuBar()
        tabs.addTab(
            "Site",
            JScrollPane(sourceArea).apply {
                name = "rewrite-site-editor-source-scroll"
            },
        )
        tabs.addTab("Preview", htmlView.scrollPane)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(buildToolbar(), BorderLayout.NORTH)
            add(tabs, BorderLayout.CENTER)
            add(
                JPanel(BorderLayout(0, 4)).apply {
                    isOpaque = false
                    add(statusLabel, BorderLayout.NORTH)
                    add(errorLabel, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH,
            )
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosing(event: InternalFrameEvent) {
                attemptClose()
            }

            override fun internalFrameClosed(event: InternalFrameEvent) {
                insertLinkDialog?.dispose()
                insertLinkDialog = null
                windowScope.cancel()
            }
        })
        requestInitialPage()
        renderState()
    }

    internal fun currentTitleForTest(): String = currentTitle

    internal fun isDirtyForTest(): Boolean = isDirty()

    internal fun loadPageForTest(
        response: ClientPageEditorResponse,
    ) {
        applyLoadedPage(response)
    }

    internal fun applySaveResultForTest(
        response: ClientSavePageResponse,
    ) {
        applySavedPage(response)
    }

    internal fun applyTitleForTest(
        title: String,
    ) {
        applyTitleChange(title)
    }

    internal fun setSourceBodyForTest(
        body: String,
    ) {
        suppressDocumentEvents = true
        sourceArea.text = body
        suppressDocumentEvents = false
        renderState()
    }

    internal fun selectPreviewTabForTest() {
        tabs.selectedIndex = 1
    }

    internal fun previewHtmlForTest(): String = htmlView.pane.text

    private fun buildMenuBar(): JMenuBar {
        return JMenuBar().apply {
            add(
                JMenu("File").apply {
                    add(actionItem(saveAction))
                    add(actionItem(setTitleAction))
                    addSeparator()
                    add(actionItem(exitAction))
                },
            )
            add(
                JMenu("Edit").apply {
                    add(actionItem(undoAction))
                    add(actionItem(redoAction))
                    addSeparator()
                    add(actionItem(copyAction))
                    add(actionItem(cutAction))
                    add(actionItem(pasteAction))
                },
            )
            add(
                JMenu("Format").apply {
                    add(actionItem(boldAction))
                    add(actionItem(italicsAction))
                    add(actionItem(underlineAction))
                    add(actionItem(insertLinkAction))
                    add(actionItem(newLineAction))
                    add(actionItem(pageHeaderAction))
                    add(actionItem(centreTextAction))
                    add(actionItem(leftJustifyAction))
                    add(actionItem(rightJustifyAction))
                },
            )
        }
    }

    private fun actionItem(
        action: AbstractAction,
    ): JMenuItem {
        return JMenuItem(action)
    }

    private fun buildToolbar(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false
            add(toolbarButton("rewrite-site-editor-save-button", saveAction))
            add(toolbarButton("rewrite-site-editor-set-title-button", setTitleAction))
            add(toolbarButton("rewrite-site-editor-undo-button", undoAction))
            add(toolbarButton("rewrite-site-editor-redo-button", redoAction))
            add(toolbarButton("rewrite-site-editor-copy-button", copyAction))
            add(toolbarButton("rewrite-site-editor-cut-button", cutAction))
            add(toolbarButton("rewrite-site-editor-paste-button", pasteAction))
            add(toolbarButton("rewrite-site-editor-bold-button", boldAction))
            add(toolbarButton("rewrite-site-editor-italics-button", italicsAction))
            add(toolbarButton("rewrite-site-editor-underline-button", underlineAction))
            add(toolbarButton("rewrite-site-editor-insert-link-button", insertLinkAction))
            add(toolbarButton("rewrite-site-editor-new-line-button", newLineAction))
            add(toolbarButton("rewrite-site-editor-page-header-button", pageHeaderAction))
            add(toolbarButton("rewrite-site-editor-centre-text-button", centreTextAction))
            add(toolbarButton("rewrite-site-editor-left-justify-button", leftJustifyAction))
            add(toolbarButton("rewrite-site-editor-right-justify-button", rightJustifyAction))
            add(toolbarButton("rewrite-site-editor-exit-button", exitAction))
        }
    }

    private fun toolbarButton(
        name: String,
        action: AbstractAction,
    ): JButton {
        return JButton(action).apply {
            this.name = name
        }
    }

    private fun formattingAction(
        label: String,
        prefix: String,
        suffix: String,
    ): AbstractAction {
        return object : AbstractAction(label) {
            override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                applyTextEdit(
                    wrapSiteEditorSelection(
                        text = sourceArea.text,
                        selectionStart = sourceArea.selectionStart,
                        selectionEnd = sourceArea.selectionEnd,
                        prefix = prefix,
                        suffix = suffix,
                    ),
                )
            }
        }
    }

    private fun requestInitialPage() {
        requestInFlight = true
        showStatus("Loading website...")
        showError(null)
        renderState()
        windowScope.launch {
            val result = controller.requestPage()
            SwingUtilities.invokeLater {
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> applyLoadedPage(result.value)
                    is RewriteGameCommandResult.Failure -> {
                        showStatus(null)
                        showError(result.message)
                        renderState()
                    }
                }
            }
        }
    }

    private fun requestSave(
        afterSuccess: (() -> Unit)? = null,
    ) {
        if (requestInFlight) {
            return
        }
        requestInFlight = true
        showStatus("Saving website...")
        showError(null)
        renderState()
        windowScope.launch {
            val result = controller.savePage(
                title = currentTitle,
                body = sourceArea.text,
            )
            SwingUtilities.invokeLater {
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        applySavedPage(result.value)
                        afterSuccess?.invoke()
                    }

                    is RewriteGameCommandResult.Failure -> {
                        showStatus(null)
                        showError(result.message)
                        renderState()
                    }
                }
            }
        }
    }

    private fun applyLoadedPage(
        response: ClientPageEditorResponse,
    ) {
        pageLoaded = true
        loadedVersion = response.version
        loadedTitle = response.title
        loadedBody = response.body
        currentTitle = response.title
        suppressDocumentEvents = true
        sourceArea.text = response.body
        sourceArea.caretPosition = 0
        undoManager.discardAllEdits()
        suppressDocumentEvents = false
        refreshPreview()
        showStatus("Website loaded.")
        showError(null)
        renderState()
    }

    private fun applySavedPage(
        response: ClientSavePageResponse,
    ) {
        pageLoaded = true
        loadedVersion = response.version
        loadedTitle = response.title
        loadedBody = response.body
        currentTitle = response.title
        suppressDocumentEvents = true
        if (sourceArea.text != response.body) {
            sourceArea.text = response.body
        }
        suppressDocumentEvents = false
        refreshPreview()
        showStatus("Website saved.")
        showError(null)
        renderState()
    }

    private fun promptForTitleChange() {
        val updatedTitle = JOptionPane.showInputDialog(
            this,
            "Enter a website title.",
            currentTitle,
        ) ?: return
        applyTitleChange(updatedTitle)
    }

    private fun applyTitleChange(
        updatedTitle: String,
    ) {
        currentTitle = updatedTitle
        renderState()
    }

    private fun handleDocumentEdited() {
        if (suppressDocumentEvents) {
            return
        }
        renderState()
    }

    private fun handleTabChanged(
        @Suppress("UNUSED_PARAMETER") event: ChangeEvent,
    ) {
        if (tabs.selectedIndex == 1) {
            refreshPreview()
        }
        renderState()
    }

    private fun refreshPreview() {
        htmlView.renderHtml(
            body = buildSiteEditorPreviewHtml(
                title = currentTitle,
                body = sourceArea.text,
            ),
        )
    }

    private fun applyTextEdit(
        edit: RewriteSiteEditorTextEdit,
    ) {
        sourceArea.text = edit.text
        sourceArea.requestFocusInWindow()
        sourceArea.select(edit.selectionStart, edit.selectionEnd)
        renderState()
    }

    private fun showStatus(message: String?) {
        statusLabel.text = message?.takeIf { it.isNotBlank() } ?: " "
    }

    private fun showError(message: String?) {
        errorLabel.text = message?.takeIf { it.isNotBlank() } ?: " "
    }

    private fun isDirty(): Boolean {
        return currentTitle != loadedTitle || sourceArea.text != loadedBody
    }

    private fun attemptClose() {
        if (!isDirty()) {
            dispose()
            return
        }
        when (promptForUnsavedChanges()) {
            UnsavedChoice.SAVE -> requestSave(afterSuccess = ::dispose)
            UnsavedChoice.DONT_SAVE -> dispose()
            UnsavedChoice.CANCEL -> Unit
        }
    }

    private fun promptForUnsavedChanges(): UnsavedChoice {
        val choice = JOptionPane.showOptionDialog(
            this,
            "Save changes to website?",
            "Unsaved Changes",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            arrayOf("Save", "Don't Save", "Cancel"),
            "Save",
        )
        return when (choice) {
            0 -> UnsavedChoice.SAVE
            1 -> UnsavedChoice.DONT_SAVE
            else -> UnsavedChoice.CANCEL
        }
    }

    private fun openInsertLinkDialog() {
        val existing = insertLinkDialog
        if (existing != null && existing.isDisplayable) {
            existing.toFront()
            existing.requestFocus()
            return
        }
        val owner = SwingUtilities.getWindowAncestor(this)
        val dialog = RewriteInsertLinkDialog(owner) { link, name ->
            applyTextEdit(
                insertSiteEditorMarkup(
                    text = sourceArea.text,
                    caretPosition = sourceArea.caretPosition,
                    insertion = "<a href=\"$link\">$name</a>",
                ),
            )
        }
        dialog.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(event: java.awt.event.WindowEvent) {
                if (insertLinkDialog === dialog) {
                    insertLinkDialog = null
                }
            }
        })
        insertLinkDialog = dialog
        dialog.setLocationRelativeTo(owner)
        dialog.isVisible = true
        dialog.toFront()
        dialog.requestFocus()
    }

    private fun renderState() {
        val loaded = pageLoaded
        val editingEnabled = !requestInFlight
        sourceArea.isEditable = loaded && editingEnabled
        saveAction.isEnabled = loaded && editingEnabled
        setTitleAction.isEnabled = loaded && editingEnabled
        undoAction.isEnabled = editingEnabled && undoManager.canUndo()
        redoAction.isEnabled = editingEnabled && undoManager.canRedo()
        copyAction.isEnabled = loaded
        cutAction.isEnabled = editingEnabled && loaded
        pasteAction.isEnabled = editingEnabled && loaded
        boldAction.isEnabled = editingEnabled && loaded
        italicsAction.isEnabled = editingEnabled && loaded
        underlineAction.isEnabled = editingEnabled && loaded
        insertLinkAction.isEnabled = editingEnabled && loaded
        newLineAction.isEnabled = editingEnabled && loaded
        pageHeaderAction.isEnabled = editingEnabled && loaded
        centreTextAction.isEnabled = editingEnabled && loaded
        leftJustifyAction.isEnabled = editingEnabled && loaded
        rightJustifyAction.isEnabled = editingEnabled && loaded
        exitAction.isEnabled = true
        title = if (loaded) "Website Editor - $currentTitle" else "Site Editor"
        contentPane.revalidate()
        contentPane.repaint()
    }

    private enum class UnsavedChoice {
        SAVE,
        DONT_SAVE,
        CANCEL,
    }
}

private class RewriteInsertLinkDialog(
    owner: Window?,
    onInsert: (link: String, name: String) -> Unit,
) : JDialog(owner, "Insert Link", ModalityType.MODELESS) {
    private val linkField = JTextField().apply {
        name = "rewrite-site-editor-link-url-field"
    }
    private val nameField = JTextField().apply {
        name = "rewrite-site-editor-link-name-field"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-site-editor-link-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }

    init {
        name = "rewrite-site-editor-insert-link-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            add(
                JPanel().apply {
                    layout = java.awt.GridLayout(2, 2, 8, 8)
                    add(JLabel("Link"))
                    add(linkField)
                    add(JLabel("Name"))
                    add(nameField)
                },
                BorderLayout.CENTER,
            )
            add(errorLabel, BorderLayout.NORTH)
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    add(
                        JButton("Insert").apply {
                            name = "rewrite-site-editor-link-insert-button"
                            addActionListener {
                                val link = linkField.text.trim()
                                val name = nameField.text.trim()
                                if (link.isBlank() || name.isBlank()) {
                                    errorLabel.text = "Link and name are required."
                                    return@addActionListener
                                }
                                onInsert(link, name)
                                dispose()
                            }
                        },
                    )
                    add(
                        JButton("Cancel").apply {
                            name = "rewrite-site-editor-link-cancel-button"
                            addActionListener { dispose() }
                        },
                    )
                },
                BorderLayout.SOUTH,
            )
        }
        preferredSize = Dimension(420, 160)
        pack()
    }
}
