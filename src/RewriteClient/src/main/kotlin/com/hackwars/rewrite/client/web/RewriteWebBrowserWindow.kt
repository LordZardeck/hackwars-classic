package com.hackwars.rewrite.client.web

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientWebsiteRenderResponse
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import javax.swing.text.html.FormSubmitEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal enum class RewriteWebNavigationMode {
    REQUEST,
    SUBMIT,
}

internal data class RewriteWebNavigationIntent(
    val target: String,
    val parameters: Map<String, String> = emptyMap(),
    val mode: RewriteWebNavigationMode = RewriteWebNavigationMode.REQUEST,
)

internal data class RewriteLoadedWebPage(
    val requestedTarget: String,
    val resolvedTarget: String,
    val response: ClientWebsiteRenderResponse,
)

internal class RewriteWebHistory(
    private val maxEntries: Int = 10,
) {
    private val entries = mutableListOf<RewriteWebNavigationIntent>()
    private var currentIndex: Int = -1

    fun current(): RewriteWebNavigationIntent? = entries.getOrNull(currentIndex)

    fun canGoBack(): Boolean = currentIndex > 0

    fun canGoForward(): Boolean = currentIndex in 0 until entries.lastIndex

    fun push(intent: RewriteWebNavigationIntent) {
        if (currentIndex in 0 until entries.lastIndex) {
            entries.subList(currentIndex + 1, entries.size).clear()
        }
        entries += intent
        if (entries.size > maxEntries) {
            val overflow = entries.size - maxEntries
            repeat(overflow) {
                entries.removeAt(0)
            }
            currentIndex = entries.lastIndex
        } else {
            currentIndex = entries.lastIndex
        }
    }

    fun back(): RewriteWebNavigationIntent? {
        if (!canGoBack()) {
            return null
        }
        currentIndex -= 1
        return current()
    }

    fun forward(): RewriteWebNavigationIntent? {
        if (!canGoForward()) {
            return null
        }
        currentIndex += 1
        return current()
    }
}

internal fun normalizeBrowserTarget(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) {
        return ""
    }
    val parsedHost = parseBrowserUri(trimmed)?.host?.takeIf { it.isNotBlank() }
    val baseValue = parsedHost ?: trimmed
    return baseValue
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .removeSuffix("/")
        .lowercase()
}

internal fun parseBrowserAddressNavigation(
    rawValue: String,
): RewriteWebNavigationIntent? {
    val trimmed = rawValue.trim()
    if (trimmed.isBlank()) {
        return null
    }
    val parsedUri = parseBrowserUri(trimmed)
    val target = normalizeBrowserTarget(trimmed)
    if (target.isBlank()) {
        return null
    }
    val parameters = parseBrowserQueryParameters(
        parsedUri?.rawQuery ?: trimmed.substringAfter('?', missingDelimiterValue = ""),
    )
    return RewriteWebNavigationIntent(
        target = target,
        parameters = parameters,
    )
}

internal fun parseBrowserHyperlinkNavigation(
    reference: String,
    currentResolvedTarget: String,
): RewriteWebNavigationIntent? {
    val trimmed = reference.trim()
    if (trimmed.isBlank() || trimmed.startsWith("#")) {
        return null
    }
    return if (isAbsoluteBrowserReference(trimmed)) {
        parseBrowserAddressNavigation(trimmed)
    } else {
        RewriteWebNavigationIntent(
            target = currentResolvedTarget,
            parameters = parseBrowserQueryParameters(trimmed.substringAfter('?', missingDelimiterValue = "")),
        )
    }
}

internal fun parseBrowserFormNavigation(
    actionReference: String?,
    currentResolvedTarget: String,
    parameters: Map<String, String>,
): RewriteWebNavigationIntent? {
    val trimmed = actionReference?.trim().orEmpty()
    return if (trimmed.isBlank() || !isAbsoluteBrowserReference(trimmed)) {
        RewriteWebNavigationIntent(
            target = currentResolvedTarget,
            parameters = parameters,
            mode = RewriteWebNavigationMode.SUBMIT,
        )
    } else {
        val absoluteIntent = parseBrowserAddressNavigation(trimmed) ?: return null
        absoluteIntent.copy(
            parameters = absoluteIntent.parameters + parameters,
            mode = RewriteWebNavigationMode.SUBMIT,
        )
    }
}

internal fun parseBrowserQueryParameters(rawQuery: String): Map<String, String> {
    if (rawQuery.isBlank()) {
        return emptyMap()
    }
    return rawQuery
        .split('&')
        .filter { it.isNotBlank() }
        .associate { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            decodeBrowserQueryComponent(key) to decodeBrowserQueryComponent(value)
        }
}

internal fun formatBrowserAddress(
    target: String,
    parameters: Map<String, String>,
): String {
    if (parameters.isEmpty()) {
        return target
    }
    return buildString {
        append(target)
        append('?')
        append(
            parameters.entries.joinToString("&") { (key, value) ->
                if (value.isBlank()) {
                    key
                } else {
                    "$key=$value"
                }
            },
        )
    }
}

private fun parseBrowserUri(rawValue: String): URI? {
    val trimmed = rawValue.trim()
    if (trimmed.isBlank() || trimmed.startsWith("/") || trimmed.startsWith("?") || trimmed.startsWith("#")) {
        return null
    }
    val candidate = if ("://" in trimmed) trimmed else "http://$trimmed"
    return runCatching { URI(candidate) }.getOrNull()
}

private fun isAbsoluteBrowserReference(reference: String): Boolean {
    if ("://" in reference) {
        return true
    }
    if (reference.startsWith("/") || reference.startsWith("?") || reference.startsWith("#")) {
        return false
    }
    return normalizeBrowserTarget(reference).isNotBlank() && !reference.contains(' ')
}

private fun decodeBrowserQueryComponent(component: String): String {
    return URLDecoder.decode(component, StandardCharsets.UTF_8)
}

internal class RewriteWebBrowserWindow(
    private val controller: RewriteRootController,
    private val command: RewriteShellCommand,
) : JInternalFrame(command.title, true, true, true, true) {
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val history = RewriteWebHistory()
    private val htmlView = RewriteHtmlView(
        paneName = "rewrite-web-html-pane",
        scrollPaneName = "rewrite-web-html-scroll",
        hyperlinkListener = ::handleHyperlinkEvent,
        autoFormSubmission = false,
    )
    private val addressField = JTextField().apply {
        name = "rewrite-web-address-field"
        addActionListener { navigateFromAddressField() }
    }
    private val backButton = JButton("Back").apply {
        name = "rewrite-web-back-button"
        addActionListener { history.back()?.let { navigate(it, pushHistory = false) } }
    }
    private val forwardButton = JButton("Forward").apply {
        name = "rewrite-web-forward-button"
        addActionListener { history.forward()?.let { navigate(it, pushHistory = false) } }
    }
    private val homeButton = JButton("Home").apply {
        name = "rewrite-web-home-button"
        addActionListener { navigateHome() }
    }
    private val refreshButton = JButton("Refresh").apply {
        name = "rewrite-web-refresh-button"
        addActionListener { refreshCurrentPage() }
    }
    private val voteButton = JButton("Vote").apply {
        name = "rewrite-web-vote-button"
        addActionListener { requestVote() }
    }
    private val statusLabel = JLabel(" ").apply {
        name = "rewrite-web-status-label"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val storeRowsPanel = JPanel().apply {
        name = "rewrite-web-store-rows"
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val storePanel = JPanel(BorderLayout(0, 8)).apply {
        name = "rewrite-web-store-panel"
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        add(JLabel("Store Listing"), BorderLayout.NORTH)
        add(JScrollPane(storeRowsPanel), BorderLayout.CENTER)
        preferredSize = Dimension(320, 0)
        isVisible = false
    }
    private val splitPane = JSplitPane(
        JSplitPane.HORIZONTAL_SPLIT,
        htmlView.scrollPane,
        storePanel,
    ).apply {
        border = null
        resizeWeight = 0.78
        dividerLocation = 740
        isOneTouchExpandable = true
    }

    private var loadedPage: RewriteLoadedWebPage? = null
    private var requestInFlight: Boolean = false
    private var closing: Boolean = false

    init {
        name = when (command) {
            RewriteShellCommand.WEB_BROWSER -> "rewrite-web-browser-window"
            RewriteShellCommand.STORE -> "rewrite-store-window"
            else -> "rewrite-web-window-${command.stableId}"
        }
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(1000, 650)
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(buildToolbar(), BorderLayout.NORTH)
            add(splitPane, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }
        addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosing(event: InternalFrameEvent) {
                closing = true
                sendExitForCurrentPageIfNeeded(nextTarget = null)
            }

            override fun internalFrameClosed(event: InternalFrameEvent) {
                closing = true
                windowScope.cancel()
            }
        })
        renderState()
        SwingUtilities.invokeLater {
            if (!isClosed && isDisplayable) {
                navigateInitial()
            }
        }
    }

    private fun buildToolbar(): JPanel {
        return JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    isOpaque = false
                    add(backButton)
                    add(forwardButton)
                    add(homeButton)
                    add(refreshButton)
                    add(voteButton)
                },
                BorderLayout.WEST,
            )
            add(addressField, BorderLayout.CENTER)
        }
    }

    private fun navigateInitial() {
        val initialTarget = when (command) {
            RewriteShellCommand.STORE -> "store"
            else -> controller.currentAuthenticatedPlayerIp()
        }
        if (initialTarget.isNullOrBlank()) {
            showError("Not connected to a rewrite game session.")
            return
        }
        navigate(
            RewriteWebNavigationIntent(target = initialTarget),
            pushHistory = true,
        )
    }

    private fun navigateHome() {
        val playerIp = controller.currentAuthenticatedPlayerIp()
        if (playerIp.isNullOrBlank()) {
            showError("Not connected to a rewrite game session.")
            return
        }
        navigate(
            RewriteWebNavigationIntent(target = playerIp),
            pushHistory = true,
        )
    }

    private fun navigateFromAddressField() {
        val intent = parseBrowserAddressNavigation(addressField.text)
        if (intent == null) {
            showError("Enter a valid website target.")
            return
        }
        navigate(intent, pushHistory = true)
    }

    private fun refreshCurrentPage() {
        history.current()?.let { navigate(it, pushHistory = false, sendExit = false) }
    }

    internal fun navigate(
        intent: RewriteWebNavigationIntent,
        pushHistory: Boolean,
        sendExit: Boolean = true,
    ) {
        if (intent.target.isBlank()) {
            showError("Enter a valid website target.")
            return
        }
        if (sendExit) {
            sendExitForCurrentPageIfNeeded(nextTarget = intent.target)
        }
        if (pushHistory) {
            history.push(intent)
        }
        requestInFlight = true
        addressField.text = formatBrowserAddress(intent.target, intent.parameters)
        showError(null)
        renderState()
        windowScope.launch {
            val result = when (intent.mode) {
                RewriteWebNavigationMode.REQUEST -> controller.requestWebpage(
                    targetIp = intent.target,
                    parameters = intent.parameters,
                )

                RewriteWebNavigationMode.SUBMIT -> controller.submitWebpage(
                    targetIp = intent.target,
                    parameters = intent.parameters,
                )
            }
            SwingUtilities.invokeLater {
                if (closing || isClosed) {
                    return@invokeLater
                }
                requestInFlight = false
                when (result) {
                    is RewriteGameCommandResult.Success -> renderPage(
                        requestedTarget = intent.target,
                        response = result.value,
                    )

                    is RewriteGameCommandResult.Failure -> {
                        showError(result.message)
                        renderState()
                    }
                }
            }
        }
    }

    internal fun handleHyperlinkReference(reference: String) {
        val currentTarget = loadedPage?.resolvedTarget ?: return
        val intent = parseBrowserHyperlinkNavigation(reference, currentTarget) ?: return
        navigate(intent, pushHistory = true)
    }

    internal fun handleFormSubmission(
        actionReference: String?,
        parameters: Map<String, String>,
    ) {
        val currentTarget = loadedPage?.resolvedTarget ?: return
        val intent = parseBrowserFormNavigation(actionReference, currentTarget, parameters) ?: return
        navigate(intent, pushHistory = true, sendExit = intent.target != currentTarget)
    }

    private fun handleHyperlinkEvent(event: HyperlinkEvent) {
        if (event.eventType != HyperlinkEvent.EventType.ACTIVATED) {
            return
        }
        if (event is FormSubmitEvent) {
            handleFormSubmission(
                actionReference = event.url?.toString(),
                parameters = parseBrowserQueryParameters(event.data.orEmpty()),
            )
            return
        }
        val reference = event.description ?: event.url?.toString() ?: return
        handleHyperlinkReference(reference)
    }

    internal fun requestPurchase(
        file: ClientStoredFile,
        quantity: Int,
    ) {
        val targetIp = currentPurchaseTarget() ?: return
        if (quantity <= 0) {
            showError("Purchase quantity must be positive.")
            return
        }
        showError(null)
        windowScope.launch {
            val result = controller.requestPurchase(
                targetIp = targetIp,
                fileName = file.name,
                quantity = quantity,
            )
            SwingUtilities.invokeLater {
                if (closing || isClosed) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> {
                        showError(null)
                        statusLabel.text = "Purchased ${result.value.fulfilledQuantity} x ${result.value.purchasedFile.name}."
                        refreshCurrentPage()
                    }

                    is RewriteGameCommandResult.Failure -> showError(result.message)
                }
                renderState()
            }
        }
    }

    private fun requestVote() {
        val targetIp = loadedPage?.resolvedTarget ?: return
        windowScope.launch {
            val result = controller.voteForWebsite(targetIp)
            SwingUtilities.invokeLater {
                if (closing || isClosed) {
                    return@invokeLater
                }
                when (result) {
                    is RewriteGameCommandResult.Success -> statusLabel.text =
                        "Vote recorded. ${result.value.votesAvailableAfter} votes remaining."

                    is RewriteGameCommandResult.Failure -> showError(result.message)
                }
                renderState()
            }
        }
    }

    private fun currentPurchaseTarget(): String? {
        val currentHistoryTarget = history.current()?.target?.takeIf { it.isNotBlank() }
        return currentHistoryTarget ?: loadedPage?.resolvedTarget?.takeIf { it.isNotBlank() }
    }

    private fun renderPage(
        requestedTarget: String,
        response: ClientWebsiteRenderResponse,
    ) {
        loadedPage = RewriteLoadedWebPage(
            requestedTarget = requestedTarget,
            resolvedTarget = response.resolvedTargetStateId,
            response = response,
        )
        title = "${command.title} - ${response.title}"
        htmlView.renderHtml(
            body = response.body,
            baseTarget = response.resolvedTargetStateId,
        )
        renderStoreFiles(response.storeFiles)
        statusLabel.text = if (response.fallback) {
            "Unable to open website."
        } else {
            " "
        }
        renderState()
    }

    private fun renderStoreFiles(files: List<ClientStoredFile>) {
        storeRowsPanel.removeAll()
        files.forEachIndexed { index, file ->
            storeRowsPanel.add(buildStoreRow(index, file))
            storeRowsPanel.add(Box.createVerticalStrut(8))
        }
        storePanel.isVisible = files.isNotEmpty()
        storeRowsPanel.revalidate()
        storeRowsPanel.repaint()
        splitPane.revalidate()
    }

    private fun buildStoreRow(
        index: Int,
        file: ClientStoredFile,
    ): JPanel {
        val quantitySpinner = JSpinner(
            SpinnerNumberModel(
                1,
                1,
                maxOf(1, file.quantity),
                1,
            ),
        ).apply {
            name = "rewrite-web-store-quantity-$index"
        }
        return JPanel(BorderLayout(8, 8)).apply {
            name = "rewrite-web-store-row-$index"
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x4A, 0x4D, 0x4D)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8),
            )
            add(
                JPanel().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(JLabel(file.name).apply { name = "rewrite-web-store-name-$index" })
                    add(JLabel("Kind: ${file.kind.name}"))
                    add(JLabel("Maker: ${file.maker.ifBlank { "Unknown" }}"))
                    add(JLabel("Price: $${"%.2f".format(file.price)}"))
                    add(JLabel("Quantity: ${file.quantity}"))
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                    isOpaque = false
                    add(quantitySpinner)
                    add(
                        JButton("Buy").apply {
                            name = "rewrite-web-store-buy-button-$index"
                            addActionListener {
                                requestPurchase(
                                    file = file,
                                    quantity = (quantitySpinner.value as Number).toInt(),
                                )
                            }
                        },
                    )
                },
                BorderLayout.SOUTH,
            )
        }
    }

    private fun sendExitForCurrentPageIfNeeded(nextTarget: String?) {
        val currentPage = loadedPage ?: return
        if (currentPage.response.fallback || currentPage.resolvedTarget.isBlank()) {
            return
        }
        if (
            nextTarget != null &&
            (
                nextTarget == currentPage.requestedTarget ||
                    nextTarget == currentPage.resolvedTarget
                )
        ) {
            return
        }
        controller.exitWebpage(currentPage.resolvedTarget)
    }

    private fun showError(message: String?) {
        statusLabel.text = message?.takeIf { it.isNotBlank() } ?: " "
    }

    private fun renderState() {
        backButton.isEnabled = history.canGoBack() && !requestInFlight
        forwardButton.isEnabled = history.canGoForward() && !requestInFlight
        refreshButton.isEnabled = history.current() != null && !requestInFlight
        voteButton.isEnabled = loadedPage?.response?.fallback == false && !requestInFlight
    }
}
