package com.hackwars.rewrite.client.shell

import com.hackwars.rewrite.client.mvc.RewriteFrameBinding
import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent

class RewriteShellWindowCoordinator(
    private val frameFactory: (RewriteShellCommand, Int?) -> RewriteFrameBinding = { command, _ ->
        RewriteFrameBinding(RewritePlaceholderInternalFrame(command))
    },
) {
    private val openWindows = linkedMapOf<RewriteShellCommand, RewriteFrameBinding>()
    private var host: RewriteShellWindowHost? = null

    fun attachHost(host: RewriteShellWindowHost?) {
        this.host = host
    }

    fun open(
        command: RewriteShellCommand,
        preferredPort: Int? = null,
    ) {
        val currentHost = host ?: return
        val existingBinding = openWindows[command]
        val existing = existingBinding?.frame
        if (existing != null && !existing.isClosed) {
            (existing as? RewritePreferredPortWindow)?.applyPreferredPort(preferredPort)
            currentHost.focusWindow(existing)
            return
        }

        val binding = frameFactory(command, preferredPort)
        val frame = binding.frame
        frame.addInternalFrameListener(object : InternalFrameAdapter() {
            override fun internalFrameClosed(event: InternalFrameEvent) {
                openWindows.remove(command, binding)
                binding.close()
            }
        })
        openWindows[command] = binding
        currentHost.showWindow(frame)
        currentHost.focusWindow(frame)
    }

    fun closeAll() {
        val windows = openWindows.values.toList()
        openWindows.clear()
        windows.forEach { binding ->
            runCatching { binding.frame.dispose() }
            binding.close()
        }
        host?.disposeAllWindows()
    }

    fun openWindowCount(): Int = openWindows.values.count { !it.frame.isClosed }

    fun openWindow(command: RewriteShellCommand): JInternalFrame? =
        openWindows[command]?.frame?.takeIf { !it.isClosed }
}
