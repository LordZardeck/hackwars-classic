package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class Unlock(val ip: String, val code: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): Unlock {
            return Unlock(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.UNLOCK
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, code))
}
