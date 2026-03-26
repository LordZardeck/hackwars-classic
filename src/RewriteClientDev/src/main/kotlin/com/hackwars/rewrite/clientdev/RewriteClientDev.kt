package com.hackwars.rewrite.clientdev

import com.hackwars.rewrite.client.RewriteRootFrame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities

fun main() {
    val environment = RewriteClientDevEnvironment()
    SwingUtilities.invokeLater {
        val frame = RewriteRootFrame(
            controller = environment.createController(),
        )
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) {
                environment.close()
            }
        })
        frame.isVisible = true
    }
}
