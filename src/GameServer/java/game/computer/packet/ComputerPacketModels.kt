package game.computer.packet

data class ComputerStandardPacketSnapshot(
    val pettyCash: Float,
    val bankMoney: Float,
    val cpuMax: Float,
    val cpuType: Int,
    val memoryType: Int,
    val defaultBank: Int,
    val defaultAttack: Int,
    val defaultHttp: Int,
    val defaultFtp: Int,
    val defaultShipping: Int,
    val hackCount: Int,
    val voteCount: Int,
    val cpuCost: Float,
    val hdType: Int,
    val hdQuantity: Int,
    val hdMaximum: Int,
    val serverLoad: Int,
    val commodities: FloatArray,
    val healDiscount: Float,
    val votesLeft: Int,
    val messages: Array<Any?> = emptyArray(),
    val choices: List<Array<Any?>> = emptyList(),
    val logMessages: List<Array<String>>? = null,
    val countdownSeconds: Int? = null,
    val preferences: Map<String, Any?>? = null,
)

data class PortHealthSnapshot(
    val portNumber: Int,
    val health: Float,
    val cpuCost: Float,
    val firewallType: Any?,
    val healCount: Int,
    val baseCpuCostAndFirewall: Float,
    val windowHandle: Int,
) {
    fun toPayload(): Array<Any?> = arrayOf(
        portNumber,
        health,
        cpuCost,
        firewallType,
        healCount,
        baseCpuCostAndFirewall,
        windowHandle,
    )
}

data class ComputerDamagePacketSnapshot(
    val attackXP: Float,
    val merchantXP: Float,
    val fireWallXP: Float,
    val watchXP: Float,
    val scanningXP: Float,
    val httpXP: Float,
    val redirectXP: Float,
    val repairXP: Float,
    val cpuCost: Float,
    val healthUpdates: List<PortHealthSnapshot> = emptyList(),
    val damageEntries: List<Array<Any?>> = emptyList(),
)
