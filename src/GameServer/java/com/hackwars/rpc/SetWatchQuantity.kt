package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetWatchQuantity(val ip: String, val watchID: Int?, val quantity: Float?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setwatchquantity"
        fun fromRpc(rfc: RemoteFunctionCall): SetWatchQuantity {
            return SetWatchQuantity(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Float?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, watchID, quantity))
}
