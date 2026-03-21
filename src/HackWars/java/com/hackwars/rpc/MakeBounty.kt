package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class MakeBounty(
    val sourceIp: String,
    val anonymous: Boolean?,
    val target: String?,
    val type: Int?,
    val fname: String?,
    val folder: String?,
    val iterations: Int?,
    val reward: Float?
) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): MakeBounty {
            return MakeBounty(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Boolean?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Int?>(rfc, 3),
                getPositionalParameter<String?>(rfc, 4),
                getPositionalParameter<String?>(rfc, 5),
                getPositionalParameter<Int?>(rfc, 6),
                getPositionalParameter<Float?>(rfc, 7),
            )
        }
    }

    override val spec = GameFunctions.MAKEBOUNTY
    override fun toRfc(requestId: Int) = RemoteFunctionCall(
        requestId,
        spec.wireName,
        arrayOf<Any?>(sourceIp, anonymous, target, type, fname, folder, iterations, reward)
    )
}
