package game

import game.payload.FloatCommandPayload
import game.payload.SaveFileRequestPayload
import java.text.DecimalFormat
import java.text.NumberFormat

class EquipmentSheet(private val MyComputer: Computer) {
    private var bonusCount = 9
    private var CardType = arrayOf("AGP Card", "PCI Card", "PCI Card")
    private var hardwareCount = 2
    private var hardwareClassification = arrayOf("Value Priced", "Consumer's", "Premium", "Experimental", "Alien")
    private var HardwareDescriptions = HashMap<Any?, Any?>()

    private var AGPEquipped: HackerFile? = null
    private var PCI0Equipped: HackerFile? = null
    private var PCI1Equipped: HackerFile? = null

    var Bonuses = ArrayList<Any?>()

    init {
        var ED = EquipmentData(HEAL_RATE, EquipmentData.INT)
        ED.setBonusChart(intArrayOf(-1, -1, -1, -1, -2, -2, -2, -2, -3, -3))
        ED.setBonusNames(arrayOf("Self-Healing", "Self-Healing"))
        HardwareDescriptions[HEAL_RATE] = ED

        ED = EquipmentData(DAMAGE_BONUS, EquipmentData.FLOAT)
        ED.setBonusChart(floatArrayOf(1.0f, 1.0f, 2.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f))
        ED.setBonusNames(arrayOf("Segmenting", "Segmentation"))
        HardwareDescriptions[DAMAGE_BONUS] = ED

        ED = EquipmentData(BANKING_BONUS, EquipmentData.FLOAT)
        ED.setBonusChart(floatArrayOf(0.01f, 0.01f, 0.015f, 0.02f, 0.02f, 0.025f, 0.03f, 0.03f, 0.035f, 0.04f))
        ED.setBonusNames(arrayOf("Reimbursing", "Reimbursement"))
        HardwareDescriptions[BANKING_BONUS] = ED

        ED = EquipmentData(HEAL_COST_BONUS, EquipmentData.FLOAT)
        ED.setBonusChart(floatArrayOf(-0.05f, -0.10f, -0.15f, -0.20f, -0.25f, -0.30f, -0.35f, -0.40f, -0.45f, -0.50f))
        ED.setBonusNames(arrayOf("System Monitoring", "System Monitoring"))
        HardwareDescriptions[HEAL_COST_BONUS] = ED

        ED = EquipmentData(CPU_BONUS, EquipmentData.FLOAT)
        ED.setBonusChart(floatArrayOf(5.0f, 10.0f, 15.0f, 20.0f, 25.0f, 30.0f, 35.0f, 40.0f, 45.0f, 50.0f))
        ED.setBonusNames(arrayOf("Hyper-Threading", "Hyper-Threading"))
        HardwareDescriptions[CPU_BONUS] = ED

        ED = EquipmentData(WATCH_BONUS, EquipmentData.INT)
        ED.setBonusChart(intArrayOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5))
        ED.setBonusNames(arrayOf("RAM Optimizing", "RAM Optimization"))
        HardwareDescriptions[WATCH_BONUS] = ED

        ED = EquipmentData(HD_BONUS, EquipmentData.INT)
        ED.setBonusChart(intArrayOf(-10, -5, 5, 5, 10, 15, 15, 20, 20, 30))
        ED.setBonusNames(arrayOf("RAID Controlling", "RAID Controlling"))
        HardwareDescriptions[HD_BONUS] = ED

        ED = EquipmentData(MINING_BONUS, EquipmentData.FLOAT)
        ED.setBonusChart(floatArrayOf(1.0f, 1.0f, 2.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f))
        ED.setBonusNames(arrayOf("Redirecting", "Redirection"))
        HardwareDescriptions[MINING_BONUS] = ED

        ED = EquipmentData(FREEZE_IMMUNE, EquipmentData.BOOLEAN)
        ED.setBonusNames(arrayOf("Non-Blocking", "Non-Blocking Operations"))
        HardwareDescriptions[FREEZE_IMMUNE] = ED

        ED = EquipmentData(DESTROY_WATCH_IMMUNE, EquipmentData.BOOLEAN)
        ED.setBonusNames(arrayOf("Parity Checking", "Parity Checking"))
        HardwareDescriptions[DESTROY_WATCH_IMMUNE] = ED
    }

    fun getHealMod(): Int {
        var HEAL_MOD = 4
        for (i in 0 until Bonuses.size) {
            val BD = Bonuses[i] as BonusData
            HEAL_MOD += BD.getHealMod()
        }
        if (HEAL_MOD < 1) {
            return 1
        }
        return HEAL_MOD
    }

    fun getDamageBonus(): Float {
        var damageBonus = 0.0f
        for (i in 0 until Bonuses.size) {
            val BD = Bonuses[i] as BonusData
            damageBonus += BD.getDamageBonus()
        }
        return damageBonus
    }

    fun getMiningBonus(): Float {
        var miningBonus = 0.0f
        for (i in 0 until Bonuses.size) {
            val BD = Bonuses[i] as BonusData
            miningBonus += BD.getMiningBonus()
        }
        return miningBonus
    }

    fun getFreezeImmune(): Boolean {
        var freezeImmune = false
        for (i in 0 until Bonuses.size) {
            val BD = Bonuses[i] as BonusData
            if (BD.getFreezeImmune()) {
                freezeImmune = true
            }
        }
        return freezeImmune
    }

    fun getDestroyWatchesImmune(): Boolean {
        var destroyWatchImmune = false
        for (i in 0 until Bonuses.size) {
            val BD = Bonuses[i] as BonusData
            if (BD.getDestroyWatchesImmune()) {
                destroyWatchImmune = true
            }
        }
        return destroyWatchImmune
    }

    fun getBankingBonus(): Float {
        var bankingBonus = 0.0f
        for (i in 0 until Bonuses.size) {
            val BD = Bonuses[i] as BonusData
            bankingBonus += BD.getBankingBonus()
        }
        if (bankingBonus > 0.07f) {
            return 0.07f
        }
        return bankingBonus
    }

    fun getHealBonus(): Float {
        var healCostBonus = 1.0f
        for (i in 0 until Bonuses.size) {
            val BD = Bonuses[i] as BonusData
            healCostBonus += BD.getHealBonus()
        }
        if (healCostBonus <= 0.25) {
            healCostBonus = 0.25f
        }
        return healCostBonus
    }

    fun getCPUBonus(): Float = getCPUBonus(null)

    val cpuBonus: Float
        get() = getCPUBonus()

    fun getCPUBonus(card: HackerFile?): Float {
        var CPUBonus = 0.0f
        var CPUMaxBonus = 0.0f
        for (i in 0 until Bonuses.size) {
            val BD = Bonuses[i] as BonusData
            if (card == null || BD.getEquipmentFile() === card) {
                CPUBonus += BD.getCPUBonus()[1]
                CPUMaxBonus += BD.getCPUBonus()[0]
            }
        }
        val spaceLeft = MyComputer.maximumCPUNoBonus - MyComputer.baseCPULoad
        if (!MyComputer.attacking) {
            if (spaceLeft + CPUBonus < 0.0f) {
                var required = Math.abs(spaceLeft + CPUBonus)
                if (required + CPUBonus > CPUMaxBonus) {
                    required = CPUMaxBonus
                }
                CPUBonus += required
            }
        }
        return CPUBonus
    }

    fun getWatchBonus(): Int = getWatchBonus(null)

    fun getWatchBonus(card: HackerFile?): Int {
        var WatchBonus = 0
        for (i in 0 until Bonuses.size) {
            val BD = Bonuses[i] as BonusData
            if (card == null || BD.getEquipmentFile() === card) {
                WatchBonus += BD.getWatchBonus()
            }
        }
        val spaceLeft = MyComputer.maximumWatchesNoBonus - MyComputer.watchHandler.watchCount
        if (spaceLeft + WatchBonus < 0) {
            WatchBonus += Math.abs(spaceLeft + WatchBonus.toDouble()).toInt()
        }
        return WatchBonus
    }

    fun getHDBonus(): Int = getHDBonus(null)

    fun getHDBonus(card: HackerFile?): Int {
        var HDBonus = 0
        for (i in 0 until Bonuses.size) {
            val BD = Bonuses[i] as BonusData
            if (card == null || BD.getEquipmentFile() === card) {
                HDBonus += BD.getHDBonus()
            }
        }
        return HDBonus
    }

    fun generateHardware(rarity: Float): HackerFile {
        var maxQuality = 5
        var maxAttribute = 8
        if (rarity == HIGH.toFloat()) {
            maxAttribute = 9
        }
        if (rarity == RARE.toFloat()) {
            maxAttribute = 10
        }
        if (rarity == RARE.toFloat()) {
            maxQuality = 10
        }
        if (rarity == HIGH.toFloat()) {
            maxQuality = 8
        }
        if (rarity == MEDIUM.toFloat()) {
            maxQuality = 6
        }

        val attribute1 = (Math.random() * maxAttribute).toInt()
        var attribute2: Int
        do {
            attribute2 = (Math.random() * maxAttribute).toInt()
        } while (attribute2 == attribute1)

        var quality1 = (Math.random() * maxQuality).toInt()
        val quality2 =
            if (quality1 < 2) {
                var q: Int
                do {
                    q = (Math.random() * maxQuality).toInt()
                } while (q < 2)
                q
            } else {
                (Math.random() * maxQuality).toInt()
            }

        if (attribute1 == 8 || attribute1 == 9) {
            quality1 = 9
        }
        var quality2Var = quality2
        if (attribute2 == 8 || attribute2 == 9) {
            quality2Var = 9
        }

        val hardwareType = (Math.random() * hardwareCount).toInt()
        var itemName = ""
        var ED = HardwareDescriptions[attribute1] as EquipmentData
        var BonusNames = ED.getBonusNames()
        itemName += BonusNames[0].toString() + " "
        itemName += CardType[hardwareType] + " "
        ED = HardwareDescriptions[attribute2] as EquipmentData
        BonusNames = ED.getBonusNames()
        itemName += "of " + BonusNames[1]

        val HF = HackerFile(hardwareType + 18)
        HF.setName(CardType[hardwareType] + ".license")
        HF.setDescription(itemName)
        val Keys = HashMap<Any?, Any?>()
        Keys["attribute0"] = "" + attribute1
        Keys["attribute1"] = "" + attribute2
        Keys["attribute2"] = "" + 0
        Keys["quality0"] = "" + quality1
        Keys["quality1"] = "" + quality2Var
        Keys["quality2"] = "" + 0

        val durability = floatArrayOf(
            50.0f, 50.0f, 50.0f, 50.0f, 50.0f,
            50.0f, 50.0f, 50.0f, 50.0f, 50.0f,
            100.0f, 100.0f, 100.0f, 100.0f, 100.0f,
            150.0f, 150.0f, 150.0f, 200.0f, 200.0f, 250.0f
        )
        val choice = (Math.random() * durability.size.toDouble()).toInt()
        Keys["maxquality"] = "" + durability[choice]
        Keys["currentquality"] = "" + durability[choice]

        HF.setContent(Keys)
        HF.setQuantity(1)
        HF.setMaker("Low")
        if (quality1 + quality2Var > 5) {
            HF.setMaker("Medium")
        }
        if (quality1 + quality2Var >= 14) {
            HF.setMaker("High")
        }
        if (quality1 + quality2Var >= 18) {
            HF.setMaker("Rare")
        }
        return HF
    }

    fun removeAllowed(card: HackerFile?): Boolean {
        val cpuRemaining = MyComputer.maximumCPULoad - MyComputer.baseCPULoad
        val cpuBonus = getCPUBonus(card)
        if (cpuRemaining - cpuBonus < 0.0f) {
            MyComputer.addMessage(MessageHandler.UNEQUIP_FAIL_CPU_RESTRICTIONS)
            return false
        }

        val spaceLeft = MyComputer.fileSystem.getSpaceLeft()
        val hdBonus = getHDBonus(card).toFloat()
        if (spaceLeft - hdBonus < 0) {
            MyComputer.addMessage(MessageHandler.UNEQUIP_FAIL_HD_FULL)
            return false
        }

        val watchSpaceLeft = MyComputer.getMaximumWatches() - MyComputer.watchHandler.watchCount
        val watchBonus = getWatchBonus(card)
        if (watchSpaceLeft - watchBonus < 0) {
            MyComputer.addMessage(MessageHandler.UNEQUIP_FAIL_WATCH_RESTRICTIONS)
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
            val cpuRemaining = MyComputer.maximumCPULoad - MyComputer.baseCPULoad
            var cpuBonus = 0.0f
            cpuBonus += bd1.getCPUBonus()[1]
            cpuBonus += bd2.getCPUBonus()[1]
            if (bd3 != null) {
                cpuBonus += bd3.getCPUBonus()[1]
            }
            if (cpuRemaining + cpuBonus < 0.0f) {
                MyComputer.addMessage(MessageHandler.EQUIP_FAIL_CPU_RESTRICTIONS)
                return false
            }

            var spaceLeft = MyComputer.fileSystem.getSpaceLeft()
            if (!isEquipped) {
                spaceLeft++
            }
            var hdBonus = 0
            hdBonus += bd1.getHDBonus()
            hdBonus += bd2.getHDBonus()
            if (bd3 != null) {
                hdBonus += bd3.getHDBonus()
            }
            if (spaceLeft + hdBonus < 0) {
                MyComputer.addMessage(MessageHandler.EQUIP_FAIL_HD_FULL)
                return false
            }

            val watchSpaceLeft = MyComputer.getMaximumWatches() - MyComputer.watchHandler.watchCount
            var watchBonus = 0
            watchBonus += bd1.getWatchBonus()
            watchBonus += bd2.getWatchBonus()
            if (bd3 != null) {
                watchBonus += bd3.getWatchBonus()
            }
            if (watchSpaceLeft + watchBonus < 0) {
                MyComputer.addMessage(MessageHandler.EQUIP_FAIL_WATCH_RESTRICTIONS)
                return false
            }
        }

        addCardToBonuses(bd1, bd2, bd3)
        return true
    }

    fun removeCardFromBonuses(ParentFile: HackerFile?) {
        val bonusIterator = Bonuses.iterator()
        while (bonusIterator.hasNext()) {
            val BD = bonusIterator.next() as BonusData
            if (BD.getEquipmentFile() === ParentFile) {
                bonusIterator.remove()
            }
        }
    }

    fun addCardToBonuses(bd1: BonusData, bd2: BonusData, bd3: BonusData?) {
        Bonuses.add(bd1)
        Bonuses.add(bd2)
        if (bd3 != null) {
            Bonuses.add(bd3)
        }
    }

    private fun getBonusData(ParentFile: HackerFile?, attribute: Int, quality: Int): BonusData {
        val ED = HardwareDescriptions[attribute] as EquipmentData
        val BD = BonusData(ParentFile)

        if (attribute == HEAL_RATE) {
            val BonusChart = ED.getBonusChart() as IntArray
            val bonus = BonusChart[quality]
            BD.setHealMod(bonus)
        } else if (attribute == DAMAGE_BONUS) {
            val BonusChart = ED.getBonusChart() as FloatArray
            val bonus = BonusChart[quality]
            BD.setDamageBonus(bonus)
        } else if (attribute == MINING_BONUS) {
            val BonusChart = ED.getBonusChart() as FloatArray
            val bonus = BonusChart[quality]
            BD.setMiningBonus(bonus)
        } else if (attribute == BANKING_BONUS) {
            val BonusChart = ED.getBonusChart() as FloatArray
            val bonus = BonusChart[quality]
            BD.setBankingBonus(bonus)
        } else if (attribute == HEAL_COST_BONUS) {
            val BonusChart = ED.getBonusChart() as FloatArray
            val bonus = BonusChart[quality]
            BD.setHealBonus(bonus)
        } else if (attribute == CPU_BONUS) {
            val BonusChart = ED.getBonusChart() as FloatArray
            val bonus = BonusChart[quality]
            BD.setCPUBonus(bonus)
        } else if (attribute == WATCH_BONUS) {
            val BonusChart = ED.getBonusChart() as IntArray
            val bonus = BonusChart[quality]
            BD.setWatchBonus(bonus)
        } else if (attribute == HD_BONUS) {
            val BonusChart = ED.getBonusChart() as IntArray
            val bonus = BonusChart[quality]
            BD.setHDBonus(bonus)
        } else if (attribute == FREEZE_IMMUNE) {
            BD.setFreezeImmune(true)
        } else if (attribute == DESTROY_WATCH_IMMUNE) {
            BD.setDestroyWatchesImmune(true)
        }

        return BD
    }

    fun fetchQualities(HF: HackerFile?): IntArray {
        val returnMe = IntArray(6)
        val Content = HF!!.getContent()
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
        var EquipFile: HackerFile? = null
        if (name != null) {
            EquipFile = MyComputer.fileSystem.getFile("", name)
            if (position == AGP && EquipFile!!.getType() != HackerFile.AGP) {
                MyComputer.addMessage(MessageHandler.EQUIP_FAIL_WRONG_TYPE)
                return null
            }
            if ((position == PCI0 || position == PCI1) && EquipFile!!.getType() != HackerFile.PCI) {
                MyComputer.addMessage(MessageHandler.EQUIP_FAIL_WRONG_TYPE)
                return null
            }
        }
        return EquipFile
    }

    private fun unequipCard(equippedCard: HackerFile?, cardToEquip: HackerFile?, position: Int): Boolean {
        if (!removeAllowed(equippedCard)) {
            return false
        }

        removeCardFromBonuses(equippedCard)
        MyComputer.computerHandler.addData(
            ApplicationData(SaveFileRequestPayload("", equippedCard!!), 0, MyComputer.getIP()),
            MyComputer.getIP()
        )

        if (position == AGP) {
            AGPEquipped = null
        } else if (position == PCI0) {
            PCI0Equipped = null
        } else if (position == PCI1) {
            PCI1Equipped = null
        }
        return true
    }

    private fun equipCard(cardToEquip: HackerFile?, position: Int, doChecks: Boolean, cardEquipped: Boolean): Boolean {
        if (!addCard(cardToEquip, doChecks, cardEquipped)) {
            return false
        }

        MyComputer.fileSystem.deleteFile("", cardToEquip!!.getName())
        if (position == AGP) {
            AGPEquipped = cardToEquip
        }
        if (position == PCI0) {
            PCI0Equipped = cardToEquip
        }
        if (position == PCI1) {
            PCI1Equipped = cardToEquip
        }
        return true
    }

    private fun equipLogic(position: Int, cardToEquip: HackerFile?, sendMessage: Boolean) {
        var unequipped = true
        var equippedCard: HackerFile? = null
        var isEquipped = false

        if (position == AGP && AGPEquipped != null) {
            equippedCard = AGPEquipped
        } else if (position == PCI0 && PCI0Equipped != null) {
            equippedCard = PCI0Equipped
        } else if (position == PCI1 && PCI1Equipped != null) {
            equippedCard = PCI1Equipped
        }

        if (equippedCard != null) {
            isEquipped = true
            unequipped = unequipCard(equippedCard, cardToEquip, position)
        }

        if (!unequipped) {
            return
        }

        if (cardToEquip != null) {
            if (equipCard(cardToEquip, position, true, isEquipped)) {
                if (sendMessage && equippedCard != null) {
                    MyComputer.addMessage(MessageHandler.EQUIP_SUCCESS, arrayOf(cardToEquip.getName()))
                }
            } else {
                if (equippedCard != null) {
                    equipCard(equippedCard, position, false, true)
                }
            }
        }
    }

    fun equip(position: Int, EquipFile: HackerFile?) {
        equipLogic(position, EquipFile, false)
    }

    fun equip(position: Int, name: String?) {
        val cardToEquip = getHackerFileFromName(position, name)
        equipLogic(position, cardToEquip, true)
    }

    fun getEquipment(): Array<Any?> {
        val returnMe = arrayOfNulls<Any>(3)
        returnMe[0] = AGPEquipped
        returnMe[1] = PCI0Equipped
        returnMe[2] = PCI1Equipped
        return returnMe
    }

    fun degradeEquipment() {
        if (MyComputer.getType() != Computer.NPC) {
            if (AGPEquipped != null) {
                degradeEquipment(AGPEquipped)
            }
            if (PCI0Equipped != null) {
                degradeEquipment(PCI0Equipped)
            }
            if (PCI1Equipped != null) {
                degradeEquipment(PCI1Equipped)
            }
        }
    }

    fun repair(EquipmentID: Int) {
        var Equipment: HackerFile? = null
        if (EquipmentID == 0) {
            Equipment = AGPEquipped
        }
        if (EquipmentID == 1) {
            Equipment = PCI0Equipped
        }
        if (EquipmentID == 2) {
            Equipment = PCI1Equipped
        }
        if (Equipment == null) {
            return
        }
        repair(Equipment)
    }

    fun repair(Equipment: HackerFile?) {
        val commodityUsed = intArrayOf(0, 0, 0, 0, 0)
        val quality = (Equipment!!.getContent()["quality0"] as String).toInt()
        val quality1 = (Equipment.getContent()["quality1"] as String).toInt()

        commodityUsed[4] = commodityAmounts[4][quality] + commodityAmounts[4][quality1]
        commodityUsed[3] = commodityAmounts[3][quality] + commodityAmounts[3][quality1]
        commodityUsed[2] = commodityAmounts[2][quality] + commodityAmounts[2][quality1]
        commodityUsed[1] = commodityAmounts[1][quality] + commodityAmounts[1][quality1]
        commodityUsed[0] = commodityAmounts[0][quality] + commodityAmounts[0][quality1]

        var commodityFailed = false
        for (i in 0..4) {
            val check = MyComputer.getCommodity(i).toInt()
            if (check < commodityUsed[i]) {
                commodityFailed = true
            }
        }

        val repairLevel = MyComputer.repairLevel.toInt()
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
                    val check = MyComputer.getCommodity(i).toInt()
                    MyComputer.setCommodityAmount(i, (check - commodityUsed[i]).toFloat())
                    xp += commodityUsed[i] * MyComputer.repairXP[i]
                }

                MyComputer.computerHandler.addData(
                    ApplicationData(
                        FloatCommandPayload(com.hackwars.rpc.GameCommands.REPAIRXP.command, xp),
                        0,
                        MyComputer.getIP()
                    ),
                    MyComputer.getIP()
                )

                val Content = Equipment.getContent()
                val max = (Content["maxquality"] as String).toFloat()
                Content["currentquality"] = "" + max

                MyComputer.setRepaired(true)
                var message = ""
                for (i in 0..3) {
                    if (commodityUsed[i] > 0) {
                        message += "[" + commodityUsed[i] + "x" + Computer.commodityString[i] + "] "
                    }
                }
                MyComputer.addMessage(MessageHandler.REPAIR_SUCCESS, arrayOf(Equipment.getName(), message))
            } else {
                MyComputer.addMessage(
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
            MyComputer.addMessage(
                MessageHandler.REPAIR_FAIL_NOT_ENOUGH_COMMODITIES,
                arrayOf(Equipment.getName(), message)
            )
        }
    }

    fun degradeEquipment(HF: HackerFile?) {
        val Content = HF!!.getContent()
        if (Content["maxquality"] == null || Content["maxquality"] == "" || Content["maxquality"] == "null") {
            Content["maxquality"] = "" + 50.0
            Content["currentquality"] = "" + 50.0
            Content["lastdegrade"] = "" + MyComputer.currentTime
        } else {
            try {
                val maxQuality = (Content["maxquality"] as String).toFloat()
                var currentQuality = (Content["currentquality"] as String).toFloat()
                val lastDegrade = (Content["lastdegrade"] as String).toLong()
                if (MyComputer.currentTime - lastDegrade >= DEGRADE_RATE.toLong()) {
                    Content["lastdegrade"] = "" + MyComputer.currentTime
                    currentQuality--
                    if (currentQuality >= 0) {
                        Content["currentquality"] = "" + currentQuality
                    }
                }
            } catch (_: Exception) {
                Content["maxquality"] = "" + 50.0
                Content["currentquality"] = "" + 50.0
                Content["lastdegrade"] = "" + MyComputer.currentTime
            }
        }
    }

    fun describeCard(TheCard: HackerFile?) {
        val BonusCheck = BonusData(TheCard)
        val attribute0 = (TheCard!!.getContent()["attribute0"] as String).toInt()
        val quality0 = (TheCard.getContent()["quality0"] as String).toInt()
        val a1 = describeAttribute(attribute0, quality0, BonusCheck)

        val attribute1 = (TheCard.getContent()["attribute1"] as String).toInt()
        val quality1 = (TheCard.getContent()["quality1"] as String).toInt()
        val a2 = describeAttribute(attribute1, quality1, BonusCheck)

        var a3 = ""
        try {
            val attribute2 = (TheCard.getContent()["attribute2"] as String).toInt()
            val quality2 = (TheCard.getContent()["quality2"] as String).toInt()
            if (a3 != "0") {
                a3 = describeAttribute(attribute2, quality2, BonusCheck)
            }
        } catch (_: Exception) {
        }

        var bonusdata = "$a1|$a2"
        if (a3 != "") {
            bonusdata += "|$a3"
        }
        TheCard.getContent()["bonusdata"] = bonusdata
    }

    fun describeAttribute(attribute: Int, quality: Int, BD: BonusData): String {
        var bonusString = ""
        val ED = HardwareDescriptions[attribute] as EquipmentData
        val decimalFormat: NumberFormat = DecimalFormat("0.00%")
        val nf: NumberFormat = DecimalFormat("0.0")
        if (attribute == HEAL_RATE) {
            val BonusChart = ED.getBonusChart() as IntArray
            val bonus = BonusChart[quality]
            BD.setHealBonus(bonus.toFloat())
            if (BD.getHealBonus() > 0) {
                var value = BD.getHealBonus().toDouble()
                value /= 4.0
                val intvalue = (value * 100.0).toInt()
                val max = bonus.toFloat() / 4.0f
                val maxS = decimalFormat.format(max)
                bonusString = "" + intvalue + "%/" + maxS + " Slower Heal Rate"
            } else {
                var value = -1.0 * BD.getHealBonus()
                value /= 4.0
                val intvalue = (value * 100.0).toInt()
                val max = bonus.toFloat() / 4.0f
                val maxS = decimalFormat.format(-max)
                bonusString = "" + intvalue + "%/" + maxS + " Faster Heal Rate"
            }
        } else if (attribute == DAMAGE_BONUS) {
            val BonusChart = ED.getBonusChart() as FloatArray
            val bonus = BonusChart[quality]
            BD.setDamageBonus(bonus)
            bonusString =
                if (BD.getDamageBonus() < 0) {
                    "" + nf.format(BD.getDamageBonus()) + "/" + nf.format(bonus) + " to Attack Damage"
                } else {
                    "+" + nf.format(BD.getDamageBonus()) + "/" + nf.format(bonus) + " to Attack Damage"
                }
        } else if (attribute == MINING_BONUS) {
            val BonusChart = ED.getBonusChart() as FloatArray
            val bonus = BonusChart[quality]
            BD.setMiningBonus(bonus)
            bonusString =
                if (BD.getMiningBonus() < 0) {
                    "" + nf.format(BD.getMiningBonus()) + "/" + nf.format(bonus) + " to Redirecting Damage"
                } else {
                    "+" + nf.format(BD.getMiningBonus()) + "/" + nf.format(bonus) + " to Redirecting Damage"
                }
        } else if (attribute == BANKING_BONUS) {
            val BonusChart = ED.getBonusChart() as FloatArray
            val bonus = BonusChart[quality]
            BD.setBankingBonus(bonus)
            if (BD.getBankingBonus() > 0) {
                val value = BD.getBankingBonus().toDouble()
                val intvalue = (value * 1000.0).toInt()
                bonusString =
                    "" + (intvalue.toDouble() / 10.0) + "%/" + decimalFormat.format(bonus) + " Lower Banking Costs"
            } else {
                val value = -1.0 * BD.getBankingBonus()
                val intvalue = (value * 1000.0).toInt()
                bonusString =
                    "" + (intvalue.toDouble() / 10.0) + "%/" + decimalFormat.format(-bonus) + " Higher Banking Costs"
            }
        } else if (attribute == HEAL_COST_BONUS) {
            val BonusChart = ED.getBonusChart() as FloatArray
            val bonus = BonusChart[quality]
            BD.setHealBonus(bonus.toFloat())
            if (BD.getHealBonus() > 0) {
                val value = BD.getHealBonus().toDouble()
                val intvalue = (value * 100.0).toInt()
                bonusString = "" + intvalue + "%/" + decimalFormat.format(bonus) + " Higher Healing Costs"
            } else {
                val value = -1.0 * BD.getHealBonus()
                val intvalue = (value * 100.0).toInt()
                bonusString = "" + intvalue + "%/" + decimalFormat.format(-bonus) + " Lower Healing Costs"
            }
        } else if (attribute == CPU_BONUS) {
            val BonusChart = ED.getBonusChart() as FloatArray
            val bonus = BonusChart[quality]
            BD.setCPUBonus(bonus)
            bonusString =
                if (BD.getCPUBonus()[1] >= 0) {
                    "+" + nf.format(BD.getCPUBonus()[1]) + "/" + nf.format(bonus) + " CPU Points"
                } else {
                    "" + nf.format(BD.getCPUBonus()[1]) + "/" + nf.format(bonus) + " CPU Points"
                }
        } else if (attribute == WATCH_BONUS) {
            val BonusChart = ED.getBonusChart() as IntArray
            val bonus = BonusChart[quality]
            BD.setWatchBonus(bonus)
            bonusString =
                if (BD.getWatchBonus() >= 0) {
                    "+" + BD.getWatchBonus() + "/" + bonus + " Watch"
                } else {
                    "" + BD.getWatchBonus() + "/" + bonus + " Watch"
                }
        } else if (attribute == HD_BONUS) {
            val BonusChart = ED.getBonusChart() as IntArray
            val bonus = BonusChart[quality]
            BD.setHDBonus(bonus)
            bonusString =
                if (BD.getHDBonus() >= 0) {
                    "+" + BD.getHDBonus() + "/" + bonus + " HD Space"
                } else {
                    "" + BD.getHDBonus() + "/" + bonus + " HD Space"
                }
        } else if (attribute == FREEZE_IMMUNE) {
            val value = BD.calculateDegradation()
            val intvalue = (value * 100.0).toInt()
            bonusString = "" + intvalue + "% Freeze Immune"
        } else if (attribute == DESTROY_WATCH_IMMUNE) {
            val value = BD.calculateDegradation()
            val intvalue = (value * 100.0).toInt()
            bonusString = "" + intvalue + "% Destroy Watch Immune"
        }

        return bonusString
    }

    fun outputXML(): String {
        var returnMe = "<equipment>\n"
        if (AGPEquipped != null) {
            returnMe += AGPEquipped!!.outputXML()
        }
        returnMe += "</equipment>\n"

        returnMe += "<equipment>\n"
        if (PCI0Equipped != null) {
            returnMe += PCI0Equipped!!.outputXML()
        }
        returnMe += "</equipment>\n"

        returnMe += "<equipment>\n"
        if (PCI1Equipped != null) {
            returnMe += PCI1Equipped!!.outputXML()
        }
        returnMe += "</equipment>\n"

        return returnMe
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

        const val LOW = 0
        const val MEDIUM = 1
        const val HIGH = 2
        const val RARE = 3

        const val AGP = 0
        const val PCI0 = 1
        const val PCI1 = 2

        const val HEAL_RATE = 0
        const val DAMAGE_BONUS = 1
        const val WATCH_BONUS = 2
        const val HD_BONUS = 3
        const val BANKING_BONUS = 4
        const val HEAL_COST_BONUS = 5
        const val CPU_BONUS = 6
        const val MINING_BONUS = 7
        const val FREEZE_IMMUNE = 8
        const val DESTROY_WATCH_IMMUNE = 9
    }
}
