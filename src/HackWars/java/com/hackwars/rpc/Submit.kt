package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class Submit(
    val targetIp: String?,
    val sourceIp: String,
    val parameters: HashMap<Any?, Any?>
) : RemoteFunctionCallImpl() {
    companion object {

        fun fromRpc(rfc: RemoteFunctionCall): Submit {
            return Submit(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
                getPositionalParameter<HashMap<Any?, Any?>?>(rfc, 2) ?: HashMap()
            )
        }
    }

    override val spec = GameFunctions.SUBMIT
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(targetIp, sourceIp, parameters))
}
