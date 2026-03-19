package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestEquipment(val ip: String) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestequipment"
        fun fromRpc(rfc: RemoteFunctionCall): RequestEquipment {
            return RequestEquipment(
                getPositionalParameter<String>(rfc, 0),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip))
}
