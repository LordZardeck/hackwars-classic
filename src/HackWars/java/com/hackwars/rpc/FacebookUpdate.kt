package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class FacebookUpdate(val ip: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): FacebookUpdate {
            return FacebookUpdate(
                getPositionalParameter<String?>(rfc, 0),
            )
        }
    }

    override val spec = GameFunctions.FACEBOOKUPDATE
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip))
}
