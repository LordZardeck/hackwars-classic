package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetDefaultPort(val encryptedIp: String, val port: Int, val type: Int?) : RemoteFunctionCallImpl() {
    companion object {

        fun fromRpc(rfc: RemoteFunctionCall): SetDefaultPort {
            return SetDefaultPort(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<Int?>(rfc, 2)
            )
        }
    }

    override val spec = GameFunctions.SETDEFAULTPORT
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(encryptedIp, port, type))
}
