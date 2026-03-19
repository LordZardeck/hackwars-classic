package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class Unlock(val ip: String, val code: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "unlock"
        fun fromRpc(rfc: RemoteFunctionCall): Unlock {
            return Unlock(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, code))
}
