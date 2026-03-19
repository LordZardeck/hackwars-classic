package game.computer.runtime

data class RuntimePortSnapshot(
    val number: Int,
    val type: Int,
    var on: Boolean = true,
    var dummy: Boolean = false,
    var attacking: Boolean = false,
    var overHeated: Boolean = false,
    var health: Float = 100f,
    var cpuCost: Float = 0f,
    var baseCpuCostTotal: Float = 0f,
    var lastDamageWindowHandle: Int = 0,
    var accessing: String = "",
    var targetPort: Int = -1,
    var targetIp: String = "",
    var maliciousTarget: String = "",
    var isZombie: Boolean = false
)
