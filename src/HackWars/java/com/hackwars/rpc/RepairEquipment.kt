package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RepairEquipment(val ip: String, val position: Int?, val name: String?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): RepairEquipment {
            return RepairEquipment(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.REPAIREQUIPMENT
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, position, name))
}
