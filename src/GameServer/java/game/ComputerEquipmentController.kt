package game

import game.payload.FloatCommandPayload
import game.payload.SaveFileRequestPayload

class ComputerEquipmentController(private val computer: Computer) {
    private val sheet: EquipmentSheet
        get() = computer.equipmentSheet

    fun equip(position: Int, name: String?) {
        equipInternal(position, getHackerFileFromName(position, name), sendMessage = true)
    }

    fun equip(position: Int, card: HackerFile?) {
        equipInternal(position, card, sendMessage = false)
    }

    fun restore(position: Int, card: HackerFile?) {
        sheet.setEquippedCard(position, card)
    }

    fun repair(position: Int) {
        repair(sheet.getEquippedCard(position))
    }

    fun repair(equipment: HackerFile?) {
        equipment ?: return

        val commodityUsed = buildCommodityUsage(equipment)
        val commodityFailed = commodityUsed.indices.any { computer.getCommodity(it).toInt() < commodityUsed[it] }

        if (commodityFailed) {
            computer.addMessage(
                MessageHandler.REPAIR_FAIL_NOT_ENOUGH_COMMODITIES,
                arrayOf(equipment.name, buildCommodityMessage(commodityUsed)),
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
                    Computer.requiredRepairLevel[repairFailedCommodity],
                ),
            )
            return
        }

        var xp = 0.0f
        for (i in commodityUsed.indices) {
            val currentAmount = computer.getCommodity(i).toInt()
            computer.setCommodityAmount(i, (currentAmount - commodityUsed[i]).toFloat())
            xp += commodityUsed[i] * computer.repairXP[i]
        }

        computer.computerHandler.addData(
            ApplicationData(
                FloatCommandPayload(com.hackwars.rpc.GameCommands.REPAIRXP.command, xp),
                0,
                computer.getIP(),
            ),
            computer.getIP(),
        )

        val content = equipment.content
        val maxQuality = content["maxquality"]?.toString()?.toFloatOrNull() ?: DEFAULT_QUALITY
        content["currentquality"] = maxQuality.toString()

        computer.setRepaired(true)
        computer.addMessage(
            MessageHandler.REPAIR_SUCCESS,
            arrayOf(equipment.name, buildCommodityMessage(commodityUsed)),
        )
    }

    fun degradeEquipped() {
        if (computer.type == Computer.NPC) {
            return
        }

        sheet.getEquipment().forEach(::degrade)
    }

    fun degrade(equipment: HackerFile?) {
        equipment ?: return

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

    private fun getHackerFileFromName(position: Int, name: String?): HackerFile? {
        val file = name?.let { computer.fileSystem.getFile("", it) } ?: return null
        val expectedType = when (position) {
            EquipmentSheet.AGP -> HackerFile.AGP
            EquipmentSheet.PCI0, EquipmentSheet.PCI1 -> HackerFile.PCI
            else -> return file
        }

        if (file.type != expectedType) {
            computer.addMessage(MessageHandler.EQUIP_FAIL_WRONG_TYPE)
            return null
        }

        return file
    }

    private fun equipInternal(position: Int, cardToEquip: HackerFile?, sendMessage: Boolean) {
        var unequipped = true
        val equippedCard = sheet.getEquippedCard(position)
        val isEquipped = equippedCard != null

        if (equippedCard != null) {
            unequipped = unequipCard(equippedCard, position)
        }

        if (!unequipped) {
            return
        }

        if (cardToEquip != null) {
            if (equipCard(cardToEquip, position, doChecks = true, cardEquipped = isEquipped)) {
                if (sendMessage && equippedCard != null) {
                    computer.addMessage(MessageHandler.EQUIP_SUCCESS, arrayOf(cardToEquip.name))
                }
            } else if (equippedCard != null) {
                restore(position, equippedCard)
            }
        }
    }

    private fun equipCard(cardToEquip: HackerFile, position: Int, doChecks: Boolean, cardEquipped: Boolean): Boolean {
        if (doChecks && !addAllowed(cardToEquip, cardEquipped)) {
            return false
        }

        computer.fileSystem.deleteFile("", cardToEquip.name)
        sheet.setEquippedCard(position, cardToEquip)
        return true
    }

    private fun unequipCard(equippedCard: HackerFile, position: Int): Boolean {
        if (!removeAllowed(equippedCard)) {
            return false
        }

        sheet.clearEquippedCard(position)
        computer.computerHandler.addData(
            ApplicationData(SaveFileRequestPayload("", equippedCard), 0, computer.getIP()),
            computer.getIP(),
        )
        return true
    }

    private fun addAllowed(cardToEquip: HackerFile, cardEquipped: Boolean): Boolean {
        val cardTotals = sheet.getCardTotals(cardToEquip)
        val cpuRemaining = computer.maximumCPULoad - computer.baseCPULoad
        if (cpuRemaining + cardTotals.cpuBonus < 0.0f) {
            computer.addMessage(MessageHandler.EQUIP_FAIL_CPU_RESTRICTIONS)
            return false
        }

        var spaceLeft = computer.fileSystem.getSpaceLeft()
        if (!cardEquipped) {
            spaceLeft++
        }
        if (spaceLeft + cardTotals.hdBonus < 0) {
            computer.addMessage(MessageHandler.EQUIP_FAIL_HD_FULL)
            return false
        }

        val watchSpaceLeft = computer.maximumWatches - computer.watchHandler.watchCount
        if (watchSpaceLeft + cardTotals.watchBonus < 0) {
            computer.addMessage(MessageHandler.EQUIP_FAIL_WATCH_RESTRICTIONS)
            return false
        }

        return true
    }

    private fun removeAllowed(card: HackerFile): Boolean {
        val cpuRemaining = computer.maximumCPULoad - computer.baseCPULoad
        val cpuBonus = sheet.getCPUBonus(computer.maximumCPUNoBonus, computer.baseCPULoad, computer.attacking, card)
        if (cpuRemaining - cpuBonus < 0.0f) {
            computer.addMessage(MessageHandler.UNEQUIP_FAIL_CPU_RESTRICTIONS)
            return false
        }

        val spaceLeft = computer.fileSystem.getSpaceLeft()
        val hdBonus = sheet.getHDBonus(card).toFloat()
        if (spaceLeft - hdBonus < 0) {
            computer.addMessage(MessageHandler.UNEQUIP_FAIL_HD_FULL)
            return false
        }

        val watchSpaceLeft = computer.maximumWatches - computer.watchHandler.watchCount
        val watchBonus = sheet.getWatchBonus(computer.maximumWatchesNoBonus, computer.watchHandler.watchCount, card)
        if (watchSpaceLeft - watchBonus < 0) {
            computer.addMessage(MessageHandler.UNEQUIP_FAIL_WATCH_RESTRICTIONS)
            return false
        }

        return true
    }

    private fun buildCommodityUsage(equipment: HackerFile): IntArray {
        val content = equipment.content
        val quality0 = content.requireInt("quality0")
        val quality1 = content.requireInt("quality1")

        return IntArray(EquipmentSheet.commodityAmounts.size) { commodityIndex ->
            EquipmentSheet.commodityAmounts[commodityIndex][quality0] + EquipmentSheet.commodityAmounts[commodityIndex][quality1]
        }
    }

    private fun buildCommodityMessage(commodityUsed: IntArray): String {
        val segments = (0..3).mapNotNull { index ->
            commodityUsed[index].takeIf { it > 0 }?.let { "[${it}x${Computer.commodityString[index]}]" }
        }
        return if (segments.isEmpty()) "" else segments.joinToString(separator = " ", postfix = " ")
    }

    private fun resetDegradation(content: MutableMap<Any?, Any?>) {
        content["maxquality"] = DEFAULT_QUALITY.toString()
        content["currentquality"] = DEFAULT_QUALITY.toString()
        content["lastdegrade"] = computer.currentTime.toString()
    }

    companion object {
        const val DEGRADE_RATE = 1400000
        private const val DEFAULT_QUALITY = 50.0f
    }
}
