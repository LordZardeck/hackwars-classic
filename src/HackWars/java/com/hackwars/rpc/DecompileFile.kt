package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class DecompileFile(val ip: String, val location: String?, val fileName: String?, val compileCost: Float?) :
    RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): DecompileFile {
            return DecompileFile(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Float?>(rfc, 3),
            )
        }
    }

    override val spec = GameFunctions.DECOMPILEFILE
    override fun toRfc(requestId: Int) =
        RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, location, fileName, compileCost))
}
