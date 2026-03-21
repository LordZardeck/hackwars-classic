package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class DoChallenge(val ip: String, val code: String?, val challengeID: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): DoChallenge {
            return DoChallenge(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.DOCHALLENGE
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, code, challengeID))
}
