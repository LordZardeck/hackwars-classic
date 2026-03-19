package com.hackwars.game.functions

import game.ApplicationData
import game.Computer

/**
 * Represents a function that requests an attack against a target's default service port.
 *
 * Based on the target type parameter, this function:
 *
 * - Builds a request payload for either default bank or default attack.
 * - Wraps that payload in a `requestattack` application message.
 * - Dispatches the request back through the computer handler to the source IP.
 *
 * @constructor Initializes `RequestAttackDefault` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class RequestAttackDefault(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val parameters = applicationData.parameters as? Array<*> ?: return
        val target = parameters.getOrNull(1) as? String ?: return

        val attackIndices = arrayOf(0)
        val attackMetadata = arrayOfNulls<Array<String?>>(3)
        val payload: Any? = when (target) {
            "Bank" -> arrayOf(computer.getIP(), computer.getDefaultBank(), attackIndices, attackMetadata, null, 0)
            "Attack" -> arrayOf(computer.getIP(), computer.getDefaultAttack(), attackIndices, attackMetadata, null, 0)
            else -> null
        }

        val request = ApplicationData("requestattack", payload, applicationData.port, applicationData.sourceIP)
        computer.computerHandler.addData(request, applicationData.sourceIP)
    }
}
