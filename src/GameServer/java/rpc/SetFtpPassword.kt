package rpc

import assignments.RemoteFunctionCall

data class SetFtpPassword(val ip: String, val password: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setftppassword"
        fun fromRpc(rfc: RemoteFunctionCall): SetFtpPassword {
            return SetFtpPassword(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, password))
}
