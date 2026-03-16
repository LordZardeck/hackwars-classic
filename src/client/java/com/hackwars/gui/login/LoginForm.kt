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
import javax.swing.border.EmptyBorder

class LoginForm : JPanel(GridBagLayout()) {
    private companion object {
        const val ROOT_WIDTH = 620
        const val ROOT_HEIGHT = 560
        const val CARD_WIDTH = 508
        const val CARD_HEIGHT = 490

        const val TOP_GLOW_X = 158
        const val TOP_GLOW_Y = -10
        const val TOP_GLOW_WIDTH = 192
        const val TOP_GLOW_HEIGHT = 2

        const val TITLE_BASELINE_Y = 65
        const val USERNAME_LABEL_BASELINE_Y = 145
        const val USERNAME_FIELD_TOP_Y = 158
        const val PASSWORD_LABEL_BASELINE_Y = 256
        const val PASSWORD_FIELD_TOP_Y = 269
        const val BUTTON_TOP_Y = 366
        const val FOOTER_SMALL_BASELINE_Y = 460
        const val FOOTER_LINK_BASELINE_Y = 458

        const val TOP_LINE_Y = 95
        const val BOTTOM_LINE_Y = 390
        const val LINE_X = 48
        const val LINE_WIDTH = 412
        const val SIGNUP_URL = "https://www.reddit.com/r/HackWars/"
    }

    private val cardPanel = LoginCardPanel()

    init {
        isOpaque = false
        preferredSize = Dimension(ROOT_WIDTH, ROOT_HEIGHT)
        minimumSize = Dimension(ROOT_WIDTH, ROOT_HEIGHT)
        maximumSize = Dimension(ROOT_WIDTH, ROOT_HEIGHT)

        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            insets = Insets(40, 56, 30, 56)
        }
        add(cardPanel, constraints)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val card = cardPanel.bounds
        if (card.width > 0 && card.height > 0) {
            paintCardShadow(g2, card.x, card.y, card.width, card.height)
            paintTopGlow(g2, card.x + TOP_GLOW_X, card.y + TOP_GLOW_Y)
        }
        g2.dispose()
    }

    private fun paintCardShadow(g2: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
        g2.color = Color(0x03, 0x10, 0x1C, 100)
        g2.fillRoundRect(x - 5, y + -5, width + 10, height + 10, 52, 52)

        g2.color = Color(0x50, 0xC9, 0xFF, 38)
        g2.fillRoundRect(x - 4, y + -4, width + 8, height + 8, 46, 46)
    }

    private fun paintTopGlow(g2: Graphics2D, x: Int, y: Int) {
        for (i in 4 downTo 0) {
            val alpha = (12 + i * 9).coerceAtMost(90)
            g2.color = Color(0x4D, 0xCD, 0xFF, alpha)
            g2.fillRoundRect(
                x - i,
                y - i,
                TOP_GLOW_WIDTH + i * 2,
                TOP_GLOW_HEIGHT + i * 2,
                6 + i * 2,
                6 + i * 2
            )
        }
    }

    private inner class LoginCardPanel : JPanel() {
        private val titleFont = interFont(Font.BOLD, 40f)
        private val labelFont = interFont(Font.BOLD, 25f)
        private val inputFont = interFont(Font.BOLD, 22f)
        private val smallFont = interFont(Font.PLAIN, 18f)
        private val linkFont = interFont(Font.BOLD, 18f)

        init {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            preferredSize = Dimension(CARD_WIDTH, CARD_HEIGHT)
            minimumSize = Dimension(CARD_WIDTH, CARD_HEIGHT)
            maximumSize = Dimension(CARD_WIDTH, CARD_HEIGHT)
            buildContent()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val backgroundPaint = LinearGradientPaint(
                Point2D.Float(8f, 0f),
                Point2D.Float(500f, 480f),
                floatArrayOf(0f, 0.55f, 1f),
                arrayOf(
                    Color(0x15, 0x39, 0x5D),
                    Color(0x0A, 0x22, 0x3B),
                    Color(0x06, 0x13, 0x21)
                )
            )
            g2.paint = backgroundPaint
            g2.fillRoundRect(0, 0, width - 1, height - 1, 40, 40)

            val strokePaint = GradientPaint(
                0f,
                0f,
                Color(0x2A, 0x5E, 0x8E),
                0f,
                height.toFloat(),
                Color(0x16, 0x32, 0x4D)
            )
            g2.paint = strokePaint
            g2.stroke = BasicStroke(2f)
            val border = RoundRectangle2D.Float(1f, 1f, width - 2f, height - 2f, 38f, 38f)
            g2.draw(border)

            paintGlowLine(g2, TOP_LINE_Y)
            paintGlowLine(g2, BOTTOM_LINE_Y)

            g2.dispose()
            super.paintComponent(g)
        }

        private fun paintGlowLine(g2: Graphics2D, y: Int) {
            val linePaint = LinearGradientPaint(
                Point2D.Float(LINE_X.toFloat(), y.toFloat()),
                Point2D.Float((LINE_X + LINE_WIDTH).toFloat(), y.toFloat()),
                floatArrayOf(0f, 0.5f, 1f),
                arrayOf(
                    Color(0x2D, 0x5E, 0x8B, 0),
                    Color(0x49, 0xC7, 0xFF, 166),
                    Color(0x2D, 0x5E, 0x8B, 0)
                )
            )
            g2.paint = linePaint
            g2.stroke = BasicStroke(1f)
            g2.drawLine(LINE_X, y, LINE_X + LINE_WIDTH, y)
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
            val passwordFieldPanel = LoginFieldPanel(passwordField)

            val loginButton = LoginButton("LOGIN")
            val footerPanel = createFooterPanel()

            val titleTop = TITLE_BASELINE_Y - getFontMetrics(titleFont).ascent
            val labelTop = USERNAME_LABEL_BASELINE_Y - getFontMetrics(labelFont).ascent
            val passwordLabelTop = PASSWORD_LABEL_BASELINE_Y - getFontMetrics(labelFont).ascent

            var cursor = 0
            fun addGapUntil(targetY: Int) {
                val gap = targetY - cursor
                if (gap > 0) {
                    add(Box.createVerticalStrut(gap))
                    cursor += gap
                }
            }

            fun addRow(targetY: Int, height: Int, row: JComponent) {
                addGapUntil(targetY)
                row.alignmentX = LEFT_ALIGNMENT
                row.minimumSize = Dimension(CARD_WIDTH, height)
                row.preferredSize = Dimension(CARD_WIDTH, height)
                row.maximumSize = Dimension(CARD_WIDTH, height)
                add(row)
                cursor += height
            }

            val labelHeight = getFontMetrics(labelFont).height
            addRow(labelTop, labelHeight, leftRow(60, usernameLabel))
            addRow(USERNAME_FIELD_TOP_Y, 58, leftRow(44, usernameFieldPanel))
            addRow(passwordLabelTop, labelHeight, leftRow(60, passwordLabel))
            addRow(PASSWORD_FIELD_TOP_Y, 58, leftRow(44, passwordFieldPanel))
            addRow(BUTTON_TOP_Y, 60, leftRow(44, loginButton))

            val footerTop = minOf(
                FOOTER_SMALL_BASELINE_Y - getFontMetrics(smallFont).ascent,
                FOOTER_LINK_BASELINE_Y - getFontMetrics(linkFont).ascent
            )
            addRow(footerTop, footerPanel.preferredSize.height, footerPanel)

            if (cursor < CARD_HEIGHT) {
                add(Box.createVerticalStrut(CARD_HEIGHT - cursor))
            }
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

            val smallMetrics = smallLabel.getFontMetrics(smallFont)
            val smallWidth = smallMetrics.stringWidth(smallLabel.text)
            val smallAscent = smallMetrics.ascent
            val smallHeight = smallMetrics.height

            val linkMetrics = linkLabel.getFontMetrics(linkFont)
            val linkAscent = linkMetrics.ascent
            val linkHeight = linkMetrics.height

            val smallTop = FOOTER_SMALL_BASELINE_Y - smallAscent
            val linkTop = FOOTER_LINK_BASELINE_Y - linkAscent
            val rowTop = minOf(smallTop, linkTop)
            val smallOffset = smallTop - rowTop
            val linkOffset = linkTop - rowTop
            val rowHeight = maxOf(smallOffset + smallHeight, linkOffset + linkHeight + 7)

            val smallStartX = (214 - (smallWidth / 2f)).toInt().coerceAtLeast(0)
            val gap = (331 - (smallStartX + smallWidth)).coerceAtLeast(8)

            return JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                preferredSize = Dimension(CARD_WIDTH, rowHeight)
                minimumSize = Dimension(CARD_WIDTH, rowHeight)
                maximumSize = Dimension(CARD_WIDTH, rowHeight)
                add(Box.createHorizontalStrut(smallStartX))
                add(wrapWithTopOffset(smallLabel, smallOffset, rowHeight))
                add(Box.createHorizontalStrut(gap))
                add(wrapWithTopOffset(linkLabel, linkOffset, rowHeight))
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

        private fun wrapWithTopOffset(component: JComponent, topOffset: Int, height: Int): JPanel {
            return JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                minimumSize = Dimension(component.preferredSize.width, height)
                preferredSize = Dimension(component.preferredSize.width, height)
                maximumSize = Dimension(component.preferredSize.width, height)
                if (topOffset > 0) {
                    add(Box.createVerticalStrut(topOffset))
                }
                component.alignmentX = LEFT_ALIGNMENT
                add(component)
                val usedHeight = topOffset + component.preferredSize.height
                if (usedHeight < height) {
                    add(Box.createVerticalStrut(height - usedHeight))
                }
            }
        }

        private fun centeredRow(component: JComponent): JPanel {
            return JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(Box.createHorizontalGlue())
                add(component)
                add(Box.createHorizontalGlue())
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
            minimumSize = Dimension(420, 58)
            preferredSize = Dimension(420, 58)
            maximumSize = Dimension(420, 58)

            if (field is JTextField) {
                field.minimumSize = Dimension(366, 58)
                field.preferredSize = Dimension(366, 58)
                field.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 58)
            }

            add(field)
            add(Box.createHorizontalStrut(14))
        }
    }

    private inner class LoginButton(text: String) : JButton(text) {
        private val buttonFont = interFont(Font.BOLD, 27f)

        init {
            isOpaque = false
            isFocusPainted = false
            isBorderPainted = false
            isContentAreaFilled = false
            border = BorderFactory.createEmptyBorder()
            foreground = Color.WHITE
            font = buttonFont
            horizontalAlignment = CENTER
            verticalAlignment = CENTER
            minimumSize = Dimension(420, 60)
            preferredSize = Dimension(420, 60)
            maximumSize = Dimension(420, 60)
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