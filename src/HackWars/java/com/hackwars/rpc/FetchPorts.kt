package com.hackwars.rpc

import assignments.RemoteFunctionCall


data class FetchPorts(val encryptedIp: String) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): FetchPorts {
            return FetchPorts(
                getPositionalParameter<String>(rfc, 0)
            )
        }
    }

    override val spec = GameFunctions.FETCHPORTS
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf(encryptedIp))

}