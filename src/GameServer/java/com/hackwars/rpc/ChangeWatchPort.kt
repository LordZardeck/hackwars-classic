package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class ChangeWatchPort(val ip: String, val watchId: Int?, val portId: Int?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "changewatchport"
        fun fromRpc(rfc: RemoteFunctionCall): ChangeWatchPort {
            return ChangeWatchPort(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Int?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, watchId, portId))
}
