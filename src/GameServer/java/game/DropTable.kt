package game

import com.hackwars.data.service.GameWorldDataService
import game.data.GameServerDataLocator
import org.w3c.dom.Node
import util.LoadXML
import java.util.ArrayList
import java.util.HashMap

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
            HF.setName(name)

            temp = LX.findNodeRecursive(N, "location", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            if (temp != null) {
                val location = temp.nodeValue
                HF.setLocation(location)
            }

            temp = LX.findNodeRecursive(N, "description", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            if (temp != null) {
                val description = temp.nodeValue
                HF.setDescription(description)
            }

            temp = LX.findNodeRecursive(N, "price", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            val price = java.lang.Float.valueOf(temp.nodeValue)
            HF.setPrice(price)

            temp = LX.findNodeRecursive(N, "quantity", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            val quantity = Integer.valueOf(temp.nodeValue)
            HF.setQuantity(quantity)

            temp = LX.findNodeRecursive(N, "cpu", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            val cpu = java.lang.Float.valueOf(temp.nodeValue)
            HF.setCPUCost(cpu)

            temp = LX.findNodeRecursive(N, "maker", 0)
            temp = LX.findNodeRecursive(temp, "#text", 0)
            if (temp != null) {
                val maker = temp.nodeValue
                HF.setMaker(maker)
            }

            val Script = HashMap<Any?, Any?>()
            val N2 = LX.findNodeRecursive(N, "content", 0)
            if (N2 != null) {
                val Keys = HF.getTypeKeys()
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
                HF.setContent(Script)
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
                val HF = parseData("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>" + O[1] as String, true)
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
        for (i in Drops.indices) {
            val O = Drops[i] as Array<*>

            val HF = parseData("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>" + O[1] as String, false)
            val HM = HF!!.getContent()
            val itemname = HF.getName()

            if (itemname != null) {
                if (itemname == name) {
                    if (HF.getName() == "Equipment" || HF.getName() == "LowEquipment" || HF.getName() == "MediumEquipment" || HF.getName() == "HighEquipment" || HF.getName() == "RareEquipment") {
                        val quality = HF.getMaker()

                        if (quality == "Low") {
                            return MyComputer.equipmentSheet.generateHardware(EquipmentSheet.LOW.toFloat())
                        } else if (quality == "Medium") {
                            return MyComputer.equipmentSheet.generateHardware(EquipmentSheet.MEDIUM.toFloat())
                        } else if (quality == "High") {
                            return MyComputer.equipmentSheet.generateHardware(EquipmentSheet.HIGH.toFloat())
                        } else if (quality == "Rare") {
                            return MyComputer.equipmentSheet.generateHardware(EquipmentSheet.RARE.toFloat())
                        }
                    }

                    return HF
                }
            }
        }
        return null
    }

    /**
     * Return the data parsed from a packet as a Hacker File.
     */
    fun parseDrop(HF: HackerFile?, parseHardware: Boolean): HackerFile? {
        if (parseHardware) {
            if (HF!!.getName() == "Equipment" || HF.getName() == "LowEquipment" || HF.getName() == "MediumEquipment" || HF.getName() == "HighEquipment" || HF.getName() == "RareEquipment") {
                val quality = HF.getMaker()
                if (quality == "Low") {
                    return MyComputer.equipmentSheet.generateHardware(EquipmentSheet.LOW.toFloat())
                } else if (quality == "Medium") {
                    return MyComputer.equipmentSheet.generateHardware(EquipmentSheet.MEDIUM.toFloat())
                } else if (quality == "High") {
                    return MyComputer.equipmentSheet.generateHardware(EquipmentSheet.HIGH.toFloat())
                } else if (quality == "Rare") {
                    return MyComputer.equipmentSheet.generateHardware(EquipmentSheet.RARE.toFloat())
                }
            }

            if (HF.getType() == HackerFile.NEW_FIREWALL) {
                return MyComputer.newFireWall!!.generateFirewall(HF.getName())
            }
        }

        return HF
    }
}
