package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SellFile(
    val ip: String,
    val location: String?,
    val fileName: String?,
    val compileCost: Float?,
    val quantity: Int?
) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SellFile {
            return SellFile(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Float?>(rfc, 3),
                getPositionalParameter<Int?>(rfc, 4),
            )
        }
    }

    override val spec = GameFunctions.SELLFILE
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, location, fileName, compileCost, quantity))
}
