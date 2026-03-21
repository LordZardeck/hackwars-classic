package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestZombieCancelAttack(val ip: String?, val port: Int, val targetIP: String) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): RequestZombieCancelAttack {
            return RequestZombieCancelAttack(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.REQUESTZOMBIECANCELATTACK
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, port, targetIP))
}
