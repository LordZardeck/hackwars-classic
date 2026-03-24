package game

import game.payload.FloatCommandPayload
import game.payload.SaveFileRequestPayload
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

private data class EquipmentAttribute(val attribute: Int, val quality: Int)

class EquipmentSheet(private val computer: Computer) {
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

    val cpuBonus: Float
        get() = getCPUBonus()

    fun getCPUBonus(): Float = getCPUBonus(null)

    fun getCPUBonus(card: HackerFile?): Float {
        var (bonus, maxBonus) = CpuBonusData().applyEquippedBonus(equippedBonuses) {
            card == null || it.equipmentFile === card
        }

        val deficit = computer.maximumCPUNoBonus - computer.baseCPULoad + bonus
        if (!computer.attacking && deficit < 0f) {
            bonus += abs(deficit).coerceAtMost(maxBonus)
        }

        return bonus
    }

    fun getWatchBonus(): Int = getWatchBonus(null)

    fun getWatchBonus(card: HackerFile?): Int {
        var bonus = equippedBonuses.sumOf { it.takeIf { card == null || it.equipmentFile === card }?.watchBonus ?: 0 }

        val deficit = computer.maximumWatchesNoBonus - computer.watchHandler.watchCount + bonus
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

    fun removeAllowed(card: HackerFile?): Boolean {
        val cpuRemaining = computer.maximumCPULoad - computer.baseCPULoad
        val cpuBonus = getCPUBonus(card)
        if (cpuRemaining - cpuBonus < 0.0f) {
            computer.addMessage(MessageHandler.UNEQUIP_FAIL_CPU_RESTRICTIONS)
            return false
        }

        val spaceLeft = computer.fileSystem.getSpaceLeft()
        val hdBonus = getHDBonus(card).toFloat()
        if (spaceLeft - hdBonus < 0) {
            computer.addMessage(MessageHandler.UNEQUIP_FAIL_HD_FULL)
            return false
        }

        val watchSpaceLeft = computer.getMaximumWatches() - computer.watchHandler.watchCount
        val watchBonus = getWatchBonus(card)
        if (watchSpaceLeft - watchBonus < 0) {
            computer.addMessage(MessageHandler.UNEQUIP_FAIL_WATCH_RESTRICTIONS)
            return false
        }
        return true
    }

    private fun addCard(cardToEquip: HackerFile, doChecks: Boolean, isEquipped: Boolean): Boolean {
        val cardBonuses = buildCardBonuses(cardToEquip)

        if (doChecks) {
            val cpuRemaining = computer.maximumCPULoad - computer.baseCPULoad
            val cpuBonus = cardBonuses.sumOf { (it.cpuBonus.getOrNull(1) ?: 0.0f).toDouble() }.toFloat()
            if (cpuRemaining + cpuBonus < 0.0f) {
                computer.addMessage(MessageHandler.EQUIP_FAIL_CPU_RESTRICTIONS)
                return false
            }

            var spaceLeft = computer.fileSystem.getSpaceLeft()
            if (!isEquipped) {
                spaceLeft++
            }
            val hdBonus = cardBonuses.sumOf { it.hdBonus }
            if (spaceLeft + hdBonus < 0) {
                computer.addMessage(MessageHandler.EQUIP_FAIL_HD_FULL)
                return false
            }

            val watchSpaceLeft = computer.getMaximumWatches() - computer.watchHandler.watchCount
            val watchBonus = cardBonuses.sumOf { it.watchBonus }
            if (watchSpaceLeft + watchBonus < 0) {
                computer.addMessage(MessageHandler.EQUIP_FAIL_WATCH_RESTRICTIONS)
                return false
            }
        }

        equippedBonuses += cardBonuses
        return true
    }

    private fun buildCardBonuses(card: HackerFile): List<BonusData> {
        val content = card.content
        return buildList {
            add(getBonusData(card, content.requireInt("attribute0"), content.requireInt("quality0")))
            add(getBonusData(card, content.requireInt("attribute1"), content.requireInt("quality1")))
            content.optionalAttribute("attribute2", "quality2")
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

        EquipmentDefinition
            .fromId(attribute)
            ?.let { data ->
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

    private fun getHackerFileFromName(position: Int, name: String?): HackerFile? {
        val file = name?.let { computer.fileSystem.getFile("", it) } ?: return null
        val expectedType = when (position) {
            AGP -> HackerFile.AGP
            PCI0, PCI1 -> HackerFile.PCI
            else -> return file
        }

        if (file.type != expectedType) {
            computer.addMessage(MessageHandler.EQUIP_FAIL_WRONG_TYPE)
            return null
        }

        return file
    }

    private fun getEquippedCard(position: Int): HackerFile? =
        when (position) {
            AGP -> equippedAgpFile
            PCI0 -> equippedPci0File
            PCI1 -> equippedPci1File
            else -> null
        }

    private fun setEquippedCard(position: Int, file: HackerFile?) {
        when (position) {
            AGP -> equippedAgpFile = file
            PCI0 -> equippedPci0File = file
            PCI1 -> equippedPci1File = file
        }
    }

    private fun unequipCard(equippedCard: HackerFile, position: Int): Boolean {
        if (!removeAllowed(equippedCard)) {
            return false
        }

        removeCardFromBonuses(equippedCard)
        computer.computerHandler.addData(
            ApplicationData(SaveFileRequestPayload("", equippedCard), 0, computer.getIP()),
            computer.getIP()
        )

        setEquippedCard(position, null)
        return true
    }

    private fun equipCard(cardToEquip: HackerFile, position: Int, doChecks: Boolean, cardEquipped: Boolean): Boolean {
        if (!addCard(cardToEquip, doChecks, cardEquipped)) {
            return false
        }

        computer.fileSystem.deleteFile("", cardToEquip.name)
        setEquippedCard(position, cardToEquip)

        return true
    }

    private fun equipLogic(position: Int, cardToEquip: HackerFile?, sendMessage: Boolean) {
        var unequipped = true
        var isEquipped = false
        val equippedCard = getEquippedCard(position)

        if (equippedCard != null) {
            isEquipped = true
            unequipped = unequipCard(equippedCard, position)
        }

        if (!unequipped) {
            return
        }

        if (cardToEquip != null) {
            if (equipCard(cardToEquip, position, true, isEquipped)) {
                if (sendMessage && equippedCard != null) {
                    computer.addMessage(MessageHandler.EQUIP_SUCCESS, arrayOf(cardToEquip.name))
                }
            } else {
                if (equippedCard != null) {
                    equipCard(equippedCard, position, doChecks = false, cardEquipped = true)
                }
            }
        }
    }

    fun equip(position: Int, cardToEquip: HackerFile?) {
        equipLogic(position, cardToEquip, false)
    }

    fun equip(position: Int, name: String?) {
        equipLogic(position, getHackerFileFromName(position, name), true)
    }

    fun getEquipment(): Array<HackerFile?> {
        return arrayOf(equippedAgpFile, equippedPci0File, equippedPci1File)
    }

    fun degradeEquipment() {
        if (computer.getType() != Computer.NPC) {
            equippedAgpFile?.let(::degradeEquipmentInternal)
            equippedPci0File?.let(::degradeEquipmentInternal)
            equippedPci1File?.let(::degradeEquipmentInternal)
        }
    }

    fun repair(equipmentId: Int) {
        getEquippedCard(equipmentId)?.let(::repair)
    }

    fun repair(equipment: HackerFile) {
        val commodityUsed = buildCommodityUsage(equipment)
        val commodityFailed = commodityUsed.indices.any { computer.getCommodity(it).toInt() < commodityUsed[it] }

        if (commodityFailed) {
            computer.addMessage(
                MessageHandler.REPAIR_FAIL_NOT_ENOUGH_COMMODITIES,
                arrayOf(equipment.name, buildCommodityMessage(commodityUsed))
            )
            return
        }

        val repairLevel = computer.repairLevel.toInt()
        val repairFailedCommodity = commodityUsed.indices.reversed().firstOrNull {
            commodityUsed[it] > 0 && repairLevel < Computer.requiredRepairLevel[it]
        }

        if (repairFailedCommodity != null) {
            computer.addMessage(
                MessageHandler.REPAIR_FAIL_LEVEL,
                arrayOf(
                    Computer.commodityString[repairFailedCommodity],
                    Computer.requiredRepairLevel[repairFailedCommodity]
                )
            )
            return
        }

        var xp = 0.0f
        for (i in commodityUsed.indices) {
            val check = computer.getCommodity(i).toInt()
            computer.setCommodityAmount(i, (check - commodityUsed[i]).toFloat())
            xp += commodityUsed[i] * computer.repairXP[i]
        }

        computer.computerHandler.addData(
            ApplicationData(
                FloatCommandPayload(com.hackwars.rpc.GameCommands.REPAIRXP.command, xp),
                0,
                computer.getIP()
            ),
            computer.getIP()
        )

        val content = equipment.content
        val maxQuality = content["maxquality"]?.toString()?.toFloatOrNull() ?: 50.0f
        content["currentquality"] = maxQuality.toString()

        computer.setRepaired(true)
        computer.addMessage(
            MessageHandler.REPAIR_SUCCESS,
            arrayOf(equipment.name, buildCommodityMessage(commodityUsed))
        )
    }

    private fun buildCommodityUsage(equipment: HackerFile): IntArray {
        val content = equipment.content
        val quality0 = content.requireInt("quality0")
        val quality1 = content.requireInt("quality1")

        return IntArray(commodityAmounts.size) { commodityIndex ->
            commodityAmounts[commodityIndex][quality0] + commodityAmounts[commodityIndex][quality1]
        }
    }

    private fun buildCommodityMessage(commodityUsed: IntArray): String {
        val segments = (0..3).mapNotNull { index ->
            commodityUsed[index].takeIf { it > 0 }?.let { "[${it}x${Computer.commodityString[index]}]" }
        }
        return if (segments.isEmpty()) "" else segments.joinToString(separator = " ", postfix = " ")
    }

    fun degradeEquipment(equipment: HackerFile?) {
        equipment?.let(::degradeEquipmentInternal)
    }

    private fun degradeEquipmentInternal(equipment: HackerFile) {
        val content = equipment.content
        val maxQuality = content["maxquality"]?.toString()?.takeUnless { it.isBlank() || it == "null" }?.toFloatOrNull()
        var currentQuality = content["currentquality"]?.toString()?.toFloatOrNull()
        val lastDegrade = content["lastdegrade"]?.toString()?.toLongOrNull()

        if (maxQuality == null || currentQuality == null || lastDegrade == null) {
            resetDegradation(content)
            return
        }

        if (computer.currentTime - lastDegrade >= DEGRADE_RATE.toLong()) {
            content["lastdegrade"] = computer.currentTime.toString()
            currentQuality--
            if (currentQuality >= 0) {
                content["currentquality"] = currentQuality.toString()
            }
        }
    }

    private fun resetDegradation(content: MutableMap<Any?, Any?>) {
        val defaultQuality = 50.0
        content["maxquality"] = defaultQuality.toString()
        content["currentquality"] = defaultQuality.toString()
        content["lastdegrade"] = computer.currentTime.toString()
    }

    fun describeCard(card: HackerFile?) {
        card ?: return
        val bonusCheck = BonusData(card)
        val content = card.content
        val descriptions = buildList {
            add(describeAttribute(content.requireInt("attribute0"), content.requireInt("quality0"), bonusCheck))
            add(describeAttribute(content.requireInt("attribute1"), content.requireInt("quality1"), bonusCheck))
            content.optionalAttribute("attribute2", "quality2")
                ?.takeUnless { it.attribute == 0 && it.quality == 0 }
                ?.let { add(describeAttribute(it.attribute, it.quality, bonusCheck)) }
        }

        card.content["bonusdata"] = descriptions.joinToString("|")
    }

    fun describeAttribute(attribute: Int, quality: Int, bonusData: BonusData): String {
        val flatPercentBonusFormat = "%d%% %s"
        val flatBonusFormat = "%+.1f/%1$+.1f %s"
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
                            "Heal Rate"
                        )
                    }

                    is EquipmentDefinition.DamageBonusEquipment -> {
                        bonusData.damageBonus = data.bonusForQuality(quality)
                        flatBonusFormat.format(bonusData.damageBonus, "to Attack Damage")
                    }

                    is EquipmentDefinition.MiningBonusEquipment -> {
                        bonusData.miningBonus = data.bonusForQuality(quality)
                        flatBonusFormat.format(bonusData.miningBonus, "to Redirecting Damage")
                    }

                    is EquipmentDefinition.BankingBonusEquipment -> {
                        bonusData.bankingBonus = data.bonusForQuality(quality)
                        percentBonusFormat.format(
                            abs(bonusData.bankingBonus * 100),
                            "Lower".takeIf { bonusData.bankingBonus > 0 } ?: "Higher",
                            "Banking Costs"
                        )
                    }

                    is EquipmentDefinition.HealCostBonusEquipment -> {
                        bonusData.healBonus = data.bonusForQuality(quality)
                        percentBonusFormat.format(
                            abs(bonusData.healBonus * 100),
                            "Higher".takeIf { bonusData.healBonus > 0 } ?: "Lower",
                            "Healing Costs"
                        )
                    }

                    is EquipmentDefinition.CpuBonusEquipment -> {
                        bonusData.setCpuBonus(data.bonusForQuality(quality))
                        flatBonusFormat.format(bonusData.cpuBonus[1], "CPU Points")
                    }

                    is EquipmentDefinition.WatchBonusEquipment -> {
                        bonusData.watchBonus = data.bonusForQuality(quality)
                        flatBonusFormat.format(bonusData.watchBonus, "Watch")
                    }

                    is EquipmentDefinition.HdBonusEquipment -> {
                        bonusData.hdBonus = data.bonusForQuality(quality)
                        flatBonusFormat.format(bonusData.hdBonus, "HD Space")
                    }

                    is EquipmentDefinition.FreezeImmuneEquipment -> {
                        bonusData.freezeImmune = data.bonusForQuality(quality)
                        flatPercentBonusFormat.format(
                            bonusData.calculateDegradation().times(100).toInt(),
                            "Freeze Immune"
                        )
                    }

                    is EquipmentDefinition.DestroyWatchImmuneEquipment -> {
                        bonusData.destroyWatchesImmune = data.bonusForQuality(quality)
                        flatPercentBonusFormat.format(
                            bonusData.calculateDegradation().times(100).toInt(),
                            "Destroy Watch Immune"
                        )
                    }
                }
            }
            ?: ""
    }

    fun outputXML(): String {
        val equipmentXMLLine = "<equipment>\n%s\n</equipment>\n"
        return """
            ${equipmentXMLLine.format(equippedAgpFile?.outputXML() ?: "")}
            ${equipmentXMLLine.format(equippedPci0File?.outputXML() ?: "")}
            ${equipmentXMLLine.format(equippedPci1File?.outputXML() ?: "")}
        """.trimIndent()
    }

    companion object {
        const val DEGRADE_RATE = 1400000

        @JvmField
        var commodityAmounts =
            arrayOf(
                intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                intArrayOf(0, 0, 0, 2, 4, 6, 8, 10, 12, 14),
                intArrayOf(0, 0, 0, 0, 0, 4, 8, 12, 16, 20),
                intArrayOf(0, 0, 0, 0, 0, 0, 0, 6, 12, 18),
                intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 8, 16)
            )

        const val AGP = 0
        const val PCI0 = 1
        const val PCI1 = 2

        const val FREEZE_IMMUNE = 8
        const val DESTROY_WATCH_IMMUNE = 9
    }
}

private fun Map<*, *>.requireInt(key: String): Int =
    get(key)?.toString()?.toIntOrNull()
        ?: error("Missing or invalid equipment field: $key")

private fun Map<*, *>.optionalAttribute(attributeKey: String, qualityKey: String): EquipmentAttribute? {
    val attribute = get(attributeKey)?.toString()?.toIntOrNull() ?: return null
    val quality = get(qualityKey)?.toString()?.toIntOrNull() ?: return null
    return EquipmentAttribute(attribute, quality)
}
