package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestDirectory(val ip: String, val path: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): RequestDirectory {
            return RequestDirectory(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.REQUESTDIRECTORY
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, path))
}
