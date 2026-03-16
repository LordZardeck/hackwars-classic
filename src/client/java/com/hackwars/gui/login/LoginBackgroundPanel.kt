package com.hackwars.gui.login

import com.github.weisj.jsvg.attributes.ViewBox
import com.github.weisj.jsvg.geometry.size.FloatSize
import com.hackwars.gui.svgResource
import java.awt.*
import javax.swing.JPanel
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

        val logoDocument = svgResource("images/hackwars-logo-split.svg")
    }

    private fun getSplitX(componentWidth: Int): Int {
        val split = (componentWidth * REFERENCE_SPLIT_RATIO).roundToInt()
        return split.coerceIn(0, minOf(componentWidth, MAX_LEFT_SPLIT_WIDTH))
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
