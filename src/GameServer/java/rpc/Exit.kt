package rpc

import assignments.RemoteFunctionCall

data class Exit(val targetIp: String?, val sourceIp: String) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "exit"
        fun fromRpc(rfc: RemoteFunctionCall): Exit {
            return Exit(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(targetIp, sourceIp))
}
