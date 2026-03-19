package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestCancelAttack(val ip: String, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestcancelattack"
        fun fromRpc(rfc: RemoteFunctionCall): RequestCancelAttack {
            return RequestCancelAttack(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, port))
}
