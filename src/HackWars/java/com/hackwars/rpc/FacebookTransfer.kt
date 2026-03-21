package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class FacebookTransfer(val ip: String?, val ip2: String?, val amount: Float, val defaultPort: Int) :
    RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): FacebookTransfer {
            return FacebookTransfer(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<Float>(rfc, 2),
                getPositionalParameter<Int>(rfc, 3),
            )
        }
    }

    override val spec = GameFunctions.FACEBOOKTRANSFER
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, ip2, amount, defaultPort))
}
