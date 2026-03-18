package rpc

import assignments.RemoteFunctionCall

data class SetWatchOnOff(val ip: String, val watchID: Int?, val state: Boolean?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setwatchonoff"
        fun fromRpc(rfc: RemoteFunctionCall): SetWatchOnOff {
            return SetWatchOnOff(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Boolean?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, watchID, state))
}
