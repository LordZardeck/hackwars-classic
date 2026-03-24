package game

import java.io.Serializable
import java.util.HashMap

/**
 * Shared transferable file envelope used across inventory, network, and persistence code.
 * Legacy numeric IDs and XML behavior live in [LegacyHackerFileCodec].
 */
class HackerFile @JvmOverloads constructor(
    kind: HackerFileKind = LegacyHackerFileCodec.kindForLegacyId(BANKING_COMPILED),
) : Serializable {
    private var cpuCost = 0.0f
    private var description: String? = ""

    var name: String? = ""
    var kind: HackerFileKind = kind
        set(value) {
            field = value
            content = LegacyHackerFileCodec.retargetContent(value, content)
        }
    var location: String? = ""

    /**
     * The dollar value of this program.
     */
    var price = 0.0f

    /**
     * The player who created this script (applies to compiled applications).
     */
    var maker: String? = ""

    /**
     * The quantity (used for stacking file types).
     */
    var quantity = 0

    /**
     * Type-safe file content whose schema is owned by [kind].
     */
    @get:JvmName("getTypedContent")
    @set:JvmName("setTypedContent")
    var content: HackerFileContent = kind.defaultContent()
        set(value) {
            field = LegacyHackerFileCodec.retargetContent(kind, value)
        }

    constructor(type: Int) : this(LegacyHackerFileCodec.kindForLegacyId(type))

    fun getSpecial(num: Int): Map<*, *>? {
        val firewallContent = content as? NewFirewallContent ?: return null
        return when (num) {
            1 -> LegacyHackerFileCodec.toLegacyContentMap(kind, firewallContent)["specialAttribute1"] as? Map<*, *>
            2 -> LegacyHackerFileCodec.toLegacyContentMap(kind, firewallContent)["specialAttribute2"] as? Map<*, *>
            else -> null
        }
    }

    var cPUCost: Float
        get() = cpuCost
        set(cpuCost) {
            this.cpuCost = cpuCost
        }

    fun setDescription(description: String?) {
        this.description = description
    }

    internal fun rawDescription(): String? = description

    val publicDescription: String?
        get() = if (kind == BountyFileKind) {
            val count = (content as? BountyContent)?.count.orEmpty()
            (description ?: "") + "Iterations Left: " + count
        } else {
            description
        }

    fun clone(): HackerFile {
        val copy = HackerFile(kind)
        copy.location = location
        copy.name = name
        copy.content = content.deepCopy()
        copy.cPUCost = cpuCost
        copy.setDescription(description)
        copy.maker = maker
        copy.price = price
        copy.quantity = quantity
        return copy
    }

    override fun toString(): String {
        return buildString {
            append("\nFile{\n")
            append("    Name: ").append(name).append('\n')
            append("    Quantity: ").append(quantity).append('\n')
            append("}\n")
        }
    }

    @Deprecated("Use kind.displayName", ReplaceWith("kind.displayName"))
    val typeString: String
        get() = when (kind) {
            is ProgramKind -> if ((kind as ProgramKind).form == ProgramForm.COMPILED) "compiled" else "script"
            TextFileKind -> "text"
            NewFirewallFileKind, LegacyLevelKind(LegacyLevelFamily.FIREWALL) -> "firewall"
            ImageFileKind -> "image"
            is EquipmentLicenseKind -> "hardware"
            TrashFileKind -> "trash"
            else -> ""
        }

    @Deprecated("Use kind.stackingPolicy", ReplaceWith("kind.isStacking()"))
    val isStacking: Boolean
        get() = kind.isStacking()

    @Deprecated("Use LegacyHackerFileCodec.typeKeys(kind)", ReplaceWith("LegacyHackerFileCodec.typeKeys(kind).toTypedArray()"))
    val typeKeys: Array<String>
        get() = LegacyHackerFileCodec.typeKeys(kind).toTypedArray()

    @Deprecated("Use LegacyHackerFileCodec.specialKeys(kind)", ReplaceWith("LegacyHackerFileCodec.specialKeys(kind).toTypedArray()"))
    val specialKeys: Array<String?>
        get() = LegacyHackerFileCodec.specialKeys(kind).map { it as String? }.toTypedArray()

    @Deprecated("Use kind.installPortType", ReplaceWith("kind.installPortType ?: -1"))
    val portType: Int
        get() = kind.installPortType ?: -1

    @Deprecated("Use LegacyHackerFileCodec.serializeXml(this)")
    fun outputXML(): String = LegacyHackerFileCodec.serializeXml(this)

    @Deprecated("Use LegacyHackerFileCodec.checksumFailed(this, other)")
    fun checkSumFailed(other: HackerFile): Boolean = LegacyHackerFileCodec.checksumFailed(this, other)

    @Deprecated("Use typed content from Kotlin or getTypedContent() from Java")
    fun getContent(): HashMap<*, *> = HashMap(HackerFileInterop.legacyContentMap(this))

    @Deprecated("Use typed content from Kotlin or setTypedContent() from Java")
    fun setContent(content: HashMap<*, *>?) {
        this.content = LegacyHackerFileCodec.parseLegacyContent(kind, content ?: emptyMap<Any?, Any?>())
    }

    @Deprecated("Use kind.legacyId or kind directly")
    var type: Int
        get() = kind.legacyId
        set(type) {
            val previousContent = content
            kind = LegacyHackerFileCodec.kindForLegacyId(type)
            content = LegacyHackerFileCodec.retargetContent(kind, previousContent)
        }

    companion object {
        const val BANKING_COMPILED: Int = 0
        const val BANKING_SCRIPT: Int = 1
        const val ATTACKING_COMPILED: Int = 2
        const val ATTACKING_SCRIPT: Int = 3
        const val WATCH_COMPILED: Int = 4
        const val WATCH_SCRIPT: Int = 5
        const val FTP_COMPILED: Int = 6
        const val FTP_SCRIPT: Int = 7
        const val FIREWALL: Int = 8
        const val TEXT: Int = 9
        const val CPU: Int = 10
        const val HD: Int = 11
        const val HTTP: Int = 12
        const val MEMORY: Int = 13
        const val IMAGE: Int = 14
        const val HTTP_SCRIPT: Int = 15
        const val CLUE: Int = 16
        const val BOUNTY: Int = 17
        const val AGP: Int = 18
        const val PCI: Int = 19
        const val GAME_PROJECT: Int = 20
        const val GAME: Int = 21
        const val QUEST_GAME: Int = 22
        const val SHIPPING_COMPILED: Int = 23
        const val SHIPPING_SCRIPT: Int = 24
        const val QUEST_ITEM: Int = 25
        const val COMMODITY_SLIP: Int = 26
        const val CHALLENGE: Int = 27
        const val NEW_FIREWALL: Int = 28
        const val TRASH: Int = 29
    }
}
