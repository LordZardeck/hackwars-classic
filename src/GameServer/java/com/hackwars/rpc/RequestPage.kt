package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestPage(val ip: String) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestpage"
        fun fromRpc(rfc: RemoteFunctionCall): RequestPage {
            return RequestPage(
                getPositionalParameter<String>(rfc, 0),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip))
}
