package com.hackwars.client

import com.hackwars.gui.login.LoginScene
import com.hackwars.state.GameState
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.EmptyBorder

object ClientUiBootstrap {
    @JvmStatic
    fun installLookAndFeel() {
        runCatching { UIManager.setLookAndFeel("com.hackwars.gui.HackWarsLookAndFeel") }
            .onFailure { println("Warning: Unable to set Hack Wars look and feel: ${it.message}") }
    }
}

class ApplicationWindow(
    private val authGateway: ClientAuthGateway = PlayFabClientAuthGateway(),
) : JFrame() {
    private val activePanel = LoginScene(authGateway)
    private var gameState: GameState? = null

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
        activePanel.onPlayFabAuthenticated = { playFabId, sessionTicket ->
            connectToServers(playFabId, sessionTicket)
        }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                gameState?.clean()
            }
        })
        pack()
        minimumSize = activePanel.preferredSize
        setLocationRelativeTo(null)
        isVisible = true
    }

    private fun connectToServers(playFabId: String, sessionTicket: String) {
        gameState?.clean()
        val nextState = GameState()
        nextState.addEventListener(
            GameState.MessageEventListener::class.java,
            GameState.MessageEventListener { event ->
                activePanel.onServerAuthenticationFailure(stripHtml(event.message))
            }
        )
        nextState.addEventListener(
            GameState.FinishLoadingEventListener::class.java,
            GameState.FinishLoadingEventListener {
                isVisible = false
                dispose()
            }
        )
        nextState.addEventListener(
            GameState.ExitProgramEventListener::class.java,
            GameState.ExitProgramEventListener {
                nextState.clean()
                gameState = null
            }
        )
        gameState = nextState
        nextState.loginToServer(playFabId, sessionTicket)
    }

    private fun stripHtml(text: String): String {
        return text
            .replace("<br>", "\n", ignoreCase = true)
            .replace(Regex("<[^>]+>"), "")
            .trim()
    }

    fun currentLoginScene(): LoginScene = activePanel

    fun currentGameState(): GameState? = gameState
}

fun main() {
//    This breaks normal copy/paste functionality. Hopefully we can switch between native and cross platform when we switch to the app view
    ClientUiBootstrap.installLookAndFeel()
    SwingUtilities.invokeLater(object : Runnable {
        override fun run() {
            ApplicationWindow()
        }
    })
}
