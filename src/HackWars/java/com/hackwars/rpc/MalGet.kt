package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class MalGet(
    val ip: String?,
    val port: Int,
    val name: String?,
    val fetchPath: String?,
    val putPath: String?,
    val targetIP: String,
    val attackPort: Int
) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): MalGet {
            return MalGet(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<String?>(rfc, 3),
                getPositionalParameter<String?>(rfc, 4),
                getPositionalParameter<String>(rfc, 5),
                getPositionalParameter<Int>(rfc, 6),
            )
        }
    }

    override val spec = GameFunctions.MALGET
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(
            requestId,
            spec.wireName,
            arrayOf<Any?>(ip, port, name, fetchPath, putPath, targetIP, attackPort)
        )
}
