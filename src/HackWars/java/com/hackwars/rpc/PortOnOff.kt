package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class PortOnOff(val ip: String, val port: Int, val on: Boolean?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): PortOnOff {
            return PortOnOff(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<Boolean?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.PORTONOFF
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, port, on))
}
