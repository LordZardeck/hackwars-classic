package com.hackwars.rewrite.client.web

import java.awt.Color
import java.awt.Font
import javax.swing.JEditorPane
import javax.swing.border.LineBorder
import javax.swing.text.html.HTMLDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RewriteHtmlViewTest {
    @Test
    fun htmlViewUsesConfiguredDisplayPropertiesAndViewportChrome() {
        val view = RewriteHtmlView(
            paneName = "pane",
            scrollPaneName = "scroll",
            paneBackground = Color(0xF2, 0xF2, 0xF2),
            textFont = Font(Font.MONOSPACED, Font.BOLD, 14),
        )

        assertEquals("pane", view.pane.name)
        assertEquals(Color(0xF2, 0xF2, 0xF2), view.pane.background)
        assertEquals(Font.MONOSPACED, view.pane.font.family)
        assertEquals(14, view.pane.font.size)
        assertEquals(true, view.pane.getClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES))
        assertEquals(Color(0xF2, 0xF2, 0xF2), view.scrollPane.viewport.background)
        assertTrue(view.scrollPane.border is LineBorder)
    }

    @Test
    fun renderHtmlSetsDocumentBaseWhenTargetProvided() {
        val view = RewriteHtmlView(
            paneName = "pane",
            scrollPaneName = "scroll",
        )

        view.renderHtml(
            body = "<html><body><p>Ready</p></body></html>",
            baseTarget = "198.51.100.40",
        )

        val document = view.pane.document as HTMLDocument
        assertEquals("http://198.51.100.40/", document.base.toString())
        assertTrue(view.pane.text.contains("Ready"))
    }
}
