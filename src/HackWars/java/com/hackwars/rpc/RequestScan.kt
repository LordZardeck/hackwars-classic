package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestScan(val ip: String, val targetIP: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): RequestScan {
            return RequestScan(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.REQUESTSCAN
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, targetIP))
}
