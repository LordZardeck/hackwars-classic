package com.hackwars.rewrite.client.shell

import com.hackwars.rewrite.client.mvc.RewriteDialogBinding
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog

internal interface RewriteShellDialogHost {
    val ownerWindow: Window?

    fun showDialog(dialog: JDialog)

    fun focusDialog(dialog: JDialog)
}

internal class RewriteShellDialogCoordinator(
    private val dialogFactory: (RewriteShellCommand, Window?) -> RewriteDialogBinding,
) {
    private val openDialogs = linkedMapOf<RewriteShellCommand, RewriteDialogBinding>()
    private var host: RewriteShellDialogHost? = null

    fun attachHost(host: RewriteShellDialogHost?) {
        this.host = host
    }

    fun open(command: RewriteShellCommand) {
        val currentHost = host ?: return
        val existingBinding = openDialogs[command]
        val existing = existingBinding?.dialog
        if (existing != null && existing.isDisplayable) {
            currentHost.focusDialog(existing)
            return
        }

        val binding = dialogFactory(command, currentHost.ownerWindow)
        val dialog = binding.dialog
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) {
                openDialogs.remove(command, binding)
                binding.close()
            }
        })
        openDialogs[command] = binding
        currentHost.showDialog(dialog)
        currentHost.focusDialog(dialog)
    }

    fun closeAll() {
        val dialogs = openDialogs.values.toList()
        openDialogs.clear()
        dialogs.forEach { binding ->
            runCatching { binding.dialog.dispose() }
            binding.close()
        }
    }

    fun openDialog(command: RewriteShellCommand): JDialog? {
        return openDialogs[command]?.dialog?.takeIf { it.isDisplayable }
    }
}
