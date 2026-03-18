package rpc

import assignments.RemoteFunctionCall

data class FetchWatches(val ip: String) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "fetchwatches"
        fun fromRpc(rfc: RemoteFunctionCall): FetchWatches {
            return FetchWatches(
                getPositionalParameter<String>(rfc, 0),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip))
}
