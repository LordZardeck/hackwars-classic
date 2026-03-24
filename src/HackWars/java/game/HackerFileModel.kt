package game

import java.io.Serializable
import java.util.LinkedHashMap

enum class HackerFileCategory {
    PROGRAM,
    DOCUMENT,
    FIREWALL,
    HARDWARE,
    LEGACY_LEVEL,
    GAME,
    QUEST,
    COMMODITY,
    IMAGE,
    TRASH,
}

enum class StackingPolicy {
    STACKING,
    SINGLE,
}

enum class ProgramFamily {
    BANKING,
    ATTACK,
    WATCH,
    FTP,
    HTTP,
    SHIPPING,
}

enum class ProgramForm {
    SOURCE,
    COMPILED,
}

enum class EquipmentSlotType {
    AGP,
    PCI,
}

enum class LegacyLevelFamily {
    FIREWALL,
    CPU,
    HD,
    MEMORY,
}

enum class ProgramScriptSlot(val legacyKey: String) {
    DEPOSIT("deposit"),
    WITHDRAW("withdraw"),
    TRANSFER("transfer"),
    INITIALIZE("initialize"),
    CONTINUE("continue"),
    FINALIZE("finalize"),
    FIRE("fire"),
    PUT("put"),
    GET("get"),
    ENTER("enter"),
    EXIT("exit"),
    SUBMIT("submit"),
}

sealed class HackerFileKind : Serializable {
    abstract val legacyId: Int
    abstract val displayName: String
    abstract val category: HackerFileCategory
    open val stackingPolicy: StackingPolicy = StackingPolicy.SINGLE
    open val installPortType: Int? = null

    fun isStacking(): Boolean = stackingPolicy == StackingPolicy.STACKING

    open fun defaultContent(): HackerFileContent = EmptyContent
}

data class ProgramKind(
    val family: ProgramFamily,
    val form: ProgramForm,
) : HackerFileKind() {
    override val legacyId: Int
        get() = when (family) {
            ProgramFamily.BANKING -> if (form == ProgramForm.COMPILED) 0 else 1
            ProgramFamily.ATTACK -> if (form == ProgramForm.COMPILED) 2 else 3
            ProgramFamily.WATCH -> if (form == ProgramForm.COMPILED) 4 else 5
            ProgramFamily.FTP -> if (form == ProgramForm.COMPILED) 6 else 7
            ProgramFamily.HTTP -> if (form == ProgramForm.COMPILED) 12 else 15
            ProgramFamily.SHIPPING -> if (form == ProgramForm.COMPILED) 23 else 24
        }

    override val displayName: String
        get() = when (family) {
            ProgramFamily.BANKING -> if (form == ProgramForm.COMPILED) "Banking" else "Banking Script"
            ProgramFamily.ATTACK -> if (form == ProgramForm.COMPILED) "Attack" else "Attack Script"
            ProgramFamily.WATCH -> if (form == ProgramForm.COMPILED) "Watch" else "Watch Script"
            ProgramFamily.FTP -> if (form == ProgramForm.COMPILED) "FTP" else "FTP Script"
            ProgramFamily.HTTP -> if (form == ProgramForm.COMPILED) "HTTP" else "HTTP Script"
            ProgramFamily.SHIPPING -> if (form == ProgramForm.COMPILED) "Redirect" else "Redirect Script"
        }

    override val category: HackerFileCategory = HackerFileCategory.PROGRAM
    override val stackingPolicy: StackingPolicy = if (form == ProgramForm.COMPILED) {
        StackingPolicy.STACKING
    } else {
        StackingPolicy.SINGLE
    }
    override val installPortType: Int?
        get() = if (form == ProgramForm.COMPILED) {
            when (family) {
                ProgramFamily.BANKING -> PortType.BANKING.code
                ProgramFamily.ATTACK -> PortType.ATTACK.code
                ProgramFamily.FTP -> PortType.FTP.code
                ProgramFamily.HTTP -> PortType.HTTP.code
                ProgramFamily.SHIPPING -> PortType.REDIRECT.code
                ProgramFamily.WATCH -> null
            }
        } else {
            null
        }

    override fun defaultContent(): HackerFileContent = ProgramContent(family)
}

object TextFileKind : HackerFileKind() {
    override val legacyId: Int = 9
    override val displayName: String = "Text"
    override val category: HackerFileCategory = HackerFileCategory.DOCUMENT
    override fun defaultContent(): HackerFileContent = TextContent()
}

object ClueFileKind : HackerFileKind() {
    override val legacyId: Int = 16
    override val displayName: String = "Secret Document"
    override val category: HackerFileCategory = HackerFileCategory.DOCUMENT
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = ClueContent()
}

object BountyFileKind : HackerFileKind() {
    override val legacyId: Int = 17
    override val displayName: String = "Bounty"
    override val category: HackerFileCategory = HackerFileCategory.DOCUMENT
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = BountyContent()
}

data class EquipmentLicenseKind(
    val slotType: EquipmentSlotType,
) : HackerFileKind() {
    override val legacyId: Int = if (slotType == EquipmentSlotType.AGP) 18 else 19
    override val displayName: String = if (slotType == EquipmentSlotType.AGP) "AGP Card License" else "PCI Card License"
    override val category: HackerFileCategory = HackerFileCategory.HARDWARE
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = EquipmentLicenseContent()
}

data class LegacyLevelKind(
    val family: LegacyLevelFamily,
) : HackerFileKind() {
    override val legacyId: Int
        get() = when (family) {
            LegacyLevelFamily.FIREWALL -> 8
            LegacyLevelFamily.CPU -> 10
            LegacyLevelFamily.HD -> 11
            LegacyLevelFamily.MEMORY -> 13
        }

    override val displayName: String
        get() = when (family) {
            LegacyLevelFamily.FIREWALL -> "FireWall"
            LegacyLevelFamily.CPU -> "CPU"
            LegacyLevelFamily.HD -> "HD"
            LegacyLevelFamily.MEMORY -> "Memory"
        }

    override val category: HackerFileCategory = when (family) {
        LegacyLevelFamily.FIREWALL -> HackerFileCategory.FIREWALL
        else -> HackerFileCategory.LEGACY_LEVEL
    }
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = LegacyLevelContent()
}

object ImageFileKind : HackerFileKind() {
    override val legacyId: Int = 14
    override val displayName: String = "Image"
    override val category: HackerFileCategory = HackerFileCategory.IMAGE
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = LegacyLevelContent()
}

object GameProjectFileKind : HackerFileKind() {
    override val legacyId: Int = 20
    override val displayName: String = "Game Project"
    override val category: HackerFileCategory = HackerFileCategory.GAME
    override fun defaultContent(): HackerFileContent = LegacyLevelContent()
}

object GameFileKind : HackerFileKind() {
    override val legacyId: Int = 21
    override val displayName: String = "Game"
    override val category: HackerFileCategory = HackerFileCategory.GAME
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = LegacyLevelContent()
}

object QuestGameFileKind : HackerFileKind() {
    override val legacyId: Int = 22
    override val displayName: String = "Game"
    override val category: HackerFileCategory = HackerFileCategory.QUEST
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = QuestGameContent()
}

object QuestItemFileKind : HackerFileKind() {
    override val legacyId: Int = 25
    override val displayName: String = "Quest"
    override val category: HackerFileCategory = HackerFileCategory.QUEST
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = VisualItemContent()
}

object CommoditySlipFileKind : HackerFileKind() {
    override val legacyId: Int = 26
    override val displayName: String = "Commodity"
    override val category: HackerFileCategory = HackerFileCategory.COMMODITY
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = LegacyLevelContent()
}

object ChallengeFileKind : HackerFileKind() {
    override val legacyId: Int = 27
    override val displayName: String = "Challenge"
    override val category: HackerFileCategory = HackerFileCategory.QUEST
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = ChallengeContent()
}

object NewFirewallFileKind : HackerFileKind() {
    override val legacyId: Int = 28
    override val displayName: String = "Firewall"
    override val category: HackerFileCategory = HackerFileCategory.FIREWALL
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = NewFirewallContent()
}

object TrashFileKind : HackerFileKind() {
    override val legacyId: Int = 29
    override val displayName: String = "Trash"
    override val category: HackerFileCategory = HackerFileCategory.TRASH
    override val stackingPolicy: StackingPolicy = StackingPolicy.STACKING
    override fun defaultContent(): HackerFileContent = VisualItemContent()
}

sealed interface HackerFileContent : Serializable {
    fun deepCopy(): HackerFileContent = this
}

object EmptyContent : HackerFileContent

data class ProgramContent(
    val family: ProgramFamily,
    val scripts: LinkedHashMap<ProgramScriptSlot, String> = LinkedHashMap(),
) : HackerFileContent {
    fun script(slot: ProgramScriptSlot): String = scripts[slot].orEmpty()

    override fun deepCopy(): HackerFileContent = copy(scripts = LinkedHashMap(scripts))
}

data class TextContent(
    val data: String = "",
    val level: String = "",
) : HackerFileContent

data class ClueContent(
    val currentStep: String = "",
    val clueLevel: String = "",
    val steps: List<String> = List(6) { "" },
) : HackerFileContent

data class BountyContent(
    val count: String = "",
    val script: String = "",
    val maker: String = "",
    val targetType: String = "",
    val reward: String = "",
    val target: String = "",
    val bountyIp: String = "",
    val timeout: String = "",
) : HackerFileContent

data class EquipmentLicenseContent(
    val attribute0: String = "",
    val attribute1: String = "",
    val attribute2: String = "",
    val quality0: String = "",
    val quality1: String = "",
    val quality2: String = "",
    val timeout: String = "",
    val maxQuality: String = "",
    val currentQuality: String = "",
    val lastDegrade: String = "",
    val bonusData: String? = null,
) : HackerFileContent

data class FirewallSpecialAttribute(
    val name: String = "",
    val longDescription: String = "",
    val shortDescription: String = "",
    val value: String = "",
) : Serializable

data class NewFirewallContent(
    val bankDamageModifier: String = "",
    val attackDamageModifier: String = "",
    val redirectDamageModifier: String = "",
    val ftpDamageModifier: String = "",
    val httpDamageModifier: String = "",
    val specialAttribute1: FirewallSpecialAttribute = FirewallSpecialAttribute(),
    val specialAttribute2: FirewallSpecialAttribute = FirewallSpecialAttribute(),
    val attackDamage: String = "",
    val equipLevel: String = "",
    val name: String = "",
    val storePrice: String = "",
) : HackerFileContent

data class ChallengeContent(
    val input: String = "",
    val output: String = "",
    val inputType: String = "",
    val outputType: String = "",
    val task: String = "",
    val questId: String = "",
    val identifier: String = "",
) : HackerFileContent

data class QuestGameContent(
    val data: String = "",
    val level: String = "",
    val questId: String = "",
    val task: String = "",
) : HackerFileContent

data class VisualItemContent(
    val itemName: String = "",
    val imageId: String = "",
    val questId: String? = null,
) : HackerFileContent

data class LegacyLevelContent(
    val data: String = "",
    val level: String = "",
) : HackerFileContent

object HackerFileInterop {
    @JvmStatic
    fun displayName(file: HackerFile): String = file.kind.displayName

    @JvmStatic
    fun displayNameForLegacyId(id: Int): String = kindForLegacyId(id).displayName

    @JvmStatic
    fun category(file: HackerFile): HackerFileCategory = file.kind.category

    @JvmStatic
    fun isDecompilable(file: HackerFile): Boolean =
        (file.kind as? ProgramKind)?.form == ProgramForm.COMPILED

    @JvmStatic
    fun isDecompilableLegacyId(id: Int): Boolean =
        (kindForLegacyId(id) as? ProgramKind)?.form == ProgramForm.COMPILED

    @JvmStatic
    fun isStacking(file: HackerFile): Boolean = file.kind.isStacking()

    @JvmStatic
    fun installPortType(file: HackerFile): Int = file.kind.installPortType ?: -1

    @JvmStatic
    fun scriptTabs(file: HackerFile): Array<String> =
        LegacyHackerFileCodec.programKeys(file.kind).toTypedArray()

    @JvmStatic
    fun sourceVariant(kind: HackerFileKind): HackerFileKind? =
        (kind as? ProgramKind)?.takeIf { it.form == ProgramForm.COMPILED }?.copy(form = ProgramForm.SOURCE)

    @JvmStatic
    fun compiledVariant(kind: HackerFileKind): HackerFileKind? =
        (kind as? ProgramKind)?.takeIf { it.form == ProgramForm.SOURCE }?.copy(form = ProgramForm.COMPILED)

    @JvmStatic
    fun kindForLegacyId(id: Int): HackerFileKind = LegacyHackerFileCodec.kindForLegacyId(id)

    @JvmStatic
    fun legacyId(file: HackerFile): Int = file.kind.legacyId

    @JvmStatic
    fun legacyId(kind: HackerFileKind): Int = kind.legacyId

    @JvmStatic
    fun isProgram(file: HackerFile, family: ProgramFamily, form: ProgramForm): Boolean =
        (file.kind as? ProgramKind)?.let { it.family == family && it.form == form } == true

    @JvmStatic
    fun isLegacyLevel(file: HackerFile, family: LegacyLevelFamily): Boolean =
        (file.kind as? LegacyLevelKind)?.family == family

    @JvmStatic
    fun isText(file: HackerFile): Boolean = file.kind == TextFileKind

    @JvmStatic
    fun isImage(file: HackerFile): Boolean = file.kind == ImageFileKind

    @JvmStatic
    fun isGame(file: HackerFile): Boolean = file.kind == GameFileKind || file.kind == QuestGameFileKind

    @JvmStatic
    fun isGameLegacyId(id: Int): Boolean {
        val kind = kindForLegacyId(id)
        return kind == GameFileKind || kind == QuestGameFileKind
    }

    @JvmStatic
    fun isImageLegacyId(id: Int): Boolean = kindForLegacyId(id) == ImageFileKind

    @JvmStatic
    fun isBounty(file: HackerFile): Boolean = file.kind == BountyFileKind

    @JvmStatic
    fun isCommoditySlip(file: HackerFile): Boolean = file.kind == CommoditySlipFileKind

    @JvmStatic
    fun isNewFirewall(file: HackerFile): Boolean = file.kind == NewFirewallFileKind

    @JvmStatic
    fun isEquipmentLicense(file: HackerFile): Boolean = file.kind is EquipmentLicenseKind

    @JvmStatic
    fun isEquipmentLicense(file: HackerFile, slotType: EquipmentSlotType): Boolean =
        (file.kind as? EquipmentLicenseKind)?.slotType == slotType

    @JvmStatic
    fun opensInScriptEditor(file: HackerFile): Boolean =
        (file.kind as? ProgramKind)?.form == ProgramForm.SOURCE || file.kind == TextFileKind

    @JvmStatic
    fun opensInScriptEditorLegacyId(id: Int): Boolean {
        val kind = kindForLegacyId(id)
        return (kind as? ProgramKind)?.form == ProgramForm.SOURCE || kind == TextFileKind
    }

    @JvmStatic
    fun programContent(file: HackerFile): ProgramContent? = file.content as? ProgramContent

    @JvmStatic
    fun equipmentContent(file: HackerFile): EquipmentLicenseContent? = file.content as? EquipmentLicenseContent

    @JvmStatic
    fun firewallContent(file: HackerFile): NewFirewallContent? = file.content as? NewFirewallContent

    @JvmStatic
    fun textContent(file: HackerFile): TextContent? = file.content as? TextContent

    @JvmStatic
    fun visualItemContent(file: HackerFile): VisualItemContent? = file.content as? VisualItemContent

    @JvmStatic
    fun challengeContent(file: HackerFile): ChallengeContent? = file.content as? ChallengeContent

    @JvmStatic
    fun questGameContent(file: HackerFile): QuestGameContent? = file.content as? QuestGameContent

    @JvmStatic
    fun legacyContentMap(file: HackerFile): LinkedHashMap<String, Any?> =
        LegacyHackerFileCodec.toLegacyContentMap(file.kind, file.content)
}
