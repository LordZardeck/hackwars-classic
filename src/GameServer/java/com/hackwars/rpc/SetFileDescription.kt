package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetFileDescription(val ip: String, val path: String?, val name: String?, val description: String?) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setfiledescription"
        fun fromRpc(rfc: RemoteFunctionCall): SetFileDescription {
            return SetFileDescription(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<String?>(rfc, 3),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, path, name, description))
}
