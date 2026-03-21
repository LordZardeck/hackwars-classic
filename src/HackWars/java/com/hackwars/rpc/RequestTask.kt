package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestTask(val fileName: String?, val questID: String?, val taskName: String?, val targetIP: String?) :
    RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): RequestTask {
            return RequestTask(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<String?>(rfc, 3),
            )
        }
    }

    override val spec = GameFunctions.REQUESTTASK
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(fileName, questID, taskName, targetIP))
}
