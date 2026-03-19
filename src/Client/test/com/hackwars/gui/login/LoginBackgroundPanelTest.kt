package com.hackwars.gui.login

import com.github.weisj.jsvg.geometry.size.FloatSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.roundToInt

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

        assertTrue("Scaled width must stay positive", scaled.width > 0f)
        assertTrue("Scaled height must stay positive", scaled.height > 0f)
        assertTrue("Expected logo to scale down", widthScaleFactor < 1f)
        assertTrue("Left offset must respect minimum", leftOffset >= minLeftOffset - EPSILON)
        assertTrue("Top offset must respect minimum", topOffset >= minTopOffset - EPSILON)
        assertFloatEquals(widthScaleFactor, heightScaleFactor)
    }

    @Test
    fun getScaledLogoSize_doesNotShrinkWhenSplitIsCappedAndLogoStillFits() {
        val panel = LoginBackgroundPanel()
        val componentSize = Dimension(2000, 1200)
        val original = FloatSize(361f, 418f)
        val scaled = panel.getScaledLogoSize(original, componentSize)

        assertFloatEquals(original.width, scaled.width)
        assertFloatEquals(original.height, scaled.height)
    }

    @Test
    fun paint_rendersExpectedSplitGradientColors_atTopEdge_forDynamicSplit() {
        val panel = LoginBackgroundPanel()
        val componentSize = Dimension(640, 400)
        panel.setCentered(false, animate = false)
        panel.size = componentSize

        val image = BufferedImage(640, 400, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            panel.paint(graphics)
        } finally {
            graphics.dispose()
        }

        val splitX = getSplitX(componentSize.width)
        val leftTop = Color(image.getRGB(10, 0), true)
        val rightTop = Color(image.getRGB(400, 0), true)
        val splitLeftTop = Color(image.getRGB(splitX - 1, 0), true)
        val splitRightTop = Color(image.getRGB(splitX, 0), true)

        assertEquals(Color(0x3F, 0x4D, 0x57), leftTop)
        assertEquals(Color(0x2C, 0xEE, 0xFF), rightTop)
        assertEquals(Color(0x3F, 0x4D, 0x57), splitLeftTop)
        assertEquals(Color(0x2C, 0xEE, 0xFF), splitRightTop)
        assertTrue("Left and right sides must be different colors at the split", leftTop != rightTop)
    }

    @Test
    fun paint_splitPosition_scalesWithPanelWidth() {
        val panel = LoginBackgroundPanel()
        panel.setCentered(false, animate = false)

        val smallSize = Dimension(640, 400)
        val largeSize = Dimension(960, 400)
        val smallSplitX = getSplitX(smallSize.width)
        val largeSplitX = getSplitX(largeSize.width)

        val smallImage = renderPanel(panel, smallSize)
        val largeImage = renderPanel(panel, largeSize)

        val smallLeft = Color(smallImage.getRGB(smallSplitX - 1, 0), true)
        val smallRight = Color(smallImage.getRGB(smallSplitX, 0), true)
        val largeLeft = Color(largeImage.getRGB(largeSplitX - 1, 0), true)
        val largeRight = Color(largeImage.getRGB(largeSplitX, 0), true)

        assertTrue("Expected split position to move for larger panel width", largeSplitX > smallSplitX)
        assertEquals(Color(0x3F, 0x4D, 0x57), smallLeft)
        assertEquals(Color(0x2C, 0xEE, 0xFF), smallRight)
        assertEquals(Color(0x3F, 0x4D, 0x57), largeLeft)
        assertEquals(Color(0x2C, 0xEE, 0xFF), largeRight)
    }

    @Test
    fun paint_splitPosition_capsAtMaximumWidth() {
        val panel = LoginBackgroundPanel()
        panel.setCentered(false, animate = false)
        val componentSize = Dimension(2000, 400)
        val splitX = getSplitX(componentSize.width)
        val image = renderPanel(panel, componentSize)

        val splitLeftTop = Color(image.getRGB(splitX - 1, 0), true)
        val splitRightTop = Color(image.getRGB(splitX, 0), true)

        assertEquals("Split must cap to max width", MAX_LEFT_SPLIT_WIDTH, splitX)
        assertEquals(Color(0x3F, 0x4D, 0x57), splitLeftTop)
        assertEquals(Color(0x2C, 0xEE, 0xFF), splitRightTop)
    }

    @Test
    fun paint_splitPosition_movesToCenter_whenCenteredIsTrue() {
        val panel = LoginBackgroundPanel()
        val componentSize = Dimension(640, 400)
        panel.setCentered(true, animate = false)
        val splitX = getCenteredSplitX(componentSize.width)
        val image = renderPanel(panel, componentSize)

        val splitLeftTop = Color(image.getRGB(splitX - 1, 0), true)
        val splitRightTop = Color(image.getRGB(splitX, 0), true)

        assertEquals(Color(0x3F, 0x4D, 0x57), splitLeftTop)
        assertEquals(Color(0x2C, 0xEE, 0xFF), splitRightTop)
    }

    @Test
    fun setCentered_animatedWithZeroDuration_appliesTargetStateImmediately() {
        val panel = LoginBackgroundPanel()
        val componentSize = Dimension(640, 400)
        val centeredSplitX = getCenteredSplitX(componentSize.width)

        panel.setCentered(centered = true, animate = true, durationMs = 0)
        val centeredImage = renderPanel(panel, componentSize)
        assertEquals(Color(0x3F, 0x4D, 0x57), Color(centeredImage.getRGB(centeredSplitX - 1, 0), true))
        assertEquals(Color(0x2C, 0xEE, 0xFF), Color(centeredImage.getRGB(centeredSplitX, 0), true))

        panel.setCentered(centered = false, animate = true, durationMs = 0)
        val dynamicSplitX = getSplitX(componentSize.width)
        val dynamicImage = renderPanel(panel, componentSize)
        assertEquals(Color(0x3F, 0x4D, 0x57), Color(dynamicImage.getRGB(dynamicSplitX - 1, 0), true))
        assertEquals(Color(0x2C, 0xEE, 0xFF), Color(dynamicImage.getRGB(dynamicSplitX, 0), true))
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

    private fun getCenteredSplitX(componentWidth: Int): Int {
        return (componentWidth * CENTERED_SPLIT_RATIO).roundToInt()
    }

    private fun assertFloatEquals(expected: Float, actual: Float) {
        assertTrue("Expected $expected but was $actual", abs(expected - actual) <= EPSILON)
    }

    companion object {
        private const val REFERENCE_WIDTH = 1280f
        private const val REFERENCE_HEIGHT = 862f
        private const val REFERENCE_SPLIT_RATIO = 0.24f
        private const val MAX_LEFT_SPLIT_WIDTH = 308
        private const val LOGO_LEFT_MIN_OFFSET = 100f
        private const val LOGO_TOP_MIN_OFFSET = 200f
        private const val CENTERED_SPLIT_RATIO = 0.5f
        private const val EPSILON = 0.001f
    }
}
