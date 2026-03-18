package rpc

import assignments.RemoteFunctionCall

data class Vote(val targetIp: String?, val sourceIp: String) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "vote"
        fun fromRpc(rfc: RemoteFunctionCall): Vote {
            return Vote(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(targetIp, sourceIp))
}
