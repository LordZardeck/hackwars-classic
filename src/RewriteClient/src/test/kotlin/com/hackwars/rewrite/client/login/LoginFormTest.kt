package com.hackwars.rewrite.client.login

import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.test.Test

class LoginFormTest {
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
