package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetWatchQuantity(val ip: String, val watchID: Int?, val quantity: Float?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SetWatchQuantity {
            return SetWatchQuantity(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Float?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.SETWATCHQUANTITY
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, watchID, quantity))
}
