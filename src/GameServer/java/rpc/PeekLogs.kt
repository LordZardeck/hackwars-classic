package rpc

import assignments.RemoteFunctionCall

data class PeekLogs(val ip: String, val targetIP: String?, val port: Int) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "peeklogs"
        fun fromRpc(rfc: RemoteFunctionCall): PeekLogs {
            return PeekLogs(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<Int>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, targetIP, port))
}
