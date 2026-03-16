package com.hackwars.gui.login

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.net.URI
import java.text.AttributedString
import javax.swing.*

class LoginFormSeparator : JPanel() {
    init {
        isOpaque = false
        preferredSize = Dimension(300, 50)
    }

    override fun paintComponent(g: Graphics?) {
        val width = width
        val xOffset = width * .1
        val y = (height / 2)

        (g?.create() as? Graphics2D)?.run {
            val linePaint = LinearGradientPaint(
                Point2D.Float(xOffset.toFloat(), y.toFloat()),
                Point2D.Float((width - xOffset).toFloat(), y.toFloat()),
                floatArrayOf(0f, 0.5f, 1f),
                arrayOf(
                    Color(0x2D, 0x5E, 0x8B, 0),
                    Color(0x49, 0xC7, 0xFF, 166),
                    Color(0x2D, 0x5E, 0x8B, 0)
                )
            )
            paint = linePaint
            stroke = BasicStroke(1f)
            drawLine(xOffset.toInt(), y, (width - xOffset).toInt(), y)
        }
    }
}

class LoginForm : JPanel(GridBagLayout()) {
    private companion object {
        const val GLOW_INSET = 10f
        const val OUTER_ARC = 40f
        const val INNER_INSET = 1.25f

        const val SIGNUP_URL = "https://www.reddit.com/r/HackWars/"
    }

    private val cardPanel = LoginCardPanel()

    init {
        isOpaque = false

        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            insets = Insets(0,0,0,0)
        }
        add(cardPanel, constraints)
    }

    private inner class LoginCardPanel : JPanel() {
        private val labelFont = interFont(Font.PLAIN, 14f)
        private val smallFont = interFont(Font.PLAIN, 14f)
        private val linkFont = interFont(Font.BOLD, 16f)

        init {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(30,30,30,30)
            buildContent()
        }


        private fun Color.withAlpha(alpha: Int): Color = Color(red, green, blue, alpha.coerceIn(0, 255))

        override fun paintComponent(g: Graphics) {
            val c = this
            (g?.create() as? Graphics2D)?.run {
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

                composite = AlphaComposite.SrcOver

                val width = c.width
                val height = c.height
                if (width <= 1 || height <= 1) {
                    dispose()
                    return
                }

                // Keep border/glow slightly inset so thicker strokes are not clipped by component bounds.
                val outerX = GLOW_INSET + 0.5f
                val outerY = GLOW_INSET + 0.5f
                val outerW = (width - (GLOW_INSET * 2f) - 1f).coerceAtLeast(2f)
                val outerH = (height - (GLOW_INSET * 2f) - 1f).coerceAtLeast(2f)
                val rect = RoundRectangle2D.Float(outerX, outerY, outerW, outerH, OUTER_ARC, OUTER_ARC)

                // Soft static glow
                for (i in 6 downTo 1) {
                    color = Color(4, 12, 20).withAlpha(60-(i*15).coerceAtLeast(0))
                    stroke = BasicStroke(i * 2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    draw(rect)
                }

                // Draw background over gradient lines so that the inside of the input doesn't have the gradient
                paint = LinearGradientPaint(
                    Point2D.Float(8f, 0f),
                    Point2D.Float(c.width * .8f, c.height * 1.4f),
                    floatArrayOf(0f, 0.55f, 1f),
                    arrayOf(
                        Color(0x15, 0x39, 0x5D),
                        Color(0x0A, 0x22, 0x3B),
                        Color(0x06, 0x13, 0x21)
                    )
                )

                val innerArc = (OUTER_ARC - 2f).coerceAtLeast(2f)
                fill(
                    RoundRectangle2D.Float(
                        outerX + INNER_INSET,
                        outerY + INNER_INSET,
                        (outerW - (INNER_INSET * 2f)).coerceAtLeast(1f),
                        (outerH - (INNER_INSET * 2f)).coerceAtLeast(1f),
                        innerArc,
                        innerArc
                    )
                )

                dispose()
            }

            super.paintComponent(g)
        }

        private fun buildContent() {
            val usernameLabel = createLabel("Username")
            val passwordLabel = createLabel("Password")

            val usernameField = LoginTextField().apply {
                text = "localuser"
            }

            val passwordField = LoginPasswordField().apply {
                text = "password1234"
            }

            val usernameFieldPanel = LoginFieldPanel(usernameField)
            usernameFieldPanel.preferredSize = Dimension(200, 46)
            val passwordFieldPanel = LoginFieldPanel(passwordField)
            passwordFieldPanel.preferredSize = Dimension(200, 46)

            val loginButton = LoginButton("LOGIN")
            val footerPanel = createFooterPanel()


            add(leftRow(10, usernameLabel))
            add(fillRow(usernameFieldPanel))
            add(add(Box.createVerticalStrut(10)))
            add(leftRow(10, passwordLabel))
            add(fillRow(passwordFieldPanel))
            add(fillRow(LoginFormSeparator()))
            add(fillRow(loginButton))
            add(add(Box.createVerticalStrut(20)))
            add(fillRow(footerPanel))
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

            val linkLabel = object : JLabel("Create one") {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2 = g.create() as Graphics2D
                    g2.color = Color(0x17, 0xAE, 0xFF)
                    g2.stroke = BasicStroke(1f)
                    val baseline = getFontMetrics(font).ascent
                    val y = baseline + 6
                    g2.drawLine(0, y, width - 1, y)
                    g2.dispose()
                }
            }.apply {
                isOpaque = false
                foreground = Color(0x17, 0xAE, 0xFF)
                font = linkFont
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent?) {
                        openSignupUrl()
                    }

                    override fun mouseEntered(e: MouseEvent?) {
                        foreground = Color(0x4D, 0xC5, 0xFF)
                        repaint()
                    }

                    override fun mouseExited(e: MouseEvent?) {
                        foreground = Color(0x17, 0xAE, 0xFF)
                        repaint()
                    }
                })
            }

            return JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(Box.createHorizontalGlue())
                add(smallLabel)
                add(Box.createHorizontalStrut(30))
                add(linkLabel)
                add(Box.createHorizontalGlue())
            }
        }

        private fun openSignupUrl() {
            if (!Desktop.isDesktopSupported()) {
                return
            }

            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return
            }
            runCatching {
                desktop.browse(URI(SIGNUP_URL))
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
//            ui = object : BasicButtonUI() {
//                override fun paintButtonPressed(g: Graphics, b: AbstractButton) {
//                    g.color = Color(58, 63, 73)
//                    g.fillRect(0, 0, b.size.width, b.size.height);
//                }
//            }
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            g2.color = Color(0x37, 0xC6, 0xFF, 38)
            g2.fillRoundRect(1, 1, width - 2, height - 2, 12, 12)

            val fill = LinearGradientPaint(
                Point2D.Float(0f, 0f),
                Point2D.Float(width.toFloat(), 0f),
                floatArrayOf(0f, 0.55f, 1f),
                arrayOf(
                    Color(0x0B, 0x8F, 0xFF),
                    Color(0x16, 0xB2, 0xFF),
                    Color(0x19, 0x8D, 0xFF)
                )
            )
            g2.paint = fill
            g2.fillRoundRect(0, 0, width - 1, height - 1, 12, 12)

            g2.color = Color(0x68, 0xD8, 0xFF, 140)
            g2.stroke = BasicStroke(2f)
            g2.drawRoundRect(1, 1, width - 3, height - 3, 10, 10)

            val trackingText = AttributedString(text.uppercase()).apply {
                addAttribute(TextAttribute.FONT, buttonFont)
                addAttribute(TextAttribute.FOREGROUND, foreground)
                addAttribute(TextAttribute.TRACKING, 0.022f)
            }
            val iterator = trackingText.iterator
            val layout = g2.fontMetrics.getStringBounds(text.uppercase(), g2)
            val drawX = ((width - layout.width) / 2f).toFloat()
            val drawY = ((height - layout.height) / 2f + g2.fontMetrics.ascent).toFloat()
            g2.drawString(iterator, drawX, drawY)
            g2.dispose()
        }
    }

    private inner class TrackingLabel(
        private val labelText: String,
        private val labelFont: Font,
        private val color: Color,
        private val tracking: Float
    ) : JComponent() {
        init {
            isOpaque = false
            font = labelFont
            foreground = color
            val fm = getFontMetrics(labelFont)
            val width = fm.stringWidth(labelText) + (tracking * labelText.length * labelFont.size).toInt()
            val height = fm.height
            minimumSize = Dimension(width, height)
            preferredSize = Dimension(width, height)
            maximumSize = Dimension(width, height)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val attributed = AttributedString(labelText).apply {
                addAttribute(TextAttribute.FONT, labelFont)
                addAttribute(TextAttribute.FOREGROUND, color)
                addAttribute(TextAttribute.TRACKING, tracking)
            }
            val fm = g2.getFontMetrics(labelFont)
            g2.drawString(attributed.iterator, 0f, fm.ascent.toFloat())
            g2.dispose()
        }
    }

    private fun interFont(style: Int, size: Float): Font {
        return Font("Inter", style, 1).deriveFont(size)
    }
}