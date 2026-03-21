package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class Exit(val targetIp: String?, val sourceIp: String) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): Exit {
            return Exit(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.EXIT
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(targetIp, sourceIp))
}
