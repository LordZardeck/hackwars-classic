package com.hackwars.rpc

import assignments.RemoteFunctionCall

@Suppress("ArrayInDataClass")
data class RequestZombieAttack(
    val targetIP: String,
    val targetPort: Int,
    val sourceIP: String?,
    val sourcePort: Int,
    val I: Array<Int?>?, // TODO: Rename once we figure out what this is
    val S: Array<Array<String?>?>?, // TODO: Rename once we figure out what this is
    val O: Array<Any?>?, // TODO: Rename once we figure out what this is
    val parentIP: String
) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): RequestZombieAttack {
            return RequestZombieAttack(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Int>(rfc, 3),
                getPositionalParameter<Array<Int?>?>(rfc, 4),
                getPositionalParameter<Array<Array<String?>?>?>(rfc, 5),
                getPositionalParameter<Array<Any?>?>(rfc, 6),
                getPositionalParameter<String>(rfc, 7),
            )
        }
    }

    override val spec = GameFunctions.REQUESTZOMBIEATTACK
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(
            requestId,
            spec.wireName,
            arrayOf<Any?>(targetIP, targetPort, sourceIP, sourcePort, I, S, O, parentIP)
        )
}
