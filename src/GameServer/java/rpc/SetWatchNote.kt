package rpc

import assignments.RemoteFunctionCall

data class SetWatchNote(val ip: String, val watchID: Int?, val note: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setwatchnote"
        fun fromRpc(rfc: RemoteFunctionCall): SetWatchNote {
            return SetWatchNote(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, watchID, note))
}
