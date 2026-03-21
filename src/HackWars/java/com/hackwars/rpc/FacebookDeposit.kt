package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class FacebookDeposit(val ip: String?, val amount: Float?, val defaultPort: Int) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): FacebookDeposit {
            return FacebookDeposit(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<Float?>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.FACEBOOKDEPOSIT
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, amount, defaultPort))
}
