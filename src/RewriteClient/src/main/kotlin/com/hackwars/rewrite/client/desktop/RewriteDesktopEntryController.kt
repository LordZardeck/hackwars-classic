package com.hackwars.rewrite.client.desktop

import com.hackwars.rewrite.client.auth.RewriteLoginSceneController
import com.hackwars.rewrite.client.mvc.RewriteControllerBase
import com.hackwars.rewrite.client.mvc.RewriteView
import com.hackwars.rewrite.client.mvc.RewriteViewModel
import com.hackwars.rewrite.clientmodel.RewriteClientBootstrapState
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class RewriteDesktopEntryViewModel(
    val route: RewriteClientRoute = RewriteClientRoute.LOGIN,
    val menuBarVisible: Boolean = false,
) : RewriteViewModel

class RewriteDesktopEntryController(
    bootstrapStateFlow: Flow<RewriteClientBootstrapState>,
    initialBootstrapState: RewriteClientBootstrapState,
    private val host: RewriteView<RewriteDesktopEntryViewModel>,
    private val loginSceneController: RewriteLoginSceneController,
    private val onRouteChanged: (RewriteClientRoute) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RewriteControllerBase() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        manage(loginSceneController)
        onClose { scope.cancel() }
        renderBootstrapState(initialBootstrapState)
        scope.launch {
            bootstrapStateFlow.drop(1).collect { state ->
                runOnEdt {
                    renderBootstrapState(state)
                }
            }
        }
    }

    private fun renderBootstrapState(state: RewriteClientBootstrapState) {
        onRouteChanged(state.route)
        loginSceneController.onBootstrapStateChanged(state)
        host.render(
            RewriteDesktopEntryViewModel(
                route = state.route,
                menuBarVisible = state.route == RewriteClientRoute.DESKTOP,
            ),
        )
    }

    private fun runOnEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            action()
        } else {
            SwingUtilities.invokeLater(action)
        }
    }
}
