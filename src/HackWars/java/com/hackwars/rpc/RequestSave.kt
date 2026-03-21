package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestSave(val fileName: String?, val triggerParam: HashMap<*, *>?, val targetIP: String?) :
    RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): RequestSave {
            return RequestSave(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<HashMap<*, *>?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.REQUESTSAVE
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(fileName, triggerParam, targetIP))
}
