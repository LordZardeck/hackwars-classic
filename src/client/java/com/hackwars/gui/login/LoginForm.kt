package com.hackwars.gui.login

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.net.URI
import java.text.AttributedString
import java.util.*
import javax.swing.*

class LoginForm : JPanel(GridBagLayout()) {
    sealed class AuthenticationEvent(source: Any) : EventObject(source)
    class PasswordAuthenticationEvent(source: Any, val email: String, val password: CharArray) :
        AuthenticationEvent(source)

    interface AuthenticationListener : EventListener
    fun interface PasswordAuthenticationListener : AuthenticationListener {
        fun onPasswordAuthenticate(event: PasswordAuthenticationEvent)
    }

    fun addPasswordAuthenticationListener(l: PasswordAuthenticationListener) =
        listenerList.add(PasswordAuthenticationListener::class.java, l)

    fun removePasswordAuthenticationListener(l: PasswordAuthenticationListener) =
        listenerList.remove(PasswordAuthenticationListener::class.java, l)

    fun firePasswordAuthenticationEvent(event: PasswordAuthenticationEvent) {
        // Guaranteed to return a non-null array
        val listeners = listenerList.getListenerList()
        // Process the listeners last to first, notifying
        // those that are interested in this event
        var i = listeners.size - 2
        while (i >= 0) {
            if (listeners[i] === PasswordAuthenticationListener::class.java) {
                (listeners[i + 1] as PasswordAuthenticationListener).onPasswordAuthenticate(event)
            }
            i -= 2
        }
    }

    private companion object {
        const val GLOW_INSET = 10f
        const val OUTER_ARC = 40f
        const val INNER_INSET = 1.25f

        const val SIGNUP_URL = "https://www.reddit.com/r/HackWars/"

        val MIN_FORM_SIZE = Dimension(320, 360)
        val PREFERRED_FORM_SIZE = Dimension(420, 420)
        val MAX_FORM_SIZE = Dimension(420, 520)
    }

    private val cardPanel = LoginCardPanel()

    init {
        listenerList
        isOpaque = false
        minimumSize = MIN_FORM_SIZE
        preferredSize = PREFERRED_FORM_SIZE
        maximumSize = MAX_FORM_SIZE

        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            weighty = 1.0
            fill = GridBagConstraints.BOTH
            insets = Insets(0, 0, 0, 0)
        }
        add(cardPanel, constraints)
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        // Respect max bounds, but allow shrinking below minimum when parent space is constrained.
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
            add(contentPanel, GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.CENTER
            })
            buildContent(contentPanel)
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
                    color = Color(4, 12, 20).withAlpha(60 - (i * 15).coerceAtLeast(0))
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

        private fun buildContent(container: JPanel) {
            val emailLabel = createLabel("Email")
            val passwordLabel = createLabel("Password")

            val emailField = LoginTextField().apply {
                text = "localuser"
            }

            val passwordField = LoginPasswordField().apply {
                text = "password1234"
            }

            val emailFieldPanel = LoginFieldPanel(emailField)
            emailFieldPanel.preferredSize = Dimension(200, 46)
            val passwordFieldPanel = LoginFieldPanel(passwordField)
            passwordFieldPanel.preferredSize = Dimension(200, 46)

            val loginButton = LoginButton("LOGIN").apply {
                addActionListener {
                    firePasswordAuthenticationEvent(
                        PasswordAuthenticationEvent(
                            this@LoginForm,
                            emailField.text,
                            passwordField.password
                        )
                    )
                }
            }
            val footerPanel = createFooterPanel()


            container.add(leftRow(10, emailLabel))
            container.add(fillRow(emailFieldPanel))
            container.add(Box.createVerticalStrut(10))
            container.add(leftRow(10, passwordLabel))
            container.add(fillRow(passwordFieldPanel))
            container.add(fillRow(LoginFormSeparator()))
            container.add(fillRow(loginButton))
            container.add(Box.createVerticalStrut(20))
            container.add(fillRow(footerPanel))

//            TODO: This can't be done yet since the content panel isn't set until after all initialization is complete
//            rootPane.defaultButton = loginButton
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
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val isPressed = model.isArmed && model.isPressed
            val textYOffset = if (isPressed) 1f else 0f

            g2.color = if (isPressed) Color(0x37, 0xC6, 0xFF, 20) else Color(0x37, 0xC6, 0xFF, 38)
            g2.fillRoundRect(1, 1, width - 2, height - 2, 12, 12)

            val fill = LinearGradientPaint(
                Point2D.Float(0f, 0f),
                Point2D.Float(width.toFloat(), 0f),
                floatArrayOf(0f, 0.55f, 1f),
                arrayOf(
                    if (isPressed) Color(0x08, 0x72, 0xCC) else Color(0x0B, 0x8F, 0xFF),
                    if (isPressed) Color(0x0E, 0x95, 0xD6) else Color(0x16, 0xB2, 0xFF),
                    if (isPressed) Color(0x10, 0x75, 0xC8) else Color(0x19, 0x8D, 0xFF)
                )
            )
            g2.paint = fill
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

    private fun interFont(style: Int, size: Float): Font {
        return Font("Inter", style, 1).deriveFont(size)
    }
}
