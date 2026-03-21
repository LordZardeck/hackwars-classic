package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class PeekLogs(val ip: String, val targetIP: String?, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): PeekLogs {
            return PeekLogs(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.PEEKLOGS
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, targetIP, port))
}
