package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class ChangeWatchPort(val ip: String, val watchId: Int?, val portId: Int?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): ChangeWatchPort {
            return ChangeWatchPort(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Int?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.CHANGEWATCHPORT
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, watchId, portId))
}
