package com.hackwars.rewrite.client.files

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.clientmodel.RewriteDecodedGameState
import com.hackwars.rewrite.clientmodel.SelectorStore
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientStoredFile
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

internal enum class RewriteLocalDirectoryEntryType {
    DIRECTORY,
    FILE,
}

internal data class RewriteLocalDirectoryBrowserEntry(
    val path: String,
    val name: String,
    val description: String,
    val type: RewriteLocalDirectoryEntryType,
    val directory: ClientDirectoryEntry? = null,
    val file: ClientStoredFile? = null,
) {
    val isDirectory: Boolean
        get() = type == RewriteLocalDirectoryEntryType.DIRECTORY

    override fun toString(): String = name
}

internal data class RewriteLocalFileSelection(
    val displayedPath: String,
    val file: ClientStoredFile,
)

internal data class RewriteLocalDirectoryBrowserState(
    val displayedPath: String = "/",
    val listing: ClientDirectoryListingResponse? = null,
    val entries: List<RewriteLocalDirectoryBrowserEntry> = emptyList(),
    val selectedPath: String? = null,
    val requestInFlight: Boolean = false,
    val inlineError: String? = null,
)

internal class RewriteLocalDirectoryBrowserController(
    private val rootController: RewriteRootController,
    private val directoryFilter: (ClientDirectoryEntry) -> Boolean = { true },
    private val fileFilter: (ClientStoredFile) -> Boolean = { true },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AutoCloseable {
    private val store = SelectorStore(RewriteLocalDirectoryBrowserState())
    private var disposed: Boolean = false
    private var queuedRequest: QueuedRequest? = null
    private var lastHandledFilesystemDeltaSignature: FilesystemDeltaSignature? = null

    init {
        observeDecodedGameState()
    }

    fun snapshot(): RewriteLocalDirectoryBrowserState = store.snapshot()

    fun selector(): Flow<RewriteLocalDirectoryBrowserState> = store.selector { it }

    fun activate() {
        requestDirectory(null)
    }

    fun navigateHome() {
        requestDirectory("/")
    }

    fun navigateUp() {
        requestDirectory(parentDirectoryPath(currentDisplayedPath()))
    }

    fun updateSelection(selectedPath: String?) {
        store.update { state -> state.copy(selectedPath = selectedPath) }
    }

    fun openSelectedDirectory() {
        val entry = selectedEntry() ?: return
        if (!entry.isDirectory) {
            return
        }
        requestDirectory(entry.path)
    }

    fun chooseSelectedFile(): RewriteLocalFileSelection? {
        val state = snapshot()
        val entry = selectedEntry() ?: return null
        val file = entry.file ?: return null
        return RewriteLocalFileSelection(
            displayedPath = currentDisplayedPath(),
            file = file,
        )
    }

    override fun close() {
        disposed = true
        queuedRequest = null
        scope.cancel()
    }

    private fun observeDecodedGameState() {
        scope.launch {
            rootController.gameDecodedStateSelector().collect { decodedState ->
                SwingUtilities.invokeLater {
                    if (disposed) {
                        return@invokeLater
                    }
                    maybeRefreshForFilesystemDelta(decodedState)
                }
            }
        }
    }

    private fun maybeRefreshForFilesystemDelta(decodedState: RewriteDecodedGameState) {
        val lastDelta = decodedState.lastDelta ?: return
        val signature = FilesystemDeltaSignature(
            deltaKeys = lastDelta.metadata.deltaKeys,
            changedPaths = lastDelta.metadata.changedPaths,
            receivedAtEpochMillis = lastDelta.metadata.receivedAtEpochMillis,
        )
        if (signature == lastHandledFilesystemDeltaSignature) {
            return
        }
        lastHandledFilesystemDeltaSignature = signature
        if ("filesystem" !in signature.deltaKeys) {
            return
        }
        val listing = snapshot().listing ?: return
        val shouldRefresh = signature.changedPaths.isEmpty() ||
            signature.changedPaths.any { changedPath ->
                changedPath.affectsDisplayedDirectory(listing.path)
            }
        if (shouldRefresh) {
            requestDirectory(listing.path)
        }
    }

    private fun requestDirectory(path: String?) {
        if (disposed) {
            return
        }
        val state = snapshot()
        if (state.requestInFlight) {
            queuedRequest = QueuedRequest(path)
            return
        }
        store.update { current ->
            current.copy(
                requestInFlight = true,
                inlineError = null,
            )
        }
        scope.launch {
            val result = rootController.requestDirectory(path)
            SwingUtilities.invokeLater {
                if (disposed) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> applyListing(result.value)
                    is RewriteGameCommandResult.Failure -> {
                        store.update { current ->
                            current.copy(
                                requestInFlight = false,
                                inlineError = result.message,
                            )
                        }
                    }
                }
                val nextQueued = queuedRequest
                queuedRequest = null
                if (nextQueued != null) {
                    requestDirectory(nextQueued.path)
                }
            }
        }
    }

    private fun applyListing(response: ClientDirectoryListingResponse) {
        val previousSelection = snapshot().selectedPath
        val entries = buildEntries(response)
        val nextSelection = entries
            .firstOrNull { it.path == previousSelection }
            ?.path
        store.update { current ->
            current.copy(
                displayedPath = response.path,
                listing = response,
                entries = entries,
                selectedPath = nextSelection,
                requestInFlight = false,
                inlineError = null,
            )
        }
    }

    private fun buildEntries(response: ClientDirectoryListingResponse): List<RewriteLocalDirectoryBrowserEntry> {
        val directories = response.directories
            .filter(directoryFilter)
            .map { directory ->
                RewriteLocalDirectoryBrowserEntry(
                    path = directory.path,
                    name = directory.name,
                    description = directory.description,
                    type = RewriteLocalDirectoryEntryType.DIRECTORY,
                    directory = directory,
                )
            }
        val files = response.files
            .filter(fileFilter)
            .map { file ->
                RewriteLocalDirectoryBrowserEntry(
                    path = file.path,
                    name = file.name,
                    description = file.description,
                    type = RewriteLocalDirectoryEntryType.FILE,
                    file = file,
                )
            }
        return directories + files
    }

    private fun selectedEntry(): RewriteLocalDirectoryBrowserEntry? {
        val selectedPath = snapshot().selectedPath ?: return null
        return snapshot().entries.firstOrNull { it.path == selectedPath }
    }

    private fun currentDisplayedPath(): String {
        val state = snapshot()
        return state.listing?.path ?: state.displayedPath
    }

    private data class QueuedRequest(
        val path: String?,
    )

    private data class FilesystemDeltaSignature(
        val deltaKeys: List<String>,
        val changedPaths: List<String>,
        val receivedAtEpochMillis: Long?,
    )
}

internal class RewriteLocalDirectoryBrowserPanel(
    private val browserController: RewriteLocalDirectoryBrowserController,
    private val primaryActionLabel: String,
    private val primaryActionName: String,
    private val onPrimaryAction: () -> Unit,
    private val canRunPrimaryAction: (RewriteLocalDirectoryBrowserState) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : JPanel(BorderLayout()) {
    private val pathValueLabel = JLabel("/").apply {
        name = "rewrite-files-path-label"
        font = font.deriveFont(Font.BOLD)
    }
    private val statusLabel = JLabel("Loading directory...").apply {
        name = "rewrite-files-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-files-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val listModel = DefaultListModel<RewriteLocalDirectoryBrowserEntry>()
    val entryList = JList(listModel).apply {
        name = "rewrite-files-entry-list"
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = RewriteLocalDirectoryEntryRenderer()
    }
    val upButton = JButton("Up").apply {
        name = "rewrite-files-up-button"
        addActionListener { browserController.navigateUp() }
    }
    val homeButton = JButton("Home").apply {
        name = "rewrite-files-home-button"
        addActionListener { browserController.navigateHome() }
    }
    val primaryButton = JButton(primaryActionLabel).apply {
        name = primaryActionName
        addActionListener { onPrimaryAction() }
    }

    init {
        name = "rewrite-files-browser-panel"
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        val header = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(JLabel("Path:"), BorderLayout.WEST)
            add(pathValueLabel, BorderLayout.CENTER)
        }
        val actionBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(upButton)
            add(homeButton)
            add(primaryButton)
        }
        val footer = JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(statusLabel, BorderLayout.NORTH)
            add(errorLabel, BorderLayout.SOUTH)
        }
        add(header, BorderLayout.NORTH)
        add(JScrollPane(entryList).apply {
            name = "rewrite-files-scroll-pane"
            preferredSize = Dimension(440, 260)
        }, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout(0, 8)).apply {
                isOpaque = false
                add(actionBar, BorderLayout.NORTH)
                add(footer, BorderLayout.SOUTH)
            },
            BorderLayout.SOUTH,
        )

        entryList.addListSelectionListener {
            val selected = entryList.selectedValue as? RewriteLocalDirectoryBrowserEntry
            browserController.updateSelection(selected?.path)
            render(browserController.snapshot())
        }
        entryList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount >= 2 && entryList.selectedIndex >= 0) {
                    onPrimaryAction()
                }
            }
        })

        scope.launch {
            browserController.selector().collect { state ->
                SwingUtilities.invokeLater {
                    render(state)
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun render(state: RewriteLocalDirectoryBrowserState) {
        pathValueLabel.text = state.listing?.path ?: state.displayedPath
        renderEntries(state)
        val selectedIndex = state.entries.indexOfFirst { it.path == state.selectedPath }
        if (selectedIndex >= 0) {
            entryList.selectedIndex = selectedIndex
            entryList.ensureIndexIsVisible(selectedIndex)
        } else {
            entryList.clearSelection()
        }
        val hasListing = state.listing != null
        statusLabel.text = when {
            state.requestInFlight && !hasListing -> "Loading directory..."
            hasListing && state.entries.isEmpty() -> "No files or folders in this directory."
            else -> " "
        }
        errorLabel.text = state.inlineError ?: " "
        val primaryEnabled = !state.requestInFlight && canRunPrimaryAction(state)
        primaryButton.isEnabled = primaryEnabled
        entryList.isEnabled = !state.requestInFlight
        upButton.isEnabled = !state.requestInFlight
        homeButton.isEnabled = !state.requestInFlight
    }

    private fun renderEntries(state: RewriteLocalDirectoryBrowserState) {
        val existingPaths = (0 until listModel.size()).map { index -> listModel.get(index).path }
        val nextPaths = state.entries.map { it.path }
        if (existingPaths == nextPaths) {
            for (index in state.entries.indices) {
                listModel.set(index, state.entries[index])
            }
            return
        }
        listModel.clear()
        state.entries.forEach { entry ->
            listModel.addElement(entry)
        }
    }
}

internal class RewriteHomeWindow(
    controller: RewriteRootController,
) : JInternalFrame("Home", true, true, true, true) {
    private val browserController = RewriteLocalDirectoryBrowserController(rootController = controller)
    private val browserPanel = RewriteLocalDirectoryBrowserPanel(
        browserController = browserController,
        primaryActionLabel = "Open",
        primaryActionName = "rewrite-files-open-button",
        onPrimaryAction = { browserController.openSelectedDirectory() },
        canRunPrimaryAction = { state ->
            state.entries.firstOrNull { it.path == state.selectedPath }?.isDirectory == true
        },
    )

    init {
        name = "rewrite-home-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(560, 420)
        contentPane = browserPanel
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                browserPanel.close()
                browserController.close()
            }
        })
        browserController.activate()
    }
}

internal class RewriteLocalFileChooserWindow(
    controller: RewriteRootController,
    title: String,
    private val onFileSelected: (RewriteLocalFileSelection) -> Unit,
    directoryFilter: (ClientDirectoryEntry) -> Boolean = { true },
    fileFilter: (ClientStoredFile) -> Boolean = { true },
) : JInternalFrame(title, true, true, true, true) {
    private val browserController = RewriteLocalDirectoryBrowserController(
        rootController = controller,
        directoryFilter = directoryFilter,
        fileFilter = fileFilter,
    )
    private val browserPanel = RewriteLocalDirectoryBrowserPanel(
        browserController = browserController,
        primaryActionLabel = "Choose",
        primaryActionName = "rewrite-files-choose-button",
        onPrimaryAction = {
            browserController.chooseSelectedFile()?.let(onFileSelected)
        },
        canRunPrimaryAction = { state ->
            state.entries.firstOrNull { it.path == state.selectedPath }?.file != null
        },
    )

    init {
        name = "rewrite-local-file-chooser-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(560, 420)
        contentPane = browserPanel
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                browserPanel.close()
                browserController.close()
            }
        })
        browserController.activate()
    }
}

private class RewriteLocalDirectoryEntryRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
        val entry = value as? RewriteLocalDirectoryBrowserEntry
        label.text = when (entry?.type) {
            RewriteLocalDirectoryEntryType.DIRECTORY -> "[DIR] ${entry.name}"
            RewriteLocalDirectoryEntryType.FILE -> "[FILE] ${entry.name}"
            null -> ""
        }
        return label
    }
}

private fun parentDirectoryPath(path: String): String {
    val normalized = path.ifBlank { "/" }
    if (normalized == "/") {
        return "/"
    }
    val parent = normalized.substringBeforeLast('/', "")
    return if (parent.isBlank()) "/" else parent
}

private fun String.affectsDisplayedDirectory(displayedPath: String): Boolean {
    if (this == "filesystem" || this == "filesystem.currentPath") {
        return true
    }
    val filesystemPath = when {
        startsWith("filesystem.directoriesByPath.") -> removePrefix("filesystem.directoriesByPath.")
        startsWith("filesystem.filesByPath.") -> removePrefix("filesystem.filesByPath.")
        else -> return false
    }
    if (filesystemPath == displayedPath) {
        return true
    }
    return if (displayedPath == "/") {
        val relative = filesystemPath.removePrefix("/")
        relative.isNotBlank() && !relative.contains('/')
    } else if (filesystemPath.startsWith("$displayedPath/")) {
        val relative = filesystemPath.removePrefix("$displayedPath/")
        relative.isNotBlank() && !relative.contains('/')
    } else {
        false
    }
}
