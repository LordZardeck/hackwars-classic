package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.mvc.RewriteView
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.BoxLayout

internal class RewriteWatchManagerWindow(
    private val rowSpacing: Int = 6,
) : JInternalFrame("Watch Manager", true, true, true, true),
    RewriteView<RewriteWatchManagerViewModel> {
    private val rowsPanel = JPanel().apply {
        name = "rewrite-watch-manager-rows"
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    internal val statusLabel = JLabel("Loading watches...").apply {
        name = "rewrite-watch-manager-status"
    }
    internal val errorLabel = JLabel(" ").apply {
        name = "rewrite-watch-manager-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    internal val installMenuItem = JMenuItem("Install New Watch").apply {
        name = "rewrite-watch-manager-install-menu-item"
        isEnabled = false
    }
    internal val exitMenuItem = JMenuItem("Exit").apply {
        name = "rewrite-watch-manager-exit-menu-item"
    }

    init {
        name = "rewrite-watch-manager-window"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(1140, 520)
        jMenuBar = buildMenuBar()
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(buildHeaderPanel(), BorderLayout.NORTH)
            add(
                JScrollPane(rowsPanel).apply {
                    name = "rewrite-watch-manager-scroll-pane"
                    border = BorderFactory.createLineBorder(Color(0xCC, 0xCC, 0xCC))
                },
                BorderLayout.CENTER,
            )
            add(buildFooterPanel(), BorderLayout.SOUTH)
        }
    }

    override fun render(model: RewriteWatchManagerViewModel) {
        installMenuItem.isEnabled = model.installMenuEnabled
        statusLabel.text = model.statusText
        errorLabel.text = model.errorText
        if (model.rows.isEmpty()) {
            if (model.errorText.isBlank() || model.errorText == " ") {
                statusLabel.text = "No watches installed."
            }
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    internal fun setRowComponents(components: List<JPanel>) {
        rowsPanel.removeAll()
        components.forEachIndexed { index, component ->
            rowsPanel.add(component)
            if (index < components.lastIndex) {
                rowsPanel.add(Box.createVerticalStrut(rowSpacing))
            }
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    private fun buildMenuBar(): JMenuBar {
        return JMenuBar().apply {
            add(
                JMenu("File").apply {
                    add(installMenuItem)
                    add(exitMenuItem)
                },
            )
        }
    }

    private fun buildHeaderPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(buildColumnHeaderRow(), BorderLayout.CENTER)
        }
    }

    private fun buildFooterPanel(): JPanel {
        return JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(statusLabel, BorderLayout.NORTH)
            add(errorLabel, BorderLayout.SOUTH)
        }
    }

    private fun buildColumnHeaderRow(): JPanel {
        return JPanel(java.awt.GridBagLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 4, 4, 4)
            var column = 0
            addHeader(this, "#", column++, 32)
            addHeader(this, "On/Off", column++, 76)
            addHeader(this, "Port", column++, 76)
            addHeader(this, "Type", column++, 110)
            addHeader(this, "CPU Cost", column++, 76)
            addHeader(this, "Note", column++, 160, weightx = 0.4)
            addHeader(this, "Observed Ports", column++, 130, weightx = 0.25)
            addHeader(this, "Search FireWall", column++, 142, weightx = 0.15)
            addHeader(this, "Value", column++, 120, weightx = 0.2)
            addHeader(this, "Delete", column, 70)
        }
    }

    private fun addHeader(
        panel: JPanel,
        title: String,
        gridx: Int,
        width: Int,
        weightx: Double = 0.0,
    ) {
        panel.add(
            JLabel(title),
            java.awt.GridBagConstraints().apply {
                this.gridx = gridx
                this.gridy = 0
                this.insets = java.awt.Insets(0, 4, 0, 4)
                this.anchor = java.awt.GridBagConstraints.WEST
                this.fill = java.awt.GridBagConstraints.HORIZONTAL
                this.weightx = weightx
                this.ipadx = width
            },
        )
    }
}
