package com.hackwars.rewrite.client.shell

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
    private val dialogFactory: (RewriteShellCommand, Window?) -> JDialog,
) {
    private val openDialogs = linkedMapOf<RewriteShellCommand, JDialog>()
    private var host: RewriteShellDialogHost? = null

    fun attachHost(host: RewriteShellDialogHost?) {
        this.host = host
    }

    fun open(command: RewriteShellCommand) {
        val currentHost = host ?: return
        val existing = openDialogs[command]
        if (existing != null && existing.isDisplayable) {
            currentHost.focusDialog(existing)
            return
        }

        val dialog = dialogFactory(command, currentHost.ownerWindow)
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) {
                openDialogs.remove(command, dialog)
            }
        })
        openDialogs[command] = dialog
        currentHost.showDialog(dialog)
        currentHost.focusDialog(dialog)
    }

    fun closeAll() {
        val dialogs = openDialogs.values.toList()
        openDialogs.clear()
        dialogs.forEach { dialog ->
            runCatching { dialog.dispose() }
        }
    }

    fun openDialog(command: RewriteShellCommand): JDialog? {
        return openDialogs[command]?.takeIf { it.isDisplayable }
    }
}
