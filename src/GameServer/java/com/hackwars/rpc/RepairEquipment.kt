package com.hackwars.rpc

import assignments.RemoteFunctionCall

data class RepairEquipment(val ip: String, val position: Int?, val name: String?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "repairequipment"
        fun fromRpc(rfc: RemoteFunctionCall): RepairEquipment {
            return RepairEquipment(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, position, name))
}
