package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class CreateFolder(val ip: String, val directory: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "createfolder"
        fun fromRpc(rfc: RemoteFunctionCall): CreateFolder {
            return CreateFolder(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, directory))
}
