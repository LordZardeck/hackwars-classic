package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

class IllegalParameterException(val position: Int, val expectedType: String, val actualType: String?) :
    Exception("Illegal parameter provided at position $position. Expected $expectedType, Actual ${actualType ?: "null"}")

/**
 * Represents an abstract function that operates on a computer system.
 * Subclasses of this class define specific operations or behaviors
 * that can be executed for a given application context.
 *
 * @param computer The `Computer` instance on which the function operates.
 */
abstract class Function(val computer: Computer) {
    companion object {
        inline fun <reified T> getPositionalParameter(applicationData: ApplicationData, pos: Int): T {
            val parameter = (applicationData.parameters as? Array<*>)?.getOrNull(pos)
            if (null is T) return parameter as T

            return parameter as? T ?: throw IllegalParameterException(
                pos,
                T::class.java.name,
                parameter?.javaClass?.name
            )
        }
    }

    /**
     * Executes a specific operation or behavior based on the provided application data.
     * Subclasses provide concrete implementations to define the behavior.
     *
     * @param applicationData The data related to the application context which may
     *        include parameters or additional information needed for execution.
     */
    abstract fun execute(applicationData: ApplicationData)
}
