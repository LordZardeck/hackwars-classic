package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class PortOnOff(val ip: String, val port: Int, val on: Boolean?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "portonoff"
        fun fromRpc(rfc: RemoteFunctionCall): PortOnOff {
            return PortOnOff(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<Boolean?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, port, on))
}
