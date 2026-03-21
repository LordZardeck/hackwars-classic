package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetFilePrice(val ip: String, val path: String?, val name: String?, val price: Float?) :
    RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SetFilePrice {
            return SetFilePrice(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Float?>(rfc, 3),
            )
        }
    }

    override val spec = GameFunctions.SETFILEPRICE
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, path, name, price))
}
