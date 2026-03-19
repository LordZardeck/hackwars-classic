package com.hackwars.rpc

import assignments.RemoteFunctionCall
import java.util.HashMap

data class RequestWebpage(
    val targetIp: String,
    val sourceIp: String,
    val parameters: HashMap<Any?, Any?>
) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestwebpage"

        fun fromRpc(rfc: RemoteFunctionCall): RequestWebpage {
            return RequestWebpage(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String>(rfc, 1),
                getPositionalParameter<HashMap<Any?, Any?>?>(rfc, 2) ?: HashMap()
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(targetIp, sourceIp, parameters))
}
