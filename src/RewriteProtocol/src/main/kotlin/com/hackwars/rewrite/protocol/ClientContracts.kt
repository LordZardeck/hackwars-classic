package com.hackwars.rewrite.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object RewriteClientJson {
    val codec: Json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray {
        return codec.encodeToString(serializer, value).encodeToByteArray()
    }

    fun <T> decode(serializer: KSerializer<T>, payload: ByteArray): T {
        return codec.decodeFromString(serializer, payload.decodeToString())
    }
}

@Serializable
data class ClientGameSnapshot(
    val id: String = "",
    val version: Long = 0,
    val identity: ClientComputerIdentity = ClientComputerIdentity(),
    val economy: ClientEconomyState = ClientEconomyState(),
    val hardware: ClientHardwareState = ClientHardwareState(),
    val ports: List<ClientPortState> = emptyList(),
    val watches: ClientWatchManagerState = ClientWatchManagerState(),
    val filesystem: ClientFilesystemState = ClientFilesystemState(),
    val network: ClientNetworkState = ClientNetworkState(),
    val website: ClientWebsiteState = ClientWebsiteState(),
    val preferences: ClientPreferenceState = ClientPreferenceState(),
    val stats: ClientPlayerStatsState = ClientPlayerStatsState(),
    val logs: ClientLogState = ClientLogState(),
    val runtime: ClientRuntimeState = ClientRuntimeState(),
)

@Serializable
data class ClientComputerIdentity(
    val playFabId: String = "",
    val playerIp: String = "",
    val displayName: String = "",
    val isNpc: Boolean = false,
    val lastLoginAtEpochMillis: Long? = null,
)

@Serializable
data class ClientFilesystemState(
    val currentPath: String = "/",
    val directoriesByPath: Map<String, ClientDirectoryEntry> = emptyMap(),
    val filesByPath: Map<String, ClientStoredFile> = emptyMap(),
)

@Serializable
enum class ClientNpcCategory {
    REGULAR,
    QUEST,
    MINING,
    STORE,
}

@Serializable
data class ClientNpcDirectoryEntry(
    val stateId: String = "",
    val displayName: String = "",
    val title: String = "",
    val category: ClientNpcCategory = ClientNpcCategory.REGULAR,
    val commodity: String? = null,
)

@Serializable
data class ClientNetworkState(
    val currentNetworkName: String = "",
    val storeStateId: String? = null,
    val allowedNetworks: Set<String> = emptySet(),
    val lastNetworkSwitchAtEpochMillis: Long = 0L,
    val regularNpcs: List<ClientNpcDirectoryEntry> = emptyList(),
    val questNpcs: List<ClientNpcDirectoryEntry> = emptyList(),
    val miningNpcs: List<ClientNpcDirectoryEntry> = emptyList(),
    val storeNpcs: List<ClientNpcDirectoryEntry> = emptyList(),
)

@Serializable
data class ClientDirectoryEntry(
    val path: String,
    val name: String,
    val description: String = "",
)

@Serializable
enum class ClientStoredFileKind {
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
enum class ClientScriptFamily {
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
enum class ClientProgramScriptSlot {
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
enum class ClientApplicationKind {
    GENERIC,
    BANKING,
    FTP,
    HTTP,
    ATTACK,
    REDIRECT,
    WATCH,
}

@Serializable
enum class ClientFirewallKind {
    NONE,
    BASIC,
    CUSTOM,
}

@Serializable
enum class ClientEquipmentSlot {
    CPU,
    MEMORY,
    STORAGE,
    PCI,
    AGP,
}

@Serializable
sealed interface ClientHookValue

@Serializable
@SerialName("int")
data class ClientIntHookValue(
    val value: Int,
) : ClientHookValue

@Serializable
@SerialName("float")
data class ClientFloatHookValue(
    val value: Double,
) : ClientHookValue

@Serializable
@SerialName("string")
data class ClientStringHookValue(
    val value: String,
) : ClientHookValue

@Serializable
@SerialName("boolean")
data class ClientBooleanHookValue(
    val value: Boolean,
) : ClientHookValue

@Serializable
@SerialName("array")
data class ClientArrayHookValue(
    val values: List<ClientHookValue> = emptyList(),
) : ClientHookValue

@Serializable
data class ClientProgramScriptBundle(
    val family: ClientScriptFamily = ClientScriptFamily.GENERAL,
    val scriptsBySlot: Map<ClientProgramScriptSlot, String> = emptyMap(),
)

@Serializable
data class ClientCompiledBinaryMetadata(
    val scriptFamily: ClientScriptFamily = ClientScriptFamily.GENERAL,
    val outputName: String = "",
    val applicationKind: ClientApplicationKind? = null,
    val firewallKind: ClientFirewallKind? = null,
    val firewallCombatProfile: ClientFirewallCombatProfile? = null,
    val firewallActionProfile: ClientFirewallActionProfile? = null,
    val equipmentSlot: ClientEquipmentSlot? = null,
    val healCostMultiplier: Double? = null,
    val healModifierDelta: Int? = null,
    val bankingApplication: Boolean = false,
    val strength: Int = 0,
    val experienceAward: Double = 1.0,
)

@Serializable
data class ClientSaveFileMetadata(
    val valuesByKey: Map<String, ClientHookValue> = emptyMap(),
)

@Serializable
data class ClientBountyMetadata(
    val type: Int,
    val target: String,
    val iterationsRemaining: Int,
    val reward: Double,
    val bountySourceStateId: String,
    val requiredMaker: String = "",
    val requiredScriptName: String = "",
    val anonymous: Boolean = false,
)

@Serializable
data class ClientStoredFile(
    val path: String,
    val name: String,
    val kind: ClientStoredFileKind = ClientStoredFileKind.TEXT,
    val contents: String = "",
    val description: String = "",
    val quantity: Int = 1,
    val maker: String = "",
    val compileCost: Double = 0.0,
    val cpuCost: Double = 0.0,
    val price: Double = 0.0,
    val compiledBinary: ClientCompiledBinaryMetadata? = null,
    val scriptBundle: ClientProgramScriptBundle? = null,
    val saveMetadata: ClientSaveFileMetadata? = null,
    val bountyMetadata: ClientBountyMetadata? = null,
)

@Serializable
data class ClientEconomyState(
    val pettyCash: Double = 0.0,
    val bankMoney: Double = 0.0,
    val commodities: List<Double> = List(5) { 0.0 },
    val defaultBankPort: Int? = null,
    val defaultRedirectPort: Int? = null,
    val commodityRespawn: List<Double> = List(5) { 0.0 },
)

@Serializable
data class ClientHardwareState(
    val cpuType: Int = 0,
    val cpuMax: Double = 0.0,
    val memoryType: Int = 0,
    val hdType: Int = 0,
    val hdQuantity: Int = 0,
    val hdMaximum: Int = 0,
    val equipmentSlots: Map<String, ClientInstalledEquipment> = emptyMap(),
)

@Serializable
data class ClientInstalledEquipment(
    val slot: String = "",
    val name: String = "",
    val maker: String = "",
    val binaryPath: String = "",
    val durability: Int = 100,
    val cpuBoost: Double = 0.0,
    val memoryBoost: Int = 0,
    val storageBoost: Int = 0,
    val watchCapacityBoost: Int = 0,
    val healCostMultiplier: Double = 1.0,
    val healModifierDelta: Int = 0,
    val freezeImmune: Boolean = false,
    val destroyWatchesImmune: Boolean = false,
)

@Serializable
data class ClientPortState(
    val number: Int = 0,
    val type: String = "",
    val enabled: Boolean = true,
    val defaultPort: Boolean = false,
    val dummy: Boolean = false,
    val attacking: Boolean = false,
    val health: Double = 100.0,
    val healCount: Int = 0,
    val weakenedAccess: ClientWeakenedPortAccessState? = null,
    val overheated: Boolean = false,
    val freezeExpiresAtEpochMillis: Long? = null,
    val note: String = "",
    val maxCpuCost: Double = 0.0,
    val installedApplication: ClientInstalledApplication? = null,
    val installedFirewall: ClientInstalledFirewall? = null,
)

@Serializable
data class ClientWatchManagerState(
    val watches: List<ClientInstalledWatch> = emptyList(),
)

@Serializable
enum class ClientWatchKind {
    HEALTH,
    PETTY_CASH,
    SCAN,
}

@Serializable
data class ClientInstalledWatch(
    val kind: ClientWatchKind,
    val enabled: Boolean = false,
    val note: String = "",
    val cpuCost: Double = 0.0,
    val quantityThreshold: Double = 0.0,
    val baselineQuantity: Double = 0.0,
    val installPort: Int = 0,
    val searchFirewallType: Int = 0,
    val observedPorts: List<Int> = emptyList(),
    val contents: String = "",
    val scriptBundle: ClientProgramScriptBundle? = null,
    val compiledBinary: ClientCompiledBinaryMetadata? = null,
)

@Serializable
data class ClientWeakenedPortAccessState(
    val actorStateId: String,
    val grantedAtEpochMillis: Long,
    val lastAccessedAtEpochMillis: Long = grantedAtEpochMillis,
)

@Serializable
data class ClientInstalledApplication(
    val name: String = "",
    val kind: String = "",
    val maker: String = "",
    val binaryPath: String = "",
    val cpuCost: Double = 0.0,
    val banking: Boolean = false,
    val maliciousConfig: ClientMaliciousProgramConfig = ClientMaliciousProgramConfig(),
)

@Serializable
data class ClientMaliciousProgramConfig(
    val targetIp: String? = null,
    val pettyCashTarget: Double = 0.0,
)

@Serializable
data class ClientInstalledFirewall(
    val name: String = "",
    val kind: String = "",
    val maker: String = "",
    val binaryPath: String = "",
    val strength: Int = 0,
    val cpuCost: Double = 0.0,
    val combatProfile: ClientFirewallCombatProfile = ClientFirewallCombatProfile(),
    val actionProfile: ClientFirewallActionProfile = ClientFirewallActionProfile(),
)

@Serializable
data class ClientFirewallCombatProfile(
    val bankDamageModifier: Double = 1.0,
    val ftpDamageModifier: Double = 1.0,
    val httpDamageModifier: Double = 1.0,
    val attackDamageModifier: Double = 1.0,
    val redirectDamageModifier: Double = 1.0,
    val attackBackDamage: Double = 0.0,
)

@Serializable
data class ClientFirewallActionProfile(
    val emptyPettyCashFailChance: Double = 0.0,
    val emptyPettyCashReductionMultiplier: Double = 1.0,
    val stealFileFailChance: Double = 0.0,
    val installScriptFailChance: Double = 0.0,
    val changeDailyPayFailChance: Double = 0.0,
    val changeDailyPayReductionMultiplier: Double = 1.0,
)

@Serializable
data class ClientWebsiteState(
    val title: String = "",
    val body: String = "",
    val voteCount: Int = 0,
    val votesAvailable: Int = 0,
    val storeRevenueTargetStateId: String? = null,
)

@Serializable
data class ClientPreferenceState(
    val values: Map<String, String> = emptyMap(),
)

@Serializable
data class ClientPlayerStatsState(
    val experienceByFamily: Map<String, Double> = emptyMap(),
    val totalLevel: Int = 0,
    val noobProtectionLevel: Int = 0,
)

@Serializable
data class ClientLogState(
    val entries: List<ClientComputerLogEntry> = emptyList(),
)

@Serializable
data class ClientComputerLogEntry(
    val createdAtEpochMillis: Long,
    val renderedLine: String,
    val sourceIp: String,
)

@Serializable
data class ClientRuntimeState(
    val countdownSeconds: Int = 0,
    val lastMutationVersion: Long = 0,
    val currentCpuLoad: Double = 0.0,
    val healCounter: Long = 0,
    val overheatStartedAtEpochMillis: Long? = null,
)

@Serializable
enum class ClientDefaultPortVisibility {
    UNKNOWN,
    NO,
    YES,
}

@Serializable
data class ClientFirewallView(
    val name: String = "",
    val kind: ClientFirewallKind = ClientFirewallKind.NONE,
    val maker: String = "",
    val strength: Int = 0,
    val cpuCost: Double = 0.0,
)

@Serializable
data class ClientScannedPortView(
    val number: Int = 0,
    val type: String = "",
    val enabled: Boolean = false,
    val dummy: Boolean = false,
    val attacking: Boolean = false,
    val cpuCost: Double = 0.0,
    val maxCpuCost: Double = 0.0,
    val health: Double = 0.0,
    val note: String = "",
    val defaultVisibility: ClientDefaultPortVisibility = ClientDefaultPortVisibility.UNKNOWN,
    val firewall: ClientFirewallView? = null,
)

@Serializable
enum class ClientScanFailureCode {
    INVALID_TARGET,
    TARGET_NOT_FOUND,
    SELF_TARGET,
    ACTIVE_BANK_REQUIRED,
    OVERHEATED,
    INSUFFICIENT_PETTY_CASH,
}

@Serializable
enum class ClientNetworkSwitchFailureCode {
    INVALID_TARGET,
    ALREADY_ON_NETWORK,
    JAILED,
    COOLDOWN,
    DISALLOWED,
    UNKNOWN_NETWORK,
}

@Serializable
sealed interface ClientGameDeltaProjection

@Serializable
@SerialName("state_sections")
data class ClientGameSectionsProjection(
    val identity: ClientComputerIdentity? = null,
    val economy: ClientEconomyState? = null,
    val hardware: ClientHardwareState? = null,
    val ports: List<ClientPortState>? = null,
    val watches: ClientWatchManagerState? = null,
    val filesystem: ClientFilesystemState? = null,
    val network: ClientNetworkState? = null,
    val website: ClientWebsiteState? = null,
    val preferences: ClientPreferenceState? = null,
    val stats: ClientPlayerStatsState? = null,
    val logs: ClientLogState? = null,
    val runtime: ClientRuntimeState? = null,
) : ClientGameDeltaProjection

@Serializable
@SerialName("state_summary")
data class ClientGameStateSummaryProjection(
    val version: Long,
    val playerIp: String,
) : ClientGameDeltaProjection

@Serializable
data class ClientProgramUpdate(
    val programId: String,
    val programType: String,
    val status: ClientProgramLifecycleStatus,
    val relatedStateIds: Set<String> = emptySet(),
    val progress: ClientProgramProgress = ClientProgramProgress(),
)

@Serializable
enum class ClientProgramLifecycleStatus {
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

@Serializable
data class ClientProgramProgress(
    val message: String = "",
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
)

@Serializable
data class ClientDepositPayload(
    val amount: Double,
    val ip: String,
    val port: Int,
)

@Serializable
data class ClientWithdrawPayload(
    val amount: Double,
    val ip: String,
    val port: Int,
)

@Serializable
data class ClientTransferPayload(
    val amount: Double,
    val ip: String,
    val targetIp: String,
    val port: Int,
)

@Serializable
data class ClientBankTransactionResponse(
    val stateId: String,
    val operation: String,
    val portNumber: Int,
    val requestedAmount: Double,
    val appliedAmount: Double,
    val pettyCashAfter: Double,
    val bankMoneyAfter: Double,
    val version: Long,
)

@Serializable
data class ClientTransferResponse(
    val sourceStateId: String,
    val targetStateId: String,
    val portNumber: Int,
    val requestedAmount: Double,
    val appliedAmount: Double,
    val sourcePettyCashAfter: Double,
    val targetPettyCashAfter: Double,
    val sourceVersion: Long,
    val targetVersion: Long,
)

@Serializable
data class ClientChangeNetworkPayload(
    val ip: String,
    val network: String? = null,
)

@Serializable
data class ClientNetworkSwitchResponse(
    val stateId: String,
    val requestedNetworkName: String,
    val currentNetworkName: String,
    val storeStateId: String? = null,
    val accepted: Boolean,
    val failureCode: ClientNetworkSwitchFailureCode? = null,
    val message: String,
    val version: Long,
)

@Serializable
data class ClientRequestScanPayload(
    val ip: String,
    @SerialName("targetIP")
    val targetIp: String? = null,
)

@Serializable
data class ClientScanResponse(
    val requesterStateId: String,
    val targetStateId: String,
    val accepted: Boolean,
    val failureCode: ClientScanFailureCode? = null,
    val failureMessage: String? = null,
    val chargedAmount: Double = 0.0,
    val experienceAwarded: Double = 0.0,
    val pettyCashAfter: Double? = null,
    val scanningExperienceAfter: Double? = null,
    val ports: List<ClientScannedPortView> = emptyList(),
    val requesterVersion: Long,
)

@Serializable
data class ClientMakeBountyPayload(
    val sourceIp: String,
    val anonymous: Boolean = false,
    val target: String? = null,
    val type: Int = 0,
    val fname: String? = null,
    val folder: String? = null,
    val iterations: Int = 0,
    val reward: Double = 0.0,
)

@Serializable
data class ClientBountyCreatedResponse(
    val creatorStateId: String,
    val storeStateId: String,
    val bountyFile: ClientStoredFile,
    val reward: Double,
    val creatorVersion: Long,
    val storeVersion: Long,
)

@Serializable
data class ClientRequestDirectoryPayload(
    val path: String? = null,
)

@Serializable
data class ClientRequestFilePayload(
    val path: String? = null,
    val name: String,
)

@Serializable
data class ClientHealPortPayload(
    val ip: String,
    val port: Int,
)

@Serializable
data class ClientInstallApplicationPayload(
    val path: String? = null,
    val name: String,
    val portNumber: Int,
)

@Serializable
data class ClientInstallFirewallPayload(
    val path: String? = null,
    val name: String,
    val portNumber: Int,
)

@Serializable
data class ClientInstallEquipmentPayload(
    val path: String? = null,
    val name: String,
    val slot: ClientEquipmentSlot,
)

@Serializable
data class ClientFetchWatchesPayload(
    val ip: String,
)

@Serializable
data class ClientInstallWatchPayload(
    val ip: String,
    val path: String? = null,
    val name: String? = null,
    val type: Int? = null,
    val port: Int? = null,
)

@Serializable
data class ClientSetWatchNotePayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    val note: String? = null,
)

@Serializable
data class ClientSetWatchOnOffPayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    val state: Boolean? = null,
)

@Serializable
data class ClientSetWatchQuantityPayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    val quantity: Double? = null,
)

@Serializable
data class ClientSetWatchObservedPortsPayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    val observedPorts: List<Int> = emptyList(),
)

@Serializable
data class ClientSetWatchSearchFirewallPayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    @SerialName("searchFireWall")
    val searchFirewall: Int? = null,
)

@Serializable
data class ClientChangeWatchPortPayload(
    val ip: String,
    @SerialName("watchId")
    val watchId: Int? = null,
    @SerialName("portId")
    val portId: Int? = null,
)

@Serializable
data class ClientChangeWatchTypePayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    @SerialName("portID")
    val newType: Int? = null,
)

@Serializable
data class ClientDeleteWatchPayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
)

@Serializable
data class ClientSaveFilePayload(
    val path: String? = null,
    val file: ClientStoredFile,
)

@Serializable
data class ClientCompileFilePayload(
    val path: String? = null,
    val name: String,
)

@Serializable
data class ClientDecompileFilePayload(
    val path: String? = null,
    val name: String,
)

@Serializable
data class ClientRequestWebpagePayload(
    val targetIp: String,
    val sourceIp: String,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class ClientSubmitWebpagePayload(
    val targetIp: String? = null,
    val sourceIp: String,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class ClientExitWebpagePayload(
    val targetIp: String? = null,
    val sourceIp: String,
)

@Serializable
data class ClientRequestPagePayload(
    val ip: String,
)

@Serializable
data class ClientSavePagePayload(
    val ip: String,
    val title: String,
    val body: String,
)

@Serializable
data class ClientVotePayload(
    val targetIp: String? = null,
    val sourceIp: String,
)

@Serializable
data class ClientRequestPurchasePayload(
    val targetIp: String,
    val sourceIp: String,
    val fileName: String,
    val quantity: Int,
)

@Serializable
data class ClientDirectoryListingResponse(
    val stateId: String,
    val path: String,
    val directories: List<ClientDirectoryEntry> = emptyList(),
    val files: List<ClientStoredFile> = emptyList(),
    val version: Long,
)

@Serializable
data class ClientFileContentsResponse(
    val stateId: String,
    val file: ClientStoredFile? = null,
    val version: Long,
)

@Serializable
data class ClientMutationAcceptedResponse(
    val stateId: String,
    val version: Long,
    val message: String,
)

@Serializable
data class ClientHealPortResponse(
    val stateId: String,
    val portNumber: Int,
    val accepted: Boolean,
    val outcome: ClientHealPortOutcome,
    val message: String,
    val chargedAmount: Double,
    val pettyCashAfter: Double,
    val healthAfter: Double? = null,
    val healCountAfter: Int? = null,
    val version: Long,
)

@Serializable
enum class ClientHealPortOutcome {
    SUCCESS,
    PORT_NOT_FOUND,
    INVALID_PORT,
    ACTIVE_BANK_REQUIRED,
    OVERHEATED,
    WEAKENED,
    HEAL_LIMIT_REACHED,
    INSUFFICIENT_PETTY_CASH,
}

@Serializable
data class ClientCompileFileResponse(
    val stateId: String,
    val compiledFile: ClientStoredFile,
    val pettyCashAfter: Double,
    val experienceAfter: Double,
    val version: Long,
)

@Serializable
data class ClientDecompileFileResponse(
    val stateId: String,
    val decompiledFile: ClientStoredFile,
    val pettyCashAfter: Double,
    val experienceAfter: Double,
    val version: Long,
)

@Serializable
data class ClientInstallApplicationResponse(
    val stateId: String,
    val portNumber: Int,
    val installedApplication: ClientInstalledApplication,
    val defaultBankPort: Int? = null,
    val version: Long,
)

@Serializable
data class ClientInstallEquipmentResponse(
    val stateId: String,
    val slot: ClientEquipmentSlot,
    val equipment: ClientInstalledEquipment,
    val version: Long,
)

@Serializable
data class ClientInstallFirewallResponse(
    val stateId: String,
    val portNumber: Int,
    val installedFirewall: ClientInstalledFirewall,
    val returnedFirewall: ClientStoredFile? = null,
    val version: Long,
)

@Serializable
data class ClientWatchListResponse(
    val stateId: String,
    val watches: List<ClientInstalledWatch> = emptyList(),
    val installedCount: Int,
    val maximumInstalledCount: Int,
    val activeCount: Int,
    val maximumActiveCount: Int,
    val currentCpuLoad: Double,
    val maximumCpuLoad: Double,
)

@Serializable
enum class ClientWatchMutationFailureCode {
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
data class ClientWatchMutationResponse(
    val stateId: String,
    val operation: String,
    val accepted: Boolean,
    val failureCode: ClientWatchMutationFailureCode? = null,
    val message: String,
    val affectedWatchIndex: Int? = null,
    val snapshot: ClientWatchListResponse,
)

@Serializable
data class ClientWebsiteRenderResponse(
    val resolvedTargetStateId: String,
    val title: String,
    val body: String,
    val storeFiles: List<ClientStoredFile> = emptyList(),
    val fallback: Boolean = false,
    val version: Long,
)

@Serializable
data class ClientPageEditorResponse(
    val stateId: String,
    val title: String,
    val body: String,
    val version: Long,
)

@Serializable
data class ClientSavePageResponse(
    val stateId: String,
    val title: String,
    val body: String,
    val version: Long,
)

@Serializable
data class ClientPurchaseResponse(
    val buyerStateId: String,
    val sellerStateId: String,
    val revenueTargetStateId: String,
    val purchasedFile: ClientStoredFile,
    val fulfilledQuantity: Int,
    val totalPrice: Double,
    val buyerVersion: Long,
    val sellerVersion: Long,
    val revenueTargetVersion: Long,
)

@Serializable
data class ClientVoteResponse(
    val voterStateId: String,
    val targetStateId: String,
    val votesAvailableAfter: Int,
    val targetVoteCountAfter: Int,
    val targetHttpExperienceAfter: Double,
    val voterVersion: Long,
    val targetVersion: Long,
)

@Serializable
sealed interface ClientGameUiEvent

@Serializable
enum class ClientPopupUiStyle {
    MESSAGE,
    ERROR,
}

@Serializable
@SerialName("popup")
data class ClientPopupUiEvent(
    val message: String,
    val style: ClientPopupUiStyle = ClientPopupUiStyle.MESSAGE,
) : ClientGameUiEvent

@Serializable
@SerialName("message")
data class ClientTextMessageUiEvent(
    val message: String,
) : ClientGameUiEvent

@Serializable
enum class ClientAttackPaneType {
    ATTACK,
    REDIRECT,
}

@Serializable
@SerialName("attack_message")
data class ClientAttackMessageUiEvent(
    val message: String,
    val port: Int,
    val ip: String,
    val windowHandle: Int? = null,
    val paneType: ClientAttackPaneType = ClientAttackPaneType.ATTACK,
) : ClientGameUiEvent

@Serializable
@SerialName("zombie_attack")
data class ClientZombieAttackUiEvent(
    val message: String,
    val zombieIp: String,
    val sourcePort: Int,
) : ClientGameUiEvent

@Serializable
enum class ClientShowChoicesType {
    BANK,
    FTP,
    ATTACK,
    HTTP,
    SHIPPING,
}

@Serializable
@SerialName("show_choices")
data class ClientShowChoicesUiEvent(
    val targetIp: String,
    val targetPort: Int,
    val choiceType: ClientShowChoicesType,
    val windowHandle: Int,
) : ClientGameUiEvent
