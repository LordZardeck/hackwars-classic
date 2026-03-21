package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class SetPreferences(val ip: String, val preferences: HashMap<*, *>?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SetPreferences {
            return SetPreferences(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<HashMap<*, *>?>(rfc, 1),
            )
        }
    }

    override val spec = GameFunctions.SETPREFERENCES
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, preferences))
}
