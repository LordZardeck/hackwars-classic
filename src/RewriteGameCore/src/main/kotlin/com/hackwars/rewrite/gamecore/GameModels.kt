package com.hackwars.rewrite.gamecore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
@JvmInline
value class GameStateId(val value: String)

@Serializable
data class ComputerState(
    val id: GameStateId,
    val version: Long = 0,
    val identity: ComputerIdentity = ComputerIdentity(),
    val economy: EconomyState = EconomyState(),
    val hardware: HardwareState = HardwareState(),
    val ports: List<PortState> = emptyList(),
    val filesystem: FilesystemState = FilesystemState(),
    val website: WebsiteState = WebsiteState(),
    val combat: CombatState = CombatState(),
    val quests: QuestState = QuestState(),
    val preferences: PreferenceState = PreferenceState(),
    val stats: PlayerStatsState = PlayerStatsState(),
    val runtime: RuntimeState = RuntimeState(),
) {
    companion object {
        fun empty(
            id: GameStateId,
            playFabId: String = "",
            playerIp: String = id.value,
            displayName: String = "",
        ): ComputerState = ComputerState(
            id = id,
            identity = ComputerIdentity(
                playFabId = playFabId,
                playerIp = playerIp,
                displayName = displayName,
            ),
        )
    }
}

@Serializable
data class ComputerIdentity(
    val playFabId: String = "",
    val playerIp: String = "",
    val displayName: String = "",
)

@Serializable
data class EconomyState(
    val pettyCash: Double = 0.0,
    val bankMoney: Double = 0.0,
    val commodities: List<Double> = List(5) { 0.0 },
    val defaultBankPort: Int? = null,
)

@Serializable
data class HardwareState(
    val cpuType: Int = 0,
    val cpuMax: Double = 0.0,
    val memoryType: Int = 0,
    val hdType: Int = 0,
    val hdQuantity: Int = 0,
    val hdMaximum: Int = 0,
    val equipmentSlots: Map<EquipmentSlot, InstalledEquipment> = emptyMap(),
)

@Serializable
enum class EquipmentSlot {
    CPU,
    MEMORY,
    STORAGE,
    PCI,
    AGP,
}

@Serializable
data class InstalledEquipment(
    val slot: EquipmentSlot,
    val name: String,
    val maker: String = "",
    val binaryPath: String = "",
    val durability: Int = 100,
    val cpuBoost: Double = 0.0,
    val memoryBoost: Int = 0,
    val storageBoost: Int = 0,
)

@Serializable
enum class ApplicationKind {
    GENERIC,
    BANKING,
    FTP,
    HTTP,
    ATTACK,
    WATCH,
}

@Serializable
enum class FirewallKind {
    NONE,
    BASIC,
    CUSTOM,
}

@Serializable
data class InstalledApplication(
    val name: String,
    val kind: ApplicationKind = ApplicationKind.GENERIC,
    val maker: String = "",
    val binaryPath: String = "",
    val cpuCost: Double = 0.0,
    val banking: Boolean = false,
)

@Serializable
data class InstalledFirewall(
    val name: String,
    val kind: FirewallKind = FirewallKind.CUSTOM,
    val maker: String = "",
    val binaryPath: String = "",
    val strength: Int = 0,
    val cpuCost: Double = 0.0,
)

@Serializable
data class PortState(
    val number: Int,
    val type: String = "",
    val enabled: Boolean = true,
    val defaultPort: Boolean = false,
    val dummy: Boolean = false,
    val installedApplication: InstalledApplication? = null,
    val installedFirewall: InstalledFirewall? = null,
)

@Serializable
data class FilesystemState(
    val currentPath: String = "/",
    val directoriesByPath: Map<String, DirectoryEntry> = defaultDirectoriesByPath(),
    val filesByPath: Map<String, StoredFile> = emptyMap(),
) {
    fun listDirectory(path: String?): DirectorySnapshot {
        val normalizedPath = normalizeDirectoryPath(path, currentPath)
        val directories = directoriesByPath.values
            .filter { it.path != normalizedPath && parentDirectoryOf(it.path) == normalizedPath }
            .sortedBy { it.name.lowercase() }
        val files = filesByPath.values
            .filter { parentDirectoryOf(it.path) == normalizedPath }
            .sortedBy { it.name.lowercase() }
        return DirectorySnapshot(
            path = normalizedPath,
            directories = directories,
            files = files,
        )
    }

    fun resolveFile(path: String?, name: String): StoredFile? {
        val normalizedPath = normalizeDirectoryPath(path, currentPath)
        return filesByPath[buildFilePath(normalizedPath, name)]
    }

    fun withCurrentPath(path: String): FilesystemState {
        return copy(currentPath = normalizeDirectoryPath(path, currentPath))
    }
}

@Serializable
data class DirectoryEntry(
    val path: String,
    val name: String,
    val description: String = "",
)

@Serializable
enum class StoredFileKind {
    TEXT,
    NOTE,
    SCRIPT_SOURCE,
    APPLICATION_BINARY,
    FIREWALL_BINARY,
    EQUIPMENT_BINARY,
}

@Serializable
enum class ScriptFamily {
    GENERAL,
    ATTACK,
    BANKING,
    REDIRECT,
    HTTP,
    WATCH,
}

@Serializable
data class CompiledBinaryMetadata(
    val scriptFamily: ScriptFamily = ScriptFamily.GENERAL,
    val outputName: String = "",
    val applicationKind: ApplicationKind? = null,
    val firewallKind: FirewallKind? = null,
    val equipmentSlot: EquipmentSlot? = null,
    val bankingApplication: Boolean = false,
    val strength: Int = 0,
    val experienceAward: Int = 1,
)

@Serializable
data class StoredFile(
    val path: String,
    val name: String,
    val kind: StoredFileKind = StoredFileKind.TEXT,
    val contents: String = "",
    val description: String = "",
    val quantity: Int = 1,
    val maker: String = "",
    val compileCost: Double = 0.0,
    val cpuCost: Double = 0.0,
    val price: Double = 0.0,
    val compiledBinary: CompiledBinaryMetadata? = null,
)

@Serializable
data class WebsiteState(
    val title: String = "",
    val body: String = "",
    val storeRevenueTargetStateId: GameStateId? = null,
)

@Serializable
data class CombatState(
    val activePrograms: List<String> = emptyList(),
    val healthByPort: Map<Int, Int> = emptyMap(),
)

@Serializable
data class QuestState(
    val activeQuestIds: List<String> = emptyList(),
    val completedQuestIds: List<String> = emptyList(),
)

@Serializable
data class PreferenceState(
    val values: Map<String, String> = emptyMap(),
)

@Serializable
data class PlayerStatsState(
    val experienceByFamily: Map<ScriptFamily, Int> = emptyMap(),
)

@Serializable
data class RuntimeState(
    val countdownSeconds: Int = 0,
    val lastMutationVersion: Long = 0,
)

@Serializable
sealed interface ComputerEvent {
    val changedPaths: Set<String>
    val deltaKeys: Set<String>

    fun applyTo(state: ComputerState, nextVersion: Long): ComputerState

    fun toProjection(state: ComputerState): DeltaProjection
}

@Serializable
@SerialName("preference_set")
data class PreferenceSetEvent(
    val key: String,
    val value: String,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("preferences.values.$key")
    override val deltaKeys: Set<String> = setOf("preferences")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            preferences = state.preferences.copy(
                values = state.preferences.values + (key to value),
            ),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(preferences = state.preferences)
    }
}

@Serializable
@SerialName("directory_created")
data class DirectoryCreatedEvent(
    val directory: DirectoryEntry,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("filesystem.directoriesByPath.${directory.path}")
    override val deltaKeys: Set<String> = setOf("filesystem")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        val updatedFilesystem = state.filesystem.ensureDirectory(directory.path)
        return state.copy(
            version = nextVersion,
            filesystem = updatedFilesystem,
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(filesystem = state.filesystem)
    }
}

@Serializable
@SerialName("directory_deleted")
data class DirectoryDeletedEvent(
    val directoryPath: String,
    val removedDirectoryPaths: Set<String>,
    val removedFilePaths: Set<String>,
) : ComputerEvent {
    override val changedPaths: Set<String> = buildSet {
        removedDirectoryPaths.forEach { add("filesystem.directoriesByPath.$it") }
        removedFilePaths.forEach { add("filesystem.filesByPath.$it") }
    }
    override val deltaKeys: Set<String> = setOf("filesystem")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        val updatedFilesystem = state.filesystem.deleteDirectoryTree(directoryPath)
        return state.copy(
            version = nextVersion,
            filesystem = updatedFilesystem,
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(filesystem = state.filesystem)
    }
}

@Serializable
@SerialName("file_saved")
data class FileSavedEvent(
    val file: StoredFile,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("filesystem.filesByPath.${file.path}")
    override val deltaKeys: Set<String> = setOf("filesystem")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        val updatedFilesystem = state.filesystem.saveFile(file)
        return state.copy(
            version = nextVersion,
            filesystem = updatedFilesystem,
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(filesystem = state.filesystem)
    }
}

@Serializable
@SerialName("file_deleted")
data class FileDeletedEvent(
    val filePath: String,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("filesystem.filesByPath.$filePath")
    override val deltaKeys: Set<String> = setOf("filesystem")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        val updatedFilesystem = state.filesystem.deleteFileByPath(filePath)
        return state.copy(
            version = nextVersion,
            filesystem = updatedFilesystem,
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(filesystem = state.filesystem)
    }
}

@Serializable
@SerialName("economy_balance_adjusted")
data class EconomyBalanceAdjustedEvent(
    val pettyCashDelta: Double = 0.0,
    val bankMoneyDelta: Double = 0.0,
) : ComputerEvent {
    override val changedPaths: Set<String> = buildSet {
        if (pettyCashDelta != 0.0) {
            add("economy.pettyCash")
        }
        if (bankMoneyDelta != 0.0) {
            add("economy.bankMoney")
        }
    }.ifEmpty { setOf("economy") }
    override val deltaKeys: Set<String> = setOf("economy")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            economy = state.economy.copy(
                pettyCash = state.economy.pettyCash + pettyCashDelta,
                bankMoney = state.economy.bankMoney + bankMoneyDelta,
            ),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(economy = state.economy)
    }
}

@Serializable
@SerialName("store_file_priced")
data class StoreFilePricedEvent(
    val filePath: String,
    val price: Double,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("filesystem.filesByPath.$filePath.price")
    override val deltaKeys: Set<String> = setOf("filesystem")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        val existing = state.filesystem.filesByPath[filePath] ?: return state.copy(
            version = nextVersion,
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
        return state.copy(
            version = nextVersion,
            filesystem = state.filesystem.saveFile(existing.copy(price = price)),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(filesystem = state.filesystem)
    }
}

@Serializable
data class StoreLiquidationLineItem(
    val sourceFilePath: String,
    val remainingSourceFile: StoredFile? = null,
    val creditedPettyCash: Double,
)

@Serializable
@SerialName("store_files_liquidated")
data class StoreFilesLiquidatedEvent(
    val soldItems: List<StoreLiquidationLineItem>,
) : ComputerEvent {
    override val changedPaths: Set<String> = buildSet {
        soldItems.forEach { add("filesystem.filesByPath.${it.sourceFilePath}") }
        add("economy.pettyCash")
    }
    override val deltaKeys: Set<String> = setOf("filesystem", "economy")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        var filesystem = state.filesystem
        var pettyCashDelta = 0.0
        soldItems.forEach { item ->
            filesystem = filesystem.deleteFileByPath(item.sourceFilePath)
            if (item.remainingSourceFile != null) {
                filesystem = filesystem.saveFile(item.remainingSourceFile)
            }
            pettyCashDelta += item.creditedPettyCash
        }
        return state.copy(
            version = nextVersion,
            filesystem = filesystem,
            economy = state.economy.copy(
                pettyCash = state.economy.pettyCash + pettyCashDelta,
            ),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            filesystem = state.filesystem,
            economy = state.economy,
        )
    }
}

@Serializable
@SerialName("store_inventory_received")
data class StoreInventoryReceivedEvent(
    val files: List<StoredFile>,
) : ComputerEvent {
    override val changedPaths: Set<String> = files
        .mapTo(linkedSetOf()) { "filesystem.filesByPath.${it.path}" }
    override val deltaKeys: Set<String> = setOf("filesystem")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        var filesystem = state.filesystem
        files.forEach { file ->
            filesystem = filesystem.addOrIncrement(file)
        }
        return state.copy(
            version = nextVersion,
            filesystem = filesystem,
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(filesystem = state.filesystem)
    }
}

@Serializable
@SerialName("store_listing_purchased")
data class StoreListingPurchasedEvent(
    val listingPath: String,
    val remainingListing: StoredFile? = null,
    val pettyCashDelta: Double = 0.0,
) : ComputerEvent {
    override val changedPaths: Set<String> = buildSet {
        add("filesystem.filesByPath.$listingPath")
        if (pettyCashDelta != 0.0) {
            add("economy.pettyCash")
        }
    }
    override val deltaKeys: Set<String> = buildSet {
        add("filesystem")
        if (pettyCashDelta != 0.0) {
            add("economy")
        }
    }

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        var filesystem = state.filesystem.deleteFileByPath(listingPath)
        if (remainingListing != null) {
            filesystem = filesystem.saveFile(remainingListing)
        }
        return state.copy(
            version = nextVersion,
            filesystem = filesystem,
            economy = state.economy.copy(
                pettyCash = state.economy.pettyCash + pettyCashDelta,
            ),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            filesystem = state.filesystem,
            economy = state.economy.takeIf { pettyCashDelta != 0.0 },
        )
    }
}

@Serializable
@SerialName("purchased_file_received")
data class PurchasedFileReceivedEvent(
    val file: StoredFile,
    val pettyCashDelta: Double,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf(
        "filesystem.filesByPath.${file.path}",
        "economy.pettyCash",
    )
    override val deltaKeys: Set<String> = setOf("filesystem", "economy")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            filesystem = state.filesystem.addOrIncrement(file),
            economy = state.economy.copy(
                pettyCash = state.economy.pettyCash + pettyCashDelta,
            ),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            filesystem = state.filesystem,
            economy = state.economy,
        )
    }
}

@Serializable
@SerialName("files_deleted")
data class FilesDeletedEvent(
    val filePaths: Set<String> = emptySet(),
    val directoryPaths: Set<String> = emptySet(),
) : ComputerEvent {
    override val changedPaths: Set<String> = buildSet {
        filePaths.forEach { add("filesystem.filesByPath.$it") }
        directoryPaths.forEach { add("filesystem.directoriesByPath.$it") }
    }
    override val deltaKeys: Set<String> = setOf("filesystem")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        var filesystem = state.filesystem
        filePaths.forEach { filePath ->
            filesystem = filesystem.deleteFileByPath(filePath)
        }
        directoryPaths
            .sortedByDescending { it.length }
            .forEach { directoryPath ->
                filesystem = filesystem.deleteDirectoryTree(directoryPath)
            }
        return state.copy(
            version = nextVersion,
            filesystem = filesystem,
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(filesystem = state.filesystem)
    }
}

@Serializable
@SerialName("file_compiled")
data class FileCompiledEvent(
    val sourceFilePath: String,
    val remainingSourceFile: StoredFile?,
    val compiledFile: StoredFile,
    val pettyCashDelta: Double,
    val scriptFamily: ScriptFamily,
    val experienceDelta: Int,
) : ComputerEvent {
    override val changedPaths: Set<String> = linkedSetOf<String>().apply {
        add("filesystem.filesByPath.$sourceFilePath")
        add("filesystem.filesByPath.${compiledFile.path}")
        add("economy.pettyCash")
        add("stats.experienceByFamily.$scriptFamily")
    }
    override val deltaKeys: Set<String> = setOf("filesystem", "economy", "stats")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        var filesystem = state.filesystem.deleteFileByPath(sourceFilePath)
        if (remainingSourceFile != null) {
            filesystem = filesystem.saveFile(remainingSourceFile)
        }
        filesystem = filesystem.saveFile(compiledFile)
        return state.copy(
            version = nextVersion,
            filesystem = filesystem,
            economy = state.economy.copy(
                pettyCash = state.economy.pettyCash + pettyCashDelta,
            ),
            stats = state.stats.adjustSkillExperience(scriptFamily, experienceDelta),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            filesystem = state.filesystem,
            economy = state.economy,
            stats = state.stats,
        )
    }
}

@Serializable
@SerialName("file_decompiled")
data class FileDecompiledEvent(
    val sourceFilePath: String,
    val remainingSourceFile: StoredFile?,
    val decompiledFile: StoredFile,
    val pettyCashDelta: Double,
    val scriptFamily: ScriptFamily,
    val experienceDelta: Int,
) : ComputerEvent {
    override val changedPaths: Set<String> = linkedSetOf<String>().apply {
        add("filesystem.filesByPath.$sourceFilePath")
        add("filesystem.filesByPath.${decompiledFile.path}")
        add("economy.pettyCash")
        add("stats.experienceByFamily.$scriptFamily")
    }
    override val deltaKeys: Set<String> = setOf("filesystem", "economy", "stats")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        var filesystem = state.filesystem.deleteFileByPath(sourceFilePath)
        if (remainingSourceFile != null) {
            filesystem = filesystem.saveFile(remainingSourceFile)
        }
        filesystem = filesystem.saveFile(decompiledFile)
        return state.copy(
            version = nextVersion,
            filesystem = filesystem,
            economy = state.economy.copy(
                pettyCash = state.economy.pettyCash + pettyCashDelta,
            ),
            stats = state.stats.adjustSkillExperience(scriptFamily, experienceDelta),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            filesystem = state.filesystem,
            economy = state.economy,
            stats = state.stats,
        )
    }
}

@Serializable
@SerialName("application_installed")
data class ApplicationInstalledEvent(
    val sourceFilePath: String,
    val remainingSourceFile: StoredFile?,
    val portState: PortState,
    val defaultBankPort: Int? = null,
) : ComputerEvent {
    override val changedPaths: Set<String> = linkedSetOf<String>().apply {
        add("filesystem.filesByPath.$sourceFilePath")
        add("ports.${portState.number}")
        if (defaultBankPort != null) {
            add("economy.defaultBankPort")
        }
    }
    override val deltaKeys: Set<String> = linkedSetOf<String>().apply {
        add("filesystem")
        add("ports")
        if (defaultBankPort != null) {
            add("economy")
        }
    }

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        var filesystem = state.filesystem.deleteFileByPath(sourceFilePath)
        if (remainingSourceFile != null) {
            filesystem = filesystem.saveFile(remainingSourceFile)
        }
        val updatedEconomy = if (defaultBankPort != null) {
            state.economy.copy(defaultBankPort = defaultBankPort)
        } else {
            state.economy
        }
        val updatedPort = portState.copy(
            defaultPort = updatedEconomy.defaultBankPort == portState.number,
        )
        return state.copy(
            version = nextVersion,
            filesystem = filesystem,
            economy = updatedEconomy,
            ports = state.ports.upsertPort(updatedPort, updatedEconomy.defaultBankPort),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            filesystem = state.filesystem,
            economy = state.economy,
            ports = state.ports,
        )
    }
}

@Serializable
@SerialName("equipment_installed")
data class EquipmentInstalledEvent(
    val sourceFilePath: String,
    val remainingSourceFile: StoredFile?,
    val slot: EquipmentSlot,
    val equipment: InstalledEquipment,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf(
        "filesystem.filesByPath.$sourceFilePath",
        "hardware.equipmentSlots.$slot",
    )
    override val deltaKeys: Set<String> = setOf("filesystem", "hardware")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        var filesystem = state.filesystem.deleteFileByPath(sourceFilePath)
        if (remainingSourceFile != null) {
            filesystem = filesystem.saveFile(remainingSourceFile)
        }
        return state.copy(
            version = nextVersion,
            filesystem = filesystem,
            hardware = state.hardware.copy(
                equipmentSlots = state.hardware.equipmentSlots + (slot to equipment),
            ),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            filesystem = state.filesystem,
            hardware = state.hardware,
        )
    }
}

@Serializable
@SerialName("firewall_installed")
data class FirewallInstalledEvent(
    val sourceFilePath: String,
    val remainingSourceFile: StoredFile?,
    val portState: PortState,
    val returnedFirewall: StoredFile? = null,
) : ComputerEvent {
    override val changedPaths: Set<String> = linkedSetOf<String>().apply {
        add("filesystem.filesByPath.$sourceFilePath")
        if (returnedFirewall != null) {
            add("filesystem.filesByPath.${returnedFirewall.path}")
        }
        add("ports.${portState.number}.installedFirewall")
    }
    override val deltaKeys: Set<String> = setOf("filesystem", "ports")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        var filesystem = state.filesystem.deleteFileByPath(sourceFilePath)
        if (remainingSourceFile != null) {
            filesystem = filesystem.saveFile(remainingSourceFile)
        }
        if (returnedFirewall != null) {
            filesystem = filesystem.addOrIncrement(returnedFirewall)
        }
        return state.copy(
            version = nextVersion,
            filesystem = filesystem,
            ports = state.ports.upsertPort(portState, state.economy.defaultBankPort),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            filesystem = state.filesystem,
            ports = state.ports,
        )
    }
}

@Serializable
sealed interface DeltaProjection

@Serializable
@SerialName("state_sections")
data class StateSectionsDeltaProjection(
    val filesystem: FilesystemState? = null,
    val economy: EconomyState? = null,
    val hardware: HardwareState? = null,
    val ports: List<PortState>? = null,
    val preferences: PreferenceState? = null,
    val stats: PlayerStatsState? = null,
) : DeltaProjection

@Serializable
@SerialName("state_summary")
data class StateSummaryDeltaProjection(
    val version: Long,
    val playerIp: String,
) : DeltaProjection

@Serializable
data class ComputerDelta(
    val gameStateId: GameStateId,
    val sequence: Long,
    val changedPaths: Set<String>,
    val deltaKeys: Set<String>,
    val projection: DeltaProjection,
)

enum class ProgramLifecycleStatus {
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

@Serializable
data class ProgramProgress(
    val message: String = "",
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
)

@Serializable
data class ProgramUpdate(
    val programId: String,
    val programType: String,
    val status: ProgramLifecycleStatus,
    val relatedStateIds: Set<GameStateId>,
    val progress: ProgramProgress = ProgramProgress(),
)

@Serializable
data class GameSessionBootstrapResult(
    val state: ComputerState,
)

@Serializable
data class ScanResponse(
    val targetIp: String,
    val openPorts: List<Int>,
)

@Serializable
data class DirectorySnapshot(
    val path: String,
    val directories: List<DirectoryEntry>,
    val files: List<StoredFile>,
)

@Serializable
data class DirectoryListingResponse(
    val stateId: GameStateId,
    val path: String,
    val directories: List<DirectoryEntry>,
    val files: List<StoredFile>,
    val version: Long,
)

@Serializable
data class SecondaryDirectoryListingResponse(
    val requesterStateId: GameStateId,
    val targetStateId: GameStateId,
    val portNumber: Int,
    val path: String,
    val directories: List<DirectoryEntry>,
    val files: List<StoredFile>,
    val version: Long,
)

@Serializable
data class FileContentsResponse(
    val stateId: GameStateId,
    val file: StoredFile?,
    val version: Long,
)

@Serializable
data class MutationAcceptedResponse(
    val stateId: GameStateId,
    val version: Long,
    val message: String,
)

@Serializable
data class CompileFileResponse(
    val stateId: GameStateId,
    val compiledFile: StoredFile,
    val pettyCashAfter: Double,
    val experienceAfter: Int,
    val version: Long,
)

@Serializable
data class DecompileFileResponse(
    val stateId: GameStateId,
    val decompiledFile: StoredFile,
    val pettyCashAfter: Double,
    val experienceAfter: Int,
    val version: Long,
)

@Serializable
data class InstallApplicationResponse(
    val stateId: GameStateId,
    val portNumber: Int,
    val installedApplication: InstalledApplication,
    val defaultBankPort: Int?,
    val version: Long,
)

@Serializable
data class InstallEquipmentResponse(
    val stateId: GameStateId,
    val slot: EquipmentSlot,
    val equipment: InstalledEquipment,
    val version: Long,
)

@Serializable
data class InstallFirewallResponse(
    val stateId: GameStateId,
    val portNumber: Int,
    val installedFirewall: InstalledFirewall,
    val returnedFirewall: StoredFile?,
    val version: Long,
)

@Serializable
data class BankTransactionResponse(
    val stateId: GameStateId,
    val operation: String,
    val portNumber: Int,
    val requestedAmount: Double,
    val appliedAmount: Double,
    val pettyCashAfter: Double,
    val bankMoneyAfter: Double,
    val version: Long,
)

@Serializable
data class TransferResponse(
    val sourceStateId: GameStateId,
    val targetStateId: GameStateId,
    val portNumber: Int,
    val requestedAmount: Double,
    val appliedAmount: Double,
    val sourcePettyCashAfter: Double,
    val targetPettyCashAfter: Double,
    val sourceVersion: Long,
    val targetVersion: Long,
)

@Serializable
data class SellFileResponse(
    val stateId: GameStateId,
    val file: StoredFile,
    val version: Long,
)

@Serializable
data class SellFileMultiResponse(
    val stateId: GameStateId,
    val storeStateId: GameStateId,
    val soldFiles: List<StoredFile>,
    val creditedAmount: Double,
    val version: Long,
)

@Serializable
data class PurchaseResponse(
    val buyerStateId: GameStateId,
    val sellerStateId: GameStateId,
    val revenueTargetStateId: GameStateId,
    val purchasedFile: StoredFile,
    val fulfilledQuantity: Int,
    val totalPrice: Double,
    val buyerVersion: Long,
    val sellerVersion: Long,
    val revenueTargetVersion: Long,
)

@Serializable
data class SetPreferencePayload(
    val key: String,
    val value: String,
)

@Serializable
data class SetPreferenceCommandResponse(
    val key: String,
    val value: String,
    val version: Long,
)

data class CommandLifetime(val timeout: Duration) {
    companion object {
        val defaultFireAndForget: CommandLifetime = CommandLifetime(5.seconds)
        val defaultRequest: CommandLifetime = CommandLifetime(30.seconds)
        val defaultProgram: CommandLifetime = CommandLifetime(60.seconds)
    }
}

fun ComputerState.applyEvents(events: List<ComputerEvent>): ComputerState {
    var state = this
    events.forEach { event ->
        state = event.applyTo(state, state.version + 1)
    }
    return state
}

fun buildComputerDelta(
    stateId: GameStateId,
    updatedState: ComputerState,
    events: List<ComputerEvent>,
): ComputerDelta {
    val changedPaths = events.flatMapTo(linkedSetOf()) { it.changedPaths }
    val deltaKeys = events.flatMapTo(linkedSetOf()) { it.deltaKeys }
    val projection = when {
        events.isEmpty() -> StateSummaryDeltaProjection(
            version = updatedState.version,
            playerIp = updatedState.identity.playerIp,
        )

        events.distinctBy { it::class }.size == 1 -> events.last().toProjection(updatedState)

        else -> StateSummaryDeltaProjection(
            version = updatedState.version,
            playerIp = updatedState.identity.playerIp,
        )
    }
    return ComputerDelta(
        gameStateId = stateId,
        sequence = updatedState.version,
        changedPaths = changedPaths,
        deltaKeys = deltaKeys,
        projection = projection,
    )
}

fun normalizeDirectoryPath(
    rawPath: String?,
    currentPath: String = "/",
): String {
    val candidate = rawPath?.trim().takeUnless { it.isNullOrBlank() } ?: currentPath
    val normalized = candidate.replace('\\', '/')
    val parts = normalized.split('/')
    val resolved = mutableListOf<String>()
    parts.forEach { part ->
        when {
            part.isBlank() || part == "." -> Unit
            part == ".." -> if (resolved.isNotEmpty()) {
                resolved.removeLast()
            }
            else -> resolved += part
        }
    }
    return if (resolved.isEmpty()) "/" else resolved.joinToString(prefix = "/", separator = "/")
}

fun buildFilePath(directoryPath: String, fileName: String): String {
    val normalizedDirectory = normalizeDirectoryPath(directoryPath, "/")
    val cleanName = fileName.trim().removePrefix("/")
    return if (normalizedDirectory == "/") {
        "/$cleanName"
    } else {
        "$normalizedDirectory/$cleanName"
    }
}

fun parentDirectoryOf(path: String): String {
    val normalized = normalizeDirectoryPath(path, "/")
    if (normalized == "/") {
        return "/"
    }
    val lastSlash = normalized.lastIndexOf('/')
    return if (lastSlash <= 0) "/" else normalized.substring(0, lastSlash)
}

fun fileNameOf(path: String): String = normalizeDirectoryPath(path, "/").substringAfterLast('/')

fun defaultDirectoriesByPath(): Map<String, DirectoryEntry> = linkedMapOf(
    "/" to DirectoryEntry(path = "/", name = "/"),
)

fun FilesystemState.ensureDirectory(path: String): FilesystemState {
    val normalizedPath = normalizeDirectoryPath(path, currentPath)
    var updatedDirectories = directoriesByPath.toMutableMap()
    var cursor = "/"
    if (!updatedDirectories.containsKey("/")) {
        updatedDirectories["/"] = DirectoryEntry("/", "/")
    }
    if (normalizedPath != "/") {
        normalizedPath.removePrefix("/").split('/').filter { it.isNotBlank() }.forEach { segment ->
            cursor = if (cursor == "/") "/$segment" else "$cursor/$segment"
            updatedDirectories.putIfAbsent(cursor, DirectoryEntry(path = cursor, name = segment))
        }
    }
    return copy(directoriesByPath = updatedDirectories.toSortedMap())
}

fun FilesystemState.saveFile(file: StoredFile): FilesystemState {
    val normalizedPath = buildFilePath(parentDirectoryOf(file.path), file.name)
    val normalizedFile = file.copy(
        path = normalizedPath,
        name = file.name.trim(),
    )
    val withDirectory = ensureDirectory(parentDirectoryOf(normalizedPath))
    return withDirectory.copy(
        filesByPath = withDirectory.filesByPath + (normalizedPath to normalizedFile),
    )
}

fun FilesystemState.addOrIncrement(file: StoredFile): FilesystemState {
    val existing = filesByPath[file.path]
    return if (existing == null) {
        saveFile(file)
    } else {
        saveFile(existing.copy(quantity = existing.quantity + max(file.quantity, 1)))
    }
}

fun FilesystemState.deleteFileByPath(filePath: String): FilesystemState {
    val normalizedPath = buildFilePath(parentDirectoryOf(filePath), fileNameOf(filePath))
    return copy(filesByPath = filesByPath - normalizedPath)
}

fun FilesystemState.deleteDirectoryTree(path: String): FilesystemState {
    val normalizedPath = normalizeDirectoryPath(path, currentPath)
    if (normalizedPath == "/") {
        return copy(
            currentPath = "/",
            directoriesByPath = defaultDirectoriesByPath(),
            filesByPath = emptyMap(),
        )
    }
    val remainingDirectories = directoriesByPath.filterKeys { key ->
        key != normalizedPath && !key.startsWith("$normalizedPath/")
    }.toMutableMap()
    if (!remainingDirectories.containsKey("/")) {
        remainingDirectories["/"] = DirectoryEntry("/", "/")
    }
    val remainingFiles = filesByPath.filterKeys { key ->
        !key.startsWith("$normalizedPath/")
    }
    val updatedCurrentPath = if (
        currentPath == normalizedPath || currentPath.startsWith("$normalizedPath/")
    ) {
        parentDirectoryOf(normalizedPath)
    } else {
        currentPath
    }
    return copy(
        currentPath = updatedCurrentPath,
        directoriesByPath = remainingDirectories.toSortedMap(),
        filesByPath = remainingFiles,
    )
}

fun List<PortState>.upsertPort(
    port: PortState,
    defaultBankPort: Int?,
): List<PortState> {
    val updated = associateBy { it.number }.toMutableMap()
    updated[port.number] = port
    return updated.values
        .map { existing ->
            existing.copy(defaultPort = defaultBankPort == existing.number)
        }
        .sortedBy { it.number }
}

fun PlayerStatsState.adjustSkillExperience(
    family: ScriptFamily,
    delta: Int,
): PlayerStatsState {
    val current = experienceByFamily[family] ?: 0
    val next = max(0, current + delta)
    return copy(experienceByFamily = experienceByFamily + (family to next))
}

private fun RuntimeState.withMutationVersion(version: Long): RuntimeState {
    return copy(lastMutationVersion = version)
}
