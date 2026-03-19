package game.computer.dispatch

import game.ApplicationData

/**
 * Handles a single application command.
 */
fun interface CommandHandler {
    fun handle(applicationData: ApplicationData)
}
