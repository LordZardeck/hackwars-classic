package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetWatchNote(val ip: String, val watchID: Int?, val note: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SetWatchNote {
            return SetWatchNote(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.SETWATCHNOTE
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, watchID, note))
}
