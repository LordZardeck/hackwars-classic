package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class Withdraw(val amount: Float, val ip: String, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "withdraw"
        fun fromRpc(rfc: RemoteFunctionCall): Withdraw {
            return Withdraw(
                getPositionalParameter<Float>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(amount, ip, port))
}
