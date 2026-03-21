package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class Deposit(val amount: Float, val ip: String, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): Deposit {
            return Deposit(
                getPositionalParameter<Float>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.DEPOSIT
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(amount, ip, port))
}
