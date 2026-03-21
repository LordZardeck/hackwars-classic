package browser

import gui.TutorialWindow
import gui.WebBrowser
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.lobobrowser.html.FormInput
import org.lobobrowser.html.HtmlRendererContext
import org.lobobrowser.html.gui.HtmlPanel
import org.lobobrowser.html.parser.DocumentBuilderImpl
import org.lobobrowser.html.parser.InputSourceImpl
import org.lobobrowser.html.test.SimpleHtmlRendererContext
import org.lobobrowser.html.test.SimpleUserAgentContext
import org.w3c.dom.html2.HTMLElement
import org.w3c.dom.html2.HTMLLinkElement
import org.xml.sax.InputSource
import java.awt.Cursor
import java.awt.EventQueue
import java.awt.event.MouseEvent
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.net.URL
import java.util.Enumeration
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import javax.swing.JInternalFrame
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

open class HtmlHandler {
    private val htmlPanel = HtmlPanel()
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("HtmlHandler"))
    private val renderQueue = Channel<RenderTask>(Channel.UNLIMITED)

    @Volatile
    private var parent: Any? = null

    init {
        renderScope.launch {
            processQueue()
        }
    }

    val view: HtmlPanel
        get() = htmlPanel

    fun parseDocument(url: URL?, frame: JInternalFrame?) {
        enqueue(RenderTask.Url(url, frame))
    }

    @JvmOverloads
    fun parseDocument(data: String?, frame: JInternalFrame? = null) {
        enqueue(RenderTask.Html(safeHtml(data), frame))
    }

    fun setParent(parent: Any?) {
        this.parent = parent
    }

    fun shutdown() {
        renderQueue.close()
        renderScope.cancel()
    }

    private fun enqueue(task: RenderTask) {
        if (!renderQueue.trySend(task).isSuccess) {
            renderScope.launch {
                renderQueue.send(task)
            }
        }
    }

    private suspend fun processQueue() {
        for (task in renderQueue) {
            runCatching {
                when (task) {
                    is RenderTask.Url -> renderUrl(task.url, task.frame)
                    is RenderTask.Html -> renderHtml(task.data, task.frame)
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    private suspend fun renderUrl(url: URL?, frame: JInternalFrame?) {
        if (url == null) {
            renderHtml(safeHtml(null), frame)
            return
        }

        url.openConnection().getInputStream().use { input ->
            InputStreamReader(input).use { reader ->
                val renderedDocument = buildDocument(reader, url.toExternalForm())
                showDocument(renderedDocument, frame, preferredWidth = null)
            }
        }
    }

    private suspend fun renderHtml(data: String, frame: JInternalFrame?) {
        StringReader(data).use { reader ->
            val renderedDocument = buildDocument(reader, "about:blank")
            showDocument(renderedDocument, frame, preferredWidth = 800)
        }
    }

    private fun buildDocument(reader: Reader, uri: String): RenderedDocument {
        val rendererContext: HtmlRendererContext = LocalHtmlRendererContext(htmlPanel)
        val inputSource: InputSource = InputSourceImpl(reader, uri)
        val builder = DocumentBuilderImpl(rendererContext.userAgentContext, rendererContext)
        val document = builder.parse(inputSource)
        return RenderedDocument(document, rendererContext)
    }

    private suspend fun showDocument(
        renderedDocument: RenderedDocument,
        frame: JInternalFrame?,
        preferredWidth: Int?
    ) {
        if (EventQueue.isDispatchThread()) {
            applyRenderedDocument(renderedDocument, frame, preferredWidth)
            return
        }

        suspendCancellableCoroutine { continuation ->
            EventQueue.invokeLater {
                runCatching {
                    applyRenderedDocument(renderedDocument, frame, preferredWidth)
                }.onSuccess {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }.onFailure {
                    if (continuation.isActive) {
                        continuation.resumeWithException(it)
                    }
                }
            }
        }
    }

    private fun applyRenderedDocument(
        renderedDocument: RenderedDocument,
        frame: JInternalFrame?,
        preferredWidth: Int?
    ) {
        preferredWidth?.let {
            htmlPanel.setPreferredWidth(it)
        }
        htmlPanel.setDocument(renderedDocument.document, renderedDocument.rendererContext)
        silenceLoboLoggers()
        frame?.validate()
    }

    private fun silenceLoboLoggers() {
        val loggerNames: Enumeration<String> = LogManager.getLogManager().loggerNames
        while (loggerNames.hasMoreElements()) {
            val loggerName = loggerNames.nextElement()
            Logger.getLogger(loggerName).level = Level.OFF
        }
    }

    private fun safeHtml(data: String?): String {
        if (data != null) {
            return data
        }
        return "<html><body><h2>Page unavailable</h2>" +
            "<p>The server returned no page content.</p>" +
            "<p>Check server connectivity and try again.</p>" +
            "</body></html>"
    }

    private sealed interface RenderTask {
        data class Url(val url: URL?, val frame: JInternalFrame?) : RenderTask
        data class Html(val data: String, val frame: JInternalFrame?) : RenderTask
    }

    private data class RenderedDocument(
        val document: org.w3c.dom.Document,
        val rendererContext: HtmlRendererContext
    )

    private class LocalHtmlRendererContext(contextComponent: HtmlPanel?) :
        SimpleHtmlRendererContext(contextComponent, SimpleUserAgentContext()) {
        override fun navigate(href: URL, target: String?) {
            println("URL CLick.")
            println("HREF: ${href}")
            if (parent is WebBrowser) {
                val webBrowser = parent as WebBrowser
                webBrowser.setLink(href)
            } else if (parent is TutorialWindow) {
                val tutorialWindow = parent as TutorialWindow
                tutorialWindow.linkGo(href)
            }
        }

        override fun submitForm(
            method: String?,
            action: URL,
            target: String?,
            enctype: String?,
            formInputs: Array<FormInput?>
        ) {
            if (parent is WebBrowser) {
                val webBrowser = parent as WebBrowser
                val link = action.toString().split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                var page = ""
                if (link.size > 3) {
                    page = link[3]
                }
                if (page == "search.html") {
                    webBrowser.newSearch(formInputs[0]!!.textValue, "")
                } else {
                    webBrowser.submitForm(formInputs)
                }
            }
        }

        override fun alert(message: String?) {
        }

        override fun confirm(message: String?): Boolean {
            return false
        }

        override fun prompt(message: String?, inputDefault: String?): String {
            return ""
        }

        override fun reload() {
        }

        override fun back() {
        }

        override fun onMouseOut(element: HTMLElement?, event: MouseEvent?) {
            if (element is HTMLLinkElement && parent is JInternalFrame) {
                val internalFrame = parent as JInternalFrame
                internalFrame.cursor = Cursor(Cursor.DEFAULT_CURSOR)
            }
        }

        override fun onMouseOver(element: HTMLElement?, event: MouseEvent?) {
            if (element is HTMLLinkElement && parent is JInternalFrame) {
                val internalFrame = parent as JInternalFrame
                internalFrame.cursor = Cursor(Cursor.HAND_CURSOR)
            }
        }

        override fun onContextMenu(element: HTMLElement?, event: MouseEvent): Boolean {
            if (element is HTMLLinkElement && parent is JInternalFrame) {
                val menu = JPopupMenu()
                var menuItem = JMenuItem("Open")
                menu.add(menuItem)
                menuItem = JMenuItem("Open in new tab")
                menu.add(menuItem)
            }
            return true
        }
    }
}
