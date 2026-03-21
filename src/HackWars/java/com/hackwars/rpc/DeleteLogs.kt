package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class DeleteLogs(val ip: String) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): DeleteLogs {
            return DeleteLogs(
                getPositionalParameter<String>(rfc, 0),
            )
        }
    }

    override val spec = GameFunctions.DELETELOGS
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip))
}
