package com.hackwars.gui.login

import java.awt.*
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JTextField
import javax.swing.Timer
import javax.swing.border.EmptyBorder


interface TextFieldUIBehavior {
    fun installFieldDefaults(c: JTextField)
    fun paintFieldBackground(c: JTextField, g: Graphics?)
}

class LoginTextFieldUIBehavior : TextFieldUIBehavior {
    private var scanX = -80f

    private var component: JTextField? = null

    init {
        Timer(16) {
            component?.let {
                if(!it.isFocusOwner) {
                    scanX = -80f
                    return@let
                }

                scanX += 3.2f
                if (scanX > it.width + 80f) scanX = -80f
                it.repaint()
            }
        }.start()
    }

    override fun installFieldDefaults(c: JTextField) {
        component = c
        c.isOpaque = false
        c.border = EmptyBorder(0, 24, 0, 24)
        c.background = Color(0x08, 0x11, 0x1D)
        c.foreground = Color(0xD3, 0xD9, 0xE3)
        c.caretColor = Color(0xD3, 0xD9, 0xE3)
        c.font = Font("Inter", Font.BOLD, 1).deriveFont(22f)
    }

    override fun paintFieldBackground(c: JTextField, g: Graphics?) {
        (g?.create() as? Graphics2D)?.run {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            composite = AlphaComposite.SrcOver

            val width = c.width
            val height = c.height
            if (width <= 1 || height <= 1) {
                dispose()
                return
            }

            /** Draw Scan Line around input */
            val rect: RoundRectangle2D =
                RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, 12f, 12f)

            // Ensure a deterministic dark base each repaint without painting square corners.
            color = c.background
            fill(rect)

            // Soft static glow
            color = Color(73, 199, 255, 10)
            for (i in 3 downTo 1) {
                stroke = BasicStroke(i * 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                draw(rect)
            }

            val scanner = LinearGradientPaint(
                scanX - 70f, 0f, scanX + 70f, 0f,
                floatArrayOf(0f, 0.5f, 1f),
                arrayOf<Color>(
                    Color(73, 199, 255, 0),
                    Color(73, 199, 255, 180),
                    Color(73, 199, 255, 0)
                )
            )
            paint = scanner
            stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            if(c.isFocusOwner) {
                draw(rect)
            }

            color = Color(73, 199, 255, 70)
            stroke = BasicStroke(1f)
            draw(rect)

            // Draw background over gradient lines so that the inside of the input doesn't have the gradient
            val fillPaint = LinearGradientPaint(
                Point2D.Float(0f, 0f),
                Point2D.Float(width.toFloat(), 0f),
                floatArrayOf(0f, 0.55f, 1f),
                arrayOf(
                    Color(0x08, 0x11, 0x1D),
                    Color(0x09, 0x15, 0x22),
                    Color(0x0A, 0x13, 0x20)
                )
            )
            paint = fillPaint
            fill(
                RoundRectangle2D.Float(1.5f, 1.5f, width - 3f, height - 3f, 10f, 10f)
            )

            dispose()
        }
    }

    private fun Color.withAlpha(alpha: Int): Color = Color(red, green, blue, alpha.coerceIn(0, 255))
}
