package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestSave(val fileName: String?, val triggerParam: HashMap<*, *>?, val targetIP: String?) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestsave"
        fun fromRpc(rfc: RemoteFunctionCall): RequestSave {
            return RequestSave(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<HashMap<*, *>?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(fileName, triggerParam, targetIP))
}
