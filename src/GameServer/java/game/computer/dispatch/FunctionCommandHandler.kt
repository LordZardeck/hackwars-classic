package game.computer.dispatch

import com.hackwars.game.functions.Function
import game.ApplicationData

/**
 * Adapts a legacy `Function` subclass into a command handler.
 */
class FunctionCommandHandler(
    private val function: Function
) : CommandHandler {
    override fun handle(applicationData: ApplicationData) {
        function.execute(applicationData)
    }
}
