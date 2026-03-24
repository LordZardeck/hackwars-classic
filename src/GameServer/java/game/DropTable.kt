package game

import com.hackwars.data.service.GameWorldDataService
import game.data.GameServerDataLocator
import util.LoadXML

/**
 * By Alexander Morrison
 */
open class DropTable @JvmOverloads constructor(
    dropTable: Int,
    private val MyComputer: Computer,
    private val worldDataService: GameWorldDataService? = null,
) {
    private var totalWeight = 0
    private val Drops = ArrayList<Any?>()
    private val clueLevel = 0

    init {
        try {
            val service = worldDataService ?: GameServerDataLocator.worldService()
            service.findDropItems(dropTable).forEach { item ->
                totalWeight += item.weight
                Drops.add(arrayOf<Any?>(Integer.valueOf(totalWeight), item.data))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun parseData(data: String, parseHardware: Boolean): HackerFile? {
        var HF: HackerFile? = null
        try {
            val LX = LoadXML()
            LX.loadByteArray(data.toByteArray())

            val N = LX.findNodeRecursive("file", 0)

            var temp = LX.findNodeRecursive(N, "type", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            val type = Integer.valueOf(temp.nodeValue)

            HF = HackerFile(type)

            temp = LX.findNodeRecursive(N, "name", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            var name = "CURRUPT(DELETE)"
            if (temp != null) {
                name = temp.nodeValue
            }
            HF.name = name

            temp = LX.findNodeRecursive(N, "location", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            if (temp != null) {
                val location = temp.nodeValue
                HF.location = location
            }

            temp = LX.findNodeRecursive(N, "description", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            if (temp != null) {
                HF.setDescription(temp.nodeValue)
            }

            temp = LX.findNodeRecursive(N, "price", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            val price = java.lang.Float.valueOf(temp.nodeValue)
            HF.price = price

            temp = LX.findNodeRecursive(N, "quantity", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            val quantity = Integer.valueOf(temp.nodeValue)
            HF.quantity = quantity

            temp = LX.findNodeRecursive(N, "cpu", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            val cpu = java.lang.Float.valueOf(temp.nodeValue)
            HF.cPUCost = cpu

            temp = LX.findNodeRecursive(N, "maker", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            if (temp != null) {
                val maker = temp.nodeValue
                HF.maker = maker
            }

            val Script = HashMap<Any?, Any?>()
            val N2 = LX.findNodeRecursive(N, "content", 0)
            if (N2 != null) {
                val Keys = HF.typeKeys
                for (ii in Keys.indices) {
                    temp = LX.findNodeRecursive(N2, Keys[ii], 0)
                    if (temp != null) {
                        temp = LX.findNodeRecursive(temp, "#text", 0)
                        if (temp != null) {
                            val script = temp.nodeValue
                            Script[Keys[ii]] = script
                        } else {
                            Script[Keys[ii]] = ""
                        }
                    } else {
                        Script[Keys[ii]] = ""
                    }
                }
                HF.content = Script
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        HF = parseDrop(HF, parseHardware)
        return HF
    }

    fun generateDrop(): HackerFile {
        val drop = (Math.random() * totalWeight).toInt()
        for (i in Drops.indices) {
            val O = Drops[i] as Array<*>
            val currentRange = O[0] as Integer
            if (drop < currentRange.toInt()) {
                val HF =
                    parseData("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>" + O[1] as String, true)
                return HF!!
            }
        }

        return null!!
    }

    /**
     * This function searches through the drop table and returns a
     * quest item based on the name of the quest item.
     */
    fun getQuestItem(name: String): HackerFile? {
        for (drop in Drops) {
            val file = parseData(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>"
                        + ((drop as? Array<*>)?.getOrNull(1) as? String ?: continue),
                false
            )

            when (file?.name) {
                "Equipment", "LowEquipment", "MediumEquipment", "HighEquipment", "RareEquipment" -> {
                    return generateHardware(HardwareRarity.fromMaker(file.maker.orEmpty()))
                }
            }

            if (file?.name == name) return file
        }
        return null
    }

    /**
     * Return the data parsed from a packet as a Hacker File.
     */
    fun parseDrop(file: HackerFile?, parseHardware: Boolean): HackerFile? {
        if(!parseHardware) return file

        when (file?.name) {
            "Equipment", "LowEquipment", "MediumEquipment", "HighEquipment", "RareEquipment" -> {
                return generateHardware(HardwareRarity.fromMaker(file.maker.orEmpty()))
            }
        }

        if (file?.type == HackerFile.NEW_FIREWALL) {
            return MyComputer.newFireWall!!.generateFirewall(file.name)
        }

        return file
    }
}
