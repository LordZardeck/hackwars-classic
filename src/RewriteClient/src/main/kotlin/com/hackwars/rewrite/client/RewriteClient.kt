package com.hackwars.rewrite.client

import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private class RewriteClientFrame : JFrame("HackWars Rewrite Client") {
    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        contentPane.add(
            JLabel(
                "Rewrite client scaffold. Milestone 8 will replace this with the copied login panel and root controller.",
                SwingConstants.CENTER,
            )
        )
        setSize(960, 540)
        setLocationRelativeTo(null)
    }
}

fun main() {
    SwingUtilities.invokeLater {
        RewriteClientFrame().isVisible = true
    }
}
