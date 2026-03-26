package com.hackwars.rewrite.client.login

import com.github.weisj.jsvg.geometry.size.FloatSize
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginBackgroundPanelTest {
    @Test
    fun getScaledLogoSizeReturnsOriginalSizeWhenOffsetsAlreadyFit() {
        val panel = LoginBackgroundPanel()
        val original = FloatSize(300f, 300f)

        val scaled = panel.getScaledLogoSize(original, Dimension(1280, 832))

        assertFloatEquals(original.width, scaled.width)
        assertFloatEquals(original.height, scaled.height)
    }

    @Test
    fun getScaledLogoSizeScalesDownAndRespectsMinimumOffsets() {
        val panel = LoginBackgroundPanel()
        val componentSize = Dimension(1280, 832)
        val original = FloatSize(600f, 900f)

        val scaled = panel.getScaledLogoSize(original, componentSize)

        val splitX = getSplitX(componentSize.width).toFloat()
        val widthScale = (componentSize.width / REFERENCE_WIDTH).coerceAtMost(1f)
        val heightScale = (componentSize.height / REFERENCE_HEIGHT).coerceAtMost(1f)
        val minLeftOffset = LOGO_LEFT_MIN_OFFSET * widthScale
        val minTopOffset = LOGO_TOP_MIN_OFFSET * heightScale
        val leftOffset = splitX - (scaled.width / 2f)
        val topOffset = (componentSize.height / 2f) - (scaled.height / 2f)
        val widthScaleFactor = scaled.width / original.width
        val heightScaleFactor = scaled.height / original.height

        assertTrue(scaled.width > 0f)
        assertTrue(scaled.height > 0f)
        assertTrue(widthScaleFactor < 1f)
        assertTrue(leftOffset >= minLeftOffset - EPSILON)
        assertTrue(topOffset >= minTopOffset - EPSILON)
        assertFloatEquals(widthScaleFactor, heightScaleFactor)
    }

    @Test
    fun splitGradientColorsRenderAtTheExpectedBoundary() {
        val panel = LoginBackgroundPanel()
        val componentSize = Dimension(640, 400)
        panel.setCentered(false, animate = false)
        panel.size = componentSize

        val image = renderPanel(panel, componentSize)
        val splitX = getSplitX(componentSize.width)

        assertEquals(Color(0x3F, 0x4D, 0x57), Color(image.getRGB(10, 0), true))
        assertEquals(Color(0x2C, 0xEE, 0xFF), Color(image.getRGB(400, 0), true))
        assertEquals(Color(0x3F, 0x4D, 0x57), Color(image.getRGB(splitX - 1, 0), true))
        assertEquals(Color(0x2C, 0xEE, 0xFF), Color(image.getRGB(splitX, 0), true))
    }

    @Test
    fun splitPositionCapsAtMaximumWidth() {
        val panel = LoginBackgroundPanel()
        panel.setCentered(false, animate = false)
        val componentSize = Dimension(2000, 400)
        val splitX = getSplitX(componentSize.width)

        val image = renderPanel(panel, componentSize)

        assertEquals(MAX_LEFT_SPLIT_WIDTH, splitX)
        assertEquals(Color(0x3F, 0x4D, 0x57), Color(image.getRGB(splitX - 1, 0), true))
        assertEquals(Color(0x2C, 0xEE, 0xFF), Color(image.getRGB(splitX, 0), true))
    }

    private fun renderPanel(panel: LoginBackgroundPanel, componentSize: Dimension): BufferedImage {
        panel.size = componentSize
        return BufferedImage(componentSize.width, componentSize.height, BufferedImage.TYPE_INT_ARGB).also { image ->
            val graphics = image.createGraphics()
            try {
                panel.paint(graphics)
            } finally {
                graphics.dispose()
            }
        }
    }

    private fun getSplitX(componentWidth: Int): Int {
        val split = (componentWidth * REFERENCE_SPLIT_RATIO).roundToInt()
        return split.coerceIn(0, minOf(componentWidth, MAX_LEFT_SPLIT_WIDTH))
    }

    private fun assertFloatEquals(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) <= EPSILON, "Expected $expected but was $actual")
    }

    companion object {
        private const val REFERENCE_WIDTH = 1280f
        private const val REFERENCE_HEIGHT = 862f
        private const val REFERENCE_SPLIT_RATIO = 0.24f
        private const val MAX_LEFT_SPLIT_WIDTH = 308
        private const val LOGO_LEFT_MIN_OFFSET = 100f
        private const val LOGO_TOP_MIN_OFFSET = 200f
        private const val EPSILON = 0.001f
    }
}
