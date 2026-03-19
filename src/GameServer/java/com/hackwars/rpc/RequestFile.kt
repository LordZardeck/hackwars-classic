package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestFile(val ip: String, val path: String?, val name: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestfile"
        fun fromRpc(rfc: RemoteFunctionCall): RequestFile {
            return RequestFile(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, path, name))
}
