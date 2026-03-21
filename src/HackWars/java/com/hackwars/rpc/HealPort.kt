package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class HealPort(val encryptedIp: String, val port: Int) : RemoteFunctionCallImpl() {
    companion object {

        fun fromRpc(rfc: RemoteFunctionCall): HealPort {
            return HealPort(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1)
            )
        }
    }

    override val spec = GameFunctions.HEALPORT
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(encryptedIp, port))
}
