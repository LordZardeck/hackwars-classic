package game

import game.payload.FloatCommandPayload
import game.payload.SaveFileRequestPayload
import kotlin.math.abs

const val HEAL_MODIFIER_BASE = 4
const val HEAL_MODIFIER_MIN = 1
const val BANKING_BONUS_MAX = 0.07f

data class CpuBonusData(var bonus: Float = 0.0f, var maxBonus: Float = 0.0f) {
    fun applyEquippedBonus(equippedBonuses: ArrayList<BonusData>, canApply: (BonusData) -> Boolean): CpuBonusData {
        return equippedBonuses.fold(this) { acc, data ->
            if (canApply(data)) {
                acc.maxBonus += data.cpuBonus.getOrNull(0) ?: 0.0f
                acc.bonus += data.cpuBonus.getOrNull(1) ?: 0.0f
            }

            acc
        }
    }
}

class EquipmentSheet(private val computer: Computer) {
    private var equippedAGPFile: HackerFile? = null
    private var equippedPCI0File: HackerFile? = null
    private var equippedPCI1File: HackerFile? = null

    private var equippedBonuses = ArrayList<BonusData>()

    fun getHealModifier() = (HEAL_MODIFIER_BASE + equippedBonuses.sumOf { it.healMod }).coerceAtLeast(HEAL_MODIFIER_MIN)
    fun getDamageBonus() = equippedBonuses.sumOf { it.damageBonus.toDouble() }.toFloat()
    fun getMiningBonus() = equippedBonuses.sumOf { it.miningBonus.toDouble() }.toFloat()
    fun getFreezeImmune() = equippedBonuses.firstOrNull { it.freezeImmune }?.let { return true } ?: false
    fun getDestroyWatchesImmune() =
        equippedBonuses.firstOrNull { it.destroyWatchesImmune }?.let { return true } ?: false

    fun getBankingBonus() =
        equippedBonuses.sumOf { it.bankingBonus.toDouble() }.toFloat().coerceAtMost(BANKING_BONUS_MAX)

    fun getHealBonus() = (1.0f + equippedBonuses.sumOf { it.healBonus.toDouble() }.toFloat()).coerceAtLeast(0.25f)

    val cpuBonus: Float
        get() = getCPUBonus(null)

    fun getCPUBonus(card: HackerFile?): Float {
        var (bonus, maxBonus) = CpuBonusData().applyEquippedBonus(equippedBonuses) { card == null || it.equipmentFile === card }

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

    val driveBonus = getHDBonus(null)

    fun getHDBonus(card: HackerFile?) =
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

    fun addCard(cardToEquip: HackerFile?, doChecks: Boolean, isEquipped: Boolean): Boolean {
        val check = fetchQualities(cardToEquip)
        val bd1 = getBonusData(cardToEquip, check[0], check[1])
        val bd2 = getBonusData(cardToEquip, check[2], check[3])
        var bd3: BonusData? = null
        if (!(check[4] == 0 && check[5] == 0)) {
            bd3 = getBonusData(cardToEquip, check[4], check[5])
        }

        if (doChecks) {
            val cpuRemaining = computer.maximumCPULoad - computer.baseCPULoad
            var cpuBonus = 0.0f
            cpuBonus += bd1.cpuBonus[1]
            cpuBonus += bd2.cpuBonus[1]
            if (bd3 != null) {
                cpuBonus += bd3.cpuBonus[1]
            }
            if (cpuRemaining + cpuBonus < 0.0f) {
                computer.addMessage(MessageHandler.EQUIP_FAIL_CPU_RESTRICTIONS)
                return false
            }

            var spaceLeft = computer.fileSystem.getSpaceLeft()
            if (!isEquipped) {
                spaceLeft++
            }
            var hdBonus = 0
            hdBonus += bd1.hdBonus
            hdBonus += bd2.hdBonus
            if (bd3 != null) {
                hdBonus += bd3.hdBonus
            }
            if (spaceLeft + hdBonus < 0) {
                computer.addMessage(MessageHandler.EQUIP_FAIL_HD_FULL)
                return false
            }

            val watchSpaceLeft = computer.getMaximumWatches() - computer.watchHandler.watchCount
            var watchBonus = 0
            watchBonus += bd1.watchBonus
            watchBonus += bd2.watchBonus
            if (bd3 != null) {
                watchBonus += bd3.watchBonus
            }
            if (watchSpaceLeft + watchBonus < 0) {
                computer.addMessage(MessageHandler.EQUIP_FAIL_WATCH_RESTRICTIONS)
                return false
            }
        }

        addCardToBonuses(bd1, bd2, bd3)
        return true
    }

    fun removeCardFromBonuses(ParentFile: HackerFile?) {
        val bonusIterator = equippedBonuses.iterator()
        while (bonusIterator.hasNext()) {
            val BD = bonusIterator.next() as BonusData
            if (BD.equipmentFile === ParentFile) {
                bonusIterator.remove()
            }
        }
    }

    fun addCardToBonuses(attribute1Bonus: BonusData, attribute2Bonus: BonusData, attribute3Bonus: BonusData?) {
        equippedBonuses.add(attribute1Bonus)
        equippedBonuses.add(attribute2Bonus)
        attribute3Bonus?.let(equippedBonuses::add)
    }

    private fun getBonusData(parentFile: HackerFile?, attribute: Int, quality: Int): BonusData {
        // TODO: Change the contract so that bonus data can handle an Equipment Definition to process what property to apply to
        val bonusData = BonusData(parentFile)

        EquipmentBonusType
            .fromId(attribute)
            ?.let { EquipmentDefinition.fromType(it) }
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

    fun fetchQualities(file: HackerFile?): IntArray {
        val returnMe = IntArray(6)
        val Content = file!!.content
        returnMe[0] = (Content["attribute0"] as String).toInt()
        returnMe[1] = (Content["quality0"] as String).toInt()
        returnMe[2] = (Content["attribute1"] as String).toInt()
        returnMe[3] = (Content["quality1"] as String).toInt()
        try {
            returnMe[4] = (Content["attribute2"] as String).toInt()
            returnMe[5] = (Content["quality2"] as String).toInt()
        } catch (_: Exception) {
            returnMe[4] = 0
            returnMe[5] = 0
        }
        return returnMe
    }

    private fun getHackerFileFromName(position: Int, name: String?): HackerFile? {
        var file: HackerFile? = null
        if (name != null) {
            file = computer.fileSystem.getFile("", name)
            if (position == AGP && file!!.type != HackerFile.AGP) {
                computer.addMessage(MessageHandler.EQUIP_FAIL_WRONG_TYPE)
                return null
            }
            if ((position == PCI0 || position == PCI1) && file!!.type != HackerFile.PCI) {
                computer.addMessage(MessageHandler.EQUIP_FAIL_WRONG_TYPE)
                return null
            }
        }
        return file
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

        when (position) {
            AGP -> equippedAGPFile = null
            PCI0 -> equippedPCI0File = null
            PCI1 -> equippedPCI1File = null
        }
        return true
    }

    private fun equipCard(cardToEquip: HackerFile?, position: Int, doChecks: Boolean, cardEquipped: Boolean): Boolean {
        if (!addCard(cardToEquip, doChecks, cardEquipped)) {
            return false
        }

        if (cardToEquip?.name != null) {
            computer.fileSystem.deleteFile("", cardToEquip.name)
        }

        when (position) {
            AGP -> equippedAGPFile = cardToEquip
            PCI0 -> equippedPCI0File = cardToEquip
            PCI1 -> equippedPCI1File = cardToEquip
        }

        return true
    }

    private fun equipLogic(position: Int, cardToEquip: HackerFile?, sendMessage: Boolean) {
        var unequipped = true
        var equippedCard: HackerFile? = null
        var isEquipped = false

        if (position == AGP && equippedAGPFile != null) {
            equippedCard = equippedAGPFile
        } else if (position == PCI0 && equippedPCI0File != null) {
            equippedCard = equippedPCI0File
        } else if (position == PCI1 && equippedPCI1File != null) {
            equippedCard = equippedPCI1File
        }

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
                    equipCard(equippedCard, position, false, true)
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
        return arrayOf(equippedAGPFile, equippedPCI0File, equippedPCI1File)
    }

    fun degradeEquipment() {
        if (computer.getType() != Computer.NPC) {
            equippedAGPFile?.let(::degradeEquipment)
            equippedPCI0File?.let(::degradeEquipment)
            equippedPCI1File?.let(::degradeEquipment)
        }
    }

    fun repair(equipmentId: Int) {
        (when (equipmentId) {
            0 -> equippedAGPFile
            1 -> equippedPCI0File
            2 -> equippedPCI1File
            else -> null
        })?.let(::repair)
    }

    fun repair(equipment: HackerFile?) {
        val commodityUsed = intArrayOf(0, 0, 0, 0, 0)
        val quality = (equipment!!.content["quality0"] as String).toInt()
        val quality1 = (equipment.content["quality1"] as String).toInt()

        commodityUsed[4] = commodityAmounts[4][quality] + commodityAmounts[4][quality1]
        commodityUsed[3] = commodityAmounts[3][quality] + commodityAmounts[3][quality1]
        commodityUsed[2] = commodityAmounts[2][quality] + commodityAmounts[2][quality1]
        commodityUsed[1] = commodityAmounts[1][quality] + commodityAmounts[1][quality1]
        commodityUsed[0] = commodityAmounts[0][quality] + commodityAmounts[0][quality1]

        var commodityFailed = false
        for (i in 0..4) {
            val check = computer.getCommodity(i).toInt()
            if (check < commodityUsed[i]) {
                commodityFailed = true
            }
        }

        val repairLevel = computer.repairLevel.toInt()
        if (!commodityFailed) {
            var repairFailed = false
            var repairFailString = ""
            var repairFailedCommodity = 0

            for (i in 4 downTo 0) {
                if (commodityUsed[i] > 0) {
                    if (repairLevel < Computer.requiredRepairLevel[i]) {
                        repairFailed = true
                        repairFailString = Computer.commodityString[i]
                        repairFailedCommodity = i
                        break
                    }
                }
            }

            if (!repairFailed) {
                var xp = 0.0f
                for (i in 0..4) {
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

                val Content = equipment.content
                val max = (Content["maxquality"] as String).toFloat()
                Content["currentquality"] = "" + max

                computer.setRepaired(true)
                var message = ""
                for (i in 0..3) {
                    if (commodityUsed[i] > 0) {
                        message += "[" + commodityUsed[i] + "x" + Computer.commodityString[i] + "] "
                    }
                }
                computer.addMessage(MessageHandler.REPAIR_SUCCESS, arrayOf(equipment.name, message))
            } else {
                computer.addMessage(
                    MessageHandler.REPAIR_FAIL_LEVEL,
                    arrayOf(repairFailString, Computer.requiredRepairLevel[repairFailedCommodity])
                )
            }
        } else {
            var message = ""
            for (i in 0..3) {
                if (commodityUsed[i] > 0) {
                    message += "[" + commodityUsed[i] + "x" + Computer.commodityString[i] + "] "
                }
            }
            computer.addMessage(
                MessageHandler.REPAIR_FAIL_NOT_ENOUGH_COMMODITIES,
                arrayOf(equipment.name, message)
            )
        }
    }

    fun degradeEquipment(HF: HackerFile?) {
        val Content = HF!!.content
        if (Content["maxquality"] == null || Content["maxquality"] == "" || Content["maxquality"] == "null") {
            Content["maxquality"] = "" + 50.0
            Content["currentquality"] = "" + 50.0
            Content["lastdegrade"] = "" + computer.currentTime
        } else {
            try {
                val maxQuality = (Content["maxquality"] as String).toFloat()
                var currentQuality = (Content["currentquality"] as String).toFloat()
                val lastDegrade = (Content["lastdegrade"] as String).toLong()
                if (computer.currentTime - lastDegrade >= DEGRADE_RATE.toLong()) {
                    Content["lastdegrade"] = "" + computer.currentTime
                    currentQuality--
                    if (currentQuality >= 0) {
                        Content["currentquality"] = "" + currentQuality
                    }
                }
            } catch (_: Exception) {
                Content["maxquality"] = "" + 50.0
                Content["currentquality"] = "" + 50.0
                Content["lastdegrade"] = "" + computer.currentTime
            }
        }
    }

    fun describeCard(card: HackerFile?) {
        val BonusCheck = BonusData(card)
        val attribute0 = (card!!.content["attribute0"] as String).toInt()
        val quality0 = (card.content["quality0"] as String).toInt()
        val a1 = describeAttribute(attribute0, quality0, BonusCheck)

        val attribute1 = (card.content["attribute1"] as String).toInt()
        val quality1 = (card.content["quality1"] as String).toInt()
        val a2 = describeAttribute(attribute1, quality1, BonusCheck)

        var a3 = ""
        try {
            val attribute2 = (card.content["attribute2"] as String).toInt()
            val quality2 = (card.content["quality2"] as String).toInt()
            if (a3 != "0") {
                a3 = describeAttribute(attribute2, quality2, BonusCheck)
            }
        } catch (_: Exception) {
        }

        var bonusdata = "$a1|$a2"
        if (a3 != "") {
            bonusdata += "|$a3"
        }
        card.content["bonusdata"] = bonusdata
    }

    fun describeAttribute(attribute: Int, quality: Int, bonusData: BonusData): String {
        val flatPercentBonusFormat = "%d%% %s"
        val flatBonusFormat = "%+.1f/%1$+.1f %s"
        val percentBonusFormat = "%.1f%%/%1$.1f%% %s %s"
        return EquipmentBonusType
            .fromId(attribute)
            ?.let { EquipmentDefinition.fromType(it) }
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
            ${equipmentXMLLine.format(equippedAGPFile?.outputXML() ?: "")}
            ${equipmentXMLLine.format(equippedPCI0File?.outputXML() ?: "")}
            ${equipmentXMLLine.format(equippedPCI1File?.outputXML() ?: "")}
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
