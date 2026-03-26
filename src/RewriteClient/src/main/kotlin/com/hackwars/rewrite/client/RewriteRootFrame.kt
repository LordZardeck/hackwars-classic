package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.login.LoginScene
import com.hackwars.rewrite.client.login.LoginUiDefaults
import com.hackwars.rewrite.client.shell.DEFAULT_FRAME_TITLE
import com.hackwars.rewrite.client.shell.RewriteDesktopShellView
import com.hackwars.rewrite.client.shell.RewriteShellDialogHost
import com.hackwars.rewrite.client.shell.RewriteShellChromePresenter
import com.hackwars.rewrite.client.ui.RewriteUiBootstrap
import com.hackwars.rewrite.clientmodel.RewriteClientBootstrapState
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RewriteRootFrame(
    uiBootstrap: Unit = RewriteUiBootstrap.installHackWarsLookAndFeel(),
    val controller: RewriteRootController = RewriteRootController(),
) : JFrame(DEFAULT_FRAME_TITLE) {
    companion object {
        private const val LOGIN_CARD = "login"
        private const val DESKTOP_CARD = "desktop"
    }

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cardLayout = CardLayout()
    private val shellChromePresenter = RewriteShellChromePresenter()
    private val countdownTimer = Timer(1_000) {
        shellChromePresenter.tick()
        renderShellChrome()
    }.apply {
        isRepeats = true
        start()
    }
    val loginScene: LoginScene = LoginScene().apply {
        name = "rewrite-root-login"
        onSubmitCredentials = { email, password ->
            controller.submitLogin(email, password)
        }
    }
    val shellHost = RewriteDesktopShellView(
        onCommandSelected = { command ->
            controller.launchShellCommand(command)
        },
    )
    val desktopPane = shellHost.desktopPane
    private val desktopCard = JPanel(BorderLayout()).apply {
        name = "rewrite-root-desktop-card"
        add(shellHost, BorderLayout.CENTER)
    }
    private val cardPanel = JPanel(cardLayout).apply {
        name = "rewrite-root-cards"
        add(loginScene, LOGIN_CARD)
        add(desktopCard, DESKTOP_CARD)
    }

    init {
        check(uiBootstrap == Unit)
        LoginUiDefaults.install()
        controller.attachShellHost(shellHost)
        controller.attachDialogHost(object : RewriteShellDialogHost {
            override val ownerWindow = this@RewriteRootFrame

            override fun showDialog(dialog: javax.swing.JDialog) {
                dialog.setLocationRelativeTo(this@RewriteRootFrame)
                dialog.isVisible = true
            }

            override fun focusDialog(dialog: javax.swing.JDialog) {
                dialog.toFront()
                dialog.requestFocus()
            }
        })
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane.layout = BorderLayout()
        contentPane.add(
            JPanel(BorderLayout()).apply {
                name = "rewrite-root-container"
                add(cardPanel, BorderLayout.CENTER)
            },
            BorderLayout.CENTER,
        )
        setSize(1280, 800)
        setLocationRelativeTo(null)
        renderBootstrapState(controller.bootstrapState())
        bindController()
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) {
                countdownTimer.stop()
                controller.shutdown()
                uiScope.cancel()
            }

            override fun windowClosed(event: WindowEvent) {
                countdownTimer.stop()
                controller.shutdown()
                uiScope.cancel()
            }
        })
    }

    private fun bindController() {
        uiScope.launch {
            controller.bootstrapStateSelector().collect { state ->
                SwingUtilities.invokeLater {
                    renderBootstrapState(state)
                }
            }
        }
        uiScope.launch {
            controller.gameShellStateSelector().collect { shellState ->
                SwingUtilities.invokeLater {
                    shellChromePresenter.updateShellState(shellState)
                    renderShellChrome()
                }
            }
        }
        uiScope.launch {
            controller.gameAcceptedPlayerIpSelector().collect { playerIp ->
                SwingUtilities.invokeLater {
                    shellChromePresenter.updateAcceptedPlayerIp(playerIp)
                    renderShellChrome()
                }
            }
        }
    }

    private fun renderBootstrapState(state: RewriteClientBootstrapState) {
        shellChromePresenter.updateRoute(state.route)
        when (state.route) {
            RewriteClientRoute.LOGIN -> {
                if (jMenuBar != null) {
                    jMenuBar = null
                }
                cardLayout.show(cardPanel, LOGIN_CARD)
                val loginError = state.loginError
                if (loginError != null) {
                    loginScene.onServerAuthenticationFailure(loginError)
                } else {
                    loginScene.clearError()
                }
            }

            RewriteClientRoute.BOOTSTRAPPING_GAME -> {
                if (jMenuBar != null) {
                    jMenuBar = null
                }
                cardLayout.show(cardPanel, LOGIN_CARD)
                loginScene.showBootstrapStarted()
            }

            RewriteClientRoute.DESKTOP -> {
                loginScene.clearError()
                if (jMenuBar !== shellHost.menuBar) {
                    jMenuBar = shellHost.menuBar
                }
                cardLayout.show(cardPanel, DESKTOP_CARD)
            }
        }
        renderShellChrome()
        rootPane.revalidate()
        rootPane.repaint()
    }

    private fun renderShellChrome() {
        val chromeState = shellChromePresenter.currentState()
        shellHost.renderChrome(chromeState)
        title = chromeState.frameTitle
    }
}
