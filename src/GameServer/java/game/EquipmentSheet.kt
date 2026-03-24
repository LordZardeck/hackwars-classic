package game

import kotlin.math.abs

const val HEAL_MODIFIER_BASE = 4
const val HEAL_MODIFIER_MIN = 1
const val BANKING_BONUS_MAX = 0.07f

data class CpuBonusData(var bonus: Float = 0.0f, var maxBonus: Float = 0.0f) {
    fun applyEquippedBonus(equippedBonuses: Iterable<BonusData>, canApply: (BonusData) -> Boolean): CpuBonusData {
        for (data in equippedBonuses) {
            if (!canApply(data)) {
                continue
            }

            maxBonus += data.cpuBonus.getOrNull(0) ?: 0.0f
            bonus += data.cpuBonus.getOrNull(1) ?: 0.0f
        }

        return this
    }
}

internal data class EquipmentCardTotals(
    val cpuBonus: Float,
    val hdBonus: Int,
    val watchBonus: Int,
)

private data class EquipmentAttribute(val attribute: Int, val quality: Int)

class EquipmentSheet {
    private var equippedAgpFile: HackerFile? = null
    private var equippedPci0File: HackerFile? = null
    private var equippedPci1File: HackerFile? = null

    private val equippedBonuses = mutableListOf<BonusData>()

    fun getHealModifier(): Int =
        (HEAL_MODIFIER_BASE + equippedBonuses.sumOf { it.healMod }).coerceAtLeast(HEAL_MODIFIER_MIN)

    fun getDamageBonus(): Float = equippedBonuses.sumOf { it.damageBonus.toDouble() }.toFloat()

    fun getMiningBonus(): Float = equippedBonuses.sumOf { it.miningBonus.toDouble() }.toFloat()

    fun getFreezeImmune(): Boolean = equippedBonuses.any { it.freezeImmune }

    fun getDestroyWatchesImmune(): Boolean = equippedBonuses.any { it.destroyWatchesImmune }

    fun getBankingBonus(): Float =
        equippedBonuses.sumOf { it.bankingBonus.toDouble() }.toFloat().coerceAtMost(BANKING_BONUS_MAX)

    fun getHealBonus(): Float =
        (1.0f + equippedBonuses.sumOf { it.healBonus.toDouble() }.toFloat()).coerceAtLeast(0.25f)

    fun getCPUBonus(
        maximumCpuNoBonus: Float,
        baseCpuLoad: Float,
        attacking: Boolean,
        card: HackerFile? = null,
    ): Float {
        var (bonus, maxBonus) = CpuBonusData().applyEquippedBonus(equippedBonuses) {
            card == null || it.equipmentFile === card
        }

        val deficit = maximumCpuNoBonus - baseCpuLoad + bonus
        if (!attacking && deficit < 0f) {
            bonus += abs(deficit).coerceAtMost(maxBonus)
        }

        return bonus
    }

    fun getWatchBonus(maximumWatchesNoBonus: Int, watchCount: Int, card: HackerFile? = null): Int {
        var bonus = equippedBonuses.sumOf { it.takeIf { card == null || it.equipmentFile === card }?.watchBonus ?: 0 }

        val deficit = maximumWatchesNoBonus - watchCount + bonus
        if (deficit < 0) {
            bonus += abs(deficit)
        }

        return bonus
    }

    val driveBonus: Int
        get() = getHDBonus()

    fun getHDBonus(): Int = getHDBonus(null)

    fun getHDBonus(card: HackerFile?): Int =
        equippedBonuses.sumOf { it.takeIf { card == null || it.equipmentFile === card }?.hdBonus ?: 0 }

    fun getEquipment(): Array<HackerFile?> = arrayOf(equippedAgpFile, equippedPci0File, equippedPci1File)

    fun getEquippedCard(position: Int): HackerFile? =
        when (position) {
            AGP -> equippedAgpFile
            PCI0 -> equippedPci0File
            PCI1 -> equippedPci1File
            else -> null
        }

    fun clearEquippedCard(position: Int): HackerFile? {
        val equippedCard = getEquippedCard(position) ?: return null
        removeCardFromBonuses(equippedCard)
        setSlot(position, null)
        return equippedCard
    }

    fun setEquippedCard(position: Int, file: HackerFile?) {
        clearEquippedCard(position)
        if (file != null) {
            equippedBonuses += buildCardBonuses(file)
        }
        setSlot(position, file)
    }

    internal fun getCardTotals(card: HackerFile): EquipmentCardTotals {
        val cardBonuses = buildCardBonuses(card)
        return EquipmentCardTotals(
            cpuBonus = cardBonuses.sumOf { (it.cpuBonus.getOrNull(1) ?: 0.0f).toDouble() }.toFloat(),
            hdBonus = cardBonuses.sumOf { it.hdBonus },
            watchBonus = cardBonuses.sumOf { it.watchBonus },
        )
    }

    private fun setSlot(position: Int, file: HackerFile?) {
        when (position) {
            AGP -> equippedAgpFile = file
            PCI0 -> equippedPci0File = file
            PCI1 -> equippedPci1File = file
        }
    }

    private fun buildCardBonuses(card: HackerFile): List<BonusData> {
        val content = card.content as? EquipmentLicenseContent ?: return emptyList()
        return buildList {
            add(getBonusData(card, content.requireInt(EquipmentField.ATTRIBUTE0), content.requireInt(EquipmentField.QUALITY0)))
            add(getBonusData(card, content.requireInt(EquipmentField.ATTRIBUTE1), content.requireInt(EquipmentField.QUALITY1)))
            content.optionalAttribute(EquipmentField.ATTRIBUTE2, EquipmentField.QUALITY2)
                ?.takeUnless { it.attribute == 0 && it.quality == 0 }
                ?.let { add(getBonusData(card, it.attribute, it.quality)) }
        }
    }

    private fun removeCardFromBonuses(parentFile: HackerFile) {
        equippedBonuses.removeAll { it.equipmentFile === parentFile }
    }

    private fun getBonusData(parentFile: HackerFile?, attribute: Int, quality: Int): BonusData {
        // TODO: Change the contract so that bonus data can handle an Equipment Definition to process what property to apply to
        val bonusData = BonusData(parentFile)

        EquipmentDefinition.fromId(attribute)?.let { data ->
            when (data) {
                is EquipmentDefinition.HealingEquipment ->
                    bonusData.healMod = data.bonusForQuality(quality)

                is EquipmentDefinition.DamageBonusEquipment ->
                    bonusData.damageBonus = data.bonusForQuality(quality)

                is EquipmentDefinition.MiningBonusEquipment ->
                    bonusData.miningBonus = data.bonusForQuality(quality)

                is EquipmentDefinition.BankingBonusEquipment ->
                    bonusData.bankingBonus = data.bonusForQuality(quality)

                is EquipmentDefinition.HealCostBonusEquipment ->
                    bonusData.healBonus = data.bonusForQuality(quality)

                is EquipmentDefinition.CpuBonusEquipment ->
                    bonusData.setCpuBonus(data.bonusForQuality(quality))

                is EquipmentDefinition.WatchBonusEquipment ->
                    bonusData.watchBonus = data.bonusForQuality(quality)

                is EquipmentDefinition.HdBonusEquipment ->
                    bonusData.hdBonus = data.bonusForQuality(quality)

                is EquipmentDefinition.FreezeImmuneEquipment ->
                    bonusData.freezeImmune = data.bonusForQuality(quality)

                is EquipmentDefinition.DestroyWatchImmuneEquipment ->
                    bonusData.destroyWatchesImmune = data.bonusForQuality(quality)
            }
        }

        return bonusData
    }

    fun describeCard(card: HackerFile?) {
        card ?: return
        val bonusCheck = BonusData(card)
        val content = card.content as? EquipmentLicenseContent ?: return
        val descriptions = buildList {
            add(describeAttribute(content.requireInt(EquipmentField.ATTRIBUTE0), content.requireInt(EquipmentField.QUALITY0), bonusCheck))
            add(describeAttribute(content.requireInt(EquipmentField.ATTRIBUTE1), content.requireInt(EquipmentField.QUALITY1), bonusCheck))
            content.optionalAttribute(EquipmentField.ATTRIBUTE2, EquipmentField.QUALITY2)
                ?.takeUnless { it.attribute == 0 && it.quality == 0 }
                ?.let { add(describeAttribute(it.attribute, it.quality, bonusCheck)) }
        }

        card.content = content.copy(bonusData = descriptions.joinToString("|"))
    }

    fun describeAttribute(attribute: Int, quality: Int, bonusData: BonusData): String {
        val flatPercentBonusFormat = "%d%% %s"
        val percentBonusFormat = "%.1f%%/%1$.1f%% %s %s"
        return EquipmentDefinition
            .fromId(attribute)
            ?.let { data ->
                when (data) {
                    is EquipmentDefinition.HealingEquipment -> {
                        bonusData.healBonus = data.bonusForQuality(quality).toFloat()
                        percentBonusFormat.format(
                            abs(bonusData.healBonus / 4.0f * 100),
                            "Slower".takeIf { bonusData.healBonus > 0 } ?: "Faster",
                            "Heal Rate",
                        )
                    }

                    is EquipmentDefinition.DamageBonusEquipment -> {
                        bonusData.damageBonus = data.bonusForQuality(quality)
                        formatFlatBonus(bonusData.damageBonus, "to Attack Damage")
                    }

                    is EquipmentDefinition.MiningBonusEquipment -> {
                        bonusData.miningBonus = data.bonusForQuality(quality)
                        formatFlatBonus(bonusData.miningBonus, "to Redirecting Damage")
                    }

                    is EquipmentDefinition.BankingBonusEquipment -> {
                        bonusData.bankingBonus = data.bonusForQuality(quality)
                        percentBonusFormat.format(
                            abs(bonusData.bankingBonus * 100),
                            "Lower".takeIf { bonusData.bankingBonus > 0 } ?: "Higher",
                            "Banking Costs",
                        )
                    }

                    is EquipmentDefinition.HealCostBonusEquipment -> {
                        bonusData.healBonus = data.bonusForQuality(quality)
                        percentBonusFormat.format(
                            abs(bonusData.healBonus * 100),
                            "Higher".takeIf { bonusData.healBonus > 0 } ?: "Lower",
                            "Healing Costs",
                        )
                    }

                    is EquipmentDefinition.CpuBonusEquipment -> {
                        bonusData.setCpuBonus(data.bonusForQuality(quality))
                        formatFlatBonus(bonusData.cpuBonus[1], "CPU Points")
                    }

                    is EquipmentDefinition.WatchBonusEquipment -> {
                        bonusData.watchBonus = data.bonusForQuality(quality)
                        formatFlatBonus(bonusData.watchBonus, "Watch")
                    }

                    is EquipmentDefinition.HdBonusEquipment -> {
                        bonusData.hdBonus = data.bonusForQuality(quality)
                        formatFlatBonus(bonusData.hdBonus, "HD Space")
                    }

                    is EquipmentDefinition.FreezeImmuneEquipment -> {
                        bonusData.freezeImmune = data.bonusForQuality(quality)
                        flatPercentBonusFormat.format(
                            bonusData.calculateDegradation().times(100).toInt(),
                            "Freeze Immune",
                        )
                    }

                    is EquipmentDefinition.DestroyWatchImmuneEquipment -> {
                        bonusData.destroyWatchesImmune = data.bonusForQuality(quality)
                        flatPercentBonusFormat.format(
                            bonusData.calculateDegradation().times(100).toInt(),
                            "Destroy Watch Immune",
                        )
                    }
                }
            }
            ?: ""
    }

    private fun formatFlatBonus(value: Number, label: String): String =
        "%+.1f/%1$+.1f %s".format(value.toDouble(), label)

    fun outputXML(): String {
        val equipmentXMLLine = "<equipment>\n%s\n</equipment>\n"
        return """
            ${equipmentXMLLine.format(equippedAgpFile?.let(LegacyHackerFileCodec::serializeXml) ?: "")}
            ${equipmentXMLLine.format(equippedPci0File?.let(LegacyHackerFileCodec::serializeXml) ?: "")}
            ${equipmentXMLLine.format(equippedPci1File?.let(LegacyHackerFileCodec::serializeXml) ?: "")}
        """.trimIndent()
    }

    companion object {
        @JvmField
        var commodityAmounts =
            arrayOf(
                intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                intArrayOf(0, 0, 0, 2, 4, 6, 8, 10, 12, 14),
                intArrayOf(0, 0, 0, 0, 0, 4, 8, 12, 16, 20),
                intArrayOf(0, 0, 0, 0, 0, 0, 0, 6, 12, 18),
                intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 8, 16),
            )

        const val AGP = 0
        const val PCI0 = 1
        const val PCI1 = 2

        const val FREEZE_IMMUNE = 8
        const val DESTROY_WATCH_IMMUNE = 9
    }
}

internal enum class EquipmentField {
    ATTRIBUTE0,
    ATTRIBUTE1,
    ATTRIBUTE2,
    QUALITY0,
    QUALITY1,
    QUALITY2,
}

internal fun EquipmentLicenseContent.requireInt(field: EquipmentField): Int =
    value(field)?.toIntOrNull() ?: error("Missing or invalid equipment field: $field")

private fun EquipmentLicenseContent.optionalAttribute(attributeField: EquipmentField, qualityField: EquipmentField): EquipmentAttribute? {
    val attribute = value(attributeField)?.toIntOrNull() ?: return null
    val quality = value(qualityField)?.toIntOrNull() ?: return null
    return EquipmentAttribute(attribute, quality)
}

private fun EquipmentLicenseContent.value(field: EquipmentField): String? = when (field) {
    EquipmentField.ATTRIBUTE0 -> attribute0
    EquipmentField.ATTRIBUTE1 -> attribute1
    EquipmentField.ATTRIBUTE2 -> attribute2
    EquipmentField.QUALITY0 -> quality0
    EquipmentField.QUALITY1 -> quality1
    EquipmentField.QUALITY2 -> quality2
}
