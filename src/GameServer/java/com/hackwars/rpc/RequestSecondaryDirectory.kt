package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RequestSecondaryDirectory(val ip: String?, val path: String?, val targetIP: String, val port: Int) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestsecondarydirectory"
        fun fromRpc(rfc: RemoteFunctionCall): RequestSecondaryDirectory {
            return RequestSecondaryDirectory(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String>(rfc, 2),
                getPositionalParameter<Int>(rfc, 3),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, path, targetIP, port))
}
