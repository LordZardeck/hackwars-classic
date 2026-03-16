package com.hackwars.gui.login

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.geom.Point2D
import javax.swing.JPanel

class LoginFormSeparator : JPanel() {
    init {
        isOpaque = false
        preferredSize = Dimension(300, 50)
    }

    override fun paintComponent(g: Graphics?) {
        val width = width
        val xOffset = width * .1
        val y = (height / 2)

        (g?.create() as? Graphics2D)?.run {
            val linePaint = LinearGradientPaint(
                Point2D.Float(xOffset.toFloat(), y.toFloat()),
                Point2D.Float((width - xOffset).toFloat(), y.toFloat()),
                floatArrayOf(0f, 0.5f, 1f),
                arrayOf(
                    Color(0x2D, 0x5E, 0x8B, 0),
                    Color(0x49, 0xC7, 0xFF, 166),
                    Color(0x2D, 0x5E, 0x8B, 0)
                )
            )
            paint = linePaint
            stroke = BasicStroke(1f)
            drawLine(xOffset.toInt(), y, (width - xOffset).toInt(), y)
        }
    }
}