package com.hackwars.client

import com.github.weisj.jsvg.attributes.ViewBox
import com.hackwars.gui.login.LoginBackgroundPanel
import com.hackwars.gui.login.LoginForm
import com.hackwars.gui.svgResource
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.geom.Rectangle2D
import java.util.Timer
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.schedule
import javax.swing.Timer as SwingTimer
import kotlin.math.hypot

class LoginScene : LoginBackgroundPanel() {
    companion object {
        private const val PANEL_WIDTH = 710
        private const val PANEL_HEIGHT = 450
        private const val LOGO_CLEARANCE_PX = 30
        private const val FORM_SIDE_INSET = 30
        private const val FORM_VERTICAL_INSET = 30
        private const val SPINNER_FRAME_DELAY_MS = 16
        private const val SPINNER_ROTATION_STEP_DEG = 3.8f
        private const val SPINNER_VERTICAL_OFFSET_PX = -10f

        val spinnerRingDocument = svgResource("images/loading.svg")
        val spinnerGlowlineDocument = svgResource("images/glowline-curved.svg")
    }

    private var isAuthenticating = false
        set(value) {
            if (field == value) {
                return
            }

            field = value
            runOnEdt {
                if (value) {
                    startSpinnerAnimation()
                } else {
                    stopSpinnerAnimation(resetAngle = false)
                }
                repaint()
            }
        }

    private var spinnerAngleDeg = 0f
    private var spinnerTimer: SwingTimer? = null

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
        fill = GridBagConstraints.BOTH
        anchor = GridBagConstraints.CENTER
        insets = Insets(0, 0, 0, 0)
    }

    init {
        isOpaque = true
        layout = null
        preferredSize = Dimension(PANEL_WIDTH, PANEL_HEIGHT)
        buildColumns()
        formPanel.isVisible = false

        addHierarchyListener(object : HierarchyListener {
            override fun hierarchyChanged(e: HierarchyEvent?) {
                e?.changeFlags?.let {
                    when {
                        (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L -> Timer().schedule(1000) { toggleLogin() }
                    }
                }
            }
        })

        formPanel.apply {
            addAuthenticationListener(object : LoginForm.AuthenticationListener {
                override fun onAuthenticationEvent(event: LoginForm.AuthenticationEvent) {
                    when (event.eventType) {
                        LoginForm.AuthenticationEvent.EventType.STARTED -> {
                            this@LoginScene.isAuthenticating = true
                            toggleLogin(false)
                        }

                        LoginForm.AuthenticationEvent.EventType.FAILURE -> {
                            this@LoginScene.isAuthenticating = false
                            toggleLogin(true)
                        }

                        LoginForm.AuthenticationEvent.EventType.SUCCESS -> {
                            this@LoginScene.isAuthenticating = false
                        }
                    }
                }
            })
        }
    }

    override fun addNotify() {
        super.addNotify()
        if (isAuthenticating) {
            startSpinnerAnimation()
        }
    }

    override fun removeNotify() {
        stopSpinnerAnimation(resetAngle = true)
        super.removeNotify()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (!isAuthenticating) {
            return
        }

        val logoBounds = getLogoBounds(size) ?: return
        paintAuthenticatingSpinner(g, logoBounds)
    }

    private fun toggleLogin(force: Boolean = !formPanel.isVisible) {
        formPanel.isVisible = force
        centered = !formPanel.isVisible
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

        formConstraints.insets = Insets(FORM_VERTICAL_INSET, requiredLeftInset, FORM_VERTICAL_INSET, FORM_SIDE_INSET)
        (formColumn.layout as GridBagLayout).setConstraints(formPanel, formConstraints)
    }

    private fun startSpinnerAnimation() {
        if (!isDisplayable || spinnerTimer?.isRunning == true) {
            return
        }

        spinnerTimer = SwingTimer(SPINNER_FRAME_DELAY_MS) {
            spinnerAngleDeg = (spinnerAngleDeg + SPINNER_ROTATION_STEP_DEG) % 360f
            repaint()
        }.apply {
            isCoalesce = true
            start()
        }
    }

    private fun stopSpinnerAnimation(resetAngle: Boolean) {
        spinnerTimer?.stop()
        spinnerTimer = null
        if (resetAngle) {
            spinnerAngleDeg = 0f
        }
    }

    private fun paintAuthenticatingSpinner(g: Graphics, logoBounds: Rectangle2D.Float) {
        val g2 = (g.create() as? Graphics2D) ?: return
        g2.run {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val baseDiagonal = hypot(logoBounds.width.toDouble(), logoBounds.height.toDouble()).toFloat()
            val diameter = baseDiagonal + 10f
            val radius = diameter / 2f
            val centerX = logoBounds.centerX.toFloat()
            val centerY = logoBounds.centerY.toFloat() + SPINNER_VERTICAL_OFFSET_PX
            val ringX = centerX - radius
            val ringY = centerY - radius
            val spinnerBounds = Rectangle2D.Float(ringX, ringY, diameter, diameter)
            renderSpinnerRingSvg(this, spinnerBounds)
            renderSpinnerGlowlineSvg(this, spinnerBounds, spinnerAngleDeg)
        }
        g2.dispose()
    }

    private fun renderSpinnerRingSvg(g2: Graphics2D, bounds: Rectangle2D.Float) {
        val ringDocument = spinnerRingDocument ?: return
        val prevClip = g2.clip
        val prevTransform = g2.transform

        g2.clipRect(bounds.x.toInt(), bounds.y.toInt(), bounds.width.toInt(), bounds.height.toInt())
        g2.translate(bounds.x.toDouble(), bounds.y.toDouble())
        ringDocument.render(this, g2, ViewBox(0f, 0f, bounds.width, bounds.height))

        g2.transform = prevTransform
        g2.clip = prevClip
    }

    private fun renderSpinnerGlowlineSvg(g2: Graphics2D, bounds: Rectangle2D.Float, rotationDeg: Float) {
        val glowlineDocument = spinnerGlowlineDocument ?: return
        val prevClip = g2.clip
        val prevTransform = g2.transform

        g2.clipRect(bounds.x.toInt(), bounds.y.toInt(), bounds.width.toInt(), bounds.height.toInt())
        g2.translate(bounds.x.toDouble(), bounds.y.toDouble())
        g2.rotate(Math.toRadians(rotationDeg.toDouble()), bounds.width / 2.0, bounds.height / 2.0)
        glowlineDocument.render(this, g2, ViewBox(0f, 0f, bounds.width, bounds.height))

        g2.transform = prevTransform
        g2.clip = prevClip
    }

    private fun runOnEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            action()
        } else {
            SwingUtilities.invokeLater(action)
        }
    }
}
