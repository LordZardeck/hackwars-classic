package game

import java.io.Serializable

/**
 * HackerFile.java
 * 
 * 
 * A file in the Hacker game.
 */

class HackerFile(type: Int) : Serializable {
    private var cpuCost = 0.0f //CPU cost associated with file.
    private var description: String? = "" //Description of file.

    var name: String? = "" //Name of file.
    var type = 0 //Type of file (from constants).
    var location: String? = "" //Location in file system of file.

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
     * Content in file. Varies depending on file type.
     */
    var content: HashMap<*, *>? = null

    //Constructor.
    init {
        this.type = type
    }

    val typeString: String
        /**
         * Return a string representing the type of file that this is.
         */
        get() {
            if (type == BANKING_COMPILED || type == ATTACKING_COMPILED || type == WATCH_COMPILED || type == FTP_COMPILED || type == HTTP || type == SHIPPING_COMPILED) return "compiled"
            if (type == BANKING_SCRIPT || type == ATTACKING_SCRIPT || type == WATCH_SCRIPT || type == FTP_SCRIPT || type == HTTP_SCRIPT || type == SHIPPING_SCRIPT) return "script"
            if (type == TEXT) return "text"
            if (type == FIREWALL) return "firewall"
            if (type == IMAGE) return "image"
            if (type == AGP || type == PCI) return "hardware"
            if (type == TRASH) return "trash"
            return ""
        }

    /**
     * Get special keys contents for Firewalls()
     */
    fun getSpecial(num: Int): HashMap<*, *>? {
        if (content == null) {
            return null
        }
        return content!!.get("specialAttribute" + num) as HashMap<*, *>?
    }


    var cPUCost: Float
        /**
         * Get the CPU cost associated with this file once installed.
         */
        get() = cpuCost
        /**
         * Set the CPU cost associated with this file once installed.
         */
        set(cpuCost) {
            this.cpuCost = cpuCost
        }

    /**
     * Set the description associated with this file (important for files in stores).
     */
    fun setDescription(description: String?) {
        this.description = description
    }

    val publicDescription: String?
        /**
         * Get the file description.
         */
        get() {
            if (type == BOUNTY) {
                return description + "Iterations Left: " + content!!.get("count")
            }

            return description
        }

    val isStacking: Boolean
        /**
         * Check whether this file is stacking.
         */
        get() {
            for (i in STACKING.indices) {
                if (STACKING[i] == type) return true
            }
            return false
        }

    val typeKeys: Array<String>
        /**
         * Get the keys associated with the file type used to parse the XML structure of this file.
         */
        get() {
            when (type) {
                BANKING_COMPILED, BANKING_SCRIPT -> {
                    return arrayOf("deposit", "withdraw", "transfer")
                }
                ATTACKING_COMPILED, ATTACKING_SCRIPT, SHIPPING_COMPILED, SHIPPING_SCRIPT -> {
                    return arrayOf("initialize", "finalize", "continue")
                }
                WATCH_COMPILED, WATCH_SCRIPT -> {
                   return arrayOf("fire")
                }
                FTP_COMPILED, FTP_SCRIPT -> {
                   return arrayOf("put", "get")
                }
                HTTP, HTTP_SCRIPT -> {
                   return arrayOf("enter", "exit", "submit")
                }
                CLUE -> {
                    return arrayOf(
                        "currentstep",
                        "cluelevel",
                        "step0",
                        "step1",
                        "step2",
                        "step3",
                        "step4",
                        "step5"
                    )
                }
                BOUNTY -> {
                    return arrayOf(
                        "count",
                        "script",
                        "maker",
                        "type",
                        "reward",
                        "target",
                        "bountyip",
                        "timeout"
                    )
                }
                PCI, AGP -> {
                    return arrayOf(
                        "attribute0",
                        "attribute1",
                        "attribute2",
                        "quality0",
                        "quality1",
                        "quality2",
                        "timeout",
                        "maxquality",
                        "currentquality",
                        "lastdegrade"
                    )
                }
                QUEST_ITEM -> {
                    return arrayOf("questid", "itemname", "imageid")
                }
                CHALLENGE -> {
                    return arrayOf(
                        "input",
                        "output",
                        "inputtype",
                        "outputtype",
                        "task",
                        "questid",
                        "identifier"
                    )
                }
                QUEST_GAME -> {
                    return arrayOf("data", "level", "questid", "task")
                }
                TRASH -> {
                    return arrayOf("itemname", "imageid")
                }
                NEW_FIREWALL -> {
                    // old firewalls only had "data" (index of the static final in in Firewall.java of the given firewall type) and "level" (purchase level)
                    // new firewalls:
                    //            "name" (the name of the firewall),
                    //             "baseSellPrice" (the base price used to calculate the 'sell to store' price),
                    //            "useLevel" is the total level required to use this FW
                    //            "cpu" is the CPU points this firewall uses
                    //            "baseDamageModifer" is ( 1 - absorption/100)  -- how much damage gets through -- used to calculate the % for each firewall
                    //            "maxDamage" (the maximum damage a firewall of this level can do)
                    //String returnMe[] = {"name", "baseSellPrice", "useLevel", "cpu", "baseDamageModifier", "maxDamage"};

                    // ON SECOND THOUGHT: I lied.  We'll just use the hashmap in NewFireWall.java to be the only place with the information about the firewalls.  Much better.

                    return arrayOf(
                        "bank_damage_modifier",
                        "attack_damage_modifier",
                        "redirect_damage_modifier",
                        "ftp_damage_modifier",
                        "http_damage_modifier",
                        "specialAttribute1",
                        "specialAttribute2",
                        "attack_damage",
                        "equip_level",
                        "name",
                        "store_price"
                    )
                }
                else -> return arrayOf("data", "level")
            }
        }

    val specialKeys: Array<String?>
        // this is only called in the case that it's a new firewall
        get() = arrayOf("name", "long_desc", "short_desc", "value")

    val portType: Int
        /**
         * Returns the translated type of this file to a port type (necessary since different constants are used).
         */
        get() {
            if (type == BANKING_COMPILED) return PortType.BANKING.getCode()
            if (type == ATTACKING_COMPILED) return PortType.ATTACK.getCode()
            if (type == FTP_COMPILED) return PortType.FTP.getCode()
            if (type == HTTP) return PortType.HTTP.getCode()
            if (type == SHIPPING_COMPILED) return PortType.REDIRECT.getCode()

            return -1
        }

    /**
     * Simple string representation of file for testing.
     */
    override fun toString(): String {
        var returnMe = "\nFile{\n"
        returnMe += "    Name: " + name + "\n"
        returnMe += "    Quantity: " + quantity + "\n"
        returnMe += "}\n"
        return returnMe
    }

    /**
     * Output the file system in XML format.
     */
    fun outputXML(): String {
        var returnMe: String? = "<file>\n"

        if (name != null) returnMe += "<name><![CDATA[" + name!!.replace("]]>".toRegex(), "]]&gt;") + "]]></name>\n"
        else returnMe += "<name><![CDATA[" + name + "]]></name>\n"

        returnMe += "<maker><![CDATA[" + maker + "]]></maker>\n"

        if (location != null) returnMe += "<location><![CDATA[" + location!!.replace(
            "]]>".toRegex(),
            "]]&gt;"
        ) + "]]></location>\n"
        else returnMe += "<location><![CDATA[" + location + "]]></location>\n"

        if (description != null) returnMe += "<description><![CDATA[" + description!!.replace(
            "]]>".toRegex(),
            "]]&gt;"
        ) + "]]></description>\n"
        else returnMe += "<description><![CDATA[" + description + "]]></description>\n"

        returnMe += "<quantity>" + quantity + "</quantity>\n"
        returnMe += "<type>" + type + "</type>\n"
        returnMe += "<price>" + price + "</price>\n"
        returnMe += "<cpu>" + cpuCost + "</cpu>\n"
        returnMe += "<content>\n"
        val Keys = this.typeKeys
        for (i in Keys.indices) {
            val key = Keys[i]
            if (key == "specialAttribute1" || key == "specialAttribute2") {
                returnMe += "<" + key + ">\n"
                var specials: HashMap<*, *>? = null
                if (key == "specialAttribute1") {
                    specials = getSpecial(1)
                } else {
                    specials = getSpecial(2)
                }

                val specialKeys = this.specialKeys
                for (j in specialKeys.indices) {
                    val specialKey = specialKeys[j]
                    val specialValue = if (specials == null) null else specials.get(specialKey) as String?
                    returnMe += "<" + specialKey + "><![CDATA["

                    if (specialValue != null) returnMe += specialValue.replace("]]>".toRegex(), "]]&gt;")
                    else returnMe += specialValue

                    returnMe += "]]></" + specialKey + ">\n"
                }
                returnMe += "</" + key + ">\n"
            } else {
                returnMe += "<" + key + "><![CDATA["
                if (content!!.get(key) != null) returnMe += ("" + content!!.get(key)).replace(
                    "]]>".toRegex(),
                    "]]&gt;"
                )
                else returnMe += "" + content!!.get(key)
                returnMe += "]]></" + key + ">\n"
            }
        }
        returnMe += "</content>\n"
        returnMe += "</file>\n"
        return returnMe
    }

    /**
     * Clone this hacker file.
     */
    fun clone(): HackerFile {
        val returnMe = HackerFile(type)
        returnMe.location = location
        returnMe.name = name
        returnMe.content = content!!.clone() as HashMap<*, *>?
        returnMe.cPUCost = cpuCost
        returnMe.setDescription(description)
        returnMe.maker = maker
        returnMe.price = price
        returnMe.quantity = quantity
        return returnMe
    }

    /**
     * Checksum two hacker files.
     */
    fun checkSumFailed(HFCheck: HackerFile): Boolean {
        /*
                if(type==NEW_FIREWALL){
                    return true;
                }
                */

        val HF = this
        var checkSum1 = ""
        var checkSum2 = ""

        checkSum1 = getCheckSumString(HF)
        checkSum2 = getCheckSumString(HFCheck)


        /*
		Keys=HFCheck.getTypeKeys();
		for(int i=0;i<Keys.length;i++){
			checkSum2+=HFCheck.getContent().get(Keys[i]);
		}
		*/

        //if(HF.getType()==HFCheck.getType())
        if (!(checkSum1 == checkSum2) || HF.cPUCost != HFCheck.cPUCost || !(HF.name == HFCheck.name)) {
            return true
        }
        return false
    }

    private fun getCheckSumString(file: HackerFile): String {
        var checkSum = ""
        val Keys = file.typeKeys

        for (i in Keys.indices) {
            if (Keys[i] == "specialAttribute1" || Keys[i] == "specialAttribute2") {
                var specials: HashMap<*, *>? = null
                if (Keys[i] == "specialAttribute1") {
                    specials = getSpecial(1)
                } else {
                    specials = getSpecial(2)
                }

                val specialKeys = this.specialKeys
                for (j in specialKeys.indices) {
                    val specialKey = specialKeys[j]
                    val specialValue = if (specials == null) null else specials.get(specialKey) as String?
                    if (specialValue != null) checkSum += specialValue
                    else checkSum += specialValue
                }
            } else {
                checkSum += file.content!!.get(Keys[i])
            }
        }

        return checkSum
    }

    companion object {
        //File types.
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

        // new firewall type
        const val NEW_FIREWALL: Int = 28

        // trash file
        const val TRASH: Int = 29

        // old firewalls can still stack, because they'll all have the same traits, and can be sold for the old values. Any type not in here will just be overwritten.
        val STACKING: IntArray =
            intArrayOf(0, 2, 4, 6, 8, 10, 11, 12, 13, 14, 16, 17, 18, 19, 21, 22, 23, 25, 26, 27, 28, 29)
    }
}
