package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class InstallWatch(
    val ip: String,
    val path: String?,
    val name: String?,
    val type: Int,
    val port: Int
) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "installwatch"
        fun fromRpc(rfc: RemoteFunctionCall): InstallWatch {
            return InstallWatch(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Int>(rfc, 3),
                getPositionalParameter<Int>(rfc, 4),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, path, name, type, port))
}
