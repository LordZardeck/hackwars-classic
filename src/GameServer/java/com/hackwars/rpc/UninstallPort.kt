package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class UninstallPort(val ip: String, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "uninstallport"
        fun fromRpc(rfc: RemoteFunctionCall): UninstallPort {
            return UninstallPort(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, port))
}
