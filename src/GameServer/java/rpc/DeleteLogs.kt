package rpc

import assignments.RemoteFunctionCall

data class DeleteLogs(val ip: String) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "deletelogs"
        fun fromRpc(rfc: RemoteFunctionCall): DeleteLogs {
            return DeleteLogs(
                getPositionalParameter<String>(rfc, 0),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip))
}
