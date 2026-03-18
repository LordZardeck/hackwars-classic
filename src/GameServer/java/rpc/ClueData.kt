package rpc

import assignments.RemoteFunctionCall

data class ClueData(val ip: String, val data: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "cluedata"
        fun fromRpc(rfc: RemoteFunctionCall): ClueData {
            return ClueData(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, data))
}
