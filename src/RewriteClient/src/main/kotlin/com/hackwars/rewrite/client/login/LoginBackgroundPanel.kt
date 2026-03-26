package com.hackwars.rewrite.client.login

import com.github.weisj.jsvg.attributes.ViewBox
import com.github.weisj.jsvg.geometry.size.FloatSize
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.ceil
import kotlin.math.roundToInt

open class LoginBackgroundPanel : JPanel() {
    private enum class BackgroundGradient(
        private val stops: FloatArray,
        private val colors: Array<Color>,
    ) {
        LEFT(
            floatArrayOf(0.0f, 0.52f, 1.0f),
            arrayOf(
                Color(0x3F, 0x4D, 0x57),
                Color(0x17, 0x1F, 0x2B),
                Color(0x06, 0x09, 0x0F),
            ),
        ),
        RIGHT(
            floatArrayOf(0.0f, 0.45f, 1.0f),
            arrayOf(
                Color(0x2C, 0xEE, 0xFF),
                Color(0x0E, 0xD0, 0xFF),
                Color(0x0A, 0x7A, 0xD2),
            ),
        );

        fun toLinearGradientPaint(height: Float): LinearGradientPaint {
            return LinearGradientPaint(
                0.0f,
                0.0f,
                0.0f,
                height.coerceAtLeast(1.0f),
                stops,
                colors,
            )
        }
    }

    companion object {
        private const val REFERENCE_WIDTH = 1280f
        private const val REFERENCE_HEIGHT = 862f
        private const val REFERENCE_SPLIT_RATIO = 0.24f
        private const val MAX_LEFT_SPLIT_WIDTH = 308
        private const val LOGO_LEFT_MIN_OFFSET = 100f
        private const val LOGO_TOP_MIN_OFFSET = 200f
        private const val DEFAULT_CENTER_ANIMATION_DURATION_MS = 320
        private const val ANIMATION_FRAME_DELAY_MS = 16

        val logoDocument = svgResource("images/hackwars-logo-split.svg")
    }

    private var centeredState = true
    private var centeredProgress = 1f
    private var centerAnimationTimer: Timer? = null

    var centered: Boolean
        get() = centeredState
        set(value) {
            applyCenteredState(value, animate = true, durationMs = DEFAULT_CENTER_ANIMATION_DURATION_MS)
        }

    @JvmOverloads
    fun setCentered(centered: Boolean, animate: Boolean, durationMs: Int = DEFAULT_CENTER_ANIMATION_DURATION_MS) {
        applyCenteredState(centered = centered, animate = animate, durationMs = durationMs)
    }

    @JvmOverloads
    fun animateCentering(durationMs: Int = DEFAULT_CENTER_ANIMATION_DURATION_MS) {
        applyCenteredState(centered = true, animate = true, durationMs = durationMs)
    }

    @JvmOverloads
    fun animateUncentering(durationMs: Int = DEFAULT_CENTER_ANIMATION_DURATION_MS) {
        applyCenteredState(centered = false, animate = true, durationMs = durationMs)
    }

    protected fun getSplitX(componentWidth: Int): Int {
        val split = (componentWidth * REFERENCE_SPLIT_RATIO).roundToInt()
        val leftSplit = split.coerceIn(0, minOf(componentWidth, MAX_LEFT_SPLIT_WIDTH))
        val centeredSplit = (componentWidth * 0.5f).roundToInt()
        return lerp(leftSplit, centeredSplit, centeredProgress).roundToInt()
    }

    protected fun getLogoRightEdgeX(componentSize: Dimension): Int {
        val splitX = getSplitX(componentSize.width)
        val document = logoDocument ?: return splitX
        val scaledSize = getScaledLogoSize(document.size(), componentSize)
        val logoLeft = (splitX - (scaledSize.width / 2f)).toInt()
        return logoLeft + ceil(scaledSize.width.toDouble()).toInt()
    }

    protected fun getLogoBounds(componentSize: Dimension): Rectangle2D.Float? {
        val splitX = getSplitX(componentSize.width)
        val document = logoDocument ?: return null
        val scaledSize = getScaledLogoSize(document.size(), componentSize)
        return Rectangle2D.Float(
            splitX - (scaledSize.width / 2f),
            (componentSize.height / 2f) - (scaledSize.height / 2f),
            scaledSize.width,
            scaledSize.height,
        )
    }

    internal fun getScaledLogoSize(documentSize: FloatSize, componentSize: Dimension): FloatSize {
        val splitX = getSplitX(componentSize.width)
        val widthScale = (componentSize.width / REFERENCE_WIDTH).coerceAtMost(1f)
        val heightScale = (componentSize.height / REFERENCE_HEIGHT).coerceAtMost(1f)
        val minLeftOffset = LOGO_LEFT_MIN_OFFSET * widthScale
        val minTopOffset = LOGO_TOP_MIN_OFFSET * heightScale
        val leftOffset = splitX - (documentSize.width / 2f)
        val topOffset = (componentSize.height / 2f) - (documentSize.height / 2f)

        if (leftOffset >= minLeftOffset && topOffset >= minTopOffset) {
            return documentSize
        }

        val maxAllowedWidth = ((splitX - minLeftOffset) * 2f).coerceAtLeast(1f)
        val maxAllowedHeight = (componentSize.height - (minTopOffset * 2f)).coerceAtLeast(1f)
        val scale = minOf(
            1f,
            maxAllowedWidth / documentSize.width.coerceAtLeast(1f),
            maxAllowedHeight / documentSize.height.coerceAtLeast(1f),
        )
        return FloatSize(
            documentSize.width * scale,
            documentSize.height * scale,
        )
    }

    override fun removeNotify() {
        stopCenterAnimation()
        super.removeNotify()
    }

    private fun applyCenteredState(centered: Boolean, animate: Boolean, durationMs: Int) {
        centeredState = centered
        val target = if (centered) 1f else 0f
        if (!animate || durationMs <= 0) {
            stopCenterAnimation()
            centeredProgress = target
            onSplitChanged()
            return
        }
        startCenterAnimation(targetProgress = target, durationMs = durationMs.coerceAtLeast(1))
    }

    private fun startCenterAnimation(targetProgress: Float, durationMs: Int) {
        if (centeredProgress == targetProgress) {
            stopCenterAnimation()
            centeredProgress = targetProgress
            onSplitChanged()
            return
        }

        stopCenterAnimation()
        val startProgress = centeredProgress
        val animationStart = RewriteClientClock.nowNanos()
        centerAnimationTimer = Timer(ANIMATION_FRAME_DELAY_MS) {
            val elapsedMs = (RewriteClientClock.nowNanos() - animationStart) / 1_000_000f
            val t = (elapsedMs / durationMs).coerceIn(0f, 1f)
            centeredProgress = lerp(startProgress, targetProgress, easeInOutCubic(t))
            onSplitChanged()
            if (t >= 1f) {
                centeredProgress = targetProgress
                stopCenterAnimation()
                onSplitChanged()
            }
        }.apply {
            isCoalesce = true
            start()
        }
    }

    private fun stopCenterAnimation() {
        centerAnimationTimer?.stop()
        centerAnimationTimer = null
    }

    private fun onSplitChanged() {
        revalidate()
        repaint()
    }

    private fun easeInOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) {
            4f * x * x * x
        } else {
            1f - (((-2f * x) + 2f) * ((-2f * x) + 2f) * ((-2f * x) + 2f)) / 2f
        }
    }

    private fun lerp(start: Int, end: Int, t: Float): Float = lerp(start.toFloat(), end.toFloat(), t)

    private fun lerp(start: Float, end: Float, t: Float): Float {
        val ratio = t.coerceIn(0f, 1f)
        return start + ((end - start) * ratio)
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val component = this as Component
        val g2 = graphics as? Graphics2D ?: return
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val splitX = getSplitX(width)

        g2.paint = BackgroundGradient.LEFT.toLinearGradientPaint(height.toFloat())
        g2.fillRect(0, 0, splitX, height)
        g2.paint = BackgroundGradient.RIGHT.toLinearGradientPaint(height.toFloat())
        g2.fillRect(splitX, 0, width - splitX, height)

        val document = logoDocument ?: return
        val scaledSize = getScaledLogoSize(document.size(), size)
        val drawWidth = scaledSize.width
        val drawHeight = scaledSize.height
        val x = splitX - (drawWidth / 2f)
        val y = (height / 2f) - (drawHeight / 2f)
        val previousClip = g2.clip
        val previousTransform = g2.transform
        g2.clipRect(x.toInt(), y.toInt(), drawWidth.toInt(), drawHeight.toInt())
        g2.translate(x.toDouble(), y.toDouble())
        document.render(component, g2, ViewBox(0f, 0f, drawWidth, drawHeight))
        g2.transform = previousTransform
        g2.clip = previousClip
    }
}
