package com.hackwars.rewrite.client.login

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

    override fun paintComponent(graphics: Graphics?) {
        val width = width
        val xOffset = width * 0.1
        val y = height / 2

        val g2 = graphics?.create() as? Graphics2D ?: return
        g2.paint = LinearGradientPaint(
            Point2D.Float(xOffset.toFloat(), y.toFloat()),
            Point2D.Float((width - xOffset).toFloat(), y.toFloat()),
            floatArrayOf(0f, 0.5f, 1f),
            arrayOf(
                Color(0x2D, 0x5E, 0x8B, 0),
                Color(0x49, 0xC7, 0xFF, 166),
                Color(0x2D, 0x5E, 0x8B, 0),
            ),
        )
        g2.stroke = BasicStroke(1f)
        g2.drawLine(xOffset.toInt(), y, (width - xOffset).toInt(), y)
        g2.dispose()
    }
}
