package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class DeleteWatch(val ip: String, val watchID: Int?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): DeleteWatch {
            return DeleteWatch(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.DELETEWATCH
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, watchID))
}
