package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestGame(val ip: String, val path: String?, val name: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): RequestGame {
            return RequestGame(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.REQUESTGAME
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, path, name))
}
