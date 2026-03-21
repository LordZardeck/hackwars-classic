package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetDummyPort(val ip: String, val port: Int, val dummy: Boolean?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SetDummyPort {
            return SetDummyPort(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<Boolean?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.SETDUMMYPORT
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, port, dummy))
}
