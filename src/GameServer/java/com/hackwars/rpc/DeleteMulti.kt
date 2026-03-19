package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class DeleteMulti(val ip: String, val allFiles: Array<Any?>?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "deletemulti"
        fun fromRpc(rfc: RemoteFunctionCall): DeleteMulti {
            return DeleteMulti(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Array<Any?>?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, allFiles))
}
