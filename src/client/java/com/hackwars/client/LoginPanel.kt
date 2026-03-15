package com.hackwars.client

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.attributes.ViewBox
import com.github.weisj.jsvg.parser.SVGLoader
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.plaf.ButtonUI
import javax.swing.plaf.basic.BasicButtonUI
import kotlin.math.max

class LoginPanel : JPanel() {
    companion object {
        private const val DESIGN_WIDTH = 1280f
        private const val DESIGN_HEIGHT = 832f
        private const val PANEL_WIDTH = 600
        private const val PANEL_HEIGHT = 400
        private const val LOGO_RESOURCE_PATH = "images/hackwars-logo-split.svg"
        private const val SIGNUP_URL = "https://www.reddit.com/r/HackWars/"
        private const val SPLIT_X = 308f
        private const val LOGO_X = 127.5f
        private const val LOGO_Y = 207f
        private const val LOGO_WIDTH = 361f
        private const val LOGO_HEIGHT = 418f
        private const val USERNAME_X = 711f
        private const val FIRST_FIELD_Y = 354f
        private const val SECOND_FIELD_Y = 426f
        private const val FIELD_WIDTH = 300f
        private const val FIELD_HEIGHT = 35f
        private const val LINK_Y = 481f
        private const val BUTTON_X = 925f
        private const val BUTTON_Y = 479f
        private const val BUTTON_WIDTH = 86f
        private const val BUTTON_HEIGHT = 29f
        private val RIGHT_BACKGROUND_COLORS = arrayOf(
            Color(0x2C, 0xEE, 0xFF),
            Color(0x0E, 0xD0, 0xFF),
            Color(0x0A, 0x7A, 0xD2)
        )
        private val RIGHT_BACKGROUND_FRACTIONS = floatArrayOf(0.0f, 0.45f, 1.0f)
        private val BACKGROUND_COLORS = arrayOf(
            Color(0x3F, 0x4D, 0x57),
            Color(0x17, 0x1F, 0x2B),
            Color(0x06, 0x09, 0x0F)
        )
        private val BACKGROUND_FRACTIONS = floatArrayOf(0.0f, 0.52f, 1.0f)
        private val FIELD_BACKGROUND = Color(0x2D, 0x31, 0x39)
        private val FIELD_BORDER = Color(0x6F, 0x77, 0x82)
        private val LABEL_COLOR = Color(0x1E, 0x1E, 0x1E)
        private val TEXT_COLOR = Color(0xD8, 0xDF, 0xE6)
    }

    private val logoDocument = loadLogoDocument()

    init {
        isOpaque = true
        layout = null
        preferredSize = Dimension(PANEL_WIDTH, PANEL_HEIGHT)
        minimumSize = Dimension(PANEL_WIDTH, PANEL_HEIGHT)
        maximumSize = Dimension(PANEL_WIDTH, PANEL_HEIGHT)
        buildForm()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val splitX = scaleX(SPLIT_X)
        g2.paint = LinearGradientPaint(
            0.0f,
            0.0f,
            0.0f,
            height.toFloat().coerceAtLeast(1.0f),
            BACKGROUND_FRACTIONS,
            BACKGROUND_COLORS
        )
        g2.fillRect(0, 0, splitX, height)

        g2.paint = LinearGradientPaint(
            0.0f,
            0.0f,
            0.0f,
            height.toFloat().coerceAtLeast(1.0f),
            RIGHT_BACKGROUND_FRACTIONS,
            RIGHT_BACKGROUND_COLORS
        )
        g2.fillRect(splitX, 0, width - splitX, height)

        paintLogo(g2)
    }

    private fun paintLogo(g2: Graphics2D) {
        val svg = logoDocument ?: return
        val logoX = scaleX(LOGO_X)
        val logoY = scaleY(LOGO_Y)
        val logoWidth = scaleX(LOGO_WIDTH).coerceAtLeast(1)
        val logoHeight = scaleY(LOGO_HEIGHT).coerceAtLeast(1)
        val oldClip = g2.clip
        val oldTransform = g2.transform
        g2.clipRect(logoX, logoY, logoWidth, logoHeight)
        g2.translate(logoX.toDouble(), logoY.toDouble())
        svg.render(this as Component, g2, ViewBox(0f, 0f, logoWidth.toFloat(), logoHeight.toFloat()))
        g2.transform = oldTransform
        g2.clip = oldClip
    }

    private fun buildForm() {
        val formX = 280
        val formY = 130
        val fieldWidth = 100
        val fieldHeight = 30
        val buttonWidth = 86
        val buttonHeight = 29
        val baseFont = Font("SansSerif", Font.BOLD, 16)

        val usernameLabel = JLabel("Username").apply {
            foreground = LABEL_COLOR
            font = baseFont
        }

        val usernameField = JTextField(15).apply {
            styleField(this)
            preferredSize = Dimension(fieldWidth, fieldHeight)
            minimumSize = Dimension(fieldWidth, fieldHeight)
        }

        val passwordLabel = JLabel("Password").apply {
            foreground = LABEL_COLOR
            font = baseFont
        }

        val passwordField = JPasswordField(15).apply {
            styleField(this)
            preferredSize = Dimension(fieldWidth, fieldHeight)
            minimumSize = Dimension(fieldWidth, fieldHeight)
        }

        val signupLink = JLabel("<html><span style='color:#DFEDFA; text-decoration:underline;'>Create Account</span></html>").apply {
            font = Font("SansSerif", Font.BOLD, 14)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(112, 22)
            minimumSize = Dimension(112, 22)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    openSignupUrl()
                }
            })
        }

        val loginButton = object : JButton("Login") {
//            override fun paintComponent(g: Graphics) {
//                val g2 = g.create() as Graphics2D
//                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
//                g2.color = FIELD_BACKGROUND
//                g2.fillRoundRect(0, 0, width - 1, height - 1, 8, 8)
//                g2.color = FIELD_BORDER
//                g2.stroke = BasicStroke(2f)
//                g2.drawRoundRect(1, 1, width - 3, height - 3, 8, 8)
//                g2.dispose()
//                super.paintComponent(g)
//            }
        }.apply {
            horizontalAlignment = SwingConstants.CENTER
            foreground = TEXT_COLOR
            font = Font("SansSerif", Font.BOLD, 14)
//            isOpaque = false
//            isContentAreaFilled = false
            isFocusPainted = false
//            border = EmptyBorder(0, 0, 0, 0)
            background = FIELD_BACKGROUND
            ui = object : BasicButtonUI() {
                override fun paintButtonPressed(g: Graphics, b: AbstractButton) {
                    g.color = Color(58, 63, 73)
                    g.fillRect(0, 0, b.size.width, b.size.height);
                }
            }
            border = BorderFactory.createLineBorder(FIELD_BORDER)
            preferredSize = Dimension(buttonWidth, buttonHeight)
            minimumSize = Dimension(buttonWidth, buttonHeight)
            maximumSize = Dimension(buttonWidth, buttonHeight)
            addActionListener {
                // Placeholder action: login wiring comes later.
            }
        }

        val linkSection = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            isOpaque = false
            add(signupLink)
        }

        val actionsRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(linkSection, java.awt.BorderLayout.WEST)
            add(loginButton, java.awt.BorderLayout.EAST)
            preferredSize = Dimension(fieldWidth, buttonHeight)
        }

        val formPanel = JPanel(java.awt.GridBagLayout()).apply {
            isOpaque = false
        }

        val constraints = java.awt.GridBagConstraints().apply {
            gridx = 0
            fill = java.awt.GridBagConstraints.HORIZONTAL
            anchor = java.awt.GridBagConstraints.WEST
            weightx = 1.0
        }

        constraints.gridy = 0
        constraints.insets = java.awt.Insets(0, 0, 4, 0)
        formPanel.add(usernameLabel, constraints)

        constraints.gridy = 1
        constraints.insets = java.awt.Insets(0, 0, 12, 0)
        formPanel.add(usernameField, constraints)

        constraints.gridy = 2
        constraints.insets = java.awt.Insets(0, 0, 4, 0)
        formPanel.add(passwordLabel, constraints)

        constraints.gridy = 3
        constraints.insets = java.awt.Insets(0, 0, 12, 0)
        formPanel.add(passwordField, constraints)

        constraints.gridy = 4
        constraints.insets = java.awt.Insets(0, 0, 0, 0)
        formPanel.add(actionsRow, constraints)

        val formSize = formPanel.preferredSize
        formPanel.setBounds(formX, formY, formSize.width, formSize.height)
        add(formPanel)
    }

    private fun styleField(field: JTextField) {
        field.foreground = TEXT_COLOR
        field.background = FIELD_BACKGROUND
        field.caretColor = TEXT_COLOR
        field.font = Font("SansSerif", Font.BOLD, 14)
        field.border = CompoundBorder(
            LineBorder(FIELD_BORDER, 2, true),
            EmptyBorder(2, 12, 2, 12)
        )
    }

    private fun scaleX(value: Float): Int {
        return (value / DESIGN_WIDTH * PANEL_WIDTH).toInt()
    }

    private fun scaleY(value: Float): Int {
        return (value / DESIGN_HEIGHT * PANEL_HEIGHT).toInt()
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

    private fun loadLogoDocument(): SVGDocument? {
        return runCatching {
            javaClass.classLoader.getResource(LOGO_RESOURCE_PATH)?.let { resourceUrl ->
                SVGLoader().load(resourceUrl)
            }
        }.getOrNull()
    }
}
