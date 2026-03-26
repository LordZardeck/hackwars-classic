package com.hackwars.rewrite.client.shell

import com.hackwars.rewrite.client.mvc.RewriteView
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JInternalFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

class RewriteDesktopTaskBar : JPanel(BorderLayout()), RewriteView<RewriteShellTaskBarState> {
    private enum class ScrollDirection {
        LEFT,
        RIGHT,
    }

    private val minimizedApplications = JPanel().apply {
        name = "rewrite-shell-taskbar-items"
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = null
        isOpaque = false
    }

    private val applicationScrollPane = JScrollPane().apply {
        name = "rewrite-shell-taskbar-scroll-pane"
        border = null
        isOpaque = false
        viewport.isOpaque = false
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
        viewport.view = minimizedApplications
    }

    val leftScrollButton = createScrollButton("rewrite-shell-taskbar-scroll-left", "<")
    val rightScrollButton = createScrollButton("rewrite-shell-taskbar-scroll-right", ">")

    init {
        name = "rewrite-shell-taskbar"
        isOpaque = false
        add(applicationScrollPane, BorderLayout.CENTER)
        add(
            JPanel().apply {
                name = "rewrite-shell-taskbar-scroll-buttons"
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(leftScrollButton)
                add(rightScrollButton)
            },
            BorderLayout.EAST,
        )
        updateScrollButtonsEnabledState()
    }

    fun minimizedApplicationCount(): Int = minimizedApplications.componentCount

    fun scrollLeft() {
        scrollMinimizedApplications(ScrollDirection.LEFT)
    }

    fun scrollRight() {
        scrollMinimizedApplications(ScrollDirection.RIGHT)
    }

    override fun doLayout() {
        super.doLayout()
        updateScrollButtonsEnabledState()
    }

    override fun render(model: RewriteShellTaskBarState) {
        minimizedApplications.removeAll()
        model.minimizedIcons.forEach { icon ->
            styleDesktopIcon(icon)
            minimizedApplications.add(icon)
        }
        revalidateTaskBar()
    }

    private fun revalidateTaskBar() {
        minimizedApplications.revalidate()
        minimizedApplications.repaint()
        queueScrollButtonsEnabledStateUpdate()
    }

    private fun createScrollButton(name: String, text: String): JButton {
        return JButton(text).apply {
            this.name = name
            isContentAreaFilled = false
            isFocusPainted = false
            isEnabled = false
            preferredSize = Dimension(18, preferredSize.height)
        }
    }

    private fun styleDesktopIcon(icon: JInternalFrame.JDesktopIcon) {
        icon.maximumSize = Dimension(120, Int.MAX_VALUE)
        icon.mouseListeners.forEach(icon::removeMouseListener)
        icon.mouseMotionListeners.forEach(icon::removeMouseMotionListener)
        if (icon.componentCount > 1) {
            icon.remove(1)
        }

        val button = icon.ui.getAccessibleChild(icon, 0) as? JButton
        button?.apply {
            foreground = Color.WHITE
            background = Color(41, 42, 41)
            isFocusPainted = false
        }

        val borderInsets = icon.border?.getBorderInsets(icon) ?: Insets(1, 1, 1, 1)
        icon.border = EmptyBorder(borderInsets)
    }

    private fun scrollMinimizedApplications(direction: ScrollDirection) {
        val viewRect = applicationScrollPane.viewport.viewRect
        val step = (viewRect.width / 2).coerceAtLeast(32)
        val delta = when (direction) {
            ScrollDirection.LEFT -> -step
            ScrollDirection.RIGHT -> step
        }

        val viewWidth = applicationScrollPane.viewport.view.preferredSize.width
        val maxX = (viewWidth - viewRect.width).coerceAtLeast(0)
        val targetX = (viewRect.x + delta).coerceIn(0, maxX)
        applicationScrollPane.viewport.viewPosition = Point(targetX, viewRect.y)
        updateScrollButtonsEnabledState()
    }

    private fun queueScrollButtonsEnabledStateUpdate() {
        SwingUtilities.invokeLater { updateScrollButtonsEnabledState() }
    }

    fun updateScrollButtonsEnabledState() {
        val viewRect = applicationScrollPane.viewport.viewRect
        val viewWidth = applicationScrollPane.viewport.view.preferredSize.width
        val hasOverflow = viewRect.width > 0 && viewWidth > viewRect.width

        leftScrollButton.isEnabled = hasOverflow && viewRect.x > 0
        rightScrollButton.isEnabled = hasOverflow && (viewRect.x + viewRect.width) < viewWidth
    }
}
