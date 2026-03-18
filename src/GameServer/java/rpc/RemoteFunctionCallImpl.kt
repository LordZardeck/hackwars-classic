package rpc

import assignments.RemoteFunctionCall

class IllegalParameterException(val function: String, val position: Int, val expectedType: String) :
    Exception("Illegal parameter provided to $function function at position $position. Expected $expectedType")

abstract class RemoteFunctionCallImpl {
    companion object {
        inline fun <reified T> getPositionalParameter(rfc: RemoteFunctionCall, pos: Int): T {
            return (rfc.parameters as? Array<*>)?.getOrNull(pos) as? T ?: throw IllegalParameterException(
                rfc.function,
                pos,
                T::class.java.name
            )
        }
    }

    abstract val function: String
    abstract fun toRfc(): RemoteFunctionCall
}