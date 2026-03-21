package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class EmptyPettyCash(val ip: String, val targetIP: String?, val targetPort: Int, val windowHandle: Int) :
    RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): EmptyPettyCash {
            return EmptyPettyCash(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
                getPositionalParameter<Int>(rfc, 3),
            )
        }
    }

    override val spec = GameFunctions.EMPTYPETTYCASH
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, targetIP, targetPort, windowHandle))
}
