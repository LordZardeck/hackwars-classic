package game.computer.dispatch

import com.hackwars.game.functions.Function
import game.ApplicationData
import java.util.LinkedHashMap

/**
 * Mutable command registry that can act as a dispatcher.
 *
 * The registry is intentionally small and generic so it can serve as the
 * bridge between the legacy `Function` map and the future sub-agent owned
 * handlers.
 */
class CommandRegistry private constructor(
    private val handlers: LinkedHashMap<String, CommandHandler>
) : CommandDispatcher {

    constructor() : this(LinkedHashMap())

    fun register(command: String, handler: CommandHandler): CommandRegistry {
        handlers[command] = handler
        return this
    }

    fun register(command: String, function: Function): CommandRegistry {
        return register(command, FunctionCommandHandler(function))
    }

    fun registerAll(functions: Map<String, Function>): CommandRegistry {
        functions.forEach { (command, function) ->
            register(command, function)
        }
        return this
    }

    fun contains(command: String): Boolean = handlers.containsKey(command)

    fun commands(): Set<String> = handlers.keys.toSet()

    override fun dispatch(applicationData: ApplicationData): Boolean {
        val handler = handlers[applicationData.command.wireName()] ?: return false
        handler.handle(applicationData)
        return true
    }

    companion object {
        fun fromFunctions(functions: Map<String, Function>): CommandRegistry {
            return CommandRegistry().registerAll(functions)
        }
    }
}
