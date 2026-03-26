package com.hackwars.rewrite.client.files

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientCompileFileResponse
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientMutationAcceptedResponse
import com.hackwars.rewrite.protocol.ClientProgramScriptBundle
import com.hackwars.rewrite.protocol.ClientProgramScriptSlot
import com.hackwars.rewrite.protocol.ClientScriptFamily
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val DEFAULT_SCRIPT_TEMPLATE = "int main(){\n\n}"

internal enum class RewriteScriptEditorTemplate(
    private val label: String,
) {
    BANKING("Banking"),
    ATTACK("Attack"),
    FTP("FTP"),
    WATCH("Watch"),
    HTTP("HTTP"),
    REDIRECT("Redirect"),
    TEXT("Text");

    override fun toString(): String = label
}

internal data class RewriteEditorTabDefinition(
    val title: String,
    val slot: ClientProgramScriptSlot? = null,
)

internal data class RewriteNewEditorDocumentSpec(
    val kind: ClientStoredFileKind,
    val tabDefinitions: List<RewriteEditorTabDefinition>,
    val initialTextsByKey: Map<String, String>,
    val scriptBundleFamily: ClientScriptFamily? = null,
    val compiledBinary: ClientCompiledBinaryMetadata? = null,
)

internal fun buildNewEditorDocumentSpec(
    template: RewriteScriptEditorTemplate,
): RewriteNewEditorDocumentSpec {
    return when (template) {
        RewriteScriptEditorTemplate.BANKING -> scriptTemplateSpec(
            family = ClientScriptFamily.BANKING,
            applicationKind = ClientApplicationKind.BANKING,
            tabs = listOf(
                RewriteEditorTabDefinition("Deposit", ClientProgramScriptSlot.DEPOSIT),
                RewriteEditorTabDefinition("Withdraw", ClientProgramScriptSlot.WITHDRAW),
                RewriteEditorTabDefinition("Transfer", ClientProgramScriptSlot.TRANSFER),
            ),
        )

        RewriteScriptEditorTemplate.ATTACK -> scriptTemplateSpec(
            family = ClientScriptFamily.ATTACK,
            applicationKind = ClientApplicationKind.ATTACK,
            tabs = listOf(
                RewriteEditorTabDefinition("Initialize", ClientProgramScriptSlot.INITIALIZE),
                RewriteEditorTabDefinition("Finalize", ClientProgramScriptSlot.FINALIZE),
                RewriteEditorTabDefinition("Continue", ClientProgramScriptSlot.CONTINUE),
            ),
        )

        RewriteScriptEditorTemplate.FTP -> scriptTemplateSpec(
            family = ClientScriptFamily.GENERAL,
            applicationKind = ClientApplicationKind.FTP,
            tabs = listOf(
                RewriteEditorTabDefinition("Put", ClientProgramScriptSlot.PUT),
                RewriteEditorTabDefinition("Get", ClientProgramScriptSlot.GET),
            ),
        )

        RewriteScriptEditorTemplate.WATCH -> scriptTemplateSpec(
            family = ClientScriptFamily.WATCH,
            applicationKind = ClientApplicationKind.WATCH,
            tabs = listOf(
                RewriteEditorTabDefinition("Fire", ClientProgramScriptSlot.FIRE),
            ),
        )

        RewriteScriptEditorTemplate.HTTP -> scriptTemplateSpec(
            family = ClientScriptFamily.HTTP,
            applicationKind = ClientApplicationKind.HTTP,
            tabs = listOf(
                RewriteEditorTabDefinition("Enter", ClientProgramScriptSlot.ENTER),
                RewriteEditorTabDefinition("Exit", ClientProgramScriptSlot.EXIT),
                RewriteEditorTabDefinition("Submit", ClientProgramScriptSlot.SUBMIT),
            ),
        )

        RewriteScriptEditorTemplate.REDIRECT -> scriptTemplateSpec(
            family = ClientScriptFamily.REDIRECT,
            applicationKind = ClientApplicationKind.REDIRECT,
            tabs = listOf(
                RewriteEditorTabDefinition("Initialize", ClientProgramScriptSlot.INITIALIZE),
                RewriteEditorTabDefinition("Finalize", ClientProgramScriptSlot.FINALIZE),
                RewriteEditorTabDefinition("Continue", ClientProgramScriptSlot.CONTINUE),
            ),
        )

        RewriteScriptEditorTemplate.TEXT -> RewriteNewEditorDocumentSpec(
            kind = ClientStoredFileKind.TEXT,
            tabDefinitions = listOf(RewriteEditorTabDefinition("Content")),
            initialTextsByKey = linkedMapOf(editorTabKey(RewriteEditorTabDefinition("Content")) to ""),
        )
    }
}

internal fun buildEditableEditorTabDefinitions(
    file: ClientStoredFile,
): List<RewriteEditorTabDefinition> {
    if (file.kind != ClientStoredFileKind.SCRIPT_SOURCE) {
        return listOf(RewriteEditorTabDefinition("Content"))
    }

    val scriptsBySlot = file.scriptBundle?.scriptsBySlot.orEmpty()
    val slotGroups = listOf(
        listOf(
            RewriteEditorTabDefinition("Deposit", ClientProgramScriptSlot.DEPOSIT),
            RewriteEditorTabDefinition("Withdraw", ClientProgramScriptSlot.WITHDRAW),
            RewriteEditorTabDefinition("Transfer", ClientProgramScriptSlot.TRANSFER),
        ),
        listOf(
            RewriteEditorTabDefinition("Initialize", ClientProgramScriptSlot.INITIALIZE),
            RewriteEditorTabDefinition("Finalize", ClientProgramScriptSlot.FINALIZE),
            RewriteEditorTabDefinition("Continue", ClientProgramScriptSlot.CONTINUE),
        ),
        listOf(
            RewriteEditorTabDefinition("Put", ClientProgramScriptSlot.PUT),
            RewriteEditorTabDefinition("Get", ClientProgramScriptSlot.GET),
        ),
        listOf(
            RewriteEditorTabDefinition("Enter", ClientProgramScriptSlot.ENTER),
            RewriteEditorTabDefinition("Exit", ClientProgramScriptSlot.EXIT),
            RewriteEditorTabDefinition("Submit", ClientProgramScriptSlot.SUBMIT),
        ),
        listOf(
            RewriteEditorTabDefinition("Fire", ClientProgramScriptSlot.FIRE),
        ),
    )

    slotGroups.forEach { group ->
        val matchingTabs = group.filter { definition ->
            definition.slot != null && definition.slot in scriptsBySlot
        }
        if (matchingTabs.isNotEmpty()) {
            return matchingTabs
        }
    }

    return listOf(RewriteEditorTabDefinition("Content"))
}

internal fun serializeReadableScriptContents(
    tabDefinitions: List<RewriteEditorTabDefinition>,
    textsByKey: Map<String, String>,
): String {
    return tabDefinitions.joinToString("\n\n") { definition ->
        val text = textsByKey[editorTabKey(definition)].orEmpty()
        "[${definition.title}]\n$text"
    }
}

internal class RewriteScriptEditorWindow(
    private val controller: RewriteRootController,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit = {},
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit = {},
) : JInternalFrame("Script Editor", true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val emptyStateLabel = JLabel("Open a file from Home to view it.").apply {
        name = "rewrite-script-editor-empty-state"
        horizontalAlignment = JLabel.CENTER
    }
    private val fileTabs = JTabbedPane().apply {
        name = "rewrite-script-editor-file-tabs"
        tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
    }
    private val templateCombo = JComboBox(RewriteScriptEditorTemplate.entries.toTypedArray()).apply {
        name = "rewrite-script-editor-new-template"
    }
    private val newButton = JButton("New").apply {
        name = "rewrite-script-editor-new-button"
        addActionListener { openNewDocument(templateCombo.selectedItem as RewriteScriptEditorTemplate) }
    }
    private val saveButton = JButton("Save").apply {
        name = "rewrite-script-editor-save-button"
        addActionListener { selectedDocument()?.let { requestSave(it, forceSaveAs = false) } }
    }
    private val saveAsButton = JButton("Save As").apply {
        name = "rewrite-script-editor-save-as-button"
        addActionListener { selectedDocument()?.let { requestSave(it, forceSaveAs = true) } }
    }
    private val compileButton = JButton("Compile").apply {
        name = "rewrite-script-editor-compile-button"
        addActionListener { selectedDocument()?.let(::requestCompile) }
    }
    private val closeTabButton = JButton("Close Tab").apply {
        name = "rewrite-script-editor-close-tab-button"
        addActionListener { selectedDocument()?.let { attemptCloseDocument(it) } }
    }
    private val contentLayout = CardLayout()
    private val contentPanel = JPanel(contentLayout)
    private val documents = linkedMapOf<String, RewriteEditorDocument>()
    private val documentsByPath = linkedMapOf<String, RewriteEditorDocument>()
    private val documentsByComponent = linkedMapOf<java.awt.Component, RewriteEditorDocument>()
    private val auxiliaryWindows = linkedSetOf<JInternalFrame>()

    init {
        name = "rewrite-script-editor-window"
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        size = Dimension(820, 580)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(buildToolbar(), BorderLayout.NORTH)
            add(contentPanel, BorderLayout.CENTER)
        }
        contentPanel.add(emptyStateLabel, "empty")
        contentPanel.add(fileTabs, "tabs")
        fileTabs.addChangeListener { renderState() }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosing(event: InternalFrameEvent) {
                attemptCloseWindow()
            }

            override fun internalFrameClosed(event: InternalFrameEvent) {
                closeAuxiliaryWindows()
                windowScope.cancel()
            }
        })
        renderState()
    }

    internal fun openNewDocument(
        template: RewriteScriptEditorTemplate,
    ) {
        addDocument(RewriteEditorDocument.newDocument(template) { handleDocumentEdited() })
    }

    fun openFile(file: ClientStoredFile) {
        val existing = documentsByPath[file.path]
        if (existing != null) {
            existing.refreshFromFile(file)
            selectDocument(existing)
            return
        }
        addDocument(RewriteEditorDocument.openedFile(file) { handleDocumentEdited() })
    }

    private fun buildToolbar(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(templateCombo)
            add(newButton)
            add(saveButton)
            add(saveAsButton)
            add(compileButton)
            add(closeTabButton)
        }
    }

    private fun addDocument(
        document: RewriteEditorDocument,
    ) {
        documents[document.documentId] = document
        document.savedPath?.let { documentsByPath[it] = document }
        documentsByComponent[document.container] = document
        fileTabs.addTab(document.displayTabTitle(), document.container)
        fileTabs.selectedComponent = document.container
        renderState()
    }

    private fun selectedDocument(): RewriteEditorDocument? {
        return documentsByComponent[fileTabs.selectedComponent]
    }

    private fun selectDocument(document: RewriteEditorDocument) {
        fileTabs.selectedComponent = document.container
        renderState()
    }

    private fun handleDocumentEdited() {
        refreshTabTitles()
        renderState()
    }

    private fun refreshTabTitles() {
        for (index in 0 until fileTabs.tabCount) {
            val document = documentsByComponent[fileTabs.getComponentAt(index)] ?: continue
            fileTabs.setTitleAt(index, document.displayTabTitle())
        }
    }

    private fun renderState() {
        refreshTabTitles()
        val hasDocuments = documents.isNotEmpty()
        contentLayout.show(contentPanel, if (hasDocuments) "tabs" else "empty")
        val selected = selectedDocument()
        saveButton.isEnabled = selected != null && !selected.requestInFlight
        saveAsButton.isEnabled = selected != null && !selected.requestInFlight
        compileButton.isEnabled = selected?.kind == ClientStoredFileKind.SCRIPT_SOURCE && !selected.requestInFlight
        closeTabButton.isEnabled = selected != null && !selected.requestInFlight
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun requestSave(
        document: RewriteEditorDocument,
        forceSaveAs: Boolean,
        afterSuccess: (() -> Unit)? = null,
    ) {
        if (document.requestInFlight) {
            return
        }
        val currentPath = document.savedPath
        if (!forceSaveAs && currentPath != null) {
            performSave(
                document = document,
                directoryPath = directoryOf(currentPath),
                fileName = document.currentFileName(),
                afterSuccess = afterSuccess,
            )
            return
        }

        val chooser = RewriteLocalFileSaveChooserWindow(
            controller = controller,
            title = "Save File",
            initialPath = currentPath?.let(::directoryOf) ?: controller.gameFilesystemState()?.currentPath ?: "/",
            initialFileName = document.currentFileName(),
            onSaveSelected = { selection ->
                performSave(
                    document = document,
                    directoryPath = selection.directoryPath,
                    fileName = selection.fileName,
                    afterSuccess = afterSuccess,
                )
            },
        )
        trackAuxiliaryWindow(chooser)
        onOpenAuxiliaryWindow(chooser)
        onFocusAuxiliaryWindow(chooser)
    }

    private fun performSave(
        document: RewriteEditorDocument,
        directoryPath: String,
        fileName: String,
        afterSuccess: (() -> Unit)? = null,
    ) {
        val trimmedName = fileName.trim()
        if (trimmedName.isBlank()) {
            document.showError("File name is required.")
            renderState()
            return
        }
        val saveFile = document.toStoredFile(
            directoryPath = directoryPath,
            fileName = trimmedName,
            maker = controller.currentAuthenticatedPlayerIp().orEmpty(),
        )
        document.beginRequest()
        renderState()
        windowScope.launch {
            val result = controller.requestSaveFile(
                path = directoryPath,
                file = saveFile,
            )
            SwingUtilities.invokeLater {
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        val savedFile = saveFile.copy(path = buildFilePath(directoryPath, trimmedName))
                        resolveSavedPathConflict(document, savedFile.path)
                        document.finishSave(savedFile, result.value)
                        afterSuccess?.invoke()
                    }

                    is RewriteGameCommandResult.Failure -> {
                        document.finishFailure(result.message)
                    }
                }
                renderState()
            }
        }
    }

    private fun resolveSavedPathConflict(
        currentDocument: RewriteEditorDocument,
        savedPath: String,
    ) {
        val previousPath = currentDocument.savedPath
        previousPath?.let { documentsByPath.remove(it, currentDocument) }
        val conflicting = documentsByPath[savedPath]
        if (conflicting != null && conflicting !== currentDocument) {
            removeDocument(conflicting)
        }
        documentsByPath[savedPath] = currentDocument
    }

    private fun requestCompile(
        document: RewriteEditorDocument,
    ) {
        if (document.kind != ClientStoredFileKind.SCRIPT_SOURCE || document.requestInFlight) {
            return
        }
        if (document.isDirty()) {
            requestSave(
                document = document,
                forceSaveAs = document.savedPath == null,
                afterSuccess = { performCompile(document) },
            )
            return
        }
        performCompile(document)
    }

    private fun performCompile(
        document: RewriteEditorDocument,
    ) {
        val savedPath = document.savedPath ?: return
        document.beginRequest()
        renderState()
        windowScope.launch {
            val result = controller.requestCompileFile(
                path = directoryOf(savedPath),
                name = document.currentFileName(),
            )
            SwingUtilities.invokeLater {
                if (isClosed || !isDisplayable) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        document.finishCompile(result.value)
                    }

                    is RewriteGameCommandResult.Failure -> {
                        document.finishFailure(result.message)
                    }
                }
                renderState()
            }
        }
    }

    private fun attemptCloseDocument(
        document: RewriteEditorDocument,
        onClosed: (() -> Unit)? = null,
    ) {
        if (!document.isDirty()) {
            removeDocument(document)
            onClosed?.invoke()
            return
        }
        when (promptForUnsavedChanges(document)) {
            UnsavedChoice.SAVE -> requestSave(
                document = document,
                forceSaveAs = document.savedPath == null,
                afterSuccess = {
                    removeDocument(document)
                    onClosed?.invoke()
                },
            )

            UnsavedChoice.DONT_SAVE -> {
                removeDocument(document)
                onClosed?.invoke()
            }

            UnsavedChoice.CANCEL -> return
        }
    }

    private fun attemptCloseWindow() {
        val closingOrder = orderedDocumentsForWindowClose()
        if (closingOrder.isEmpty()) {
            dispose()
            return
        }
        closeDocumentsSequentially(closingOrder)
    }

    private fun closeDocumentsSequentially(
        remaining: List<RewriteEditorDocument>,
    ) {
        if (remaining.isEmpty()) {
            dispose()
            return
        }
        val document = remaining.first()
        if (documents[document.documentId] == null) {
            closeDocumentsSequentially(remaining.drop(1))
            return
        }
        attemptCloseDocument(document) {
            closeDocumentsSequentially(remaining.drop(1))
        }
    }

    private fun orderedDocumentsForWindowClose(): List<RewriteEditorDocument> {
        val selected = selectedDocument()
        return listOfNotNull(selected) + documents.values.filter { it !== selected }
    }

    private fun removeDocument(
        document: RewriteEditorDocument,
    ) {
        documents.remove(document.documentId)
        document.savedPath?.let { documentsByPath.remove(it, document) }
        documentsByComponent.remove(document.container)
        fileTabs.remove(document.container)
        renderState()
    }

    private fun promptForUnsavedChanges(
        document: RewriteEditorDocument,
    ): UnsavedChoice {
        val choice = JOptionPane.showOptionDialog(
            this,
            "Save changes to ${document.currentFileName()}?",
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

    private fun trackAuxiliaryWindow(
        window: JInternalFrame,
    ) {
        auxiliaryWindows += window
        window.addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                auxiliaryWindows.remove(window)
            }
        })
    }

    private fun closeAuxiliaryWindows() {
        val windows = auxiliaryWindows.toList()
        auxiliaryWindows.clear()
        windows.forEach { window ->
            runCatching { window.dispose() }
        }
    }

    private enum class UnsavedChoice {
        SAVE,
        DONT_SAVE,
        CANCEL,
    }
}

private class RewriteEditorDocument private constructor(
    val documentId: String = UUID.randomUUID().toString(),
    initialPath: String?,
    private var name: String,
    val kind: ClientStoredFileKind,
    private var description: String,
    private var quantity: Int,
    private var maker: String,
    private var compileCost: Double,
    private var cpuCost: Double,
    private var price: Double,
    private var compiledBinary: ClientCompiledBinaryMetadata?,
    private var scriptBundleFamily: ClientScriptFamily?,
    private val tabDefinitions: List<RewriteEditorTabDefinition>,
    initialTextsByKey: Map<String, String>,
    initiallySaved: Boolean,
    private val onEdited: () -> Unit,
) {
    var savedPath: String? = initialPath
        private set
    val container = JPanel(BorderLayout(0, 8))
    private val documentTabs = JTabbedPane().apply {
        tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
    }
    private val statusLabel = JLabel(" ").apply {
        foreground = java.awt.Color(0x1F, 0x4D, 0x24)
    }
    private val errorLabel = JLabel(" ").apply {
        foreground = java.awt.Color(0xAA, 0x22, 0x22)
    }
    private val areasByKey = linkedMapOf<String, JTextArea>()
    private var baselineTextsByKey = LinkedHashMap(initialTextsByKey)
    private var hasSavedBaseline = initiallySaved
    var requestInFlight: Boolean = false
        private set

    init {
        container.name = "rewrite-script-editor-document-${componentKey()}"
        container.border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
        documentTabs.name = "rewrite-script-editor-document-tabs-${componentKey()}"
        tabDefinitions.forEach { definition ->
            val key = editorTabKey(definition)
            val area = JTextArea(initialTextsByKey[key].orEmpty()).apply {
                isEditable = true
                lineWrap = false
                wrapStyleWord = false
                font = Font(Font.MONOSPACED, Font.PLAIN, 13)
                name = "rewrite-script-editor-content-${componentKey()}-${sanitizeWindowKey(definition.title)}"
                document.addDocumentListener(
                    object : DocumentListener {
                        override fun insertUpdate(event: DocumentEvent?) = onTextChanged()
                        override fun removeUpdate(event: DocumentEvent?) = onTextChanged()
                        override fun changedUpdate(event: DocumentEvent?) = onTextChanged()
                    },
                )
            }
            areasByKey[key] = area
            documentTabs.addTab(definition.title, JScrollPane(area))
        }
        container.add(documentTabs, BorderLayout.CENTER)
        container.add(
            JPanel(BorderLayout(0, 4)).apply {
                isOpaque = false
                add(statusLabel, BorderLayout.NORTH)
                add(errorLabel, BorderLayout.SOUTH)
            },
            BorderLayout.SOUTH,
        )
    }

    fun displayTabTitle(): String {
        val baseTitle = if (savedPath == null) "Untitled" else name
        return if (isDirty()) "$baseTitle *" else baseTitle
    }

    fun currentFileName(): String = if (savedPath == null) name else name

    fun isDirty(): Boolean {
        return !hasSavedBaseline || currentTextsByKey() != baselineTextsByKey
    }

    fun refreshFromFile(
        file: ClientStoredFile,
    ) {
        if (isDirty()) {
            return
        }
        name = file.name
        savedPath = file.path
        description = file.description
        quantity = file.quantity
        maker = file.maker
        compileCost = file.compileCost
        cpuCost = file.cpuCost
        price = file.price
        compiledBinary = file.compiledBinary
        scriptBundleFamily = file.scriptBundle?.family ?: file.compiledBinary?.scriptFamily
        val texts = initialTextsFor(file, tabDefinitions)
        baselineTextsByKey = LinkedHashMap(texts)
        hasSavedBaseline = true
        areasByKey.forEach { (key, area) ->
            area.text = texts[key].orEmpty()
            area.caretPosition = 0
        }
        refreshComponentNames()
        showStatus(null)
        showError(null)
        onEdited()
    }

    fun toStoredFile(
        directoryPath: String,
        fileName: String,
        maker: String,
    ): ClientStoredFile {
        val textsByKey = currentTextsByKey()
        val normalizedPath = buildFilePath(directoryPath, fileName)
        val scriptBundle = if (kind == ClientStoredFileKind.SCRIPT_SOURCE) {
            ClientProgramScriptBundle(
                family = scriptBundleFamily ?: ClientScriptFamily.GENERAL,
                scriptsBySlot = tabDefinitions.mapNotNull { definition ->
                    definition.slot?.let { slot ->
                        slot to textsByKey[editorTabKey(definition)].orEmpty()
                    }
                }.toMap(),
            )
        } else {
            null
        }
        return ClientStoredFile(
            path = normalizedPath,
            name = fileName,
            kind = kind,
            contents = if (kind == ClientStoredFileKind.SCRIPT_SOURCE) {
                serializeReadableScriptContents(tabDefinitions, textsByKey)
            } else {
                textsByKey.values.firstOrNull().orEmpty()
            },
            description = description,
            quantity = quantity.coerceAtLeast(1),
            maker = maker.ifBlank { this.maker },
            compileCost = compileCost,
            cpuCost = cpuCost,
            price = price,
            compiledBinary = if (kind == ClientStoredFileKind.SCRIPT_SOURCE) compiledBinary else null,
            scriptBundle = scriptBundle,
        )
    }

    fun beginRequest() {
        requestInFlight = true
        showError(null)
        showStatus(null)
        onEdited()
    }

    fun finishSave(
        savedFile: ClientStoredFile,
        response: ClientMutationAcceptedResponse,
    ) {
        requestInFlight = false
        name = savedFile.name
        savedPath = savedFile.path
        description = savedFile.description
        quantity = savedFile.quantity
        maker = savedFile.maker
        compileCost = savedFile.compileCost
        cpuCost = savedFile.cpuCost
        price = savedFile.price
        compiledBinary = savedFile.compiledBinary
        scriptBundleFamily = savedFile.scriptBundle?.family ?: savedFile.compiledBinary?.scriptFamily
        baselineTextsByKey = LinkedHashMap(currentTextsByKey())
        hasSavedBaseline = true
        refreshComponentNames()
        showStatus("Saved.")
        showError(null)
        onEdited()
    }

    fun finishCompile(
        response: ClientCompileFileResponse,
    ) {
        requestInFlight = false
        showStatus("Compiled ${response.compiledFile.name}.")
        showError(null)
        onEdited()
    }

    fun finishFailure(
        message: String,
    ) {
        requestInFlight = false
        showError(message)
        showStatus(null)
        onEdited()
    }

    fun showError(
        message: String?,
    ) {
        errorLabel.text = message ?: " "
    }

    private fun showStatus(
        message: String?,
    ) {
        statusLabel.text = message ?: " "
    }

    private fun currentTextsByKey(): Map<String, String> {
        return areasByKey.mapValues { (_, area) -> area.text }
    }

    private fun refreshComponentNames() {
        container.name = "rewrite-script-editor-document-${componentKey()}"
        documentTabs.name = "rewrite-script-editor-document-tabs-${componentKey()}"
        tabDefinitions.forEach { definition ->
            val key = editorTabKey(definition)
            areasByKey[key]?.name = "rewrite-script-editor-content-${componentKey()}-${sanitizeWindowKey(definition.title)}"
        }
    }

    private fun onTextChanged() {
        showStatus(null)
        showError(null)
        onEdited()
    }

    private fun componentKey(): String {
        return sanitizeWindowKey(savedPath ?: "untitled-$documentId")
    }

    companion object {
        fun newDocument(
            template: RewriteScriptEditorTemplate,
            onEdited: () -> Unit,
        ): RewriteEditorDocument {
            val spec = buildNewEditorDocumentSpec(template)
            return RewriteEditorDocument(
                initialPath = null,
                name = "Untitled",
                kind = spec.kind,
                description = "",
                quantity = 1,
                maker = "",
                compileCost = 0.0,
                cpuCost = 0.0,
                price = 0.0,
                compiledBinary = spec.compiledBinary,
                scriptBundleFamily = spec.scriptBundleFamily,
                tabDefinitions = spec.tabDefinitions,
                initialTextsByKey = spec.initialTextsByKey,
                initiallySaved = false,
                onEdited = onEdited,
            )
        }

        fun openedFile(
            file: ClientStoredFile,
            onEdited: () -> Unit,
        ): RewriteEditorDocument {
            val definitions = buildEditableEditorTabDefinitions(file)
            return RewriteEditorDocument(
                initialPath = file.path,
                name = file.name,
                kind = file.kind,
                description = file.description,
                quantity = file.quantity,
                maker = file.maker,
                compileCost = file.compileCost,
                cpuCost = file.cpuCost,
                price = file.price,
                compiledBinary = file.compiledBinary,
                scriptBundleFamily = file.scriptBundle?.family ?: file.compiledBinary?.scriptFamily,
                tabDefinitions = definitions,
                initialTextsByKey = initialTextsFor(file, definitions),
                initiallySaved = true,
                onEdited = onEdited,
            )
        }

        private fun initialTextsFor(
            file: ClientStoredFile,
            definitions: List<RewriteEditorTabDefinition>,
        ): Map<String, String> {
            if (file.kind != ClientStoredFileKind.SCRIPT_SOURCE) {
                return linkedMapOf(editorTabKey(definitions.single()) to file.contents)
            }
            val scriptsBySlot = file.scriptBundle?.scriptsBySlot.orEmpty()
            if (definitions.size == 1 && definitions.single().slot == null) {
                return linkedMapOf(editorTabKey(definitions.single()) to file.contents)
            }
            return definitions.associate { definition ->
                editorTabKey(definition) to scriptsBySlot[definition.slot].orEmpty()
            }
        }
    }
}

private fun scriptTemplateSpec(
    family: ClientScriptFamily,
    applicationKind: ClientApplicationKind,
    tabs: List<RewriteEditorTabDefinition>,
): RewriteNewEditorDocumentSpec {
    return RewriteNewEditorDocumentSpec(
        kind = ClientStoredFileKind.SCRIPT_SOURCE,
        tabDefinitions = tabs,
        initialTextsByKey = tabs.associate { definition ->
            editorTabKey(definition) to DEFAULT_SCRIPT_TEMPLATE
        },
        scriptBundleFamily = family,
        compiledBinary = ClientCompiledBinaryMetadata(
            scriptFamily = family,
            applicationKind = applicationKind,
        ),
    )
}

private fun editorTabKey(
    definition: RewriteEditorTabDefinition,
): String = definition.slot?.name ?: "CONTENT"

private fun buildFilePath(
    directoryPath: String,
    fileName: String,
): String {
    val normalizedDirectory = if (directoryPath.isBlank()) "/" else directoryPath
    return if (normalizedDirectory == "/") {
        "/$fileName"
    } else {
        "${normalizedDirectory.trimEnd('/')}/$fileName"
    }
}

private fun directoryOf(
    filePath: String,
): String {
    val parent = filePath.substringBeforeLast('/', "")
    return if (parent.isBlank()) "/" else parent
}
