package com.hackwars.gui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Customizes the appearance and behavior of a desktop icon for a `JInternalFrame` to be suitable for a taskbar.
 *
 * This function adjusts the maximum size of the desktop icon, removes unnecessary components,
 * and applies custom styling to the button within the icon. Additionally, it ensures that
 * the desktop icon's border is modified but maintains proper layout alignment.
 *
 * @param desktopIcon the `JInternalFrame.JDesktopIcon` to be styled
 * @return the styled `JInternalFrame.JDesktopIcon` instance
 */
fun styleDesktopIcon(desktopIcon: JInternalFrame.JDesktopIcon): JInternalFrame.JDesktopIcon {
    // Limit the width of the desktop icon as it wants to be much bigger than it should
    desktopIcon.maximumSize = Dimension(120, Integer.MAX_VALUE)
    // Remove the drag handle
    desktopIcon.components.elementAtOrNull(1)?.let { desktopIcon.remove(it) }

    val iconButton = desktopIcon.ui.getAccessibleChild(desktopIcon, 0) as JButton
    iconButton.foreground = Color.WHITE
    iconButton.background = Color(41, 42, 41)
    iconButton.isFocusPainted = false

    // If we remove the border, the internal button gets placed poorly, so we need to recreate
    // the border as an empty one to preserve layout
    val borderInsets = desktopIcon.border?.getBorderInsets(desktopIcon) ?: Insets(1, 1, 1, 1)
    desktopIcon.border = EmptyBorder(borderInsets)

    return desktopIcon
}

class TaskBar : JPanel(BorderLayout()) {
    private val minimizedApplications = JScrollPane().run {
        // TODO: Add manual scrolling using left/right arrow buttons
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
        border = null

        this@TaskBar.add(this, BorderLayout.CENTER)
        JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).also {
            it.border = null
            setViewportView(it)
        }
    }

    fun addMinimizedApplication(icon: JInternalFrame.JDesktopIcon) {
        minimizedApplications.add(styleDesktopIcon(icon))
    }

    fun removeRestoredApplication(icon: JInternalFrame.JDesktopIcon) {
        minimizedApplications.remove(icon)
    }
}
