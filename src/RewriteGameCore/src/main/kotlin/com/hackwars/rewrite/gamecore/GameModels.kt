package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.HookValue
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
    val network: NetworkState = NetworkState(),
    val watches: WatchManagerState = WatchManagerState(),
    val filesystem: FilesystemState = FilesystemState(),
    val website: WebsiteState = WebsiteState(),
    val dailyPay: DailyPayState = DailyPayState(),
    val combat: CombatState = CombatState(),
    val quests: QuestState = QuestState(),
    val preferences: PreferenceState = PreferenceState(),
    val stats: PlayerStatsState = PlayerStatsState(),
    val logs: LogState = LogState(),
    val runtime: RuntimeState = RuntimeState(),
) {
    companion object {
        fun empty(
            id: GameStateId,
            playFabId: String = "",
            playerIp: String = id.value,
            displayName: String = "",
            isNpc: Boolean = false,
        ): ComputerState = ComputerState(
            id = id,
            identity = ComputerIdentity(
                playFabId = playFabId,
                playerIp = playerIp,
                displayName = displayName,
                isNpc = isNpc,
            ),
            dailyPay = DailyPayState(revenueTargetStateId = id),
        )
    }
}

@Serializable
data class ComputerIdentity(
    val playFabId: String = "",
    val playerIp: String = "",
    val displayName: String = "",
    val isNpc: Boolean = false,
    val lastLoginAtEpochMillis: Long? = null,
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
    val watchCapacityBoost: Int = 0,
    val freezeImmune: Boolean = false,
    val destroyWatchesImmune: Boolean = false,
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
data class FirewallCombatProfile(
    val bankDamageModifier: Double = 1.0,
    val ftpDamageModifier: Double = 1.0,
    val httpDamageModifier: Double = 1.0,
    val attackDamageModifier: Double = 1.0,
    val redirectDamageModifier: Double = 1.0,
    val attackBackDamage: Double = 0.0,
)

@Serializable
data class FirewallActionProfile(
    val emptyPettyCashFailChance: Double = 0.0,
    val emptyPettyCashReductionMultiplier: Double = 1.0,
    val stealFileFailChance: Double = 0.0,
    val installScriptFailChance: Double = 0.0,
    val changeDailyPayFailChance: Double = 0.0,
    val changeDailyPayReductionMultiplier: Double = 1.0,
)

@Serializable
data class MaliciousProgramConfig(
    val targetIp: String? = null,
    val pettyCashTarget: Double = 0.0,
)

@Serializable
data class InstalledApplication(
    val name: String,
    val kind: ApplicationKind = ApplicationKind.GENERIC,
    val maker: String = "",
    val binaryPath: String = "",
    val cpuCost: Double = 0.0,
    val banking: Boolean = false,
    val scriptBundle: ProgramScriptBundle? = null,
    val maliciousConfig: MaliciousProgramConfig = MaliciousProgramConfig(),
)

@Serializable
data class InstalledFirewall(
    val name: String,
    val kind: FirewallKind = FirewallKind.CUSTOM,
    val maker: String = "",
    val binaryPath: String = "",
    val strength: Int = 0,
    val cpuCost: Double = 0.0,
    val combatProfile: FirewallCombatProfile = FirewallCombatProfile(),
    val actionProfile: FirewallActionProfile = FirewallActionProfile(),
)

@Serializable
data class PortState(
    val number: Int,
    val type: String = "",
    val enabled: Boolean = true,
    val defaultPort: Boolean = false,
    val dummy: Boolean = false,
    val attacking: Boolean = false,
    val health: Double = 100.0,
    val freezeExpiresAtEpochMillis: Long? = null,
    val note: String = "",
    val maxCpuCost: Double = 0.0,
    val installedApplication: InstalledApplication? = null,
    val installedFirewall: InstalledFirewall? = null,
)

@Serializable
enum class NpcCategory {
    REGULAR,
    QUEST,
    MINING,
    STORE,
}

@Serializable
data class NpcDirectoryEntry(
    val stateId: GameStateId,
    val displayName: String,
    val title: String = "",
    val category: NpcCategory,
    val commodity: String? = null,
)

@Serializable
data class NetworkState(
    val currentNetworkName: String = ROOT_NETWORK_NAME,
    val storeStateId: GameStateId? = null,
    val allowedNetworks: Set<String> = emptySet(),
    val lastNetworkSwitchAtEpochMillis: Long = 0L,
    val regularNpcs: List<NpcDirectoryEntry> = emptyList(),
    val questNpcs: List<NpcDirectoryEntry> = emptyList(),
    val miningNpcs: List<NpcDirectoryEntry> = emptyList(),
    val storeNpcs: List<NpcDirectoryEntry> = emptyList(),
)

@Serializable
data class WatchManagerState(
    val watches: List<InstalledWatch> = emptyList(),
)

@Serializable
enum class WatchKind(val legacyCode: Int) {
    HEALTH(0),
    PETTY_CASH(1),
    SCAN(2);

    companion object {
        fun fromLegacyCode(code: Int): WatchKind? = entries.firstOrNull { it.legacyCode == code }
    }
}

@Serializable
data class InstalledWatch(
    val kind: WatchKind,
    val enabled: Boolean = false,
    val note: String = "",
    val cpuCost: Double = 0.0,
    val quantityThreshold: Double = 0.0,
    val baselineQuantity: Double = 0.0,
    val installPort: Int = 0,
    val searchFirewallType: Int = 0,
    val observedPorts: List<Int> = emptyList(),
    val contents: String = "",
    val scriptBundle: ProgramScriptBundle? = null,
    val compiledBinary: CompiledBinaryMetadata? = null,
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
    SAVE_DATA,
    BOUNTY,
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
    SCANNING,
    FIREWALL,
    REDIRECT,
    HTTP,
    WATCH,
}

@Serializable
enum class ProgramScriptSlot {
    DEPOSIT,
    WITHDRAW,
    TRANSFER,
    INITIALIZE,
    CONTINUE,
    FINALIZE,
    FIRE,
    PUT,
    GET,
    ENTER,
    EXIT,
    SUBMIT,
}

@Serializable
data class ProgramScriptBundle(
    val family: ScriptFamily = ScriptFamily.GENERAL,
    val scriptsBySlot: Map<ProgramScriptSlot, String> = emptyMap(),
) {
    fun script(slot: ProgramScriptSlot): String = scriptsBySlot[slot].orEmpty()
}

@Serializable
data class CompiledBinaryMetadata(
    val scriptFamily: ScriptFamily = ScriptFamily.GENERAL,
    val outputName: String = "",
    val applicationKind: ApplicationKind? = null,
    val firewallKind: FirewallKind? = null,
    val firewallCombatProfile: FirewallCombatProfile? = null,
    val firewallActionProfile: FirewallActionProfile? = null,
    val equipmentSlot: EquipmentSlot? = null,
    val bankingApplication: Boolean = false,
    val strength: Int = 0,
    val experienceAward: Double = 1.0,
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
    val scriptBundle: ProgramScriptBundle? = null,
    val saveMetadata: SaveFileMetadata? = null,
    val bountyMetadata: BountyMetadata? = null,
)

@Serializable
data class WebsiteState(
    val title: String = "",
    val body: String = "",
    val voteCount: Int = 0,
    val votesAvailable: Int = 0,
    val storeRevenueTargetStateId: GameStateId? = null,
)

@Serializable
data class DailyPayState(
    val baseAmount: Double = 1000.0,
    val reductionMultiplier: Double = 1.0,
    val revenueTargetStateId: GameStateId? = null,
    val lastPaidAtEpochMillis: Long = 0L,
    val inactive: Boolean = false,
    val lastBountyHttpStateId: GameStateId? = null,
)

@Serializable
data class CombatState(
    val activeAttacksBySourcePort: Map<Int, AttackSessionState> = emptyMap(),
    val incomingAttacksByTargetPort: Map<Int, IncomingAttackState> = emptyMap(),
)

@Serializable
data class AttackScriptReference(
    val folder: String,
    val name: String,
)

@Serializable
data class AttackLoadout(
    val secondaryPorts: List<Int> = emptyList(),
    val maliciousScripts: List<AttackScriptReference?> = emptyList(),
    val extraInfo: List<HookValue> = emptyList(),
)

@Serializable
enum class AttackMode {
    DIRECT,
    ZOMBIE,
}

@Serializable
data class AttackSessionState(
    val programId: String,
    val sourcePort: Int,
    val targetStateId: GameStateId,
    val targetPort: Int,
    val attackMode: AttackMode = AttackMode.DIRECT,
    val controllerStateId: GameStateId? = null,
    val authorizedZombieStateId: GameStateId? = null,
    val targetView: AttackTargetView = AttackTargetView(),
    val targetCyclePorts: List<Int> = emptyList(),
    val targetCycleCursor: Int = 0,
    val windowHandle: Int = 0,
    val secondaryPorts: List<Int> = emptyList(),
    val maliciousScripts: List<AttackScriptReference?> = emptyList(),
    val extraInfo: List<HookValue> = emptyList(),
    val startedAtEpochMillis: Long = 0L,
    val iterationCount: Int = 0,
)

@Serializable
data class IncomingAttackState(
    val attackerStateId: GameStateId = GameStateId(""),
    val attackerSourcePort: Int = 0,
    val targetPort: Int = 0,
    val startedAtEpochMillis: Long = 0L,
    val windowHandle: Int = 0,
)

@Serializable
data class AttackTargetView(
    val targetStateId: GameStateId = GameStateId(""),
    val targetPort: Int = 0,
    val health: Double = 0.0,
    val pettyCash: Double = 0.0,
    val cpuCost: Double = 0.0,
    val watchPresent: Boolean = false,
    val npc: Boolean = false,
    val lastAppliedDamage: Double = 0.0,
    val completed: Boolean = false,
)

@Serializable
data class QuestState(
    val activeQuestsById: Map<String, ActiveQuestProgress> = emptyMap(),
    val completedQuestIds: List<String> = emptyList(),
    val lastClueDataByIp: Map<String, String> = emptyMap(),
)

fun QuestState.hasCompletedQuest(questId: String): Boolean = completedQuestIds.contains(questId)

fun QuestState.recordTask(questId: String, taskName: String): QuestState {
    if (hasCompletedQuest(questId)) {
        return this
    }
    val quest = activeQuestsById[questId] ?: return this
    val updatedQuest = quest.copy(
        tasksByName = quest.tasksByName + (taskName to QuestTaskProgress(completed = true, note = "")),
    )
    return copy(activeQuestsById = activeQuestsById + (questId to updatedQuest))
}

fun QuestState.storeClueData(targetIp: String, data: String): QuestState {
    return copy(lastClueDataByIp = lastClueDataByIp + (targetIp to data))
}

@Serializable
data class ActiveQuestProgress(
    val questId: String,
    val label: String = "",
    val tasksByName: Map<String, QuestTaskProgress> = emptyMap(),
)

@Serializable
data class QuestTaskProgress(
    val completed: Boolean = false,
    val note: String = "",
)

@Serializable
data class SaveFileMetadata(
    val valuesByKey: Map<String, HookValue> = emptyMap(),
)

@Serializable
data class BountyMetadata(
    val type: Int,
    val target: String,
    val iterationsRemaining: Int,
    val reward: Double,
    val bountySourceStateId: GameStateId,
    val requiredMaker: String = "",
    val requiredScriptName: String = "",
    val anonymous: Boolean = false,
)

object BountyTypes {
    const val SCAN: Int = 0
    const val KILL: Int = 1
    const val INSTALL: Int = 2
    const val VOTE: Int = 3
    const val CHANGE: Int = 4
    const val DESTROY_WATCH: Int = 5

    fun isSupported(type: Int): Boolean {
        return type in SCAN..DESTROY_WATCH
    }

    fun displayName(type: Int): String = when (type) {
        SCAN -> "Scan"
        KILL -> "Kill"
        INSTALL -> "Install"
        VOTE -> "Vote"
        CHANGE -> "Change HTTP"
        DESTROY_WATCH -> "Destroy Watch"
        else -> "Unknown"
    }
}

@Serializable
data class PreferenceState(
    val values: Map<String, String> = emptyMap(),
)

@Serializable
data class ComputerLogEntry(
    val createdAtEpochMillis: Long,
    val renderedLine: String,
    val sourceIp: String,
)

@Serializable
data class LogState(
    val entries: List<ComputerLogEntry> = emptyList(),
) {
    fun append(entry: ComputerLogEntry, maximumEntries: Int = 50): LogState {
        val nextEntries = (entries + entry).takeLast(maximumEntries)
        return copy(entries = nextEntries)
    }

    fun deleteBySourceIp(sourceIp: String): LogState {
        return copy(entries = entries.filterNot { it.sourceIp == sourceIp })
    }

    fun replaceRenderedText(data: String, replace: String): LogState {
        return copy(
            entries = entries.map { entry ->
                entry.copy(renderedLine = entry.renderedLine.replace(data, replace))
            },
        )
    }
}

@Serializable
data class PlayerStatsState(
    val experienceByFamily: Map<ScriptFamily, Double> = emptyMap(),
    val totalLevel: Int = 0,
    val noobProtectionLevel: Int = 0,
)

@Serializable
data class RuntimeState(
    val countdownSeconds: Int = 0,
    val lastMutationVersion: Long = 0,
    val currentCpuLoad: Double = 0.0,
)

@Serializable
sealed interface ComputerEvent {
    val changedPaths: Set<String>
    val deltaKeys: Set<String>

    fun applyTo(state: ComputerState, nextVersion: Long): ComputerState

    fun toProjection(state: ComputerState): DeltaProjection
}

@Serializable
@SerialName("last_login_recorded")
data class LastLoginRecordedEvent(
    val occurredAtEpochMillis: Long,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("identity.lastLoginAtEpochMillis")
    override val deltaKeys: Set<String> = setOf("identity")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            identity = state.identity.copy(lastLoginAtEpochMillis = occurredAtEpochMillis),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(identity = state.identity)
    }
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
@SerialName("website_saved")
data class WebsiteSavedEvent(
    val title: String,
    val body: String,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("website.title", "website.body")
    override val deltaKeys: Set<String> = setOf("website")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            website = state.website.copy(
                title = title,
                body = body,
            ),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(website = state.website)
    }
}

@Serializable
@SerialName("website_votes_available_adjusted")
data class WebsiteVotesAvailableAdjustedEvent(
    val delta: Int,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("website.votesAvailable")
    override val deltaKeys: Set<String> = setOf("website")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            website = state.website.copy(
                votesAvailable = max(0, state.website.votesAvailable + delta),
            ),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(website = state.website)
    }
}

@Serializable
@SerialName("website_vote_count_adjusted")
data class WebsiteVoteCountAdjustedEvent(
    val delta: Int,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("website.voteCount")
    override val deltaKeys: Set<String> = setOf("website")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            website = state.website.copy(
                voteCount = max(0, state.website.voteCount + delta),
            ),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(website = state.website)
    }
}

@Serializable
@SerialName("daily_pay_state_updated")
data class DailyPayStateUpdatedEvent(
    private val changedPathList: Set<String>,
    val dailyPay: DailyPayState,
) : ComputerEvent {
    override val changedPaths: Set<String> = changedPathList
    override val deltaKeys: Set<String> = setOf("dailyPay")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            dailyPay = dailyPay,
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(dailyPay = state.dailyPay)
    }
}

@Serializable
@SerialName("http_experience_adjusted")
data class HttpExperienceAdjustedEvent(
    val delta: Double,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("stats.experienceByFamily.${ScriptFamily.HTTP}")
    override val deltaKeys: Set<String> = setOf("stats")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            stats = state.stats.adjustSkillExperience(ScriptFamily.HTTP, delta),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(stats = state.stats)
    }
}

@Serializable
@SerialName("host_log_appended")
data class HostLogAppendedEvent(
    val entry: ComputerLogEntry,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("logs.entries")
    override val deltaKeys: Set<String> = setOf("logs")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            logs = state.logs.append(entry),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(logs = state.logs)
    }
}

@Serializable
@SerialName("host_logs_deleted_by_source_ip")
data class HostLogsDeletedBySourceIpEvent(
    val sourceIp: String,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("logs.entries")
    override val deltaKeys: Set<String> = setOf("logs")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            logs = state.logs.deleteBySourceIp(sourceIp),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(logs = state.logs)
    }
}

@Serializable
@SerialName("host_log_rendered_text_replaced")
data class HostLogRenderedTextReplacedEvent(
    val data: String,
    val replace: String,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("logs.entries")
    override val deltaKeys: Set<String> = setOf("logs")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            logs = state.logs.replaceRenderedText(data, replace),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(logs = state.logs)
    }
}

@Serializable
@SerialName("quest_task_progress_recorded")
data class QuestTaskProgressRecordedEvent(
    val questId: String,
    val taskName: String,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("quests.activeQuestsById.$questId.tasksByName.$taskName")
    override val deltaKeys: Set<String> = setOf("quests")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            quests = state.quests.recordTask(questId, taskName),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(quests = state.quests)
    }
}

@Serializable
@SerialName("clue_data_stored")
data class ClueDataStoredEvent(
    val targetIp: String,
    val data: String,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("quests.lastClueDataByIp.$targetIp")
    override val deltaKeys: Set<String> = setOf("quests")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            quests = state.quests.storeClueData(targetIp, data),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(quests = state.quests)
    }
}

@Serializable
@SerialName("network_state_changed")
data class NetworkStateChangedEvent(
    val network: NetworkState,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("network")
    override val deltaKeys: Set<String> = setOf("network")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            network = network,
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(network = state.network)
    }
}

@Serializable
@SerialName("watch_installed")
data class WatchInstalledEvent(
    val sourceFilePath: String,
    val remainingSourceFile: StoredFile?,
    val installedWatch: InstalledWatch,
) : ComputerEvent {
    override val changedPaths: Set<String> = buildSet {
        add("filesystem.filesByPath.$sourceFilePath")
        remainingSourceFile?.let { add("filesystem.filesByPath.${it.path}") }
        add("watches.watches")
    }
    override val deltaKeys: Set<String> = setOf("filesystem", "watches")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        var filesystem = state.filesystem.deleteFileByPath(sourceFilePath)
        if (remainingSourceFile != null) {
            filesystem = filesystem.saveFile(remainingSourceFile)
        }
        return state.copy(
            version = nextVersion,
            filesystem = filesystem,
            watches = state.watches.copy(watches = state.watches.watches + installedWatch),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            filesystem = state.filesystem,
            watches = state.watches,
        )
    }
}

@Serializable
@SerialName("watch_manager_updated")
data class WatchManagerUpdatedEvent(
    private val changedPathList: Set<String>,
    private val deltaKeyList: Set<String>,
    val watches: WatchManagerState,
    val currentCpuLoad: Double,
    val includeRuntime: Boolean = false,
) : ComputerEvent {
    override val changedPaths: Set<String> = changedPathList
    override val deltaKeys: Set<String> = deltaKeyList

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            watches = watches,
            runtime = state.runtime.copy(currentCpuLoad = currentCpuLoad).withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            watches = state.watches,
            runtime = state.runtime.takeIf { includeRuntime },
        )
    }
}

@Serializable
@SerialName("skill_experience_adjusted")
data class SkillExperienceAdjustedEvent(
    val family: ScriptFamily,
    val delta: Double,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("stats.experienceByFamily.$family")
    override val deltaKeys: Set<String> = setOf("stats")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            stats = state.stats.adjustSkillExperience(family, delta),
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(stats = state.stats)
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
    val experienceDelta: Double,
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
    val experienceDelta: Double,
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
    val dailyPay: DailyPayState? = null,
) : ComputerEvent {
    override val changedPaths: Set<String> = linkedSetOf<String>().apply {
        add("filesystem.filesByPath.$sourceFilePath")
        add("ports.${portState.number}")
        if (defaultBankPort != null) {
            add("economy.defaultBankPort")
        }
        if (dailyPay != null) {
            add("dailyPay")
        }
    }
    override val deltaKeys: Set<String> = linkedSetOf<String>().apply {
        add("filesystem")
        add("ports")
        if (defaultBankPort != null) {
            add("economy")
        }
        if (dailyPay != null) {
            add("dailyPay")
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
            dailyPay = dailyPay ?: state.dailyPay,
            runtime = state.runtime.withMutationVersion(nextVersion),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return StateSectionsDeltaProjection(
            filesystem = state.filesystem,
            economy = state.economy,
            ports = state.ports,
            dailyPay = state.dailyPay.takeIf { dailyPay != null },
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
    val identity: ComputerIdentity? = null,
    val network: NetworkState? = null,
    val watches: WatchManagerState? = null,
    val filesystem: FilesystemState? = null,
    val economy: EconomyState? = null,
    val hardware: HardwareState? = null,
    val ports: List<PortState>? = null,
    val website: WebsiteState? = null,
    val dailyPay: DailyPayState? = null,
    val combat: CombatState? = null,
    val quests: QuestState? = null,
    val preferences: PreferenceState? = null,
    val stats: PlayerStatsState? = null,
    val logs: LogState? = null,
    val runtime: RuntimeState? = null,
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
sealed interface GameUiEvent

@Serializable
enum class PopupUiStyle {
    MESSAGE,
    ERROR,
}

@Serializable
@SerialName("popup")
data class PopupUiEvent(
    val message: String,
    val style: PopupUiStyle = PopupUiStyle.MESSAGE,
) : GameUiEvent

@Serializable
@SerialName("message")
data class TextMessageUiEvent(
    val message: String,
) : GameUiEvent

@Serializable
@SerialName("attack_message")
data class AttackMessageUiEvent(
    val message: String,
    val port: Int,
    val ip: String,
) : GameUiEvent

@Serializable
sealed interface TriggerSelector {
    @Serializable
    @SerialName("by_index")
    data class ByIndex(val index: Int) : TriggerSelector

    @Serializable
    @SerialName("by_note")
    data class ByNote(val note: String) : TriggerSelector
}

data class WatchTriggerIntent(
    val targetStateId: GameStateId,
    val selector: TriggerSelector,
    val sourceIp: String,
    val parameters: Map<String, HookValue>,
    val external: Boolean,
    val originCommandName: String,
    val requestId: String?,
)

@Serializable
data class GameSessionBootstrapResult(
    val state: ComputerState,
)

@Serializable
data class NetworkDirectoryRefreshResult(
    val stateId: GameStateId,
    val changed: Boolean,
    val network: NetworkState,
    val version: Long,
)

@Serializable
enum class NetworkSwitchFailureCode {
    INVALID_TARGET,
    ALREADY_ON_NETWORK,
    JAILED,
    COOLDOWN,
    DISALLOWED,
    UNKNOWN_NETWORK,
}

@Serializable
data class NetworkSwitchResponse(
    val stateId: GameStateId,
    val requestedNetworkName: String,
    val currentNetworkName: String,
    val storeStateId: GameStateId?,
    val accepted: Boolean,
    val failureCode: NetworkSwitchFailureCode? = null,
    val message: String,
    val version: Long,
)

@Serializable
enum class DefaultPortVisibility {
    UNKNOWN,
    NO,
    YES,
}

@Serializable
data class FirewallView(
    val name: String,
    val kind: FirewallKind,
    val maker: String = "",
    val strength: Int = 0,
    val cpuCost: Double = 0.0,
)

@Serializable
data class ScannedPortView(
    val number: Int,
    val type: String,
    val enabled: Boolean,
    val dummy: Boolean,
    val attacking: Boolean,
    val cpuCost: Double,
    val maxCpuCost: Double,
    val health: Double,
    val note: String,
    val defaultVisibility: DefaultPortVisibility,
    val firewall: FirewallView? = null,
)

@Serializable
enum class ScanFailureCode {
    INVALID_TARGET,
    TARGET_NOT_FOUND,
    SELF_TARGET,
    ACTIVE_BANK_REQUIRED,
    OVERHEATED,
    INSUFFICIENT_PETTY_CASH,
}

@Serializable
data class ScanResponse(
    val requesterStateId: GameStateId,
    val targetStateId: GameStateId,
    val accepted: Boolean,
    val failureCode: ScanFailureCode? = null,
    val failureMessage: String? = null,
    val chargedAmount: Double = 0.0,
    val experienceAwarded: Double = 0.0,
    val pettyCashAfter: Double? = null,
    val scanningExperienceAfter: Double? = null,
    val ports: List<ScannedPortView> = emptyList(),
    val requesterVersion: Long,
)

@Serializable
data class SearchResultEntry(
    val address: String,
    val title: String,
    val description: String,
)

@Serializable
data class SearchResultsResponse(
    val queryTerms: List<String>,
    val startIndex: Int,
    val totalSize: Int,
    val results: List<SearchResultEntry>,
)

@Serializable
enum class WatchMutationFailureCode {
    WATCH_NOT_FOUND,
    PORT_NOT_FOUND,
    MISSING_FILE,
    INVALID_FILE_TYPE,
    INVALID_WATCH_KIND,
    INSTALLED_LIMIT_REACHED,
    ACTIVE_LIMIT_REACHED,
    CPU_HEADROOM_EXCEEDED,
    OVERHEATED,
    INVALID_OBSERVED_PORTS,
}

@Serializable
data class WatchListResponse(
    val stateId: GameStateId,
    val watches: List<InstalledWatch>,
    val installedCount: Int,
    val maximumInstalledCount: Int,
    val activeCount: Int,
    val maximumActiveCount: Int,
    val currentCpuLoad: Double,
    val maximumCpuLoad: Double,
)

@Serializable
data class WatchMutationResponse(
    val stateId: GameStateId,
    val operation: String,
    val accepted: Boolean,
    val failureCode: WatchMutationFailureCode? = null,
    val message: String,
    val affectedWatchIndex: Int? = null,
    val snapshot: WatchListResponse,
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
data class TaskProgressResponse(
    val stateId: GameStateId,
    val questId: String,
    val taskName: String,
    val progress: QuestTaskProgress?,
    val changed: Boolean,
    val version: Long,
)

@Serializable
data class SaveFileRequestResponse(
    val stateId: GameStateId,
    val file: StoredFile,
    val version: Long,
)

@Serializable
data class ClueDataAcceptedResponse(
    val stateId: GameStateId,
    val targetIp: String,
    val changed: Boolean,
    val version: Long,
)

@Serializable
data class BountyCreatedResponse(
    val creatorStateId: GameStateId,
    val storeStateId: GameStateId,
    val bountyFile: StoredFile,
    val reward: Double,
    val creatorVersion: Long,
    val storeVersion: Long,
)

@Serializable
data class TriggerRequestResponse(
    val stateId: GameStateId,
    val targetStateId: GameStateId,
    val selector: TriggerSelector,
    val sourceIp: String,
    val accepted: Boolean,
    val matchedWatchIndex: Int? = null,
    val executed: Boolean = false,
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
    val experienceAfter: Double,
    val version: Long,
)

@Serializable
data class DecompileFileResponse(
    val stateId: GameStateId,
    val decompiledFile: StoredFile,
    val pettyCashAfter: Double,
    val experienceAfter: Double,
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
data class PageEditorResponse(
    val stateId: GameStateId,
    val title: String,
    val body: String,
    val version: Long,
)

@Serializable
data class SavePageResponse(
    val stateId: GameStateId,
    val title: String,
    val body: String,
    val version: Long,
)

@Serializable
data class WebsiteRenderResponse(
    val resolvedTargetStateId: GameStateId,
    val title: String,
    val body: String,
    val storeFiles: List<StoredFile>,
    val fallback: Boolean,
    val version: Long,
)

@Serializable
data class VoteResponse(
    val voterStateId: GameStateId,
    val targetStateId: GameStateId,
    val votesAvailableAfter: Int,
    val targetVoteCountAfter: Int,
    val targetHttpExperienceAfter: Double,
    val voterVersion: Long,
    val targetVersion: Long,
)

@Serializable
enum class ChangeDailyPayOutcome {
    SUCCESS,
    ALREADY_CONTROLLED,
    WRONG_PORT_TYPE,
    FIREWALL_NOOP,
    BOUNTY_GUARD,
}

@Serializable
data class ChangeDailyPayResponse(
    val actorStateId: GameStateId,
    val targetStateId: GameStateId,
    val targetPort: Int,
    val requestedRevenueTargetStateId: GameStateId,
    val accepted: Boolean,
    val outcome: ChangeDailyPayOutcome,
    val message: String,
    val reductionMultiplierAfter: Double,
    val revenueTargetStateIdAfter: GameStateId,
    val requesterHttpExperienceAfter: Double,
    val actorVersion: Long,
    val targetVersion: Long,
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
    val projections = events.map { it.toProjection(updatedState) }
    val projection = when {
        events.isEmpty() -> StateSummaryDeltaProjection(
            version = updatedState.version,
            playerIp = updatedState.identity.playerIp,
        )

        projections.all { it is StateSectionsDeltaProjection } -> {
            mergeStateSectionsProjection(projections.filterIsInstance<StateSectionsDeltaProjection>())
        }

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

private fun mergeStateSectionsProjection(
    projections: List<StateSectionsDeltaProjection>,
): StateSectionsDeltaProjection {
    fun <T> latest(selector: (StateSectionsDeltaProjection) -> T?): T? {
        return projections.mapNotNull(selector).lastOrNull()
    }
    return StateSectionsDeltaProjection(
        identity = latest { it.identity },
        network = latest { it.network },
        watches = latest { it.watches },
        filesystem = latest { it.filesystem },
        economy = latest { it.economy },
        hardware = latest { it.hardware },
        ports = latest { it.ports },
        website = latest { it.website },
        dailyPay = latest { it.dailyPay },
        combat = latest { it.combat },
        quests = latest { it.quests },
        preferences = latest { it.preferences },
        stats = latest { it.stats },
        logs = latest { it.logs },
        runtime = latest { it.runtime },
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
            val kind = existing.installedApplication?.kind
            existing.copy(
                defaultPort = when (kind) {
                    ApplicationKind.BANKING -> defaultBankPort != null && defaultBankPort == existing.number
                    else -> existing.defaultPort
                },
            )
        }
        .sortedBy { it.number }
}

fun PlayerStatsState.adjustSkillExperience(
    family: ScriptFamily,
    delta: Double,
): PlayerStatsState {
    val current = experienceByFamily[family] ?: 0.0
    val next = max(0.0, current + delta)
    return copy(experienceByFamily = experienceByFamily + (family to next))
}

fun PlayerStatsState.skillExperience(family: ScriptFamily): Double = experienceByFamily[family] ?: 0.0

fun legacyLevelForXp(experience: Double): Int {
    var level = 0
    while (level < LEGACY_XP_TABLE.lastIndex && experience > LEGACY_XP_TABLE[level]) {
        level++
    }
    return level + 1
}

private val LEGACY_XP_TABLE: IntArray = IntArray(100).also { table ->
    var xp = 83
    var xpDiff = 83
    for (index in table.indices) {
        table[index] = xp
        xpDiff = (xpDiff + xpDiff / 9.525).toInt()
        xp += xpDiff
    }
}

const val ROOT_NETWORK_NAME: String = "UGOPNet"
const val JAIL_NETWORK_NAME: String = "JuniperPenetentiary"
const val NETWORK_SWITCH_COOLDOWN_MS: Long = 180000L

internal fun RuntimeState.withMutationVersion(version: Long): RuntimeState {
    return copy(lastMutationVersion = version)
}

internal fun RuntimeState.withCpuLoad(currentCpuLoad: Double): RuntimeState {
    return copy(currentCpuLoad = currentCpuLoad)
}
