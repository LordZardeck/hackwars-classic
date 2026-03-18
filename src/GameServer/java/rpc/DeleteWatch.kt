package rpc

import assignments.RemoteFunctionCall

data class DeleteWatch(val ip: String, val watchID: Int?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "deletewatch"
        fun fromRpc(rfc: RemoteFunctionCall): DeleteWatch {
            return DeleteWatch(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, watchID))
}
