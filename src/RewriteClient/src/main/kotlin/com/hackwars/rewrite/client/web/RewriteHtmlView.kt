package com.hackwars.rewrite.client.web

import java.net.URI
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.event.HyperlinkListener
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

internal class RewriteHtmlView(
    paneName: String,
    scrollPaneName: String,
    hyperlinkListener: HyperlinkListener? = null,
    autoFormSubmission: Boolean = false,
) {
    val editorKit = HTMLEditorKit().apply {
        isAutoFormSubmission = autoFormSubmission
    }
    val pane = JEditorPane().apply {
        name = paneName
        contentType = "text/html"
        isEditable = false
        editorKit = this@RewriteHtmlView.editorKit
        hyperlinkListener?.let(::addHyperlinkListener)
    }
    val scrollPane = JScrollPane(pane).apply {
        name = scrollPaneName
    }

    fun renderHtml(
        body: String,
        baseTarget: String? = null,
    ) {
        val document = editorKit.createDefaultDocument() as HTMLDocument
        if (!baseTarget.isNullOrBlank()) {
            runCatching {
                document.base = URI("http://$baseTarget/").toURL()
            }
        }
        pane.document = document
        pane.text = body
        pane.caretPosition = 0
    }
}
