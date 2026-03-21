package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class DeleteFirewall(val ip: String, val portID: Int?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): DeleteFirewall {
            return DeleteFirewall(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.DELETEFIREWALL
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, portID))
}
