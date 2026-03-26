package com.hackwars.rewrite.client.desktop

import com.hackwars.rewrite.client.auth.RewriteLoginActionScheduler
import com.hackwars.rewrite.client.auth.RewriteLoginSceneController
import com.hackwars.rewrite.client.login.LoginScene
import com.hackwars.rewrite.clientmodel.RewriteClientBootstrapState
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import javax.swing.SwingUtilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteDesktopEntryControllerTest {
    @Test
    fun bootstrapStateFlowDrivesMenuVisibilityRouteChromeAndLoginSceneRendering() {
        val bootstrapStates = MutableStateFlow(RewriteClientBootstrapState())
        val loginScene = LoginScene()
        val loginController = RewriteLoginSceneController(
            view = loginScene,
            submitLogin = { _, _ -> },
            scheduler = RewriteLoginActionScheduler { _, action ->
                action()
                AutoCloseable {}
            },
        )
        val hostStates = mutableListOf<RewriteDesktopEntryViewModel>()
        val routeUpdates = mutableListOf<RewriteClientRoute>()
        val controller = RewriteDesktopEntryController(
            bootstrapStateFlow = bootstrapStates,
            initialBootstrapState = bootstrapStates.value,
            host = { state -> hostStates += state },
            loginSceneController = loginController,
            onRouteChanged = { routeUpdates += it },
            dispatcher = UnconfinedTestDispatcher(),
        )
        try {
            flushEdt()
            assertEquals(RewriteClientRoute.LOGIN, hostStates.last().route)
            assertFalse(hostStates.last().menuBarVisible)

            bootstrapStates.value = RewriteClientBootstrapState(route = RewriteClientRoute.BOOTSTRAPPING_GAME)
            flushEdt()
            assertEquals(RewriteClientRoute.BOOTSTRAPPING_GAME, hostStates.last().route)
            assertFalse(hostStates.last().menuBarVisible)
            assertFalse(invokeAndWaitResult { loginScene.loginForm.isVisible })

            bootstrapStates.value = RewriteClientBootstrapState(
                route = RewriteClientRoute.LOGIN,
                loginError = "bad ticket",
            )
            flushEdt()
            assertEquals("bad ticket", invokeAndWaitResult { loginScene.loginForm.displayedErrorText() })
            assertTrue(invokeAndWaitResult { loginScene.loginForm.isVisible })

            bootstrapStates.value = RewriteClientBootstrapState(route = RewriteClientRoute.DESKTOP)
            flushEdt()
            assertEquals(RewriteClientRoute.DESKTOP, hostStates.last().route)
            assertTrue(hostStates.last().menuBarVisible)
            assertEquals(
                listOf(
                    RewriteClientRoute.LOGIN,
                    RewriteClientRoute.BOOTSTRAPPING_GAME,
                    RewriteClientRoute.LOGIN,
                    RewriteClientRoute.DESKTOP,
                ),
                routeUpdates,
            )
        } finally {
            controller.close()
        }
    }

    private fun flushEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            return
        }
        SwingUtilities.invokeAndWait {}
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
