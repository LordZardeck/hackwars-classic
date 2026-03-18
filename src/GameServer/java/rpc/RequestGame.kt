package rpc

import assignments.RemoteFunctionCall

data class RequestGame(val ip: String, val path: String?, val name: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestgame"
        fun fromRpc(rfc: RemoteFunctionCall): RequestGame {
            return RequestGame(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, path, name))
}
