package rpc

import assignments.RemoteFunctionCall

data class FacebookUpdate(val ip: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "facebookupdate"
        fun fromRpc(rfc: RemoteFunctionCall): FacebookUpdate {
            return FacebookUpdate(
                getPositionalParameter<String?>(rfc, 0),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip))
}
