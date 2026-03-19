package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class DeleteFirewall(val ip: String, val portID: Int?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "deletefirewall"
        fun fromRpc(rfc: RemoteFunctionCall): DeleteFirewall {
            return DeleteFirewall(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, portID))
}
