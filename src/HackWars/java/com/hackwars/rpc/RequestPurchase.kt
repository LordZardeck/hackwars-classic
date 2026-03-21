package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestPurchase(val targetIp: String, val sourceIp: String, val fileName: String?, val quantity: Int?) :
    RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): RequestPurchase {
            return RequestPurchase(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Int?>(rfc, 3),
            )
        }
    }

    override val spec = GameFunctions.REQUESTPURCHASE
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(targetIp, sourceIp, fileName, quantity))
}
