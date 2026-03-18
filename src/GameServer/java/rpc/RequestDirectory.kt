package rpc

import assignments.RemoteFunctionCall

data class RequestDirectory(val ip: String, val path: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestdirectory"
        fun fromRpc(rfc: RemoteFunctionCall): RequestDirectory {
            return RequestDirectory(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, path))
}
