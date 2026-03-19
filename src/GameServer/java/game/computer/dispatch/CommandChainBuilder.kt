package game.computer.dispatch

import com.hackwars.game.functions.Function
import game.ApplicationData

/**
 * Builder for an ordered legacy command chain.
 *
 * The builder is intentionally lightweight so the integrator can define routes
 * in the same order as the old `if/else if` chain without wiring them directly
 * into `Computer.java`.
 */
class CommandChainBuilder {
    private val routes = mutableListOf<CommandRoute>()

    fun onCommand(command: String, handler: CommandHandler): CommandChainBuilder {
        routes += CommandRoute(command, { applicationData -> applicationData.function == command }, handler)
        return this
    }

    fun onFunction(command: String, function: Function): CommandChainBuilder {
        return onCommand(command, FunctionCommandHandler(function))
    }

    fun onPredicate(
        name: String,
        predicate: (ApplicationData) -> Boolean,
        handler: CommandHandler
    ): CommandChainBuilder {
        routes += CommandRoute(name, predicate, handler)
        return this
    }

    fun fallback(handler: CommandHandler): CommandChainBuilder {
        return onPredicate("fallback", { true }, handler)
    }

    fun build(): CommandChainDispatcher {
        return CommandChainDispatcher(routes)
    }
}
