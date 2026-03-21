package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class FinalizeCancelled(val ip: String, val targetIP: String?, val targetPort: Int) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): FinalizeCancelled {
            return FinalizeCancelled(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.FINALIZECANCELLED
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, targetIP, targetPort))
}
