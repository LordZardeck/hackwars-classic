package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class ChangeWatchType(val ip: String, val watchID: Int?, val portID: Int?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): ChangeWatchType {
            return ChangeWatchType(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Int?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.CHANGEWATCHTYPE
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, watchID, portID))
}
