package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SavePortNote(val ip: String, val port: Int, val note: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "saveportnote"
        fun fromRpc(rfc: RemoteFunctionCall): SavePortNote {
            return SavePortNote(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, port, note))
}
