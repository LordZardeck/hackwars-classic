package rpc

import assignments.RemoteFunctionCall

data class Deposit(val amount: Float, val ip: String, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "deposit"
        fun fromRpc(rfc: RemoteFunctionCall): Deposit {
            return Deposit(
                getPositionalParameter<Float>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(amount, ip, port))
}
