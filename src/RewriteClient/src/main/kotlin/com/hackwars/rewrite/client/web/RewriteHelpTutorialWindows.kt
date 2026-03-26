package com.hackwars.rewrite.client.web

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.mvc.RewriteControllerBase
import com.hackwars.rewrite.client.mvc.RewriteFrameBinding
import com.hackwars.rewrite.client.mvc.RewriteView
import com.hackwars.rewrite.client.mvc.RewriteViewModel
import com.hackwars.rewrite.protocol.ClientHelpTopicEntry
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private val HELP_API_GROUPS: List<String> = listOf("Banking", "Attack", "FTP", "Watch", "Challenge", "Other")
private const val HELP_TUTORIALS_GROUP: String = "Tutorials"
private const val HELP_CHALLENGES_GROUP: String = "Challenges"
private const val FIRST_ATTACK_TUTORIAL_ID: String = "first-attack"

internal sealed interface RewriteHelpTreeNodeValue {
    val label: String

    data class Branch(
        override val label: String,
    ) : RewriteHelpTreeNodeValue

    data class Topic(
        val id: String,
        val name: String,
        val targetUrl: String,
    ) : RewriteHelpTreeNodeValue {
        override val label: String = name

        override fun toString(): String = name
    }
}

internal data class RewriteHelpTopicGroup(
    val label: String,
    val topics: List<RewriteHelpTreeNodeValue.Topic>,
)

internal data class RewriteHelpWindowModel(
    val tutorials: RewriteHelpTopicGroup = RewriteHelpTopicGroup("Tutorials", emptyList()),
    val apiGroups: List<RewriteHelpTopicGroup> = emptyList(),
    val challenges: RewriteHelpTopicGroup = RewriteHelpTopicGroup("Challenges", emptyList()),
    val selectedTopicId: String? = null,
    val renderedHtml: String = wrapHtmlBody("<p>Select a help topic.</p>"),
    val statusText: String = " ",
    val errorText: String = " ",
) : RewriteViewModel

internal data class RewriteTutorialWindowModel(
    val titleText: String = "Tutorial",
    val renderedHtml: String = wrapHtmlBody("<p>Loading tutorial...</p>"),
    val showOnStartup: Boolean = true,
    val statusText: String = " ",
    val errorText: String = " ",
) : RewriteViewModel

internal class RewriteHelpWindow :
    JInternalFrame("Help", true, true, true, true),
    RewriteView<RewriteHelpWindowModel> {
    private val treeRoot = DefaultMutableTreeNode(RewriteHelpTreeNodeValue.Branch("Help"))
    private val treeModel = DefaultTreeModel(treeRoot)
    private val topicPathsById = linkedMapOf<String, TreePath>()
    private val htmlView = RewriteHtmlView(
        paneName = "rewrite-help-html-pane",
        scrollPaneName = "rewrite-help-html-scroll",
    )

    internal val tree = JTree(treeModel).apply {
        name = "rewrite-help-tree"
        isRootVisible = true
        border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
    }
    internal val statusLabel = JLabel(" ").apply {
        name = "rewrite-help-status"
        foreground = Color(0x22, 0x55, 0x22)
    }
    internal val errorLabel = JLabel(" ").apply {
        name = "rewrite-help-error"
        foreground = Color(0x88, 0x22, 0x22)
    }

    init {
        name = "rewrite-help-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(700, 500)
        contentPane = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(
                JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    JScrollPane(tree).apply {
                        name = "rewrite-help-tree-scroll"
                        minimumSize = Dimension(220, 200)
                    },
                    htmlView.scrollPane,
                ).apply {
                    name = "rewrite-help-split"
                    resizeWeight = 0.30
                    dividerLocation = 220
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(BorderLayout()).apply {
                    name = "rewrite-help-status-panel"
                    add(statusLabel, BorderLayout.WEST)
                    add(errorLabel, BorderLayout.EAST)
                },
                BorderLayout.SOUTH,
            )
        }
    }

    override fun render(model: RewriteHelpWindowModel) {
        val newRoot = DefaultMutableTreeNode(RewriteHelpTreeNodeValue.Branch("Help"))
        topicPathsById.clear()

        val tutorialsNode = DefaultMutableTreeNode(RewriteHelpTreeNodeValue.Branch("Tutorials"))
        appendTopics(parent = tutorialsNode, topics = model.tutorials.topics)
        newRoot.add(tutorialsNode)

        val apisNode = DefaultMutableTreeNode(RewriteHelpTreeNodeValue.Branch("APIs"))
        model.apiGroups.forEach { group ->
            val groupNode = DefaultMutableTreeNode(RewriteHelpTreeNodeValue.Branch(group.label))
            appendTopics(parent = groupNode, topics = group.topics)
            apisNode.add(groupNode)
        }
        newRoot.add(apisNode)

        val challengesNode = DefaultMutableTreeNode(RewriteHelpTreeNodeValue.Branch("Challenges"))
        appendTopics(parent = challengesNode, topics = model.challenges.topics)
        newRoot.add(challengesNode)

        treeModel.setRoot(newRoot)
        expandAllRows()
        model.selectedTopicId?.let { selectedId ->
            topicPathsById[selectedId]?.let(tree::setSelectionPath)
        }
        htmlView.renderHtml(model.renderedHtml)
        statusLabel.text = model.statusText
        errorLabel.text = model.errorText
    }

    internal fun bindTreeSelection(listener: TreeSelectionListener) {
        tree.addTreeSelectionListener(listener)
    }

    internal fun unbindTreeSelection(listener: TreeSelectionListener) {
        tree.removeTreeSelectionListener(listener)
    }

    internal fun selectedTopic(): RewriteHelpTreeNodeValue.Topic? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return node.userObject as? RewriteHelpTreeNodeValue.Topic
    }

    private fun appendTopics(
        parent: DefaultMutableTreeNode,
        topics: List<RewriteHelpTreeNodeValue.Topic>,
    ) {
        topics.forEach { topic ->
            val child = DefaultMutableTreeNode(topic)
            parent.add(child)
            topicPathsById[topic.id] = TreePath(child.path)
        }
    }

    private fun expandAllRows() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row += 1
        }
    }
}

internal class RewriteTutorialWindow :
    JInternalFrame("Tutorial", true, true, true, true),
    RewriteView<RewriteTutorialWindowModel> {
    private val htmlView = RewriteHtmlView(
        paneName = "rewrite-tutorial-html-pane",
        scrollPaneName = "rewrite-tutorial-html-scroll",
    )

    internal val helpButton = JButton("Open Help").apply {
        name = "rewrite-tutorial-help-button"
    }
    internal val showOnStartupCheckBox = JCheckBox().apply {
        name = "rewrite-tutorial-show-on-startup"
    }
    internal val titleLabel = JLabel("Tutorial").apply {
        name = "rewrite-tutorial-title"
    }
    internal val startupLabel = JLabel("Show this tutorial on startup.").apply {
        name = "rewrite-tutorial-startup-label"
    }
    internal val statusLabel = JLabel(" ").apply {
        name = "rewrite-tutorial-status"
        foreground = Color(0x22, 0x55, 0x22)
    }
    internal val errorLabel = JLabel(" ").apply {
        name = "rewrite-tutorial-error"
        foreground = Color(0x88, 0x22, 0x22)
    }

    init {
        name = "rewrite-tutorial-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(600, 500)
        contentPane = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(
                JPanel(BorderLayout()).apply {
                    name = "rewrite-tutorial-header"
                    add(titleLabel, BorderLayout.WEST)
                    add(helpButton, BorderLayout.EAST)
                },
                BorderLayout.NORTH,
            )
            add(htmlView.scrollPane, BorderLayout.CENTER)
            add(
                JPanel(BorderLayout()).apply {
                    name = "rewrite-tutorial-footer"
                    add(
                        JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                            name = "rewrite-tutorial-startup-panel"
                            add(showOnStartupCheckBox)
                            add(startupLabel)
                        },
                        BorderLayout.WEST,
                    )
                    add(
                        JPanel(BorderLayout()).apply {
                            add(statusLabel, BorderLayout.WEST)
                            add(errorLabel, BorderLayout.EAST)
                        },
                        BorderLayout.SOUTH,
                    )
                },
                BorderLayout.SOUTH,
            )
        }
    }

    override fun render(model: RewriteTutorialWindowModel) {
        title = model.titleText
        titleLabel.text = model.titleText
        htmlView.renderHtml(model.renderedHtml)
        showOnStartupCheckBox.isSelected = model.showOnStartup
        statusLabel.text = model.statusText
        errorLabel.text = model.errorText
    }
}

internal class RewriteHelpWindowController(
    private val controller: RewriteRootController,
    private val view: RewriteHelpWindow,
) : RewriteControllerBase() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val treeSelectionListener = TreeSelectionListener {
        val topic = view.selectedTopic() ?: return@TreeSelectionListener
        if (topic.id == currentModel.selectedTopicId) {
            return@TreeSelectionListener
        }
        loadTopic(topic)
    }
    private var currentModel = RewriteHelpWindowModel(statusText = "Loading help topics...")

    init {
        onClose {
            view.unbindTreeSelection(treeSelectionListener)
            scope.cancel()
        }
        view.bindTreeSelection(treeSelectionListener)
        render(currentModel)
        scope.launch {
            loadHelpGroups()
        }
    }

    private suspend fun loadHelpGroups() {
        val tutorials = requestTopics(HELP_TUTORIALS_GROUP)
        val apiGroups = HELP_API_GROUPS.map { group ->
            RewriteHelpTopicGroup(
                label = group,
                topics = requestTopics(group),
            )
        }
        val challenges = requestTopics(HELP_CHALLENGES_GROUP)
        val firstTopic = tutorials.firstOrNull()
            ?: apiGroups.firstNotNullOfOrNull { group -> group.topics.firstOrNull() }
            ?: challenges.firstOrNull()
        currentModel = currentModel.copy(
            tutorials = RewriteHelpTopicGroup("Tutorials", tutorials),
            apiGroups = apiGroups,
            challenges = RewriteHelpTopicGroup("Challenges", challenges),
            statusText = if (firstTopic == null) "No help topics available." else "Select a help topic.",
            errorText = " ",
        )
        render(currentModel)
        firstTopic?.let(::loadTopic)
    }

    private suspend fun requestTopics(group: String): List<RewriteHelpTreeNodeValue.Topic> {
        return when (val result = controller.requestHelpTopicList(group)) {
            is RewriteGameCommandResult.Success -> result.value.topics.map(::toTopicNode)
            is RewriteGameCommandResult.Failure -> {
                currentModel = currentModel.copy(
                    statusText = " ",
                    errorText = result.message,
                )
                emptyList()
            }
        }
    }

    private fun loadTopic(topic: RewriteHelpTreeNodeValue.Topic) {
        scope.launch {
            val targetIp = parseHelpTopicTarget(topic.targetUrl)
            if (targetIp == null) {
                currentModel = currentModel.copy(
                    selectedTopicId = topic.id,
                    statusText = " ",
                    errorText = "The selected help topic could not be opened.",
                )
                render(currentModel)
                return@launch
            }
            currentModel = currentModel.copy(
                selectedTopicId = topic.id,
                statusText = "Loading ${topic.name}...",
                errorText = " ",
            )
            render(currentModel)
            currentModel = when (val result = controller.requestWebpage(targetIp = targetIp)) {
                is RewriteGameCommandResult.Success -> currentModel.copy(
                    selectedTopicId = topic.id,
                    renderedHtml = normalizeHtml(result.value.body),
                    statusText = "Viewing ${result.value.title}.",
                    errorText = " ",
                )

                is RewriteGameCommandResult.Failure -> currentModel.copy(
                    selectedTopicId = topic.id,
                    statusText = " ",
                    errorText = result.message,
                )
            }
            render(currentModel)
        }
    }

    private fun toTopicNode(entry: ClientHelpTopicEntry): RewriteHelpTreeNodeValue.Topic {
        return RewriteHelpTreeNodeValue.Topic(
            id = entry.id,
            name = entry.name,
            targetUrl = entry.targetUrl,
        )
    }

    private fun render(model: RewriteHelpWindowModel) {
        if (SwingUtilities.isEventDispatchThread()) {
            view.render(model)
            return
        }
        SwingUtilities.invokeLater { view.render(model) }
    }
}

internal class RewriteTutorialWindowController(
    private val controller: RewriteRootController,
    private val view: RewriteTutorialWindow,
    private val onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    private val onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
) : RewriteControllerBase() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var helpBinding: RewriteFrameBinding? = null
    private var currentModel = RewriteTutorialWindowModel(
        showOnStartup = tutorialStartupPreferenceEnabled(controller.gamePreferenceState()?.values?.get("attacktutorial")),
    )

    init {
        val helpListener = java.awt.event.ActionListener { openHelpWindow() }
        val startupListener = java.awt.event.ActionListener {
            persistTutorialPreference(view.showOnStartupCheckBox.isSelected)
        }
        view.helpButton.addActionListener(helpListener)
        view.showOnStartupCheckBox.addActionListener(startupListener)
        onClose {
            view.helpButton.removeActionListener(helpListener)
            view.showOnStartupCheckBox.removeActionListener(startupListener)
            helpBinding?.let { binding ->
                runCatching { binding.frame.dispose() }
                binding.close()
            }
            scope.cancel()
        }
        render(currentModel)
        scope.launch {
            loadTutorial()
        }
    }

    private fun openHelpWindow() {
        val existing = helpBinding?.frame
        if (existing != null && existing.isDisplayable && !existing.isClosed) {
            onFocusAuxiliaryWindow(existing)
            return
        }

        val helpView = RewriteHelpWindow()
        val helpController = RewriteHelpWindowController(
            controller = controller,
            view = helpView,
        )
        helpView.addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                helpBinding = null
            }
        })
        val binding = RewriteFrameBinding(
            frame = helpView,
            controller = helpController,
        )
        helpBinding = binding
        onOpenAuxiliaryWindow(binding.frame)
    }

    private suspend fun loadTutorial() {
        currentModel = currentModel.copy(
            statusText = "Loading tutorial...",
            errorText = " ",
        )
        render(currentModel)
        currentModel = when (val result = controller.requestTutorial(FIRST_ATTACK_TUTORIAL_ID)) {
            is RewriteGameCommandResult.Success -> currentModel.copy(
                titleText = result.value.title.ifBlank { "Tutorial" },
                renderedHtml = normalizeHtml(result.value.body),
                statusText = "Tutorial loaded.",
                errorText = " ",
            )

            is RewriteGameCommandResult.Failure -> currentModel.copy(
                statusText = " ",
                errorText = result.message,
            )
        }
        render(currentModel)
    }

    private fun persistTutorialPreference(selected: Boolean) {
        scope.launch {
            val persistedValue = if (selected) "true" else "false"
            currentModel = currentModel.copy(
                showOnStartup = selected,
                statusText = "Saving tutorial preference...",
                errorText = " ",
            )
            render(currentModel)
            currentModel = when (val result = controller.requestSetPreference("attacktutorial", persistedValue)) {
                is RewriteGameCommandResult.Success -> currentModel.copy(
                    showOnStartup = selected,
                    statusText = "Tutorial preference saved.",
                    errorText = " ",
                )

                is RewriteGameCommandResult.Failure -> currentModel.copy(
                    showOnStartup = !selected,
                    statusText = " ",
                    errorText = result.message,
                )
            }
            render(currentModel)
        }
    }

    private fun render(model: RewriteTutorialWindowModel) {
        if (SwingUtilities.isEventDispatchThread()) {
            view.render(model)
            return
        }
        SwingUtilities.invokeLater { view.render(model) }
    }
}

internal fun createTutorialWindowBinding(
    controller: RewriteRootController,
    onOpenAuxiliaryWindow: (JInternalFrame) -> Unit,
    onFocusAuxiliaryWindow: (JInternalFrame) -> Unit,
): RewriteFrameBinding {
    val view = RewriteTutorialWindow()
    val controllerBinding = RewriteTutorialWindowController(
        controller = controller,
        view = view,
        onOpenAuxiliaryWindow = onOpenAuxiliaryWindow,
        onFocusAuxiliaryWindow = onFocusAuxiliaryWindow,
    )
    return RewriteFrameBinding(
        frame = view,
        controller = controllerBinding,
    )
}

private fun tutorialStartupPreferenceEnabled(rawValue: String?): Boolean {
    return rawValue.isNullOrBlank() || rawValue.equals("true", ignoreCase = true)
}

private fun parseHelpTopicTarget(targetUrl: String): String? {
    return runCatching { URI(targetUrl).host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

private fun wrapHtmlBody(body: String): String {
    return "<html><body>$body</body></html>"
}

private fun normalizeHtml(body: String): String {
    val trimmed = body.trim()
    return if (trimmed.startsWith("<html", ignoreCase = true)) trimmed else wrapHtmlBody(trimmed)
}
