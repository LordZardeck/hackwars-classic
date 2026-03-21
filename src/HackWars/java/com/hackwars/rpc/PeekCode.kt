package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class PeekCode(val ip: String, val targetIP: String?, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): PeekCode {
            return PeekCode(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.PEEKCODE
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, targetIP, port))
}
