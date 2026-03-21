package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class ReplaceApplication(val ip: String, val port: Int, val path: String?, val name: String?) :
    RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): ReplaceApplication {
            return ReplaceApplication(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<String?>(rfc, 3),
            )
        }
    }

    override val spec = GameFunctions.REPLACEAPPLICATION
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, port, path, name))
}
