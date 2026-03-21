package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class FetchWatches(val ip: String) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): FetchWatches {
            return FetchWatches(
                getPositionalParameter<String>(rfc, 0),
            )
        }
    }

    override val spec = GameFunctions.FETCHWATCHES
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip))
}
