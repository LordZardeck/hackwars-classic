package com.hackwars.gui.login

import com.github.weisj.jsvg.geometry.size.FloatSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.math.abs

class LoginBackgroundPanelTest {
    @Test
    fun getScaledLogoSize_returnsOriginalSize_whenOffsetsAlreadyValid() {
        val panel = LoginBackgroundPanel()
        val original = FloatSize(300f, 300f)
        val scaled = panel.getScaledLogoSize(original, Dimension(1280, 832))

        assertFloatEquals(original.width, scaled.width)
        assertFloatEquals(original.height, scaled.height)
    }

    @Test
    fun getScaledLogoSize_scalesDown_andRespectsMinOffsets() {
        val panel = LoginBackgroundPanel()
        val original = FloatSize(600f, 900f)
        val scaled = panel.getScaledLogoSize(original, Dimension(1280, 832))

        val leftOffset = SPLIT_X - (scaled.width / 2f)
        val topOffset = (832f / 2f) - (scaled.height / 2f)
        val widthScale = scaled.width / original.width
        val heightScale = scaled.height / original.height

        assertTrue("Scaled width must stay positive", scaled.width > 0f)
        assertTrue("Scaled height must stay positive", scaled.height > 0f)
        assertTrue("Expected logo to scale down", widthScale < 1f)
        assertTrue("Left offset must respect minimum", leftOffset >= LOGO_LEFT_MIN_OFFSET - EPSILON)
        assertTrue("Top offset must respect minimum", topOffset >= LOGO_TOP_MIN_OFFSET - EPSILON)
        assertFloatEquals(widthScale, heightScale)
    }

    @Test
    fun paint_rendersExpectedSplitGradientColors_atTopEdge() {
        val panel = LoginBackgroundPanel()
        panel.size = Dimension(640, 400)

        val image = BufferedImage(640, 400, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            panel.paint(graphics)
        } finally {
            graphics.dispose()
        }

        val leftTop = Color(image.getRGB(10, 0), true)
        val rightTop = Color(image.getRGB(400, 0), true)
        val splitLeftTop = Color(image.getRGB(307, 0), true)
        val splitRightTop = Color(image.getRGB(308, 0), true)

        assertEquals(Color(0x3F, 0x4D, 0x57), leftTop)
        assertEquals(Color(0x2C, 0xEE, 0xFF), rightTop)
        assertEquals(Color(0x3F, 0x4D, 0x57), splitLeftTop)
        assertEquals(Color(0x2C, 0xEE, 0xFF), splitRightTop)
        assertTrue("Left and right sides must be different colors at the split", leftTop != rightTop)
    }

    private fun assertFloatEquals(expected: Float, actual: Float) {
        assertTrue("Expected $expected but was $actual", abs(expected - actual) <= EPSILON)
    }

    companion object {
        private const val SPLIT_X = 308f
        private const val LOGO_LEFT_MIN_OFFSET = 60f
        private const val LOGO_TOP_MIN_OFFSET = 60f
        private const val EPSILON = 0.001f
    }
}
