package com.hackwars.rewrite.client.login

import com.github.weisj.jsvg.attributes.ViewBox
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
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.concurrent.schedule
import kotlin.math.hypot
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

abstract class LoginSceneView : LoginBackgroundPanel() {
    companion object {
        private const val PANEL_WIDTH = 710
        private const val PANEL_HEIGHT = 450
        private const val LOGO_CLEARANCE_PX = 30
        private const val FORM_SIDE_INSET = 30
        private const val FORM_VERTICAL_INSET = 30
        private const val SPINNER_FRAME_DELAY_MS = 16
        private const val SPINNER_ROTATION_STEP_DEG = 3.8f
        private const val SPINNER_VERTICAL_OFFSET_PX = -10f
        private val loginMinDuration = 2.seconds

        val spinnerRingDocument = svgResource("images/loading.svg")
        val spinnerGlowlineDocument = svgResource("images/glowline-curved.svg")
    }

    private var authStartedAt = RewriteClientClock.nowNanos()
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
    private var spinnerTimer: Timer? = null
    private val emptyColumn = JPanel().apply { isOpaque = false }
    private val formColumn = JPanel(GridBagLayout()).apply { isOpaque = false }
    protected val formPanel = LoginForm()
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
            override fun hierarchyChanged(event: HierarchyEvent?) {
                when {
                    event != null && (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L ->
                        java.util.Timer().schedule(1000) {
                            runOnEdt { toggleLogin() }
                        }
                }
            }
        })

        formPanel.addPasswordAuthenticationListener { event ->
            authStartedAt = RewriteClientClock.nowNanos()
            onUsernamePasswordAuthenticate(event.email, event.password)
        }
    }

    fun clearError() {
        formPanel.showError(null)
    }

    protected open fun onUsernamePasswordAuthenticate(email: String, password: CharArray) {
        formPanel.showError(null)
        isAuthenticating = true
        toggleLogin(force = false)
    }

    open fun onAuthenticationFailure(reason: String) {
        val resetLoginForm = {
            formPanel.showError(reason)
            isAuthenticating = false
            toggleLogin(force = true)
        }
        val timeSinceAuthStarted = (RewriteClientClock.nowNanos() - authStartedAt).toDuration(DurationUnit.NANOSECONDS)
        if (timeSinceAuthStarted < loginMinDuration) {
            Timer((loginMinDuration - timeSinceAuthStarted).inWholeMilliseconds.toInt()) {
                resetLoginForm()
            }.apply {
                isRepeats = false
                start()
            }
            return
        }
        resetLoginForm()
    }

    fun showBootstrapStarted() {
        runOnEdt {
            clearError()
            isAuthenticating = true
            toggleLogin(force = false)
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

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        if (!isAuthenticating) {
            return
        }
        val logoBounds = getLogoBounds(size) ?: return
        paintAuthenticatingSpinner(graphics, logoBounds)
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
        spinnerTimer = Timer(SPINNER_FRAME_DELAY_MS) {
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

    private fun paintAuthenticatingSpinner(graphics: Graphics, logoBounds: Rectangle2D.Float) {
        val g2 = graphics.create() as? Graphics2D ?: return
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val baseDiagonal = hypot(logoBounds.width.toDouble(), logoBounds.height.toDouble()).toFloat()
        val diameter = baseDiagonal + 10f
        val radius = diameter / 2f
        val centerX = logoBounds.centerX.toFloat()
        val centerY = logoBounds.centerY.toFloat() + SPINNER_VERTICAL_OFFSET_PX
        val ringX = centerX - radius
        val ringY = centerY - radius
        val spinnerBounds = Rectangle2D.Float(ringX, ringY, diameter, diameter)
        renderSpinnerRingSvg(g2, spinnerBounds)
        renderSpinnerGlowlineSvg(g2, spinnerBounds, spinnerAngleDeg)
        g2.dispose()
    }

    private fun renderSpinnerRingSvg(g2: Graphics2D, bounds: Rectangle2D.Float) {
        val ringDocument = spinnerRingDocument ?: return
        val previousClip = g2.clip
        val previousTransform = g2.transform
        g2.clipRect(bounds.x.toInt(), bounds.y.toInt(), bounds.width.toInt(), bounds.height.toInt())
        g2.translate(bounds.x.toDouble(), bounds.y.toDouble())
        ringDocument.render(this, g2, ViewBox(0f, 0f, bounds.width, bounds.height))
        g2.transform = previousTransform
        g2.clip = previousClip
    }

    private fun renderSpinnerGlowlineSvg(g2: Graphics2D, bounds: Rectangle2D.Float, rotationDeg: Float) {
        val glowlineDocument = spinnerGlowlineDocument ?: return
        val previousClip = g2.clip
        val previousTransform = g2.transform
        g2.clipRect(bounds.x.toInt(), bounds.y.toInt(), bounds.width.toInt(), bounds.height.toInt())
        g2.translate(bounds.x.toDouble(), bounds.y.toDouble())
        g2.rotate(Math.toRadians(rotationDeg.toDouble()), bounds.width / 2.0, bounds.height / 2.0)
        glowlineDocument.render(this, g2, ViewBox(0f, 0f, bounds.width, bounds.height))
        g2.transform = previousTransform
        g2.clip = previousClip
    }

    private fun runOnEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            action()
        } else {
            SwingUtilities.invokeLater(action)
        }
    }
}
