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
        const val FUNCTION = "sellfile"
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

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, location, fileName, compileCost, quantity))
}
