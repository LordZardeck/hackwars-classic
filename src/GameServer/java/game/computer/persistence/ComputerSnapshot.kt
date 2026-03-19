package game.computer.persistence

data class ComputerSnapshot(
    val ip: String = "",
    val name: String? = null,
    val cpuType: Int = 0,
    val memoryType: Int = 0,
    val password: String? = null,
    val hackCount: Int = 0,
    val voteCount: Int = 0,
    val playerType: Int = 0,
    val network: String? = null,
    val dailyPaySize: Float = 0f,
    val dailyPayReduction: Float = 1f,
    val respawnMoney: Float = 0f,
    val maximumPettyCash: Float = 0f,
    val dropTable: Int = 0,
    val currentQuests: List<CurrentQuestSnapshot> = emptyList(),
    val involvedQuests: List<Int> = emptyList(),
    val completedQuests: List<CompletedQuestSnapshot> = emptyList(),
    val allowedNetworks: List<String> = emptyList(),
    val logEntries: List<LogEntrySnapshot> = emptyList(),
    val globals: List<GlobalSnapshot> = defaultGlobals(),
    val hdType: Int = 0,
    val lastPaid: Long = 0L,
    val pettyCash: Float = 0f,
    val bank: Float = 0f,
    val defaultAttack: Int = 0,
    val defaultBank: Int = 0,
    val defaultFtp: Int = 0,
    val defaultHttp: Int = 0,
    val defaultShipping: Int = 0,
    val stats: ComputerStatsSnapshot = ComputerStatsSnapshot(),
    val commodityAmount: List<Float> = defaultCommodityValues(),
    val commodityRespawn: List<Float> = defaultCommodityValues(),
    val portsXml: String = defaultPortsXml(),
    val watchesXml: String = defaultWatchesXml(),
    val fileSystemXml: String = defaultFilesXml(),
    val website: ComputerWebsiteSnapshot = ComputerWebsiteSnapshot(),
    val equipmentXml: String = defaultEquipmentXml(),
    val preferences: Map<String, String> = emptyMap()
)

data class ComputerStatsSnapshot(
    val attackXp: Float = 0f,
    val merchantingXp: Float = 0f,
    val firewallXp: Float = 0f,
    val watchXp: Float = 0f,
    val scanningXp: Float = 0f,
    val webDesignXp: Float = 0f,
    val redirectingXp: Float = 0f,
    val repairXp: Float = 0f
)

data class CurrentQuestSnapshot(
    val id: Int,
    val label: String = "",
    val tasks: List<CurrentQuestTaskSnapshot> = emptyList()
)

data class CurrentQuestTaskSnapshot(
    val name: String,
    val complete: Boolean = false,
    val label: String = ""
)

data class CompletedQuestSnapshot(
    val id: Int,
    val label: String = ""
)

data class LogEntrySnapshot(
    val ip: String = "",
    val message: String? = null
)

data class GlobalSnapshot(
    val type: String = "TYPE",
    val value: String? = null
)

data class ComputerWebsiteSnapshot(
    val myVotes: Int = 0,
    val storeRevenueTarget: String = "",
    val adRevenueTarget: String? = null,
    val title: String? = null,
    val body: String? = null
)

internal fun defaultCommodityValues(): List<Float> = List(5) { 0f }

internal fun defaultGlobals(): List<GlobalSnapshot> = List(20) { GlobalSnapshot() }

internal fun defaultPortsXml(): String = "<ports>\n</ports>\n"

internal fun defaultWatchesXml(): String = "<watches>\n</watches>\n"

internal fun defaultFilesXml(): String = "<files>\n</files>\n"

internal fun defaultEquipmentXml(): String = buildString {
    append("<equipment>\n</equipment>\n")
    append("<equipment>\n</equipment>\n")
    append("<equipment>\n</equipment>\n")
}
