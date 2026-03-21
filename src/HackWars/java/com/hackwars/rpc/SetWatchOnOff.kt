package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetWatchOnOff(val ip: String, val watchID: Int?, val state: Boolean?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SetWatchOnOff {
            return SetWatchOnOff(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Boolean?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.SETWATCHONOFF
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, watchID, state))
}
