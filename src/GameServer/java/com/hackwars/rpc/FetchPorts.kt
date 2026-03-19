package com.hackwars.rpc

import assignments.RemoteFunctionCall


data class FetchPorts(val encryptedIp: String) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "fetchports"
        fun fromRpc(rfc: RemoteFunctionCall): FetchPorts {
            return FetchPorts(
                getPositionalParameter<String>(rfc, 0)
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf(encryptedIp))

}