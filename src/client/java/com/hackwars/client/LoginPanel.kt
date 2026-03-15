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
        val formX = scaleX(USERNAME_X)
        val firstFieldY = scaleY(FIRST_FIELD_Y)
        val secondFieldY = scaleY(SECOND_FIELD_Y)
        val fieldWidth = max(scaleX(FIELD_WIDTH), 220)
        val fieldHeight = max(scaleY(FIELD_HEIGHT), 26)
        val labelHeight = 20
        val linkY = scaleY(LINK_Y)
        val buttonX = max(scaleX(BUTTON_X), formX + fieldWidth - max(scaleX(BUTTON_WIDTH), 80))
        val buttonY = scaleY(BUTTON_Y)
        val buttonWidth = max(scaleX(BUTTON_WIDTH), 80)
        val buttonHeight = max(scaleY(BUTTON_HEIGHT), 30)
        val baseFont = Font("SansSerif", Font.BOLD, 16)

        val usernameLabel = JLabel("Username").apply {
            foreground = LABEL_COLOR
            font = baseFont
            setBounds(formX, firstFieldY - 26, fieldWidth, labelHeight)
        }

        val usernameField = JTextField(20).apply {
            styleField(this)
            setBounds(formX, firstFieldY, fieldWidth, fieldHeight)
        }

        val passwordLabel = JLabel("Password").apply {
            foreground = LABEL_COLOR
            font = baseFont
            setBounds(formX, secondFieldY - 26, fieldWidth, labelHeight)
        }

        val passwordField = JPasswordField(20).apply {
            styleField(this)
            setBounds(formX, secondFieldY, fieldWidth, fieldHeight)
        }

        val signupLink = JLabel("<html><span style='color:#DFEDFA; text-decoration:underline;'>Create Account</span></html>").apply {
            font = Font("SansSerif", Font.BOLD, 14)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            setBounds(formX, linkY, 140, 26)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    openSignupUrl()
                }
            })
        }

        val separator = object : JComponent() {
            init {
                isOpaque = false
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.color = Color(0xFF, 0xFF, 0xFF, 190)
                g2.stroke = BasicStroke(1f)
                g2.drawLine(0, height / 2, width, height / 2)
            }
        }.apply {
            setBounds(formX, linkY + 22, 112, 4)
        }

        val loginButton = object : JButton("Login") {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = FIELD_BACKGROUND
                g2.fillRoundRect(0, 0, width - 1, height - 1, 8, 8)
                g2.color = FIELD_BORDER
                g2.stroke = BasicStroke(2f)
                g2.drawRoundRect(1, 1, width - 3, height - 3, 8, 8)
                g2.dispose()
                super.paintComponent(g)
            }
        }.apply {
            horizontalAlignment = SwingConstants.CENTER
            foreground = TEXT_COLOR
            font = Font("SansSerif", Font.BOLD, 14)
            isOpaque = false
            isContentAreaFilled = false
            isFocusPainted = false
            border = EmptyBorder(0, 0, 0, 0)
            setBounds(buttonX, buttonY, buttonWidth, buttonHeight)
            addActionListener {
                // Placeholder action: login wiring comes later.
            }
        }

        add(usernameLabel)
        add(usernameField)
        add(passwordLabel)
        add(passwordField)
        add(signupLink)
        add(separator)
        add(loginButton)
    }

    private fun styleField(field: JTextField) {
        field.foreground = TEXT_COLOR
        field.background = FIELD_BACKGROUND
        field.caretColor = TEXT_COLOR
        field.font = Font("SansSerif", Font.BOLD, 16)
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
