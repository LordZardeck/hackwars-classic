package com.hackwars.rewrite.client.web

import java.awt.Color
import java.awt.Font
import java.awt.Insets
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

internal class RewriteHtmlView(
    paneName: String,
    scrollPaneName: String,
    paneBackground: Color = Color.WHITE,
    textFont: Font = Font(Font.SANS_SERIF, Font.PLAIN, 12),
    autoFormSubmission: Boolean = false,
) {
    val editorKit = HTMLEditorKit().apply {
        isAutoFormSubmission = autoFormSubmission
        styleSheet.addRule(
            """
                body {
                    margin: 0;
                    padding: 8px;
                    background: #FFFFFF;
                    color: #000000;
                    font-family: ${textFont.family};
                    font-size: ${textFont.size}pt;
                }
                a { color: #0000CC; }
                p, div, span { margin: 0; }
            """.trimIndent(),
        )
    }
    val pane = JEditorPane().apply {
        name = paneName
        contentType = "text/html"
        isEditable = false
        background = paneBackground
        foreground = Color.BLACK
        isOpaque = true
        editorKit = this@RewriteHtmlView.editorKit
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        this.font = textFont
        border = BorderFactory.createEmptyBorder()
        margin = Insets(0, 0, 0, 0)
    }
    val scrollPane = JScrollPane(pane).apply {
        name = scrollPaneName
        border = BorderFactory.createLineBorder(Color(0xB7, 0xB7, 0xB7))
        viewport.background = paneBackground
        isOpaque = true
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
