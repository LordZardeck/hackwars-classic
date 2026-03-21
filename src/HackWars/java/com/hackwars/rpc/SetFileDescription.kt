package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetFileDescription(val ip: String, val path: String?, val name: String?, val description: String?) :
    RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SetFileDescription {
            return SetFileDescription(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<String?>(rfc, 3),
            )
        }
    }

    override val spec = GameFunctions.SETFILEDESCRIPTION
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, path, name, description))
}
