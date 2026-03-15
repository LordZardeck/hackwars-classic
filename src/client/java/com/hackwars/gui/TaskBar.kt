package com.hackwars.gui

import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
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
    // Prevent dragging the desktop icon within the taskbar
    desktopIcon.mouseListeners.forEach(desktopIcon::removeMouseListener)
    desktopIcon.mouseMotionListeners.forEach(desktopIcon::removeMouseMotionListener)
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
    private companion object {
        const val MIN_SCROLL_STEP = 32
    }

    private enum class ScrollActionCommand(val command: String) {
        LEFT("scrollLeft"), RIGHT("scrollRight")
    }

    private val scrollHandler = object : ActionListener {
        override fun actionPerformed(e: ActionEvent?) {
            when (e?.actionCommand) {
                ScrollActionCommand.LEFT.command -> scrollMinimizedApplications(ScrollActionCommand.LEFT)
                ScrollActionCommand.RIGHT.command -> scrollMinimizedApplications(ScrollActionCommand.RIGHT)
            }
        }
    }

    private inner class ScrollButton(action: ScrollActionCommand) : JButton() {
        init {
            isContentAreaFilled = false
            preferredSize = Dimension(16, preferredSize.height)
            actionCommand = action.command
            isEnabled = false
            icon = when (action) {
                ScrollActionCommand.LEFT -> LEGACY_getImageIcon("images/taskBarLeft.png")
                ScrollActionCommand.RIGHT -> LEGACY_getImageIcon("images/taskBarRight.png")
            }
            addActionListener(scrollHandler)
        }
    }


    private val minimizedApplications = JPanel()
        .apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = null
        }
    private val applicationScrollPane = JScrollPane().apply {
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
        border = null
        setViewportView(minimizedApplications)
        viewport.addChangeListener { updateScrollButtonsEnabledState() }

        this@TaskBar.add(this, BorderLayout.CENTER)
    }

    private val scrollButtons = object : JPanel() {
        val leftScrollButton = ScrollButton(ScrollActionCommand.LEFT).also { add(it) }
        val rightScrollButton = ScrollButton(ScrollActionCommand.RIGHT).also { add(it) }
    }.also {
        it.layout = BoxLayout(it, BoxLayout.X_AXIS)
        add(it, BorderLayout.EAST)
    }

    private fun scrollMinimizedApplications(direction: ScrollActionCommand) {
        val viewRect = applicationScrollPane.viewport.viewRect
        val step = (viewRect.width / 2).coerceAtLeast(MIN_SCROLL_STEP)
        val delta = when (direction) {
            ScrollActionCommand.LEFT -> -step
            ScrollActionCommand.RIGHT -> step
        }

        val viewWidth = applicationScrollPane.viewport.view.preferredSize.width
        val maxX = (viewWidth - viewRect.width).coerceAtLeast(0)
        val targetX = (viewRect.x + delta).coerceIn(0, maxX)
        applicationScrollPane.viewport.viewPosition = Point(targetX, viewRect.y)
        updateScrollButtonsEnabledState()
    }

    private fun updateScrollButtonsEnabledState() {
        val viewRect = applicationScrollPane.viewport.viewRect
        val viewWidth = applicationScrollPane.viewport.view.preferredSize.width
        val hasOverflow = viewRect.width > 0 && viewWidth > viewRect.width

        scrollButtons.leftScrollButton.isEnabled = hasOverflow && viewRect.x > 0
        scrollButtons.rightScrollButton.isEnabled = hasOverflow && (viewRect.x + viewRect.width) < viewWidth
    }

    private fun queueScrollButtonsEnabledStateUpdate() {
        SwingUtilities.invokeLater { updateScrollButtonsEnabledState() }
    }

    fun addMinimizedApplication(icon: JInternalFrame.JDesktopIcon) {
        minimizedApplications.add(styleDesktopIcon(icon))
        applicationScrollPane.viewport.view.revalidate()
        applicationScrollPane.viewport.view.repaint()
        queueScrollButtonsEnabledStateUpdate()
    }

    fun removeRestoredApplication(icon: JInternalFrame.JDesktopIcon) {
        minimizedApplications.remove(icon)
        applicationScrollPane.viewport.view.revalidate()
        applicationScrollPane.viewport.view.repaint()
        queueScrollButtonsEnabledStateUpdate()
    }
}
