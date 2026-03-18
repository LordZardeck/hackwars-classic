package rpc

import assignments.RemoteFunctionCall

data class FacebookDeposit(val ip: String?, val amount: Float?, val defaultPort: Int) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "facebookdeposit"
        fun fromRpc(rfc: RemoteFunctionCall): FacebookDeposit {
            return FacebookDeposit(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<Float?>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, amount, defaultPort))
}
