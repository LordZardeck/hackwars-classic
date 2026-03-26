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
    val filesystem: ClientFilesystemState = ClientFilesystemState(),
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
sealed interface ClientGameDeltaProjection

@Serializable
@SerialName("state_sections")
data class ClientGameSectionsProjection(
    val identity: ClientComputerIdentity? = null,
    val economy: ClientEconomyState? = null,
    val hardware: ClientHardwareState? = null,
    val ports: List<ClientPortState>? = null,
    val filesystem: ClientFilesystemState? = null,
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
