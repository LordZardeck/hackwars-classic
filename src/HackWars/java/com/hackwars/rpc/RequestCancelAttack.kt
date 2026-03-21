package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestCancelAttack(val ip: String, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): RequestCancelAttack {
            return RequestCancelAttack(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.REQUESTCANCELATTACK
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, port))
}
