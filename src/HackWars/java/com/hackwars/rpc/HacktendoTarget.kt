package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class HacktendoTarget(
    val targetX: Int,
    val targetY: Int,
    val ip: String?,
    val currentX: Int,
    val currentY: Int
) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): HacktendoTarget {
            return HacktendoTarget(
                getPositionalParameter<Int>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Int>(rfc, 3),
                getPositionalParameter<Int>(rfc, 4),
            )
        }
    }

    override val spec = GameFunctions.HACKTENDO_TARGET
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(targetX, targetY, ip, currentX, currentY))
}
