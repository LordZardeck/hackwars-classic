package com.hackwars.rewrite.client.auth

import com.hackwars.rewrite.client.login.LoginScene
import com.hackwars.rewrite.client.login.LoginUiDefaults
import com.hackwars.rewrite.client.login.RewriteClientClock
import com.hackwars.rewrite.client.mvc.RewriteControllerBase
import com.hackwars.rewrite.clientmodel.RewriteClientBootstrapState
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import java.awt.Desktop
import java.awt.event.ActionListener
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.SwingUtilities

private const val LOGIN_FORM_REVEAL_DELAY_MS = 1_000
private const val LOGIN_MIN_AUTH_DURATION_MS = 2_000L
private const val SIGNUP_URL = "https://www.reddit.com/r/HackWars/"

fun interface RewriteLoginActionScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): AutoCloseable
}

class RewriteLoginSceneController(
    private val view: LoginScene,
    private val submitLogin: (String, CharArray) -> Unit,
    private val openSignupUrl: () -> Unit = ::openSignupUrlInBrowser,
    private val nowNanos: () -> Long = RewriteClientClock::nowNanos,
    private val scheduler: RewriteLoginActionScheduler = RewriteLoginActionScheduler { delayMillis, action ->
        val timer = javax.swing.Timer(delayMillis.toInt()) { action() }
        timer.isRepeats = false
        timer.start()
        AutoCloseable { timer.stop() }
    },
) : RewriteControllerBase() {
    private var bootstrapState = RewriteClientBootstrapState()
    private var model = RewriteLoginViewModel()
    private var authStartedAtNanos = nowNanos()
    private var introRevealScheduled = false
    private var introRevealComplete = false
    private var introRevealHandle: AutoCloseable? = null
    private var delayedFailureHandle: AutoCloseable? = null

    init {
        LoginUiDefaults.install()
        bindListeners()
        render(model)
    }

    fun onBootstrapStateChanged(state: RewriteClientBootstrapState) {
        bootstrapState = state
        when (state.route) {
            RewriteClientRoute.LOGIN -> showLoginScreen(state.loginError)
            RewriteClientRoute.BOOTSTRAPPING_GAME -> showBootstrapStarted()
            RewriteClientRoute.DESKTOP -> showDesktopEntered()
        }
    }

    internal fun onSceneShowingChanged(isShowing: Boolean) {
        if (!isShowing || introRevealComplete || introRevealScheduled) {
            return
        }
        introRevealScheduled = true
        introRevealHandle?.close()
        introRevealHandle = scheduler.schedule(LOGIN_FORM_REVEAL_DELAY_MS.toLong()) {
            runOnEdt {
                introRevealScheduled = false
                introRevealComplete = true
                introRevealHandle = null
                if (bootstrapState.route == RewriteClientRoute.LOGIN && !model.isAuthenticating) {
                    render(
                        RewriteLoginViewModel(
                            showForm = true,
                            isAuthenticating = false,
                            errorMessage = bootstrapState.loginError,
                        ),
                    )
                }
            }
        }
    }

    private fun bindListeners() {
        val loginListener = ActionListener {
            val credentials = view.loginForm.snapshotCredentials()
            authStartedAtNanos = nowNanos()
            clearDelayedFailure()
            render(
                RewriteLoginViewModel(
                    showForm = false,
                    isAuthenticating = true,
                    errorMessage = null,
                ),
            )
            submitLogin(credentials.email, credentials.password)
        }
        view.loginForm.loginButton.addActionListener(loginListener)
        onClose { view.loginForm.loginButton.removeActionListener(loginListener) }

        val signupMouseListener = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent?) {
                openSignupUrl()
            }

            override fun mouseEntered(event: MouseEvent?) {
                view.loginForm.setSignupLinkHovered(true)
            }

            override fun mouseExited(event: MouseEvent?) {
                view.loginForm.setSignupLinkHovered(false)
            }
        }
        view.loginForm.signupLinkLabel.addMouseListener(signupMouseListener)
        onClose { view.loginForm.signupLinkLabel.removeMouseListener(signupMouseListener) }

        val hierarchyListener = HierarchyListener { event ->
            if (event != null && (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L) {
                onSceneShowingChanged(view.isShowing)
            }
        }
        view.addHierarchyListener(hierarchyListener)
        onClose { view.removeHierarchyListener(hierarchyListener) }
    }

    private fun showLoginScreen(error: String?) {
        if (error != null && model.isAuthenticating) {
            val elapsedMillis = (nowNanos() - authStartedAtNanos) / 1_000_000L
            val remainingMillis = (LOGIN_MIN_AUTH_DURATION_MS - elapsedMillis).coerceAtLeast(0L)
            if (remainingMillis > 0L) {
                clearDelayedFailure()
                delayedFailureHandle = scheduler.schedule(remainingMillis) {
                    runOnEdt {
                        delayedFailureHandle = null
                        render(
                            RewriteLoginViewModel(
                                showForm = true,
                                isAuthenticating = false,
                                errorMessage = error,
                            ),
                        )
                    }
                }
                return
            }
        }
        clearDelayedFailure()
        render(
            RewriteLoginViewModel(
                showForm = introRevealComplete || error != null,
                isAuthenticating = false,
                errorMessage = error,
            ),
        )
    }

    private fun showBootstrapStarted() {
        clearDelayedFailure()
        render(
            RewriteLoginViewModel(
                showForm = false,
                isAuthenticating = true,
                errorMessage = null,
            ),
        )
    }

    private fun showDesktopEntered() {
        clearDelayedFailure()
        render(
            RewriteLoginViewModel(
                showForm = introRevealComplete,
                isAuthenticating = false,
                errorMessage = null,
            ),
        )
    }

    private fun clearDelayedFailure() {
        delayedFailureHandle?.close()
        delayedFailureHandle = null
    }

    private fun render(nextModel: RewriteLoginViewModel) {
        model = nextModel
        view.render(nextModel)
    }

    override fun onClosed() {
        clearDelayedFailure()
        introRevealHandle?.close()
        introRevealHandle = null
        introRevealScheduled = false
    }

    companion object {
        private fun openSignupUrlInBrowser() {
            if (!Desktop.isDesktopSupported()) {
                return
            }
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return
            }
            runCatching {
                desktop.browse(URI(SIGNUP_URL))
            }
        }

        private fun runOnEdt(action: () -> Unit) {
            if (SwingUtilities.isEventDispatchThread()) {
                action()
            } else {
                SwingUtilities.invokeLater(action)
            }
        }
    }
}
