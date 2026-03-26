package com.hackwars.rewrite.client.shell

import com.hackwars.rewrite.client.mvc.RewriteControllerBase
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import java.awt.event.ActionListener
import javax.swing.SwingUtilities
import javax.swing.Timer

class RewriteShellChromeController(
    private val shellHost: RewriteDesktopShellView,
    private val launchShellCommand: (RewriteShellCommand) -> Unit,
    private val attachShellWindowHost: (RewriteShellWindowHost?) -> Unit,
    private val updateFrameTitle: (String) -> Unit,
) : RewriteControllerBase() {
    private val presenter = RewriteShellChromePresenter()
    private val countdownTimer = Timer(1_000) {
        presenter.tick()
        syncCountdownTimer()
        renderChrome()
    }.apply { isRepeats = true }

    init {
        bindMenuCommands()
        bindTaskBar()
        attachShellWindowHost(shellHost)
        syncCountdownTimer()
        renderChrome()
        onClose {
            countdownTimer.stop()
            attachShellWindowHost(null)
        }
    }

    fun updateRoute(route: RewriteClientRoute) {
        presenter.updateRoute(route)
        syncCountdownTimer()
        renderChrome()
    }

    fun updateShellState(shellState: ClientGameSnapshot?) {
        presenter.updateShellState(shellState)
        syncCountdownTimer()
        renderChrome()
    }

    fun updateAcceptedPlayerIp(playerIp: String?) {
        presenter.updateAcceptedPlayerIp(playerIp)
        renderChrome()
    }

    private fun bindMenuCommands() {
        shellHost.menuBar.commandItemsByCommand().forEach { (command, item) ->
            val listener = ActionListener { launchShellCommand(command) }
            item.addActionListener(listener)
            onClose { item.removeActionListener(listener) }
        }
    }

    private fun bindTaskBar() {
        val leftListener = ActionListener { shellHost.menuBar.taskBar.scrollLeft() }
        val rightListener = ActionListener { shellHost.menuBar.taskBar.scrollRight() }
        shellHost.menuBar.taskBar.leftScrollButton.addActionListener(leftListener)
        shellHost.menuBar.taskBar.rightScrollButton.addActionListener(rightListener)
        onClose {
            shellHost.menuBar.taskBar.leftScrollButton.removeActionListener(leftListener)
            shellHost.menuBar.taskBar.rightScrollButton.removeActionListener(rightListener)
        }
    }

    private fun renderChrome() {
        val state = presenter.currentState()
        if (SwingUtilities.isEventDispatchThread()) {
            shellHost.renderChrome(state)
            updateFrameTitle(state.frameTitle)
            return
        }
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            runCatching {
                shellHost.renderChrome(state)
                updateFrameTitle(state.frameTitle)
            }.exceptionOrNull()?.also { failure = it }
        }
        failure?.let { throw it }
    }

    private fun syncCountdownTimer() {
        val countdownState = presenter.currentState().countdown
        val shouldRun = countdownState.text.isNotBlank() && !countdownState.disconnected
        if (shouldRun && !countdownTimer.isRunning) {
            countdownTimer.start()
        } else if (!shouldRun && countdownTimer.isRunning) {
            countdownTimer.stop()
        }
    }
}
