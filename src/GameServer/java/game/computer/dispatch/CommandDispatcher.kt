package game.computer.dispatch

import game.ApplicationData

/**
 * Routes an application command to the appropriate handler.
 *
 * This interface stays intentionally small so the integrator can wire it into
 * `Computer` without forcing a larger refactor boundary.
 */
interface CommandDispatcher {
    fun dispatch(applicationData: ApplicationData): Boolean
}
