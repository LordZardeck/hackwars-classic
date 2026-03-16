package com.hackwars.client

import com.hackwars.gui.LoginForm
import com.hackwars.gui.login.LoginBackgroundPanel
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.plaf.basic.BasicButtonUI

class LoginPanel : LoginBackgroundPanel() {
    companion object {
        private const val PANEL_WIDTH = 1280
        private const val PANEL_HEIGHT = 832
        private const val SIGNUP_URL = "https://www.reddit.com/r/HackWars/"
        private val FIELD_BACKGROUND = Color(0x2D, 0x31, 0x39)
        private val FIELD_BORDER = Color(0x6F, 0x77, 0x82)
        private val LABEL_COLOR = Color(0x1E, 0x1E, 0x1E)
        private val TEXT_COLOR = Color(0xD8, 0xDF, 0xE6)
    }

    init {
        isOpaque = true
        layout = null
        preferredSize = Dimension(PANEL_WIDTH, PANEL_HEIGHT)
        minimumSize = Dimension(PANEL_WIDTH, PANEL_HEIGHT)
        maximumSize = Dimension(PANEL_WIDTH, PANEL_HEIGHT)
        buildForm()
    }

    private fun buildForm() {
        val formX = 550
        val formY = 120
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

        val signupLink =
            JLabel("<html><span style='color:#DFEDFA; text-decoration:underline;'>Create Account</span></html>").apply {
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

        var formPanel = JPanel(java.awt.GridBagLayout()).apply {
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

        formPanel = LoginForm()
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
}
