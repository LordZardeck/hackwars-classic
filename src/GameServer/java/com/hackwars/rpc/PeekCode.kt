package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class PeekCode(val ip: String, val targetIP: String?, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "peekcode"
        fun fromRpc(rfc: RemoteFunctionCall): PeekCode {
            return PeekCode(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, targetIP, port))
}
