package rpc

import assignments.RemoteFunctionCall

data class ChangeWatchType(val ip: String, val watchID: Int?, val portID: Int?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "changewatchtype"
        fun fromRpc(rfc: RemoteFunctionCall): ChangeWatchType {
            return ChangeWatchType(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Int?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, watchID, portID))
}
