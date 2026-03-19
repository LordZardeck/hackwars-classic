package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetDefaultPort(val encryptedIp: String, val port: Int, val type: Int?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setdefaultport"

        fun fromRpc(rfc: RemoteFunctionCall): SetDefaultPort {
            return SetDefaultPort(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<Int?>(rfc, 2)
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(encryptedIp, port, type))
}
