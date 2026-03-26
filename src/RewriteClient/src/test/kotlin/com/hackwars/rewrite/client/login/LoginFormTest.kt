package com.hackwars.rewrite.client.login

import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginFormTest {
    @Test
    fun loginFormDimensionsMatchLegacyParitySizing() {
        val form = LoginForm()

        assertEquals(Dimension(320, 360), form.minimumSize)
        assertEquals(Dimension(420, 420), form.preferredSize)
        assertEquals(Dimension(420, 520), form.maximumSize)
    }

    @Test
    fun loginFormCanBeRepaintedRepeatedlyWithoutColorRangeErrors() {
        val form = LoginForm().apply {
            size = Dimension(420, 440)
            doLayout()
        }

        repeat(32) {
            val image = BufferedImage(form.width, form.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                form.paint(graphics)
            } finally {
                graphics.dispose()
            }
        }
    }
}
