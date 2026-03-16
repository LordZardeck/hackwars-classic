package com.hackwars.gui.login

import com.github.weisj.jsvg.attributes.ViewBox
import com.github.weisj.jsvg.geometry.size.FloatSize
import com.hackwars.gui.svgResource
import java.awt.*
import java.awt.geom.Rectangle2D
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A custom panel that renders a split gradient background and a centrally aligned SVG logo.
 *
 * This panel is designed to create a visually pleasing login background
 * with smoothly transitioning gradients on both sides and a carefully scaled
 * SVG logo in the center.
 */
open class LoginBackgroundPanel : JPanel() {
    /**
     * Represents a background gradient to be used for rendering components.
     * Each gradient consists of a set of color stops and corresponding color values.
     *
     * @property stops The relative positions of the gradient colors, ranging from 0.0 to 1.0.
     * @property colors The array of colors that define the gradient.
     */
    private enum class BackgroundGradient(private val stops: FloatArray, private val colors: Array<Color>) {
        LEFT(
            floatArrayOf(0.0f, 0.52f, 1.0f), arrayOf(
                Color(0x3F, 0x4D, 0x57),
                Color(0x17, 0x1F, 0x2B),
                Color(0x06, 0x09, 0x0F)
            )
        ),
        RIGHT(
            floatArrayOf(0.0f, 0.45f, 1.0f),
            arrayOf(
                Color(0x2C, 0xEE, 0xFF),
                Color(0x0E, 0xD0, 0xFF),
                Color(0x0A, 0x7A, 0xD2)
            )
        );

        /**
         * Creates a vertical linear gradient paint based on the component height.
         * The gradient utilizes predefined stops and colors.
         *
         * @param height The height of the gradient area. If the height is less than 1.0f, it defaults to 1.0f.
         * @return A configured LinearGradientPaint object representing the gradient.
         */
        fun toLinearGradientPaint(height: Float): LinearGradientPaint {
            return LinearGradientPaint(
                0.0f,
                0.0f,
                0.0f,
                height.coerceAtLeast(1.0f),
                stops,
                colors
            )
        }
    }

    private companion object {
        const val REFERENCE_WIDTH = 1280f
        const val REFERENCE_HEIGHT = 862f
        const val REFERENCE_SPLIT_RATIO = 0.24f
        const val MAX_LEFT_SPLIT_WIDTH = 308
        const val LOGO_LEFT_MIN_OFFSET = 100f
        const val LOGO_TOP_MIN_OFFSET = 200f
        const val DEFAULT_CENTER_ANIMATION_DURATION_MS = 320
        const val ANIMATION_FRAME_DELAY_MS = 16

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
        applyCenteredState(centered, animate, durationMs)
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

    /**
     * Returns the absolute x-coordinate of the rendered logo's right edge for the given component size.
     * If the logo is unavailable, falls back to the split position.
     */
    protected fun getLogoRightEdgeX(componentSize: Dimension): Int {
        val splitX = getSplitX(componentSize.width)
        val document = logoDocument ?: return splitX
        val scaledSize = getScaledLogoSize(document.size(), componentSize)
        val logoLeft = (splitX - (scaledSize.width / 2f)).toInt()
        return logoLeft + ceil(scaledSize.width.toDouble()).toInt()
    }

    /**
     * Returns the rendered bounds of the logo for the given component size.
     * If the logo cannot be loaded, returns null.
     */
    protected fun getLogoBounds(componentSize: Dimension): Rectangle2D.Float? {
        val splitX = getSplitX(componentSize.width)
        val document = logoDocument ?: return null
        val scaledSize = getScaledLogoSize(document.size(), componentSize)
        return Rectangle2D.Float(
            splitX - (scaledSize.width / 2f),
            (componentSize.height / 2f) - (scaledSize.height / 2f),
            scaledSize.width,
            scaledSize.height
        )
    }

    /**
     * Calculates the scaled size of the logo to ensure it fits within the specified component's viewport
     * while maintaining constraints for minimum offsets from the top and left edges.
     *
     * @param documentSize The original size of the logo as a `FloatSize` object.
     * @param componentSize The dimensions of the component's viewport as a `Dimension` object.
     * @return The adjusted size of the logo as a `FloatSize` object, scaled if necessary to meet the constraints.
     */
    internal fun getScaledLogoSize(documentSize: FloatSize, componentSize: Dimension): FloatSize {
        val splitX = getSplitX(componentSize.width)
        val widthScale = (componentSize.width / REFERENCE_WIDTH).coerceAtMost(1f)
        val heightScale = (componentSize.height / REFERENCE_HEIGHT).coerceAtMost(1f)
        val minLeftOffset = LOGO_LEFT_MIN_OFFSET * widthScale
        val minTopOffset = LOGO_TOP_MIN_OFFSET * heightScale
        val leftOffset = splitX - (documentSize.width / 2)
        val topOffset = (componentSize.height / 2) - (documentSize.height / 2)

        // If the logo already fits in the component's viewport, then leave it as is
        if (leftOffset >= minLeftOffset && topOffset >= minTopOffset) {
            return documentSize
        }

        val maxAllowedWidth = ((splitX - minLeftOffset) * 2f).coerceAtLeast(1f)
        val maxAllowedHeight = (componentSize.height - (minTopOffset * 2f)).coerceAtLeast(1f)
        val scale = minOf(
            1f,
            maxAllowedWidth / documentSize.width.coerceAtLeast(1f),
            maxAllowedHeight / documentSize.height.coerceAtLeast(1f)
        )

        return FloatSize(
            documentSize.width * scale,
            documentSize.height * scale
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

        startCenterAnimation(target, durationMs.coerceAtLeast(1))
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
        val animationStart = System.nanoTime()

        centerAnimationTimer = Timer(ANIMATION_FRAME_DELAY_MS) {
            val elapsedMs = (System.nanoTime() - animationStart) / 1_000_000f
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

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val component = this as Component

        (g as? Graphics2D)?.run {
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val splitX = getSplitX(width)

            // Paint the left and right split background gradients
            paint = BackgroundGradient.LEFT.toLinearGradientPaint(height.toFloat())
            fillRect(0, 0, splitX, height)
            paint = BackgroundGradient.RIGHT.toLinearGradientPaint(height.toFloat())
            fillRect(splitX, 0, width - splitX, height)

            // Render the logo document if it exists
            logoDocument?.let {
                val scaledSize = getScaledLogoSize(it.size(), component.size)
                val position = Point(
                    (splitX - (scaledSize.width / 2)).toInt(),
                    ((height / 2) - (scaledSize.height / 2)).toInt()
                )

                // Store previous clip and transform to restore later
                val prevClip = clip
                val prevTransform = transform

                clipRect(position.x, position.y, scaledSize.width.toInt(), scaledSize.height.toInt())
                translate(position.x.toDouble(), position.y.toDouble())

                // Render the svg to the component
                it.render(component, this, ViewBox(0f, 0f, scaledSize.width, scaledSize.height))

                // Restore the previous clip and transform
                transform = prevTransform
                clip = prevClip
            }
        }
    }
}
