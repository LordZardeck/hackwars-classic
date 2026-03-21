package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class HacktendoActivate(val activateID: Int, val activateType: Int, val ip: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): HacktendoActivate {
            return HacktendoActivate(
                getPositionalParameter<Int>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.HACKTENDO_ACTIVATE
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(activateID, activateType, ip))
}
