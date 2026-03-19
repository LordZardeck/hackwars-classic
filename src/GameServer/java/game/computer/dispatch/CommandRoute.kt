package game.computer.dispatch

import game.ApplicationData

/**
 * A single ordered route in a command chain.
 *
 * Routes are evaluated in insertion order, which makes this useful for
 * expressing legacy fallback logic where the first matching branch wins.
 */
data class CommandRoute(
    val name: String,
    val matches: (ApplicationData) -> Boolean,
    val handler: CommandHandler
)
