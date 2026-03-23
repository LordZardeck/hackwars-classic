package game.computer.persistence

data class BlobRef(
    val path: String = "",
    val kind: String = "",
)

data class TextFieldSave(
    val inlineValue: String? = null,
    val blobRef: BlobRef? = null,
)

data class JsonComputerSaveManifest(
    val schemaVersion: Int = 1,
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
    val ports: List<PortSave> = emptyList(),
    val watches: List<WatchSave> = emptyList(),
    val fileSystem: FileSystemSave = FileSystemSave(),
    val website: JsonComputerWebsiteSave = JsonComputerWebsiteSave(),
    val equipmentSlots: List<EquipmentSlotSave> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    val legacyExtras: Map<String, String> = emptyMap(),
)

data class JsonComputerWebsiteSave(
    val myVotes: Int = 0,
    val storeRevenueTarget: String = "",
    val adRevenueTarget: String? = null,
    val title: String? = null,
    val body: TextFieldSave? = null,
)

data class PortSave(
    val number: Int = 0,
    val type: Int = 0,
    val health: Float = 0f,
    val on: Boolean = false,
    val cpuCost: Float = 0f,
    val note: String? = null,
    val firewall: HackerFileSave? = null,
    val dummy: Boolean = false,
    val maliciousTarget: String? = null,
    val scripts: Map<String, TextFieldSave> = emptyMap(),
)

data class WatchSave(
    val type: Int = 0,
    val searchFireWall: Int = 0,
    val cpuCost: Float = 0f,
    val installPort: Int = 0,
    val note: String? = null,
    val on: Boolean = false,
    val observedPorts: List<Int> = emptyList(),
    val quantity: Float = 0f,
    val scripts: Map<String, TextFieldSave> = emptyMap(),
)

data class FileSystemSave(
    val directories: List<String> = emptyList(),
    val files: List<HackerFileSave> = emptyList(),
)

data class EquipmentSlotSave(
    val file: HackerFileSave? = null,
)

data class HackerFileSave(
    val type: Int = 0,
    val name: String = "",
    val location: String = "",
    val description: String = "",
    val price: Float = 0f,
    val quantity: Int = 0,
    val cpuCost: Float = 0f,
    val maker: String = "",
    val content: Map<String, TextFieldSave> = emptyMap(),
    val specialAttributes: Map<String, Map<String, TextFieldSave>> = emptyMap(),
)
