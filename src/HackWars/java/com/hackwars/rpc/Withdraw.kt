package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class Withdraw(val amount: Float, val ip: String, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): Withdraw {
            return Withdraw(
                getPositionalParameter<Float>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.WITHDRAW
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(amount, ip, port))
}
