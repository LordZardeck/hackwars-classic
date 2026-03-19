package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class DeleteFile(val ip: String, val path: String?, val name: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "deletefile"
        fun fromRpc(rfc: RemoteFunctionCall): DeleteFile {
            return DeleteFile(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, path, name))
}
