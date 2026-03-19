package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class HealPort(val encryptedIp: String, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "healport"

        fun fromRpc(rfc: RemoteFunctionCall): HealPort {
            return HealPort(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1)
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(encryptedIp, port))
}
