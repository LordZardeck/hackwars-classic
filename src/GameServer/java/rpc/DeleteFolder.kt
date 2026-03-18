package rpc

import assignments.RemoteFunctionCall

data class DeleteFolder(val ip: String, val directory: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "deletefolder"
        fun fromRpc(rfc: RemoteFunctionCall): DeleteFolder {
            return DeleteFolder(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, directory))
}
