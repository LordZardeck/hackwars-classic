package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class Transfer(val amount: Float, val ip: String, val targetIp: String?, val port: Int) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "transfer"
        fun fromRpc(rfc: RemoteFunctionCall): Transfer {
            return Transfer(
                getPositionalParameter<Float>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Int>(rfc, 3),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(amount, ip, targetIp, port))
}
