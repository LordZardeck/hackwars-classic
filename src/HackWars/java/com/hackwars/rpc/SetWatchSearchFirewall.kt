package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetWatchSearchFirewall(val ip: String, val watchID: Int?, val searchFireWall: Int?) :
    RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SetWatchSearchFirewall {
            return SetWatchSearchFirewall(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Int?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.SETWATCHSEARCHFIREWALL
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, watchID, searchFireWall))
}
