package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetDummyPort(val ip: String, val port: Int, val dummy: Boolean?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setdummyport"
        fun fromRpc(rfc: RemoteFunctionCall): SetDummyPort {
            return SetDummyPort(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<Boolean?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, port, dummy))
}
