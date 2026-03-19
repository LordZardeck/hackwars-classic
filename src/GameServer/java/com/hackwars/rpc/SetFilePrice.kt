package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetFilePrice(val ip: String, val path: String?, val name: String?, val price: Float?) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setfileprice"
        fun fromRpc(rfc: RemoteFunctionCall): SetFilePrice {
            return SetFilePrice(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Float?>(rfc, 3),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, path, name, price))
}
