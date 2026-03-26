package com.hackwars.rewrite.client.shell

import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent

class RewriteShellWindowCoordinator(
    private val frameFactory: (RewriteShellCommand) -> JInternalFrame = { RewritePlaceholderInternalFrame(it) },
) {
    private val openWindows = linkedMapOf<RewriteShellCommand, JInternalFrame>()
    private var host: RewriteShellWindowHost? = null

    fun attachHost(host: RewriteShellWindowHost?) {
        this.host = host
    }

    fun open(command: RewriteShellCommand) {
        val currentHost = host ?: return
        val existing = openWindows[command]
        if (existing != null && !existing.isClosed) {
            currentHost.focusWindow(existing)
            return
        }

        val frame = frameFactory(command)
        frame.addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                openWindows.remove(command, frame)
            }
        })
        openWindows[command] = frame
        currentHost.showWindow(frame)
        currentHost.focusWindow(frame)
    }

    fun closeAll() {
        val windows = openWindows.values.toList()
        openWindows.clear()
        windows.forEach { frame ->
            runCatching { frame.dispose() }
        }
        host?.disposeAllWindows()
    }

    fun openWindowCount(): Int = openWindows.values.count { !it.isClosed }

    fun openWindow(command: RewriteShellCommand): JInternalFrame? = openWindows[command]?.takeIf { !it.isClosed }
}
