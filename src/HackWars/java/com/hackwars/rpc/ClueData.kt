package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class ClueData(val ip: String, val data: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): ClueData {
            return ClueData(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.CLUEDATA
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, data))
}
