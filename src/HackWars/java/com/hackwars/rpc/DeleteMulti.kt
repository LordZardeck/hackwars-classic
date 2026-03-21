package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class DeleteMulti(val ip: String, val allFiles: Array<Any?>?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): DeleteMulti {
            return DeleteMulti(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Array<Any?>?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.DELETEMULTI
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, allFiles))
}
