package game

import game.payload.CombatFirewallXpPayload
import game.payload.DamagePayload
import java.util.HashMap

class NewFireWall() {
    private var MyComputerHandler: NetworkSwitch? = null
    private var hackerFile: HackerFile? = null

    private var name: String? = null
    private var equipLevel = 0
    private var maxAttack = 0
    private var baseDamageModifier = 0.0f
    private var cpuCost = 0.0f
    private var description: String? = null
    private var maker: String? = null
    private var maxSpecialAttributeValue = 0.0f

    private var bankDamageModifier = 0.0f
    private var redirectDamageModifier = 0.0f
    private var attackDamageModifier = 0.0f
    private var ftpDamageModifier = 0.0f
    private var httpDamageModifier = 0.0f
    private var parentPort: Port? = null
    private var attackDamage = 0.0f
    private var price = 0.0f
    private var pettyCashReducePct = 1.0f
    private var pettyCashFailPct = 1.0f
    private var dailyPayChangeFailPct = 1.0f
    private var dailyPayChangeReducePct = 1.0f
    private var stealFileFailPct = 1.0f
    private var installScriptFailPct = 1.0f

    constructor(MyComputerHandler: NetworkSwitch?) : this() {
        this.MyComputerHandler = MyComputerHandler
    }

    fun getPettyCashReduction(opponentIP: String?): Float = pettyCashReducePct

    fun getPettyCashFail(opponentIP: String?): Boolean {
        if (pettyCashFailPct < 1.0f) {
            val x = Math.random().toFloat()
            if (x < pettyCashFailPct) {
                return true
            }
        }
        return false
    }

    fun getStealFileFail(opponentIP: String?): Boolean {
        if (stealFileFailPct < 1.0f) {
            val x = Math.random().toFloat()
            if (x < stealFileFailPct) {
                return true
            }
        }
        return false
    }

    fun getChangeDailyPayFail(opponentIP: String?): Boolean {
        if (dailyPayChangeFailPct < 1.0f) {
            val x = Math.random().toFloat()
            if (x < dailyPayChangeFailPct) {
                return true
            }
        }
        return false
    }

    fun getChangeDailyPayReduction(opponentIP: String?): Float = dailyPayChangeReducePct

    fun getInstallScriptFail(opponentIP: String?): Boolean {
        if (installScriptFailPct < 1.0f) {
            val x = Math.random().toFloat()
            if (x < installScriptFailPct) {
                return true
            }
        }
        return false
    }

    fun setBankDamageModifier(damageModifier: Float) {
        this.bankDamageModifier = damageModifier
    }

    fun getBankDamageModifier(): Float = bankDamageModifier

    fun setAttackDamageModifier(damageModifier: Float) {
        this.attackDamageModifier = damageModifier
    }

    fun getAttackDamageModifier(): Float = attackDamageModifier

    fun setRedirectDamageModifier(damageModifier: Float) {
        this.redirectDamageModifier = damageModifier
    }

    fun getRedirectDamageModifier(): Float = redirectDamageModifier

    fun setFTPDamageModifier(damageModifier: Float) {
        this.ftpDamageModifier = damageModifier
    }

    fun getFTPDamageModifier(): Float = ftpDamageModifier

    fun setHTTPDamageModifier(damageModifier: Float) {
        this.httpDamageModifier = damageModifier
    }

    fun getHTTPDamageModifier(): Float = httpDamageModifier

    fun setParentPort(parentPort: Port?) {
        this.parentPort = parentPort
    }

    fun getCPUCost(): Float = this.cpuCost

    fun setCPUCost(cpuCost: Float) {
        this.cpuCost = cpuCost
    }

    fun modifyDamage(damage: Float, ip: String?, port: Int, damageFromFireWall: Boolean): Float {
        if (parentPort!!.overHeated || parentPort!!.weakened) {
            return damage
        }
        val myComputer = parentPort!!.myComputer
        val targetComputer = MyComputerHandler!!.getMyComputerHandler().getComputer(ip) ?: return damage
        val myFirewallLevel = myComputer.getLevel((myComputer.stats["FireWall"] as Float).toFloat())
        val targetFirewallLevel = targetComputer.getLevel((targetComputer.stats["FireWall"] as Float).toFloat())
        var missPct = (targetFirewallLevel - myFirewallLevel) * COMPLETE_MISS_PCT / 100.0
        if (missPct < 0.0) {
            missPct = 0.0
        }
        val roll = Math.random()
        if (roll < missPct) {
            return damage
        }

        var damageModifier = 0.0f
        when (parentPort!!.type) {
            Port.BANKING -> damageModifier = getBankDamageModifier()
            Port.FTP -> damageModifier = getFTPDamageModifier()
            Port.ATTACK -> damageModifier = getAttackDamageModifier()
            Port.HTTP -> damageModifier = getHTTPDamageModifier()
            Port.REDIRECT -> damageModifier = getRedirectDamageModifier()
        }

        if (damageModifier < 1.0f) {
            val variance = Math.random().toFloat() * maxDamageVariationPercent / 100.0f
            val plusMinus = Math.random().toFloat()
            if (plusMinus < 0.5f) {
                damageModifier += variance
            } else {
                damageModifier -= variance
            }
        }

        var mod_damage = damage * damageModifier
        if (parentPort!!.health - mod_damage < 0.0f) {
            mod_damage = parentPort!!.health
        }

        if (this.attackDamage > 0.0f && !damageFromFireWall) {
            var fwAttackBack = this.attackDamage
            val fwVariance = Math.random().toFloat() * maxFWDamageVariationPercent / 100.0f
            val fwPlusMinus = Math.random().toFloat()
            if (fwPlusMinus < 0.5f) {
                fwAttackBack += fwVariance
            } else {
                fwAttackBack -= fwVariance
            }
            val AD = ApplicationData(
                DamagePayload(
                    damage = fwAttackBack,
                    targetIp = parentPort!!.IP,
                    targetPort = parentPort!!.number,
                    damageFromFireWall = true,
                    zombieSource = null,
                    windowHandle = parentPort!!.windowHandle,
                    commodityId = -1
                ),
                port,
                parentPort!!.IP
            ).withSourcePort(parentPort!!.number)
            MyComputerHandler!!.addData(AD, ip)
        }

        val xp = damage - mod_damage
        if (xp > 0) {
            MyComputerHandler!!.addData(
                ApplicationData(CombatFirewallXpPayload(xp), 0, ""),
                parentPort!!.IP
            )
        }

        return mod_damage
    }

    fun generateFirewall(name: String?): HackerFile? {
        this.name = name
        var price = 0.0f
        val firewall = firewalls[name] as Array<Any?>?
        if (firewall == null) {
            return null
        }

        this.maxAttack = (firewall[ATTACK_RANGE] as Int) + (firewall[MIN_ATTACK] as Int)
        this.baseDamageModifier = ("" + firewall[DAMAGE_MODIFIER]).toFloat()
        this.cpuCost = ("" + firewall[CPU]).toFloat()
        this.description = "" + firewall[DESCRIPTION]
        this.maker = "" + firewall[MAKER]
        this.maxSpecialAttributeValue = ("" + firewall[MAX_SPECIAL_VALUE]).toFloat()

        val HF = HackerFile(HackerFile.NEW_FIREWALL)
        HF.name = this.name
        HF.setDescription(this.description)
        HF.maker = this.maker
        HF.quantity = 1
        HF.cPUCost = this.cpuCost
        val content = HashMap<Any?, Any?>()

        price += setBankAbsorption(content)
        price += setFTPAbsorption(content)
        price += setAttackAbsorption(content)
        price += setHTTPAbsorption(content)
        price += setRedirectAbsorption(content)
        price += generateSpecialAttributes(content)
        price += generateAttackValue(content)

        content["equip_level"] = "" + firewall[EQUIP_LEVEL]
        price += calculatePrice()
        content["store_price"] = "" + price
        content["name"] = name
        HF.content = LegacyHackerFileCodec.parseLegacyContent(HF.kind, content)
        return HF
    }

    private fun generateAttackValue(content: HashMap<Any?, Any?>): Float {
        val firewall = firewalls[name] as Array<Any?>
        val maxValue = (firewall[ATTACK_RANGE] as Int).toFloat()
        val valuePerBucket = maxValue / weights.size
        val i = getWeightedBucket()
        val actualValue = i * valuePerBucket + (firewall[MIN_ATTACK] as Int)
        val value = Math.round(actualValue)
        val price =
            (value - (firewall[MIN_ATTACK] as Int)) *
                ((firewalls[name] as Array<Any?>)[PRICE_DAMAGE_MULTIPLIER] as Float)
        content["attack_damage"] = "" + value
        return price
    }

    private fun generateSpecialAttributes(content: HashMap<Any?, Any?>): Float {
        val attribute1 = generateSpecialAttribute(content, "", 1)
        val attribute2 = generateSpecialAttribute(content, attribute1[0] as String, 2)
        return (attribute1[1] as Float) + (attribute2[1] as Float)
    }

    private fun generateSpecialAttribute(content: HashMap<Any?, Any?>, otherAttribute: String, num: Int): Array<Any?> {
        val special = HashMap<Any?, Any?>()
        var name = ""
        var value = ""
        var longDescription = ""
        var shortDescription = ""
        var price = 0.0f
        var `val` = Math.random()
        if (`val` > pctChanceOfNoSpecialAttribute) {
            do {
                `val` = Math.random() * specialAttributes.size
                name =
                    if (`val` <= 1) {
                        "emptyPettyCash()fail"
                    } else if (`val` <= 2) {
                        "emptyPettyCash()reduce"
                    } else if (`val` <= 3) {
                        "stealFile()fail"
                    } else if (`val` <= 4) {
                        "changeDailyPay()fail"
                    } else if (`val` <= 5) {
                        "changeDailyPay()reduce"
                    } else {
                        "installScript()fail"
                    }
            } while (name == otherAttribute)

            val attributeValues = specialAttributes[name] as Array<Any?>
            val firewall = firewalls[this.name] as Array<Any?>
            longDescription = "" + attributeValues[LONG_DESCRIPTION]
            shortDescription = "" + attributeValues[SHORT_DESCRIPTION]
            price = attributeValues[BASE_ATTRIBUTE_PRICE] as Float
            val percentageMultiplier = firewall[ATTRIBUTE_PERCENTAGE_MULTIPLIER] as Float
            val min = 0.2f
            val range = this.maxSpecialAttributeValue - min
            val percentage = (min * 100) + Math.round(Math.random().toFloat() * range * 100.0f)
            price += percentage * percentageMultiplier
            value = "" + percentage / 100.0f
        }

        special["name"] = name
        special["long_desc"] = longDescription
        special["short_desc"] = shortDescription
        special["value"] = value
        content["specialAttribute$num"] = special
        return arrayOf(name, price)
    }

    private fun calculatePrice(): Float = ("" + (firewalls[name] as Array<Any?>)[BASE_PRICE]).toFloat()

    private fun setBankAbsorption(content: HashMap<Any?, Any?>): Float {
        val change = calculateAbsorption()
        this.bankDamageModifier = baseDamageModifier + change
        content["bank_damage_modifier"] = "" + this.bankDamageModifier
        return change * ABSORPTION_PRICE_MULTIPLIER
    }

    private fun setAttackAbsorption(content: HashMap<Any?, Any?>): Float {
        val change = calculateAbsorption()
        this.attackDamageModifier = baseDamageModifier + change
        content["attack_damage_modifier"] = "" + this.attackDamageModifier
        return change * ABSORPTION_PRICE_MULTIPLIER
    }

    private fun setRedirectAbsorption(content: HashMap<Any?, Any?>): Float {
        val change = calculateAbsorption()
        this.redirectDamageModifier = baseDamageModifier + change
        content["redirect_damage_modifier"] = "" + this.redirectDamageModifier
        return change * ABSORPTION_PRICE_MULTIPLIER
    }

    private fun setFTPAbsorption(content: HashMap<Any?, Any?>): Float {
        val change = calculateAbsorption()
        this.ftpDamageModifier = baseDamageModifier + change
        content["ftp_damage_modifier"] = "" + this.ftpDamageModifier
        return change * ABSORPTION_PRICE_MULTIPLIER
    }

    private fun setHTTPAbsorption(content: HashMap<Any?, Any?>): Float {
        val change = calculateAbsorption()
        this.httpDamageModifier = baseDamageModifier + change
        content["http_damage_modifier"] = "" + this.httpDamageModifier
        return change * ABSORPTION_PRICE_MULTIPLIER
    }

    private fun calculateAbsorption(): Float {
        val i = getWeightedBucket()
        var modifier = -0.05f
        modifier += (i * 0.005).toFloat()
        return modifier
    }

    fun getWeightedBucket(): Int {
        val value = (Math.random() * 100).toFloat()
        var totalWeight = 0
        var i = 0
        while (i < weights.size) {
            totalWeight += weights[i]
            if (value <= totalWeight) {
                break
            }
            i++
        }
        return i
    }

    fun resetVariables() {
        this.name = ""
        this.equipLevel = 0
        this.cpuCost = 0.0f
        this.bankDamageModifier = 1.0f
        this.redirectDamageModifier = 1.0f
        this.attackDamageModifier = 1.0f
        this.ftpDamageModifier = 1.0f
        this.httpDamageModifier = 1.0f
        this.attackDamage = 0.0f
        this.pettyCashReducePct = 1.0f
        this.pettyCashFailPct = 1.0f
        this.dailyPayChangeFailPct = 1.0f
        this.dailyPayChangeReducePct = 1.0f
        this.stealFileFailPct = 1.0f
        this.installScriptFailPct = 1.0f
    }

    fun loadHackerFile(hf: HackerFile?) {
        resetVariables()
        this.name = hf!!.name
        val content = LegacyHackerFileCodec.toLegacyContentMap(hf.kind, hf.content)
        val special1 = hf.getSpecial(1) as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val special2 = hf.getSpecial(2) as? Map<*, *> ?: emptyMap<Any?, Any?>()

        val a1 = special1["name"] as String
        var v1 = special1["value"] as String
        if (v1 == "") {
            v1 = "0"
        }
        var value = ("" + v1).toFloat()
        if (a1 == "emptyPettyCash()fail") {
            this.pettyCashFailPct = value
        } else if (a1 == "emptyPettyCash()reduce") {
            this.pettyCashReducePct = 1.0f - value
        } else if (a1 == "stealFile()fail") {
            this.stealFileFailPct = value
        } else if (a1 == "changeDailyPay()fail") {
            this.dailyPayChangeFailPct = value
        } else if (a1 == "changeDailyPay()reduce") {
            this.dailyPayChangeReducePct = 1.0f - value
        } else if (a1 == "installScript()fail") {
            this.installScriptFailPct = value
        }

        val a2 = special2["name"] as String
        var v2 = special2["value"] as String
        if (v2 == "") {
            v2 = "0"
        }
        value = ("" + v2).toFloat()
        if (a2 == "emptyPettyCash()fail") {
            this.pettyCashFailPct = value
        } else if (a2 == "emptyPettyCash()reduce") {
            this.pettyCashReducePct = value
        } else if (a2 == "stealFile()fail") {
            this.stealFileFailPct = value
        } else if (a2 == "changeDailyPay()fail") {
            this.dailyPayChangeFailPct = value
        } else if (a2 == "changeDailyPay()reduce") {
            this.dailyPayChangeReducePct = value
        } else if (a2 == "installScript()fail") {
            this.installScriptFailPct = value
        }

        this.bankDamageModifier = ("" + content["bank_damage_modifier"]).toFloat()
        this.redirectDamageModifier = ("" + content["redirect_damage_modifier"]).toFloat()
        this.attackDamageModifier = ("" + content["attack_damage_modifier"]).toFloat()
        this.ftpDamageModifier = ("" + content["ftp_damage_modifier"]).toFloat()
        this.httpDamageModifier = ("" + content["http_damage_modifier"]).toFloat()

        if (content["attack_damage"] == null || content["attack_damage"].toString().isBlank()) {
            content["attack_damage"] = "0"
        }
        this.attackDamage = ("" + content["attack_damage"]).toFloat()
        this.equipLevel = ("" + content["equip_level"]).toFloat().toInt()
        this.cpuCost = hf.cPUCost
        if (name == "") {
            name = content["name"] as String
        }
        this.hackerFile = hf
    }

    fun getName(): String = hackerFile!!.name.orEmpty()

    fun getHackerFile(): HackerFile = hackerFile!!

    fun getType(): HashMap<Any?, Any?> = HashMap(HackerFileInterop.legacyContentMap(hackerFile!!))

    fun updateFirewall(oldFirewall: Int): HackerFile {
        val HF = HackerFile(HackerFile.NEW_FIREWALL)
        val content = HashMap<Any?, Any?>()
        var price = 0.0f
        if (oldFirewall == NoFireWall) {
            HF.name = "None"
            content["name"] = "None"
            content["attack_damage"] = "0"
        } else if (oldFirewall == BasicFireWall) {
            HF.name = "DataShield"
            content["name"] = "DataShield"
            content["attack_damage"] = "0"
        } else if (oldFirewall == MediumFireWall) {
            HF.name = "DigitalFortress"
            content["name"] = "DigitalFortress"
            content["attack_damage"] = "0"
        } else if (oldFirewall == GreaterFireWall) {
            HF.name = "RubyGuardian"
            content["name"] = "RubyGuardian"
            content["attack_damage"] = "0"
        } else if (oldFirewall == BasicAttackingFireWall) {
            HF.name = "DataShield"
            content["name"] = "DataShield"
            this.name = HF.name
            price += generateAttackValue(content)
        } else if (oldFirewall == MediumAttackingFireWall) {
            HF.name = "DigitalFortress"
            content["name"] = "DigitalFortress"
            this.name = HF.name
            price += generateAttackValue(content)
        } else if (oldFirewall == GreaterAttackingFireWall) {
            HF.name = "RubyGuardian"
            content["name"] = "RubyGuardian"
            this.name = HF.name
            price += generateAttackValue(content)
        } else if (oldFirewall == UltimateAttackingFireWall) {
            HF.name = "DiamondDefender"
            content["name"] = "DiamondDefender"
            this.name = HF.name
            price += generateAttackValue(content)
        }

        val firewall = firewalls[HF.name] as Array<Any?>
        content["equip_level"] = "" + firewall[EQUIP_LEVEL]
        val baseDamageModifier = ("" + firewall[DAMAGE_MODIFIER]).toFloat()
        val cpuCost = ("" + firewall[CPU]).toFloat()
        val description = "" + firewall[DESCRIPTION]
        val maker = "" + firewall[MAKER]
        val maxSpecialAttributeValue = ("" + firewall[MAX_SPECIAL_VALUE]).toFloat()

        HF.setDescription(description)
        HF.maker = maker
        HF.quantity = 1
        HF.cPUCost = cpuCost
        content["bank_damage_modifier"] = "" + baseDamageModifier
        content["attack_damage_modifier"] = "" + baseDamageModifier
        content["redirect_damage_modifier"] = "" + baseDamageModifier
        content["ftp_damage_modifier"] = "" + baseDamageModifier
        content["http_damage_modifier"] = "" + baseDamageModifier
        val special = HashMap<Any?, Any?>()
        for (i in 0 until 2) {
            special["name"] = ""
            special["long_desc"] = ""
            special["short_desc"] = ""
            special["value"] = ""
            content["specialAttribute" + (i + 1)] = special
        }
        price += firewall[BASE_PRICE] as Float
        content["store_price"] = price
        HF.content = LegacyHackerFileCodec.parseLegacyContent(HF.kind, content)
        HF.price = 0.0f
        return HF
    }

    companion object {
        @JvmField
        val FireWallNames =
            arrayOf(
                "None",
                "PortProtector",
                "PwnPreventer",
                "DataShield",
                "PacketBuster",
                "TrafficTender",
                "DigitalFortress",
                "ForceField",
                "RubyGuardian",
                "DiamondDefender",
                "ADNArmour"
            )

        const val ABSORPTION_PRICE_MULTIPLIER = 100.0f

        const val NoFireWall = 0
        const val BasicFireWall = 1
        const val MediumFireWall = 2
        const val GreaterFireWall = 3
        const val BasicAttackingFireWall = 4
        const val MediumAttackingFireWall = 5
        const val GreaterAttackingFireWall = 6
        const val UltimateAttackingFireWall = 7

        private const val maxDamageVariationPercent = 5
        private const val maxFWDamageVariationPercent = 5
        private const val pctChanceOfNoSpecialAttribute = 0.8
        private const val COMPLETE_MISS_PCT = 0.2

        private val weights = intArrayOf(1, 2, 2, 3, 3, 5, 5, 7, 8, 9, 10, 9, 8, 7, 5, 5, 3, 3, 2, 2, 1)

        const val EQUIP_LEVEL = 0
        const val DAMAGE_MODIFIER = 1
        const val ATTACK_RANGE = 2
        const val MIN_ATTACK = 3
        const val CPU = 4
        const val DESCRIPTION = 5
        const val MAKER = 6
        const val MAX_SPECIAL_VALUE = 7
        const val BASE_PRICE = 8
        const val PRICE_DAMAGE_MULTIPLIER = 9
        const val ATTRIBUTE_PERCENTAGE_MULTIPLIER = 10

        @JvmField
        val firewallCompanies = arrayOf("Moonstar Ltd.", "Salamander Ltd.", "DataMan Inc.", "SafeGuard Ltd.", "SecureLink Inc.")

        @JvmField
        val firewalls = HashMap<Any?, Any?>()

        private const val LONG_DESCRIPTION = 0
        private const val SHORT_DESCRIPTION = 1
        private const val BASE_ATTRIBUTE_PRICE = 2

        private val specialKeys =
            arrayOf(
                "emptyPettyCash()fail",
                "emptyPettyCash()reduce",
                "stealFile()fail",
                "changeDailyPay()fail",
                "changeDailyPay()reduce",
                "installScript()fail"
            )
        private val specialAttributes = HashMap<Any?, Any?>()

        init {
            firewalls["None"] = arrayOf<Any?>(0, 1.0f, 0, 0, 0, "", "", 0.0f, 0.0f, 0.0f, 0.0f)
            firewalls["PortProtector"] = arrayOf<Any?>(0, 0.85f, 2, 0, 2, "", firewallCompanies[0], 0.2f, 15.0f, 5.0f, 1.0f)
            firewalls["PwnPreventer"] = arrayOf<Any?>(10, 0.80f, 3, 0, 3, "", firewallCompanies[0], 0.2f, 30.0f, 10.0f, 3.0f)
            firewalls["DataShield"] = arrayOf<Any?>(20, 0.75f, 4, 0, 4, "", firewallCompanies[1], 0.2f, 50.0f, 45.0f, 10.0f)
            firewalls["PacketBuster"] = arrayOf<Any?>(30, 0.70f, 4, 2, 5, "", firewallCompanies[1], 0.3f, 100.0f, 50.0f, 20.0f)
            firewalls["TrafficTender"] = arrayOf<Any?>(40, 0.60f, 5, 3, 6, "", firewallCompanies[2], 0.4f, 200.0f, 75.0f, 30.0f)
            firewalls["DigitalFortress"] = arrayOf<Any?>(50, 0.50f, 5, 5, 7, "", firewallCompanies[2], 0.5f, 400.0f, 125.0f, 40.0f)
            firewalls["ForceField"] = arrayOf<Any?>(60, 0.40f, 6, 6, 8, "", firewallCompanies[3], 0.6f, 600.0f, 250.0f, 45.0f)
            firewalls["RubyGuardian"] = arrayOf<Any?>(70, 0.30f, 7, 7, 10, "", firewallCompanies[3], 0.7f, 2000.0f, 500.0f, 50.0f)
            firewalls["DiamondDefender"] = arrayOf<Any?>(80, 0.20f, 8, 8, 15, "", firewallCompanies[4], 0.8f, 5000.0f, 1000.0f, 60.0f)
            firewalls["ADNArmour"] = arrayOf<Any?>(90, 0.15f, 9, 9, 20, "", firewallCompanies[4], 0.8f, 2000.0f, 50.0f, 80.0f)

            specialAttributes[specialKeys[0]] =
                arrayOf<Any?>(
                    "% chance that an opponent's emptyPettyCash() fails, if this FW is protecting a banking application.",
                    "% emptyPettyCash() fails",
                    50.0f
                )
            specialAttributes[specialKeys[1]] =
                arrayOf<Any?>(
                    "% reduction in an opponent's emptyPettyCash(), if this FW is protecting a banking application.",
                    "% emptyPettyCash() reduced",
                    50.0f
                )
            specialAttributes[specialKeys[2]] =
                arrayOf<Any?>(
                    "% chance an opponent's stealFile() fails, if this FW is protecting an FTP application.",
                    "% stealFile() fails",
                    50.0f
                )
            specialAttributes[specialKeys[3]] =
                arrayOf<Any?>(
                    "% chance an opponent's changeDailyPay() fails, if this FW is protecting an HTTP application",
                    "% changeDailyPay() fails",
                    50.0f
                )
            specialAttributes[specialKeys[4]] =
                arrayOf<Any?>(
                    "% reduction in the amount of daily pay that is lost when an opponent changes your daily pay, if this FW is protecting an HTTP application.",
                    "% changeDailyPay() reduced",
                    50.0f
                )
            specialAttributes[specialKeys[5]] =
                arrayOf<Any?>("% chance that opponent's installScript() fails", "% installScript() fails", 50.0f)
        }

        @JvmStatic
        fun createNoneFirewall(): HackerFile {
            val HF = HackerFile(HackerFile.NEW_FIREWALL)
            val content = HashMap<Any?, Any?>()
            HF.name = "None"

            val firewall = firewalls[HF.name] as Array<Any?>
            content["equip_level"] = firewall[EQUIP_LEVEL]
            val baseDamageModifier = ("" + firewall[DAMAGE_MODIFIER]).toFloat()
            val cpuCost = ("" + firewall[CPU]).toFloat()
            val description = "" + firewall[DESCRIPTION]
            val maker = "" + firewall[MAKER]
            val maxSpecialAttributeValue = ("" + firewall[MAX_SPECIAL_VALUE]).toFloat()

            HF.setDescription(description)
            HF.maker = maker
            HF.quantity = 1
            HF.cPUCost = cpuCost

            content["bank_damage_modifier"] = "" + baseDamageModifier
            content["attack_damage_modifier"] = "" + baseDamageModifier
            content["redirect_damage_modifier"] = "" + baseDamageModifier
            content["ftp_damage_modifier"] = "" + baseDamageModifier
            content["http_damage_modifier"] = "" + baseDamageModifier
            content["name"] = "None"
            val special = HashMap<Any?, Any?>()
            for (i in 0 until 2) {
                special["name"] = ""
                special["long_desc"] = ""
                special["short_desc"] = ""
                special["value"] = ""
                content["specialAttribute" + (i + 1)] = special
            }

            HF.content = LegacyHackerFileCodec.parseLegacyContent(HF.kind, content)
            HF.price = 0.0f
            return HF
        }
    }
}
