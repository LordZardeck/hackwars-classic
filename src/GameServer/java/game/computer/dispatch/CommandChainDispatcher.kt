package game.computer.dispatch

import game.ApplicationData

/**
 * Dispatches through an ordered chain of routes.
 */
class CommandChainDispatcher(
    private val routes: List<CommandRoute>
) : CommandDispatcher {
    override fun dispatch(applicationData: ApplicationData): Boolean {
        val route = routes.firstOrNull { it.matches(applicationData) } ?: return false
        route.handler.handle(applicationData)
        return true
    }

    fun routeNames(): List<String> = routes.map { it.name }
}
