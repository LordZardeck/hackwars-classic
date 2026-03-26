package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.auth.RewriteLoginSceneController
import com.hackwars.rewrite.client.desktop.RewriteDesktopEntryController
import com.hackwars.rewrite.client.desktop.RewriteDesktopEntryViewModel
import com.hackwars.rewrite.client.login.LoginScene
import com.hackwars.rewrite.client.shell.DEFAULT_FRAME_TITLE
import com.hackwars.rewrite.client.shell.RewriteShellChromeBindingController
import com.hackwars.rewrite.client.shell.RewriteShellChromeController
import com.hackwars.rewrite.client.shell.RewriteDesktopShellView
import com.hackwars.rewrite.client.shell.RewriteShellDialogHost
import com.hackwars.rewrite.client.ui.RewriteUiBootstrap
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel

class RewriteRootFrame(
    uiBootstrap: Unit = RewriteUiBootstrap.installHackWarsLookAndFeel(),
    val controller: RewriteRootController = RewriteRootController(),
) : JFrame(DEFAULT_FRAME_TITLE) {
    companion object {
        private const val LOGIN_CARD = "login"
        private const val DESKTOP_CARD = "desktop"
    }

    private val cardLayout = CardLayout()
    val loginScene: LoginScene = LoginScene().apply { name = "rewrite-root-login" }
    val shellHost = RewriteDesktopShellView()
    private val shellChromeController = RewriteShellChromeController(
        shellHost = shellHost,
        launchShellCommand = controller::launchShellCommand,
        attachShellWindowHost = controller::attachShellHost,
        updateFrameTitle = { title = it },
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
    private val loginSceneController = RewriteLoginSceneController(
        view = loginScene,
        submitLogin = controller::submitLogin,
    )
    private val desktopEntryController = RewriteDesktopEntryController(
        bootstrapStateFlow = controller.bootstrapStateSelector(),
        initialBootstrapState = controller.bootstrapState(),
        host = { state -> renderDesktopEntry(state) },
        loginSceneController = loginSceneController,
        onRouteChanged = shellChromeController::updateRoute,
    )
    private val shellChromeBindingController = RewriteShellChromeBindingController(
        shellStateFlow = controller.gameShellStateSelector(),
        initialShellState = controller.gameShellState(),
        acceptedPlayerIpFlow = controller.gameAcceptedPlayerIpSelector(),
        initialAcceptedPlayerIp = controller.snapshot().game.latestAcceptedSession?.playerIp,
        shellChromeController = shellChromeController,
    )

    init {
        check(uiBootstrap == Unit)
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
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) {
                shellChromeBindingController.close()
                desktopEntryController.close()
                shellChromeController.close()
                controller.shutdown()
            }

            override fun windowClosed(event: WindowEvent) {
                shellChromeBindingController.close()
                desktopEntryController.close()
                shellChromeController.close()
                controller.shutdown()
            }
        })
    }

    private fun renderDesktopEntry(state: RewriteDesktopEntryViewModel) {
        when (state.route) {
            RewriteClientRoute.LOGIN -> {
                if (jMenuBar != null) {
                    jMenuBar = null
                }
                cardLayout.show(cardPanel, LOGIN_CARD)
            }

            RewriteClientRoute.BOOTSTRAPPING_GAME -> {
                if (jMenuBar != null) {
                    jMenuBar = null
                }
                cardLayout.show(cardPanel, LOGIN_CARD)
            }

            RewriteClientRoute.DESKTOP -> {
                if (jMenuBar !== shellHost.menuBar) {
                    jMenuBar = shellHost.menuBar
                }
                cardLayout.show(cardPanel, DESKTOP_CARD)
            }
        }
        rootPane.revalidate()
        rootPane.repaint()
    }
}
