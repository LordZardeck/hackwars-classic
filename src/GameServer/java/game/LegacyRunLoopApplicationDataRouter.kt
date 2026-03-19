package game

import game.computer.dispatch.CommandDispatcher

/**
 * Integrator-owned router that composes the extracted legacy command handlers
 * in a fixed order after the exact function dispatcher misses.
 */
object LegacyRunLoopApplicationDataRouter {
    private val defaultLegacyHandlers: List<LegacyApplicationDataHandler> = listOf(
        LegacyEconomyWebSocialCommands(),
        LegacyCombatNetworkQuestCommands()
    )

    @JvmStatic
    fun dispatch(
        computer: Computer,
        applicationData: ApplicationData,
        resolvedPort: Int,
        exactDispatcher: CommandDispatcher
    ): Boolean {
        return dispatch(
            computer,
            applicationData,
            resolvedPort,
            exactDispatcher,
            defaultLegacyHandlers
        )
    }

    @JvmStatic
    fun dispatch(
        computer: Computer,
        applicationData: ApplicationData,
        resolvedPort: Int,
        exactDispatcher: CommandDispatcher,
        legacyHandlers: List<LegacyApplicationDataHandler>
    ): Boolean {
        if (exactDispatcher.dispatch(applicationData)) {
            return true
        }
        legacyHandlers.forEach { handler ->
            if (handler.dispatch(computer, applicationData, resolvedPort)) {
                return true
            }
        }
        return false
    }
}
