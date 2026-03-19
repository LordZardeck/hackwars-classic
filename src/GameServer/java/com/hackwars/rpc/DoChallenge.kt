package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class DoChallenge(val ip: String, val code: String?, val challengeID: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "dochallenge"
        fun fromRpc(rfc: RemoteFunctionCall): DoChallenge {
            return DoChallenge(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, code, challengeID))
}
