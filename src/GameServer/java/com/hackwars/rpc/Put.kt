package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class Put(
    val ip: String?,
    val port: Int,
    val name: String?,
    val fetchPath: String?,
    val putPath: String?,
    val targetIP: String,
    val password: String?,
    val quantity: Int?
) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "put"
        fun fromRpc(rfc: RemoteFunctionCall): Put {
            return Put(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<String?>(rfc, 3),
                getPositionalParameter<String?>(rfc, 4),
                getPositionalParameter<String>(rfc, 5),
                getPositionalParameter<String?>(rfc, 6),
                getPositionalParameter<Int?>(rfc, 7),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(
        0,
        FUNCTION,
        arrayOf<Any?>(ip, port, name, fetchPath, putPath, targetIP, password, quantity)
    )
}
