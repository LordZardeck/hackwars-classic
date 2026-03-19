package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestScan(val ip: String, val targetIP: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestscan"
        fun fromRpc(rfc: RemoteFunctionCall): RequestScan {
            return RequestScan(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, targetIP))
}
