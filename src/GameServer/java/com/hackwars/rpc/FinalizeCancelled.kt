package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class FinalizeCancelled(val ip: String, val targetIP: String?, val targetPort: Int) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "finalizecancelled"
        fun fromRpc(rfc: RemoteFunctionCall): FinalizeCancelled {
            return FinalizeCancelled(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, targetIP, targetPort))
}
