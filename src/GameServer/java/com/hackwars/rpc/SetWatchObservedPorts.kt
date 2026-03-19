package com.hackwars.rpc

import assignments.RemoteFunctionCall

@Suppress("ArrayInDataClass")
data class SetWatchObservedPorts(val ip: String, val watchID: Int?, val observedPorts: Array<Int?>?) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setwatchobservedports"
        fun fromRpc(rfc: RemoteFunctionCall): SetWatchObservedPorts {
            return SetWatchObservedPorts(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Array<Int?>?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, watchID, observedPorts))
}
