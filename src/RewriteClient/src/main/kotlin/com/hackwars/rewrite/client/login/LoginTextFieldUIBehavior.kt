package com.hackwars.rewrite.client.login

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.Point
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JTextField
import javax.swing.Timer
import javax.swing.border.EmptyBorder

interface TextFieldUIBehavior {
    fun installFieldDefaults(component: JTextField)
    fun paintFieldBackground(component: JTextField, graphics: Graphics?)
}

class LoginTextFieldUIBehavior : TextFieldUIBehavior {
    companion object {
        private const val GLOW_INSET = 4f
        private const val OUTER_ARC = 12f
        private const val INNER_INSET = 1.25f
    }

    private var scanX = -80f
    private var component: JTextField? = null

    init {
        Timer(16) {
            component?.let { field ->
                if (!field.isFocusOwner) {
                    if (scanX != -80f) {
                        scanX = -80f
                        field.repaint()
                    }
                    return@let
                }

                scanX += 3.2f
                if (scanX > field.width + 80f) {
                    scanX = -80f
                }
                field.repaint()
            }
        }.start()
    }

    override fun installFieldDefaults(component: JTextField) {
        this.component = component
        component.isOpaque = false
        component.border = EmptyBorder(0, 16, 0, 16)
        component.background = Color(0x08, 0x11, 0x1D)
        component.foreground = Color(0xD3, 0xD9, 0xE3)
        component.caretColor = Color(0xD3, 0xD9, 0xE3)
        component.font = Font("Inter", Font.BOLD, 1).deriveFont(16f)
        component.preferredSize = Dimension(200, 40)
        component.minimumSize = Dimension(100, 40)
        component.maximumSize = Dimension(Int.MAX_VALUE, 40)
    }

    override fun paintFieldBackground(component: JTextField, graphics: Graphics?) {
        val g2 = graphics?.create() as? Graphics2D ?: return
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.composite = AlphaComposite.SrcOver

        val width = component.width
        val height = component.height
        if (width <= 1 || height <= 1) {
            g2.dispose()
            return
        }

        val outerX = GLOW_INSET + 0.5f
        val outerY = GLOW_INSET + 0.5f
        val outerW = (width - (GLOW_INSET * 2f) - 1f).coerceAtLeast(2f)
        val outerH = (height - (GLOW_INSET * 2f) - 1f).coerceAtLeast(2f)
        val rect = RoundRectangle2D.Float(outerX, outerY, outerW, outerH, OUTER_ARC, OUTER_ARC)

        g2.color = component.background
        g2.fill(rect)

        val isFocused = component.isFocusOwner
        val glowLayers = if (isFocused) 2 else 4
        val innerGlowColor = Color(73, 199, 255)
        val outerGlowColor = Color(4, 12, 20)
        val innerGlowAlpha = if (isFocused) 30 else 60
        val outerGlowAlpha = 20
        for (index in glowLayers downTo 1) {
            val linearT = if (glowLayers == 1) 1f else (index - 1f) / (glowLayers - 1f)
            val fade = 1f - linearT
            val falloff = fade * fade * fade * fade
            val layerAlpha =
                (outerGlowAlpha + ((innerGlowAlpha - outerGlowAlpha) * falloff)).toInt()
            g2.color = blend(outerGlowColor, innerGlowColor, fade).withAlpha(layerAlpha)
            g2.stroke = BasicStroke(index * 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.draw(rect)
        }

        if (isFocused) {
            g2.paint = LinearGradientPaint(
                scanX - 70f,
                0f,
                scanX + 70f,
                0f,
                floatArrayOf(0f, 0.5f, 1f),
                arrayOf(
                    Color(73, 199, 255, 0),
                    Color(73, 199, 255, 180),
                    Color(73, 199, 255, 0),
                ),
            )
            g2.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.draw(rect)
        }

        g2.color = Color(73, 199, 255).withAlpha(if (isFocused) 70 else 24)
        g2.stroke = BasicStroke(1f)
        g2.draw(rect)

        g2.paint = LinearGradientPaint(
            Point2D.Float(0f, 0f),
            Point2D.Float(width.toFloat(), 0f),
            floatArrayOf(0f, 0.55f, 1f),
            arrayOf(
                Color(0x08, 0x11, 0x1D),
                Color(0x09, 0x15, 0x22),
                Color(0x0A, 0x13, 0x20),
            ),
        )
        val innerArc = (OUTER_ARC - 2f).coerceAtLeast(2f)
        g2.fill(
            RoundRectangle2D.Float(
                outerX + INNER_INSET,
                outerY + INNER_INSET,
                (outerW - (INNER_INSET * 2f)).coerceAtLeast(1f),
                (outerH - (INNER_INSET * 2f)).coerceAtLeast(1f),
                innerArc,
                innerArc,
            ),
        )
        g2.dispose()
    }

    private fun blend(from: Color, to: Color, ratio: Float): Color {
        val clampedRatio = ratio.coerceIn(0f, 1f)
        val red = (from.red + ((to.red - from.red) * clampedRatio)).toInt()
        val green = (from.green + ((to.green - from.green) * clampedRatio)).toInt()
        val blue = (from.blue + ((to.blue - from.blue) * clampedRatio)).toInt()
        return Color(red, green, blue)
    }

    private fun Color.withAlpha(alpha: Int): Color {
        return Color(red, green, blue, alpha.coerceIn(0, 255))
    }
}
