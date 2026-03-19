package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class ChangeNetwork(val encryptedIp: String, val network: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "changenetwork"

        fun fromRpc(rfc: RemoteFunctionCall): ChangeNetwork {
            return ChangeNetwork(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(encryptedIp, network))
}
