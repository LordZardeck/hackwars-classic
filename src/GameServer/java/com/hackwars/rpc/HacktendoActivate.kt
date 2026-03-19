package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class HacktendoActivate(val activateID: Int, val activateType: Int, val ip: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "hacktendoActivate"
        fun fromRpc(rfc: RemoteFunctionCall): HacktendoActivate {
            return HacktendoActivate(
                getPositionalParameter<Int>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(activateID, activateType, ip))
}
