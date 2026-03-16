package com.hackwars.client

import com.hackwars.gui.login.LoginForm
import com.hackwars.gui.login.LoginBackgroundPanel
import java.awt.Dimension

class LoginPanel : LoginBackgroundPanel() {
    companion object {
        private const val PANEL_WIDTH = 1280
        private const val PANEL_HEIGHT = 832
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
        val formPanel = LoginForm()
        val formSize = formPanel.preferredSize
        formPanel.setBounds(550, 120, formSize.width, formSize.height)
        add(formPanel)
    }
}
