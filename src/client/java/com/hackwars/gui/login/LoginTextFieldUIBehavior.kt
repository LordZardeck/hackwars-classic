package com.hackwars.gui.login

import java.awt.*
import java.awt.geom.Point2D
import javax.swing.JTextField
import javax.swing.border.EmptyBorder

interface TextFieldUIBehavior {
    fun installFieldDefaults(c: JTextField)
    fun paintFieldBackground(c: JTextField, g: Graphics?)
}

class LoginTextFieldUIBehavior : TextFieldUIBehavior {
    override fun installFieldDefaults(c: JTextField) {
        c.border = EmptyBorder(0, 24, 0, 24)
        c.foreground = Color(0xD3, 0xD9, 0xE3)
        c.caretColor = Color(0xD3, 0xD9, 0xE3)
        c.font = Font("Inter", Font.BOLD, 1).deriveFont(22f)
    }

    override fun paintFieldBackground(c: JTextField, g: Graphics?) {
        (g?.create() as? Graphics2D)?.run {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val width = c.width
            val height = c.height
            val glowAlpha = if (c.isFocusOwner) 58 else 26
            color = Color(0x47, 0xC5, 0xFF, glowAlpha)
            fillRoundRect(2, 2, width - 4, height - 4, 12, 12)

            val fillPaint = LinearGradientPaint(
                Point2D.Float(0f, 0f),
                Point2D.Float(width.toFloat(), 0f),
                floatArrayOf(0f, 0.55f, 1f),
                arrayOf(
                    Color(0x08, 0x11, 0x1D, 245),
                    Color(0x09, 0x15, 0x22, 250),
                    Color(0x0A, 0x13, 0x20, 245)
                )
            )
            paint = fillPaint
            fillRoundRect(0, 0, width - 1, height - 1, 12, 12)

            paint = LinearGradientPaint(
                Point2D.Float(0f, 0f),
                Point2D.Float(width.toFloat(), 0f),
                floatArrayOf(0f, 1f),
                arrayOf(
                    Color(0x3E, 0x67, 0x8A, 204),
                    Color(0x31, 0x4D, 0x69, 204)
                )
            )
            stroke = BasicStroke(1.5f)
            drawRoundRect(1, 1, width - 3, height - 3, 11, 11)
            dispose()
        }
    }
}