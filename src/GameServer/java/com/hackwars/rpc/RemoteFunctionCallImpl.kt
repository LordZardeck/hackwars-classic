package com.hackwars.rpc

import assignments.RemoteFunctionCall
import game.ApplicationCommand
import game.ApplicationPayload

/**
 * Exception thrown when a parameter provided to a function does not match the expected type or is invalid.
 *
 * This exception is typically used in contexts where functions rely on positional parameters,
 * and a mismatch between the expected and actual parameter type or a missing parameter results in an error.
 * It provides detailed information about the function, parameter position, expected type,
 * and the actual type encountered, aiding in debugging and error resolution.
 *
 * @property function The name of the function where the exception occurred.
 * @property position The zero-based index of the parameter that caused the exception.
 * @property expectedType A string representation of the expected parameter type.
 * @property actualType A string representation of the actual parameter type provided, or null if the parameter was absent.
 */
class IllegalParameterException(val function: String, val position: Int, val expectedType: String, val actualType: String?) :
    Exception("Illegal parameter provided to $function function at position $position. Expected $expectedType, Actual ${actualType ?: "null"}")

abstract class RemoteFunctionCallImpl : ApplicationPayload {
    companion object {
        /**
         * Retrieves a positional parameter from the provided `RemoteFunctionCall` object, casts it to the specified type,
         * and returns it. If the parameter at the specified position is not present or is of an incompatible type,
         * an `IllegalParameterException` is thrown.
         *
         * If the expected type is not nullable, then the absence of the parameter or the parameter being null throws an error.
         * To allow a null type, specify the type as nullable (e.g., `String?`).
         *
         * @param T The expected type of the parameter to be retrieved.
         * @param rfc The `RemoteFunctionCall` instance containing the invocation details and parameters.
         * @param pos The zero-based index of the positional parameter to retrieve.
         * @return The parameter at the specified position cast to the expected type.
         * @throws IllegalParameterException If the parameter is missing or does not match the expected type.
         */
        inline fun <reified T> getPositionalParameter(rfc: RemoteFunctionCall, pos: Int): T {
            val parameter = (rfc.parameters as? Array<*>)?.getOrNull(pos)
            if (null is T) return parameter as T

            return parameter as? T ?: throw IllegalParameterException(
                rfc.function,
                pos,
                T::class.java.name,
                parameter?.javaClass?.name
            )
        }
    }

    abstract val function: String
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of(function)
    abstract fun toRfc(): RemoteFunctionCall
}
