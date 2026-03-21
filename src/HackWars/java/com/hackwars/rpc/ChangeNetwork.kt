package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class ChangeNetwork(val encryptedIp: String, val network: String?) : RemoteFunctionCallImpl() {
    companion object {

        fun fromRpc(rfc: RemoteFunctionCall): ChangeNetwork {
            return ChangeNetwork(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.CHANGENETWORK
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(encryptedIp, network))
}
