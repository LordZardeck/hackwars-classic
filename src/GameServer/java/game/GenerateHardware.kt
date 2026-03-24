package game

enum class HardwareRarity {
    LOW, MEDIUM, HIGH, RARE;

    companion object {
        fun fromMaker(maker: String): HardwareRarity {
            return when (maker) {
                "Rare" -> RARE
                "High" -> HIGH
                "Medium" -> MEDIUM
                else -> LOW
            }
        }
    }
}

private val HARDWARE_DURABILITY_PICKER = WeightedPicker(50f to 10, 100f to 5, 150f to 3, 200f to 2, 250f to 1)
private val HARDWARE_FILE_TYPES = mapOf(
    HackerFile.AGP to "AGP Card",
    HackerFile.PCI to "PCI Card",
)

private fun randomVal(min: Int, max: Int, exclude: Int? = null): Int {
    return generateSequence { (min..<max).random() }.first { it != exclude }
}

fun generateHardware(rarity: HardwareRarity): HackerFile {
    val maxQuality = when (rarity) {
        HardwareRarity.RARE -> 10
        HardwareRarity.HIGH -> 8
        HardwareRarity.MEDIUM -> 6
        else -> 5
    }
    val maxAttribute = when (rarity) {
        HardwareRarity.RARE -> 10
        HardwareRarity.HIGH -> 9
        else -> 8
    }

    val attribute1 = randomVal(0, maxAttribute)
    val attribute2 = randomVal(0, maxAttribute, attribute1)

    val quality1 = 9.takeIf { attribute1 == EquipmentSheet.FREEZE_IMMUNE || attribute1 == EquipmentSheet.DESTROY_WATCH_IMMUNE }
        ?: randomVal(0, maxQuality)
    val quality2 = 9.takeIf { attribute2 == EquipmentSheet.FREEZE_IMMUNE || attribute2 == EquipmentSheet.DESTROY_WATCH_IMMUNE }
        ?: randomVal(2.takeIf { quality1 < 2 } ?: 0, maxQuality, quality1)

    val attributeOneDescription = EquipmentBonusType
        .fromId(attribute1)
        ?.let { EquipmentDefinition.fromType(it) }?.run { describe() }
    val attributeTwoDescription = EquipmentBonusType
        .fromId(attribute2)
        ?.let { EquipmentDefinition.fromType(it) }?.run { describe() }

    val hardwareType = HARDWARE_FILE_TYPES.keys.random()
    val durability = HARDWARE_DURABILITY_PICKER.next().toString()

    return HackerFile(hardwareType).apply {
        name = HARDWARE_FILE_TYPES[hardwareType] + ".license"
        setDescription("$attributeOneDescription ${HARDWARE_FILE_TYPES[hardwareType]} of $attributeTwoDescription")
        content = EquipmentLicenseContent(
            attribute0 = attribute1.toString(),
            attribute1 = attribute2.toString(),
            attribute2 = "0",
            quality0 = quality1.toString(),
            quality1 = quality2.toString(),
            quality2 = "0",
            maxQuality = durability,
            currentQuality = durability,
        )
        quantity = 1
        maker = (quality1 + quality2).let { combinedQuality ->
            when {
                combinedQuality >= 18 -> "Rare"
                combinedQuality >= 14 -> "High"
                combinedQuality > 5 -> "Medium"
                else -> "Low"
            }
        }
    }
}
