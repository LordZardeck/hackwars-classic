package game.computer.dispatch

import game.ApplicationData

/**
 * Tries a primary dispatcher first, then falls back to another dispatcher.
 *
 * This is the missing piece for representing the legacy "exact command map,
 * then fall through to branch chain" behavior without keeping it inline in
 * `Computer.run()`.
 */
class FallbackCommandDispatcher(
    private val primary: CommandDispatcher,
    private val fallback: CommandDispatcher
) : CommandDispatcher {
    override fun dispatch(applicationData: ApplicationData): Boolean {
        if (primary.dispatch(applicationData)) {
            return true
        }
        return fallback.dispatch(applicationData)
    }
}
