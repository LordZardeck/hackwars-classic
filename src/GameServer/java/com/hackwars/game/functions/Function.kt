package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
/**
 * Represents an abstract function that operates on a computer system.
 * Subclasses of this class define specific operations or behaviors
 * that can be executed for a given application context.
 *
 * @param computer The `Computer` instance on which the function operates.
 */
abstract class Function(val computer: Computer) {
    /**
     * Executes a specific operation or behavior based on the provided application data.
     * Subclasses provide concrete implementations to define the behavior.
     *
     * @param applicationData The data related to the application context which may
     *        include parameters or additional information needed for execution.
     */
    abstract fun execute(applicationData: ApplicationData)
}
