package com.hackwars.rewrite.client.login

import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.LinearGradientPaint
import java.awt.Point
import java.awt.font.TextAttribute
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.text.AttributedString
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class LoginForm : JPanel(GridBagLayout()) {
    companion object {
        private const val GLOW_INSET = 10f
        private const val OUTER_ARC = 40f
        private const val INNER_INSET = 1.25f

        private val minFormSize = Dimension(320, 360)
        private val preferredFormSize = Dimension(420, 420)
        private val maxFormSize = Dimension(420, 520)
    }

    private val errorLabel = JLabel(" ").apply {
        isOpaque = false
        foreground = Color(0xFF, 0x9C, 0x9C)
        font = interFont(Font.PLAIN, 14f)
        alignmentX = CENTER_ALIGNMENT
        horizontalAlignment = JLabel.CENTER
    }
    private val errorPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        isVisible = false
        border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        add(errorLabel, BorderLayout.CENTER)
    }
    val emailField = LoginTextField().apply { text = "localuser" }
    val passwordField = LoginPasswordField().apply { text = "password1234" }
    val loginButton: JButton = LoginButton("LOGIN")
    private val signupLink = SignupLinkLabel()
    val signupLinkLabel: JLabel
        get() = signupLink
    private val cardPanel = LoginCardPanel()

    fun showError(message: String?) {
        val normalized = message?.takeIf { it.isNotBlank() }
        errorLabel.text = normalized ?: " "
        errorPanel.isVisible = normalized != null
        revalidate()
        repaint()
    }

    fun displayedErrorText(): String = errorLabel.text

    fun snapshotCredentials(): com.hackwars.rewrite.client.auth.RewriteLoginCredentials {
        return com.hackwars.rewrite.client.auth.RewriteLoginCredentials(
            email = emailField.text,
            password = passwordField.password,
        )
    }

    fun setSignupLinkHovered(hovered: Boolean) {
        signupLink.setHovered(hovered)
    }

    init {
        isOpaque = false
        minimumSize = minFormSize
        preferredSize = preferredFormSize
        maximumSize = maxFormSize

        add(
            cardPanel,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
                insets = Insets(0, 0, 0, 0)
            },
        )
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        val clampedWidth = width.coerceAtMost(maximumSize.width)
        val clampedHeight = height.coerceIn(minimumSize.height, maximumSize.height)
        val centeredX = if (width > clampedWidth) x + ((width - clampedWidth) / 2) else x
        val centeredY = if (height > clampedHeight) y + ((height - clampedHeight) / 2) else y
        super.setBounds(centeredX, centeredY, clampedWidth, clampedHeight)
    }

    private inner class LoginCardPanel : JPanel() {
        private val labelFont = interFont(Font.PLAIN, 14f)
        private val smallFont = interFont(Font.PLAIN, 14f)
        private val linkFont = interFont(Font.BOLD, 14f)
        private val contentPanel = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(30, 30, 30, 30)
        }

        init {
            isOpaque = false
            layout = GridBagLayout()
            add(
                contentPanel,
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    weightx = 1.0
                    weighty = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    anchor = GridBagConstraints.CENTER
                },
            )
            buildContent(contentPanel)
        }

        override fun paintComponent(graphics: Graphics) {
            val g2 = graphics.create() as Graphics2D
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)

            val width = width
            val height = height
            if (width <= 1 || height <= 1) {
                g2.dispose()
                return
            }

            val outerX = GLOW_INSET + 0.5f
            val outerY = GLOW_INSET + 0.5f
            val outerW = (width - (GLOW_INSET * 2f) - 1f).coerceAtLeast(2f)
            val outerH = (height - (GLOW_INSET * 2f) - 1f).coerceAtLeast(2f)
            val rect = RoundRectangle2D.Float(outerX, outerY, outerW, outerH, OUTER_ARC, OUTER_ARC)

            for (index in 6 downTo 1) {
                val alpha = (60 - (index * 15)).coerceIn(0, 255)
                g2.color = Color(4, 12, 20, alpha)
                g2.stroke = BasicStroke(index * 2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g2.draw(rect)
            }

            g2.paint = LinearGradientPaint(
                Point2D.Float(8f, 0f),
                Point2D.Float(width * 0.8f, height * 1.4f),
                floatArrayOf(0f, 0.55f, 1f),
                arrayOf(
                    Color(0x15, 0x39, 0x5D),
                    Color(0x0A, 0x22, 0x3B),
                    Color(0x06, 0x13, 0x21),
                ),
            )
            val innerArc = (OUTER_ARC - 2f).coerceAtLeast(2f)
            g2.fill(
                RoundRectangle2D.Float(
                    outerX + INNER_INSET,
                    outerY + INNER_INSET,
                    (outerW - (INNER_INSET * 2f)).coerceAtLeast(1f),
                    (outerH - (INNER_INSET * 2f)).coerceAtLeast(1f),
                    innerArc,
                    innerArc,
                ),
            )
            g2.dispose()
            super.paintComponent(graphics)
        }

        private fun buildContent(container: JPanel) {
            val emailLabel = createLabel("Email")
            val passwordLabel = createLabel("Password")
            val emailFieldPanel = LoginFieldPanel(emailField).apply { preferredSize = Dimension(200, 46) }
            val passwordFieldPanel = LoginFieldPanel(passwordField).apply { preferredSize = Dimension(200, 46) }

            container.add(leftRow(10, emailLabel))
            container.add(fillRow(emailFieldPanel))
            container.add(Box.createVerticalStrut(10))
            container.add(leftRow(10, passwordLabel))
            container.add(fillRow(passwordFieldPanel))
            container.add(fillRow(errorPanel))
            container.add(fillRow(LoginFormSeparator()))
            container.add(fillRow(loginButton))
            container.add(Box.createVerticalStrut(20))
            container.add(fillRow(createFooterPanel()))
        }

        private fun createLabel(text: String): JLabel {
            return JLabel(text).apply {
                isOpaque = false
                foreground = Color(0xE3, 0xE8, 0xF0)
                font = labelFont
            }
        }

        private fun createFooterPanel(): JPanel {
            val smallLabel = JLabel("Don't have an account?").apply {
                isOpaque = false
                foreground = Color(0x9A, 0xA8, 0xB8)
                font = smallFont
            }
            signupLinkLabel.apply {
                font = linkFont
            }

            return JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(Box.createHorizontalGlue())
                add(smallLabel)
                add(Box.createHorizontalStrut(30))
                add(signupLinkLabel)
                add(Box.createHorizontalGlue())
            }
        }

        private fun fillRow(component: JComponent): JPanel {
            return JPanel(BorderLayout()).apply {
                isOpaque = false
                add(component, BorderLayout.CENTER)
            }
        }

        private fun leftRow(leftPadding: Int, component: JComponent): JPanel {
            return JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(Box.createHorizontalStrut(leftPadding))
                add(component)
                add(Box.createHorizontalGlue())
            }
        }
    }

    private inner class LoginFieldPanel(field: JComponent) : JPanel() {
        init {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(field)
        }
    }

    private inner class LoginButton(text: String) : JButton(text) {
        private val buttonFont = interFont(Font.BOLD, 18f)

        init {
            isOpaque = false
            isFocusPainted = false
            isBorderPainted = false
            isContentAreaFilled = false
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            foreground = Color.WHITE
            font = buttonFont
        }

        override fun paintComponent(graphics: Graphics) {
            val g2 = graphics.create() as Graphics2D
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)

            val isPressed = model.isArmed && model.isPressed
            val textYOffset = if (isPressed) 1f else 0f

            g2.color = if (isPressed) Color(0x37, 0xC6, 0xFF, 20) else Color(0x37, 0xC6, 0xFF, 38)
            g2.fillRoundRect(1, 1, width - 2, height - 2, 12, 12)
            g2.paint = LinearGradientPaint(
                Point2D.Float(0f, 0f),
                Point2D.Float(width.toFloat(), 0f),
                floatArrayOf(0f, 0.55f, 1f),
                arrayOf(
                    if (isPressed) Color(0x08, 0x72, 0xCC) else Color(0x0B, 0x8F, 0xFF),
                    if (isPressed) Color(0x0E, 0x95, 0xD6) else Color(0x16, 0xB2, 0xFF),
                    if (isPressed) Color(0x10, 0x75, 0xC8) else Color(0x19, 0x8D, 0xFF),
                ),
            )
            g2.fillRoundRect(0, 0, width - 1, height - 1, 12, 12)

            if (isPressed) {
                g2.color = Color(0x04, 0x3D, 0x70, 90)
                g2.fillRoundRect(1, 1, width - 3, height - 3, 10, 10)
            }

            g2.color = if (isPressed) Color(0x68, 0xD8, 0xFF, 100) else Color(0x68, 0xD8, 0xFF, 140)
            g2.stroke = BasicStroke(2f)
            g2.drawRoundRect(1, 1, width - 3, height - 3, 10, 10)

            val trackingText = AttributedString(text.uppercase()).apply {
                addAttribute(TextAttribute.FONT, buttonFont)
                addAttribute(TextAttribute.FOREGROUND, if (isPressed) Color(0xE7, 0xF5, 0xFF) else foreground)
                addAttribute(TextAttribute.TRACKING, 0.022f)
            }
            val iterator = trackingText.iterator
            val layout = g2.fontMetrics.getStringBounds(text.uppercase(), g2)
            val drawX = ((width - layout.width) / 2f).toFloat()
            val drawY = ((height - layout.height) / 2f + g2.fontMetrics.ascent).toFloat() + textYOffset
            g2.drawString(iterator, drawX, drawY)
            g2.dispose()
        }
    }

    private inner class SignupLinkLabel : JLabel("Create one") {
        private var hovered = false

        init {
            isOpaque = false
            foreground = Color(0x17, 0xAE, 0xFF)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        fun setHovered(value: Boolean) {
            hovered = value
            foreground = if (hovered) Color(0x4D, 0xC5, 0xFF) else Color(0x17, 0xAE, 0xFF)
            repaint()
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val g2 = graphics.create() as Graphics2D
            g2.color = foreground
            g2.stroke = BasicStroke(1f)
            val baseline = getFontMetrics(font).ascent
            val y = baseline + 6
            g2.drawLine(0, y, width - 1, y)
            g2.dispose()
        }
    }

    private fun interFont(style: Int, size: Float): Font {
        return Font("Inter", style, 1).deriveFont(size)
    }
}
