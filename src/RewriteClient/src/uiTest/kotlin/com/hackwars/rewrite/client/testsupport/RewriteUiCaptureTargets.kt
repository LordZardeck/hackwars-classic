package com.hackwars.rewrite.client.testsupport

import com.hackwars.rewrite.client.RewriteRootFrame
import java.awt.Component
import java.awt.Window
import javax.swing.JDialog
import javax.swing.JInternalFrame

fun rewriteVisibleCaptureTarget(frame: RewriteRootFrame): Component {
    return invokeAndWaitResult {
        if (frame.shellHost.isShowing) {
            frame.shellHost
        } else {
            frame.loginScene
        }
    }
}

fun rewriteUiVisibleCaptureTarget(frame: RewriteRootFrame): Component = rewriteVisibleCaptureTarget(frame)

fun rewriteCaptureTarget(
    frame: RewriteRootFrame,
    targetId: String,
): Component {
    return when (targetId) {
        "rewrite-root-login" -> frame.loginScene
        "rewrite-shell-host" -> frame.shellHost
        "rewrite-shell-desktop" -> frame.desktopPane
        "rewrite-shell-menu-bar" -> frame.shellHost.menuBar
        "rewrite-shell-taskbar" -> frame.shellHost.menuBar.taskBar
        "rewrite-shell-stats-rail" -> frame.shellHost.statsRail
        "rewrite-shell-countdown" -> frame.shellHost.countdownLabel
        else -> findComponent(frame.rootPane, targetId)
            ?: Window.getWindows()
                .filterIsInstance<JDialog>()
                .firstOrNull { it.isDisplayable && (it.name == targetId || it.title == targetId) }
            ?: waitForWindow(frame, targetId)
    }
}

fun rewriteUiCaptureTarget(
    frame: RewriteRootFrame,
    targetId: String,
): Component = rewriteCaptureTarget(frame, targetId)

fun rewriteWindowCaptureTarget(
    frame: RewriteRootFrame,
    windowName: String,
): JInternalFrame = waitForWindow(frame, windowName)

fun rewriteUiWindowCaptureTarget(
    frame: RewriteRootFrame,
    windowName: String,
): JInternalFrame = rewriteWindowCaptureTarget(frame, windowName)

fun rewriteDialogCaptureTarget(dialogKey: String): JDialog = waitForDialog(dialogKey)

fun rewriteUiDialogCaptureTarget(dialogKey: String): JDialog = rewriteDialogCaptureTarget(dialogKey)
