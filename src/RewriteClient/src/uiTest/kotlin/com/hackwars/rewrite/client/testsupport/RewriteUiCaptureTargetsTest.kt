package com.hackwars.rewrite.client.testsupport

import java.awt.GraphicsEnvironment
import javax.swing.JInternalFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteUiCaptureTargetsTest {
    @Test
    fun visibleTargetTracksLoginAndDesktopRoots() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val loginFrame = invokeAndWaitResult {
            com.hackwars.rewrite.client.RewriteRootFrame().apply { isVisible = true }
        }
        try {
            assertEquals("rewrite-root-login", rewriteVisibleCaptureTarget(loginFrame).name)
        } finally {
            disposeFrame(loginFrame)
        }

        val desktopFrame = rewriteReadyFrame()
        try {
            assertEquals("rewrite-shell-host", rewriteVisibleCaptureTarget(desktopFrame).name)
        } finally {
            disposeFrame(desktopFrame)
        }
    }

    @Test
    fun captureTargetResolvesShellChromeAndNamedWindows() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = rewriteReadyFrame()
        val dialog = invokeAndWaitResult {
            javax.swing.JDialog(frame, "Capture Dialog").apply {
                name = "rewrite-capture-dialog"
                setSize(200, 100)
                isVisible = true
            }
        }
        try {
            invokeAndWait {
                frame.desktopPane.add(
                    JInternalFrame("Capture Window", true, true, true, true).apply {
                        name = "rewrite-capture-window"
                        setSize(240, 140)
                        isVisible = true
                    },
                )
            }

            assertEquals("rewrite-shell-menu-bar", rewriteCaptureTarget(frame, "rewrite-shell-menu-bar").name)
            assertEquals("rewrite-shell-taskbar", rewriteCaptureTarget(frame, "rewrite-shell-taskbar").name)
            assertEquals("rewrite-shell-stats-rail", rewriteCaptureTarget(frame, "rewrite-shell-stats-rail").name)
            assertEquals("rewrite-capture-window", rewriteCaptureTarget(frame, "rewrite-capture-window").name)
            assertEquals("rewrite-capture-dialog", rewriteCaptureTarget(frame, "rewrite-capture-dialog").name)
        } finally {
            invokeAndWait { dialog.dispose() }
            disposeFrame(frame)
        }
    }
}
