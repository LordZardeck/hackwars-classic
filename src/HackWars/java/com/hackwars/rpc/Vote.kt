package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class Vote(val targetIp: String?, val sourceIp: String) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): Vote {
            return Vote(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.VOTE
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(targetIp, sourceIp))
}
