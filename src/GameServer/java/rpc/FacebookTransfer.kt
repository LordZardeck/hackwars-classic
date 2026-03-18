package rpc

import assignments.RemoteFunctionCall

data class FacebookTransfer(val ip: String?, val ip2: String?, val amount: Float, val defaultPort: Int) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "facebooktransfer"
        fun fromRpc(rfc: RemoteFunctionCall): FacebookTransfer {
            return FacebookTransfer(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<Float>(rfc, 2),
                getPositionalParameter<Int>(rfc, 3),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, ip2, amount, defaultPort))
}
