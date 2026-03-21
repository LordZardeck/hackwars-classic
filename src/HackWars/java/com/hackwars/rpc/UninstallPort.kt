package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class UninstallPort(val ip: String, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): UninstallPort {
            return UninstallPort(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.UNINSTALLPORT
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, port))
}
