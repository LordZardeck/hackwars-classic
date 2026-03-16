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
    private companion object {
        const val GLOW_INSET = 4f
        const val OUTER_ARC = 12f
        const val INNER_INSET = 1.25f
    }

    private var scanX = -80f

    private var component: JTextField? = null

    init {
        Timer(16) {
            component?.let {
                if (!it.isFocusOwner) {
                    if (scanX != -80f) {
                        scanX = -80f
                        it.repaint()
                    }
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
        c.border = EmptyBorder(0, 16, 0, 16)
        c.background = Color(0x08, 0x11, 0x1D)
        c.foreground = Color(0xD3, 0xD9, 0xE3)
        c.caretColor = Color(0xD3, 0xD9, 0xE3)
        c.font = Font("Inter", Font.BOLD, 1).deriveFont(16f)
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

            // Keep border/glow slightly inset so thicker strokes are not clipped by component bounds.
            val outerX = GLOW_INSET + 0.5f
            val outerY = GLOW_INSET + 0.5f
            val outerW = (width - (GLOW_INSET * 2f) - 1f).coerceAtLeast(2f)
            val outerH = (height - (GLOW_INSET * 2f) - 1f).coerceAtLeast(2f)
            val rect = RoundRectangle2D.Float(outerX, outerY, outerW, outerH, OUTER_ARC, OUTER_ARC)

            // Ensure a deterministic dark base each repaint without painting square corners.
            color = c.background
            fill(rect)

            // Soft static glow
            val isFocused = c.isFocusOwner
            val glowLayers = if (isFocused) 2 else 4
            val innerGlowColor = Color(73, 199, 255)
            val outerGlowColor = Color(4, 12, 20)
            val innerGlowAlpha = if (isFocused) 30 else 60
            val outerGlowAlpha = 20
            for (i in glowLayers downTo 1) {
                val linearT = if (glowLayers == 1) 1f else (i - 1f) / (glowLayers - 1f)
                val t = linearT
                // Aggressive falloff: keep only the innermost band bright.
                val fade = 1f - t
                val falloff = fade * fade * fade * fade
                val layerAlpha =
                    (outerGlowAlpha + ((innerGlowAlpha - outerGlowAlpha) * falloff)).toInt()
                color = blend(outerGlowColor, innerGlowColor, fade).withAlpha(layerAlpha)
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
            if (isFocused) {
                draw(rect)
            }

            color = Color(73, 199, 255).withAlpha(if (isFocused) 70 else 24)
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
            val innerArc = (OUTER_ARC - 2f).coerceAtLeast(2f)
            fill(
                RoundRectangle2D.Float(
                    outerX + INNER_INSET,
                    outerY + INNER_INSET,
                    (outerW - (INNER_INSET * 2f)).coerceAtLeast(1f),
                    (outerH - (INNER_INSET * 2f)).coerceAtLeast(1f),
                    innerArc,
                    innerArc
                )
            )

            dispose()
        }
    }

    private fun blend(from: Color, to: Color, t: Float): Color {
        val ratio = t.coerceIn(0f, 1f)
        val r = (from.red + ((to.red - from.red) * ratio)).toInt()
        val g = (from.green + ((to.green - from.green) * ratio)).toInt()
        val b = (from.blue + ((to.blue - from.blue) * ratio)).toInt()
        return Color(r, g, b)
    }

    private fun Color.withAlpha(alpha: Int): Color = Color(red, green, blue, alpha.coerceIn(0, 255))
}
