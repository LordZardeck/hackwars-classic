package com.hackwars.rpc

import assignments.RemoteFunctionCall
import game.ApplicationCommand
import game.ApplicationPayload

class IllegalParameterException(
    val function: String,
    val position: Int,
    val expectedType: String,
    val actualType: String?
) :
    Exception("Illegal parameter provided to $function function at position $position. Expected $expectedType, Actual ${actualType ?: "null"}")

abstract class RemoteFunctionCallImpl : ApplicationPayload {
    companion object {
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

    abstract val spec: GameFunctionSpec

    val function: String
        get() = spec.wireName

    final override fun getCommand(): ApplicationCommand = spec.command

    abstract fun toRfc(requestId: Int = 0): RemoteFunctionCall
}
