package com.hackwars.client

import com.hackwars.gui.login.LoginBackgroundPanel
import com.hackwars.gui.login.LoginForm
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JPanel

class LoginPanel : LoginBackgroundPanel() {
    companion object {
        private const val PANEL_WIDTH = 700
        private const val PANEL_HEIGHT = 450
        private const val FORM_TOP_INSET = 0
        private const val LOGO_CLEARANCE_PX = 30
    }

    private val emptyColumn = JPanel().apply {
        isOpaque = false
    }

    private val formColumn = JPanel(GridBagLayout()).apply {
        isOpaque = false
    }
    private val formPanel = LoginForm()
    private val formConstraints = GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        weightx = 1.0
        weighty = 1.0
        anchor = GridBagConstraints.LINE_START
        insets = Insets(FORM_TOP_INSET, 0, 0, 0)
    }
    private var appliedLeftInset = Int.MIN_VALUE

    init {
        isOpaque = true
        layout = null
        preferredSize = Dimension(PANEL_WIDTH, PANEL_HEIGHT)
        buildColumns()
    }

    private fun buildColumns() {
        formColumn.add(formPanel, formConstraints)

        add(emptyColumn)
        add(formColumn)
    }

    override fun doLayout() {
        val splitX = getSplitX(width)
        emptyColumn.setBounds(0, 0, splitX, height)
        formColumn.setBounds(splitX, 0, (width - splitX).coerceAtLeast(0), height)
        updateFormInsets(splitX)
    }

    private fun updateFormInsets(splitX: Int) {
        val logoRightEdge = getLogoRightEdgeX(size)
        val requiredLeftInset = (logoRightEdge - splitX + LOGO_CLEARANCE_PX).coerceAtLeast(0)
        if (requiredLeftInset == appliedLeftInset) {
            return
        }

        appliedLeftInset = requiredLeftInset
        formConstraints.insets = Insets(FORM_TOP_INSET, requiredLeftInset, 0, 0)
        (formColumn.layout as GridBagLayout).setConstraints(formPanel, formConstraints)
    }
}
