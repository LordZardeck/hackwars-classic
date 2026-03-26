package com.hackwars.rewrite.client.shell

import com.github.weisj.jsvg.attributes.ViewBox
import com.hackwars.rewrite.client.mvc.RewriteView
import com.hackwars.rewrite.client.login.svgResource
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JLayeredPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

interface RewriteShellWindowHost {
    fun showWindow(frame: JInternalFrame)
    fun focusWindow(frame: JInternalFrame)
    fun disposeAllWindows()
    fun renderTaskBar(state: RewriteShellTaskBarState)
}

class RewriteDesktopShellView : JPanel(BorderLayout()), RewriteShellWindowHost, RewriteView<RewriteShellChromeState> {
    val desktopPane = RewriteDesktopBackgroundPane()
    val menuBar = RewriteDesktopMenuBar()
    val statsRail = RewriteShellStatsRail().apply {
        isVisible = false
    }
    val countdownLabel = RewriteShellCountdownLabel().apply {
        isVisible = false
    }

    private var nextCascadeIndex: Int = 0

    init {
        name = "rewrite-shell-host"
        desktopPane.add(statsRail, JLayeredPane.PALETTE_LAYER)
        desktopPane.add(countdownLabel, JLayeredPane.PALETTE_LAYER)
        add(desktopPane, BorderLayout.CENTER)
    }

    override fun doLayout() {
        super.doLayout()
        layoutShellChrome()
    }

    override fun showWindow(frame: JInternalFrame) {
        if (frame.parent !== desktopPane) {
            desktopPane.add(frame)
            frame.setLocation(
                48 + ((nextCascadeIndex % 8) * 28),
                42 + ((nextCascadeIndex % 8) * 22),
            )
            nextCascadeIndex += 1
        }
        if (!frame.isVisible) {
            frame.isVisible = true
        }
        desktopPane.moveToFront(frame)
        desktopPane.repaint()
    }

    override fun focusWindow(frame: JInternalFrame) {
        if (frame.parent !== desktopPane) {
            showWindow(frame)
        }
        if (frame.isIcon) {
            runCatching { frame.isIcon = false }
        }
        if (!frame.isVisible) {
            frame.isVisible = true
        }
        desktopPane.moveToFront(frame)
        runCatching { frame.isSelected = true }
    }

    override fun disposeAllWindows() {
        desktopPane.allFrames.toList().forEach { frame ->
            runCatching { frame.dispose() }
        }
        menuBar.taskBar.render(RewriteShellTaskBarState())
    }

    override fun renderTaskBar(state: RewriteShellTaskBarState) {
        menuBar.taskBar.render(state)
    }

    override fun render(model: RewriteShellChromeState) {
        statsRail.render(model.stats)
        statsRail.isVisible = model.statsVisible
        countdownLabel.render(model.countdown)
        countdownLabel.isVisible = model.countdownVisible
        layoutShellChrome()
        desktopPane.repaint()
    }

    fun renderChrome(state: RewriteShellChromeState) {
        render(state)
    }

    private fun layoutShellChrome() {
        val desktopWidth = desktopPane.width
        val desktopHeight = desktopPane.height
        if (desktopWidth <= 0 || desktopHeight <= 0) {
            return
        }

        val statsSize = statsRail.preferredSize
        statsRail.setBounds(
            desktopWidth - statsSize.width - 24,
            16,
            statsSize.width,
            statsSize.height,
        )

        val countdownSize = countdownLabel.preferredSize
        countdownLabel.setBounds(
            desktopWidth - countdownSize.width - 30,
            desktopHeight - 210 - countdownSize.height,
            countdownSize.width,
            countdownSize.height,
        )
    }
}

class RewriteDesktopBackgroundPane : JDesktopPane() {
    companion object {
        private val backgroundColor = Color(41, 42, 41)
        private val logoDocument = svgResource("images/hackwars-logo-split.svg")
    }

    init {
        name = "rewrite-shell-desktop"
        background = backgroundColor
        layout = null
        isOpaque = true
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g2 = graphics as? Graphics2D ?: return
        g2.color = backgroundColor
        g2.fillRect(0, 0, width, height)

        val document = logoDocument ?: return
        val component = this
        val documentSize = document.size()
        val scale = minOf(
            1.0f,
            (width * 0.42f) / documentSize.width.coerceAtLeast(1f),
            (height * 0.55f) / documentSize.height.coerceAtLeast(1f),
        )
        val drawWidth = documentSize.width * scale
        val drawHeight = documentSize.height * scale
        val x = ((width - drawWidth) / 2f) - 100f
        val y = ((height - drawHeight) / 2f) - 50f

        val previousComposite = g2.composite
        val previousTransform = g2.transform
        val previousClip = g2.clip
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.composite = AlphaComposite.SrcOver.derive(0.18f)
        g2.translate(x.toDouble(), y.toDouble())
        g2.clipRect(0, 0, drawWidth.toInt(), drawHeight.toInt())
        document.render(component, g2, ViewBox(0f, 0f, drawWidth, drawHeight))
        g2.transform = previousTransform
        g2.clip = previousClip
        g2.composite = previousComposite
    }
}

class RewritePlaceholderInternalFrame(
    val command: RewriteShellCommand,
) : JInternalFrame(command.title, true, true, true, true) {
    init {
        name = "rewrite-shell-window-${command.stableId}"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        size = Dimension(480, 320)
        contentPane = JPanel(BorderLayout()).apply {
            name = "rewrite-shell-window-content-${command.stableId}"
            add(
                JLabel(
                    "Rewrite placeholder window for ${command.title}.",
                    SwingConstants.CENTER,
                ).apply {
                    name = "rewrite-shell-window-label-${command.stableId}"
                },
                BorderLayout.CENTER,
            )
        }
    }
}
