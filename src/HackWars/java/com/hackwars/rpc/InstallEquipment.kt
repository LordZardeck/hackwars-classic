package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class InstallEquipment(val ip: String, val position: Int?, val name: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): InstallEquipment {
            return InstallEquipment(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.INSTALLEQUIPMENT
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, position, name))
}
