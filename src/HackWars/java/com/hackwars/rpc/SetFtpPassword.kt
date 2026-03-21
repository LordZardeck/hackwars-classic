package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetFtpPassword(val ip: String, val password: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SetFtpPassword {
            return SetFtpPassword(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.SETFTPPASSWORD
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, password))
}
