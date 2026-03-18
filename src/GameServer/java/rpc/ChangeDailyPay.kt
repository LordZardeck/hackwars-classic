package rpc

import assignments.RemoteFunctionCall

data class ChangeDailyPay(
    val ip: String?,
    val port: Int,
    val change: String?,
    val finalizeIP: String,
    val attackPort: Int
) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "changedailypay"
        fun fromRpc(rfc: RemoteFunctionCall): ChangeDailyPay {
            return ChangeDailyPay(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<String>(rfc, 3),
                getPositionalParameter<Int>(rfc, 4),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, port, change, finalizeIP, attackPort))
}
