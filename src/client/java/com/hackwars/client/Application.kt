package com.hackwars.client

import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.EmptyBorder

class ApplicationWindow : JFrame() {
    private val activePanel = LoginPanel()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        }
        rootPane.border = EmptyBorder(0, 0, 0, 0)
        contentPane = JPanel(BorderLayout()).apply {
            border = EmptyBorder(0, 0, 0, 0)
            isOpaque = true
            add(activePanel, BorderLayout.CENTER)
        }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                // TODO("Handle closing down any running panels")
            }
        })
        pack()
        minimumSize = activePanel.preferredSize
        setLocationRelativeTo(null)
        isVisible = true
    }
}

fun main() {
//    This breaks normal copy/paste functionality. Hopefully we can switch between native and cross platform when we switch to the app view
    runCatching { UIManager.setLookAndFeel("com.hackwars.gui.HackWarsLookAndFeel") }.onFailure { println("Warning: Unable to set system look and feel: ${it.message}") }
    SwingUtilities.invokeLater(object : Runnable {
        override fun run() {
            ApplicationWindow()
        }
    })
}
