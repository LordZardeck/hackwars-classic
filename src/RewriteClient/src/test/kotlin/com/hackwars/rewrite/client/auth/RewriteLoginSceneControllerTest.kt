package com.hackwars.rewrite.client.auth

import com.hackwars.rewrite.client.login.LoginScene
import com.hackwars.rewrite.clientmodel.RewriteClientBootstrapState
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewriteLoginSceneControllerTest {
    @Test
    fun sceneShowingRevealsLoginFormThroughControllerScheduler() {
        val scheduler = RecordingScheduler()
        val scene = LoginScene()
        val controller = RewriteLoginSceneController(
            view = scene,
            submitLogin = { _, _ -> },
            scheduler = scheduler,
        )
        try {
            invokeAndWait {
                controller.onBootstrapStateChanged(RewriteClientBootstrapState(route = RewriteClientRoute.LOGIN))
                controller.onSceneShowingChanged(isShowing = true)
            }

            assertFalse(invokeAndWaitResult { scene.loginForm.isVisible })
            assertEquals(1_000L, scheduler.singleDelay())

            invokeAndWait {
                scheduler.runNext()
            }

            assertTrue(invokeAndWaitResult { scene.loginForm.isVisible })
            assertEquals(" ", invokeAndWaitResult { scene.loginForm.displayedErrorText() })
        } finally {
            controller.close()
        }
    }

    @Test
    fun failedAuthenticationKeepsSpinnerVisibleUntilMinimumDurationExpires() {
        val scheduler = RecordingScheduler()
        val scene = LoginScene()
        val submittedEmails = mutableListOf<String>()
        val now = RecordingClock()
        val controller = RewriteLoginSceneController(
            view = scene,
            submitLogin = { email, password ->
                submittedEmails += email
                password.fill('\u0000')
            },
            nowNanos = now::nowNanos,
            scheduler = scheduler,
        )
        try {
            invokeAndWait {
                controller.onBootstrapStateChanged(RewriteClientBootstrapState(route = RewriteClientRoute.LOGIN))
                controller.onSceneShowingChanged(isShowing = true)
                scheduler.runNext()
                scene.loginForm.emailField.text = "localuser"
                scene.loginForm.passwordField.text = "password1234"
                now.currentNanos = 0L
                scene.loginForm.loginButton.doClick()
            }

            assertEquals(listOf("localuser"), submittedEmails)
            assertFalse(invokeAndWaitResult { scene.loginForm.isVisible })

            invokeAndWait {
                controller.onBootstrapStateChanged(
                    RewriteClientBootstrapState(
                        route = RewriteClientRoute.LOGIN,
                        loginError = "bad ticket",
                    ),
                )
            }

            assertFalse(invokeAndWaitResult { scene.loginForm.isVisible })
            assertEquals(2_000L, scheduler.singleDelay())

            now.currentNanos = 2_000_000_000L
            invokeAndWait {
                scheduler.runNext()
            }

            assertTrue(invokeAndWaitResult { scene.loginForm.isVisible })
            assertEquals("bad ticket", invokeAndWaitResult { scene.loginForm.displayedErrorText() })
        } finally {
            controller.close()
        }
    }

    private class RecordingClock(var currentNanos: Long = 0L) {
        fun nowNanos(): Long = currentNanos
    }

    private class RecordingScheduler : RewriteLoginActionScheduler {
        private val scheduled = ArrayDeque<Pair<Long, () -> Unit>>()

        override fun schedule(delayMillis: Long, action: () -> Unit): AutoCloseable {
            val entry = delayMillis to action
            scheduled += entry
            return AutoCloseable { scheduled.remove(entry) }
        }

        fun singleDelay(): Long {
            assertEquals(1, scheduled.size)
            return scheduled.single().first
        }

        fun runNext() {
            val action = scheduled.removeFirst().second
            action()
        }
    }

    private inline fun invokeAndWait(crossinline block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
            return
        }
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            runCatching { block() }.exceptionOrNull()?.also { failure = it }
        }
        failure?.let { throw it }
    }

    private fun <T> invokeAndWaitResult(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return block()
        }
        var result: T? = null
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            runCatching { block() }
                .onSuccess { result = it }
                .onFailure { failure = it }
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
