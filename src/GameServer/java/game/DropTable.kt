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
            val node = LX.findNodeRecursive("file", 0)
            HF = LegacyHackerFileCodec.parseLegacyXml(node, LX)
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

        if (file?.kind == NewFirewallFileKind) {
            return MyComputer.newFireWall!!.generateFirewall(file.name)
        }

        return file
    }
}
