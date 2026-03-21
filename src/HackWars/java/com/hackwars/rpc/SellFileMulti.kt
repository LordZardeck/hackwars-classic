package com.hackwars.rpc

import assignments.RemoteFunctionCall

@Suppress("ArrayInDataClass")
data class SellFileMulti(val ip: String, val allFiles: Array<Any?>?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SellFileMulti {
            return SellFileMulti(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Array<Any?>?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.SELLFILEMULTI
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, allFiles))
}
