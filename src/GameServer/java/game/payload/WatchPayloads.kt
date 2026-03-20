package game.payload

import game.ApplicationCommand
import game.ApplicationPayload

data class WatchXpPayload(
    val amount: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("watchxp")
    fun legacyParameters(): Any = amount
}
