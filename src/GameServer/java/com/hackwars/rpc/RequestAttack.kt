package com.hackwars.rpc

import assignments.RemoteFunctionCall

@Suppress("ArrayInDataClass")
data class RequestAttack(
    val targetIP: String,
    val targetPort: Int,
    val sourceIP: String,
    val sourcePort: Int,
    val secondaryPorts: Array<Int?>?,
    val scripts: Array<Array<String?>?>?,
    val extraInfo: Array<Any?>?,
    val windowHandle: Int?
) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestattack"
        fun fromRpc(rfc: RemoteFunctionCall): RequestAttack {
            return RequestAttack(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String>(rfc, 2),
                getPositionalParameter<Int>(rfc, 3),
                getPositionalParameter<Array<Int?>?>(rfc, 4),
                getPositionalParameter<Array<Array<String?>?>?>(rfc, 5),
                getPositionalParameter<Array<Any?>?>(rfc, 6),
                getPositionalParameter<Int?>(rfc, 7),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(
        0,
        FUNCTION,
        arrayOf<Any?>(targetIP, targetPort, sourceIP, sourcePort, secondaryPorts, scripts, extraInfo, windowHandle)
    )
}
