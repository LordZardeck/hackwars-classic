package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class InstallFirewall(val ip: String, val port: Int, val path: String?, val name: String?) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "installfirewall"
        fun fromRpc(rfc: RemoteFunctionCall): InstallFirewall {
            return InstallFirewall(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<String?>(rfc, 3),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, port, path, name))
}
