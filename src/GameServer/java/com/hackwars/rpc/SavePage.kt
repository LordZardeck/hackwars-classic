package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SavePage(val ip: String, val title: String?, val body: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "savepage"
        fun fromRpc(rfc: RemoteFunctionCall): SavePage {
            return SavePage(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, title, body))
}
