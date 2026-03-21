package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class CreateFolder(val ip: String, val directory: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): CreateFolder {
            return CreateFolder(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.CREATEFOLDER
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, directory))
}
