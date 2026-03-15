package com.hackwars.client

import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants

class ApplicationWindow : JFrame("HackWars") {
    private val activePanel = LoginPanel()

    init {
        rootPane
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        isResizable = false
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                TODO("Handle closing down any running panels")
            }
        })
        setLocationRelativeTo(null)
        isVisible = true
        contentPane = activePanel
        pack()
    }
}

fun main() {
    runCatching { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()) }.onFailure { println("Warning: Unable to set system look and feel: ${it.message}") }
    SwingUtilities.invokeLater(object : Runnable {
        override fun run() {
            ApplicationWindow()
        }
    })
}