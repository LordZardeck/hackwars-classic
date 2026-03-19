package game

import com.hackwars.game.program.AttackProgram
import com.hackwars.game.program.Banking
import com.hackwars.game.program.FTPProgram
import com.hackwars.game.program.HTTPProgram
import com.hackwars.game.program.Program
import com.hackwars.game.program.ShippingProgram
import com.hackwars.game.program.WatchProgram
import game.computer.persistence.CompletedQuestSnapshot
import game.computer.persistence.ComputerPersistence
import game.computer.persistence.ComputerSnapshot
import game.computer.persistence.ComputerStatsSnapshot
import game.computer.persistence.ComputerWebsiteSnapshot
import game.computer.persistence.CurrentQuestSnapshot
import game.computer.persistence.CurrentQuestTaskSnapshot
import game.computer.persistence.GlobalSnapshot
import game.computer.persistence.LogEntrySnapshot
import hackscript.model.TypeBoolean
import hackscript.model.TypeFloat
import hackscript.model.TypeInteger
import hackscript.model.TypeString
import org.w3c.dom.Node
import util.LoadXML
import java.util.ArrayList
import java.util.HashMap

class LegacyComputerPersistenceSupport(
    private val persistence: ComputerPersistence
) {
    fun captureSnapshot(computer: Computer): ComputerSnapshot {
        val currentQuests = mutableListOf<CurrentQuestSnapshot>()
        computer.CurrentQuests.entries.forEach { rawEntry ->
            val entry = rawEntry as Map.Entry<*, *>
            val questId = entry.key as Integer
            val payload = entry.value as Array<*>
            val currentTasks = payload[0] as HashMap<*, *>
            val label = payload[1] as? String ?: ""

            val tasks = mutableListOf<CurrentQuestTaskSnapshot>()
            currentTasks.entries.forEach { rawTaskEntry ->
                val taskEntry = rawTaskEntry as Map.Entry<*, *>
                val taskPayload = taskEntry.value as Array<*>
                tasks += CurrentQuestTaskSnapshot(
                    name = taskEntry.key as String,
                    complete = (taskPayload[0] as Boolean?) ?: false,
                    label = taskPayload[1] as? String ?: ""
                )
            }

            currentQuests += CurrentQuestSnapshot(
                id = questId.toInt(),
                label = label,
                tasks = tasks
            )
        }

        val completedQuests = mutableListOf<CompletedQuestSnapshot>()
        computer.CompletedQuests.forEach { completed ->
            val payload = completed as Array<*>
            completedQuests += CompletedQuestSnapshot(
                id = (payload[0] as Integer).toInt(),
                label = payload[1] as? String ?: ""
            )
        }

        val logEntries = mutableListOf<LogEntrySnapshot>()
        computer.LogMessages.forEach { raw ->
            val entry = raw as Array<*>
            logEntries += LogEntrySnapshot(
                ip = entry.getOrNull(1) as? String ?: "",
                message = entry.getOrNull(0) as? String
            )
        }

        val globals = (0 until 20).map { index ->
            val value = computer.Globals.getOrNull(index)
            val type = when (value) {
                is hackscript.model.TypeFloat -> "FLOAT"
                is hackscript.model.TypeBoolean -> "BOOLEAN"
                is hackscript.model.TypeString -> "STRING"
                is hackscript.model.TypeInteger -> "INTEGER"
                else -> "TYPE"
            }
            GlobalSnapshot(type = type, value = value?.toString())
        }

        val preferences = linkedMapOf<String, String>()
        computer.preferences?.entries?.forEach { entry ->
            val mapEntry = entry as? Map.Entry<*, *> ?: return@forEach
            preferences[mapEntry.key.toString()] = mapEntry.value?.toString() ?: ""
        }

        return ComputerSnapshot(
            ip = computer.ip,
            name = computer.userName,
            cpuType = computer.cputype,
            memoryType = computer.memorytype,
            password = computer.password,
            hackCount = computer.successfulHacks,
            voteCount = computer.voteCount,
            playerType = computer.type,
            network = if (computer.type == Computer.NPC) computer.network else null,
            dailyPaySize = computer.dailyPaySize,
            dailyPayReduction = computer.dailyPayReduction,
            respawnMoney = computer.respawnMoney,
            maximumPettyCash = computer.maximumPettyCash,
            dropTable = computer.dropTable,
            currentQuests = currentQuests,
            involvedQuests = computer.InvolvedQuests.map { (it as Integer).toInt() },
            completedQuests = completedQuests,
            allowedNetworks = computer.AllowedNetworks.map { it.toString() },
            logEntries = logEntries,
            globals = globals,
            hdType = computer.MyFileSystem.getHDType(),
            lastPaid = computer.lastPaid,
            pettyCash = computer.pettyCash,
            bank = computer.bankMoney,
            defaultAttack = computer.defaultAttack,
            defaultBank = computer.defaultBank,
            defaultFtp = computer.defaultFTP,
            defaultHttp = computer.defaultHTTP,
            defaultShipping = computer.defaultShipping,
            stats = ComputerStatsSnapshot(
                attackXp = (computer.Stats["Attack"] as? Float) ?: 0f,
                merchantingXp = (computer.Stats["Bank"] as? Float) ?: 0f,
                firewallXp = (computer.Stats["FireWall"] as? Float) ?: 0f,
                watchXp = (computer.Stats["Watch"] as? Float) ?: 0f,
                scanningXp = (computer.Stats["Scanning"] as? Float) ?: 0f,
                webDesignXp = (computer.Stats["Webdesign"] as? Float) ?: 0f,
                redirectingXp = (computer.Stats["Redirecting"] as? Float) ?: 0f,
                repairXp = (computer.Stats["Repair"] as? Float) ?: 0f
            ),
            commodityAmount = computer.commodityAmount.toList(),
            commodityRespawn = computer.commodityRespawn.toList(),
            portsXml = buildPortsXml(computer),
            watchesXml = computer.MyWatchHandler.outputXML(),
            fileSystemXml = computer.MyFileSystem.outputXML(),
            website = ComputerWebsiteSnapshot(
                myVotes = computer.myVotes,
                storeRevenueTarget = computer.storeRevenueTarget,
                adRevenueTarget = computer.adRevenueTarget,
                title = computer.pageTitle,
                body = computer.pageBody
            ),
            equipmentXml = computer.MyEquipmentSheet.outputXML(),
            preferences = preferences
        )
    }

    fun outputXml(computer: Computer): String {
        return persistence.serialize(captureSnapshot(computer))
    }

    fun restoreSnapshot(computer: Computer, snapshot: ComputerSnapshot) {
        resetMutableState(computer)

        computer.ip = snapshot.ip
        computer.cputype = snapshot.cpuType
        computer.memorytype = snapshot.memoryType
        computer.password = snapshot.password
        computer.successfulHacks = snapshot.hackCount
        computer.voteCount = snapshot.voteCount
        computer.type = snapshot.playerType
        computer.network = snapshot.network?.takeIf { it.isNotBlank() } ?: Network.ROOT_NETWORK
        computer.dailyPaySize = snapshot.dailyPaySize
        computer.dailyPayReduction = snapshot.dailyPayReduction
        computer.respawnMoney = snapshot.respawnMoney
        computer.maximumPettyCash = snapshot.maximumPettyCash
        computer.dropTable = snapshot.dropTable
        computer.lastPaid = snapshot.lastPaid
        computer.pettyCash = snapshot.pettyCash
        computer.bankMoney = snapshot.bank
        computer.defaultAttack = snapshot.defaultAttack
        computer.defaultBank = snapshot.defaultBank
        computer.defaultFTP = snapshot.defaultFtp
        computer.defaultHTTP = snapshot.defaultHttp
        computer.defaultShipping = snapshot.defaultShipping
        computer.myVotes = snapshot.website.myVotes
        computer.storeRevenueTarget = computer.ip
        computer.adRevenueTarget = snapshot.website.adRevenueTarget ?: ""
        computer.pageTitle = snapshot.website.title ?: ""
        computer.pageBody = snapshot.website.body ?: ""
        computer.MyFileSystem.setHDType(snapshot.hdType)

        if (computer.type != Computer.NPC && !snapshot.name.isNullOrBlank()) {
            computer.userName = snapshot.name
        } else if (computer.type == Computer.NPC) {
            computer.inactive = false
        }

        if (computer.lastPaid == 0L) {
            computer.lastPaid = computer.MyTime.getCurrentTime()
        }

        computer.Stats.put("Attack", snapshot.stats.attackXp)
        computer.Stats.put("Bank", snapshot.stats.merchantingXp)
        computer.Stats.put("FireWall", snapshot.stats.firewallXp)
        computer.Stats.put("Watch", snapshot.stats.watchXp)
        computer.Stats.put("Scanning", snapshot.stats.scanningXp)
        computer.Stats.put("Webdesign", snapshot.stats.webDesignXp)
        computer.Stats.put("Redirecting", snapshot.stats.redirectingXp)
        computer.Stats.put("Repair", snapshot.stats.repairXp)

        snapshot.currentQuests.forEach { quest ->
            val currentTasks = HashMap<String, Array<Any>>()
            quest.tasks.forEach { task ->
                currentTasks[task.name] = arrayOf(task.complete, task.label)
            }
            computer.CurrentQuests[quest.id] = arrayOf(currentTasks, quest.label)
        }

        snapshot.involvedQuests.forEach { computer.InvolvedQuests.add(it) }
        snapshot.completedQuests.forEach { quest ->
            computer.CompletedQuests.add(arrayOf(quest.id, quest.label))
        }
        snapshot.allowedNetworks.forEach { computer.AllowedNetworks.add(it) }
        snapshot.logEntries.forEach { entry ->
            computer.LogMessages.add(arrayOf(entry.message, entry.ip))
        }

        snapshot.globals.take(20).forEachIndexed { index, global ->
            global.value?.let { value ->
                when (global.type) {
                    "INTEGER" -> computer.setGlobal(index, TypeInteger(value.toInt()))
                    "FLOAT" -> computer.setGlobal(index, TypeFloat(value.toFloat()))
                    "STRING" -> computer.setGlobal(index, TypeString(value))
                    "BOOLEAN" -> computer.setGlobal(index, TypeBoolean(value.toBoolean()))
                }
            }
        }

        snapshot.commodityAmount.take(5).forEachIndexed { index, value ->
            computer.commodityAmount[index] = value
        }
        snapshot.commodityRespawn.take(5).forEachIndexed { index, value ->
            computer.commodityRespawn[index] = value
        }

        val preferenceMap = HashMap<Any?, Any?>()
        preferenceMap.putAll(snapshot.preferences)
        computer.preferences = preferenceMap
        computer.LOG_UPDATE = true

        loadEquipment(computer, snapshot.equipmentXml)
        loadFileSystem(computer, snapshot.fileSystemXml)
        loadPorts(computer, snapshot.portsXml)
        loadWatches(computer, snapshot.watchesXml)
    }

    fun loadFile(node: Node, loadXml: LoadXML): HackerFile {
        val type = text(loadXml.findNode(node, "type", 0), loadXml)?.toIntOrNull() ?: 0
        val file = HackerFile(type)

        file.setName(text(loadXml.findNode(node, "name", 0), loadXml) ?: "CORRUPT(DELETE)")
        text(loadXml.findNode(node, "location", 0), loadXml)?.let(file::setLocation)
        text(loadXml.findNode(node, "description", 0), loadXml)?.let(file::setDescription)
        file.setPrice(text(loadXml.findNode(node, "price", 0), loadXml)?.toFloatOrNull() ?: 0f)
        file.setQuantity(text(loadXml.findNode(node, "quantity", 0), loadXml)?.toIntOrNull() ?: 0)
        file.setCPUCost(text(loadXml.findNode(node, "cpu", 0), loadXml)?.toFloatOrNull() ?: 0f)
        text(loadXml.findNode(node, "maker", 0), loadXml)?.let(file::setMaker)

        val content = HashMap<String, Any>()
        val contentNode = loadXml.findNode(node, "content", 0)
        if (contentNode != null) {
            file.getTypeKeys().forEach { rawKey ->
                val key = rawKey ?: return@forEach
                val keyNode = loadXml.findNode(contentNode, key, 0)
                if (keyNode == null) {
                    content[key] = ""
                } else if (key == "specialAttribute1" || key == "specialAttribute2") {
                    val nested = HashMap<String, String>()
                    nested["name"] = text(loadXml.findNode(keyNode, "name", 0), loadXml) ?: ""
                    nested["value"] = text(loadXml.findNode(keyNode, "value", 0), loadXml) ?: ""
                    nested["long_desc"] = text(loadXml.findNode(keyNode, "long_desc", 0), loadXml) ?: ""
                    nested["short_desc"] = text(loadXml.findNode(keyNode, "short_desc", 0), loadXml) ?: ""
                    content[key] = nested
                } else {
                    content[key] = text(keyNode, loadXml) ?: ""
                }
            }
            file.setContent(content)
        }

        return file
    }

    private fun buildPortsXml(computer: Computer): String = buildString {
        append("<ports>\n")
        val portIterator = computer.Ports.entries.iterator()
        while (portIterator.hasNext()) {
            val port = (portIterator.next() as Map.Entry<*, *>).value as Port
            append(port.outputXML())
        }
        append("</ports>\n")
    }

    private fun resetMutableState(computer: Computer) {
        computer.Ports = HashMap<Any?, Any?>()
        computer.CurrentQuests = HashMap<Any?, Any?>()
        computer.CompletedQuests = ArrayList<Any?>()
        computer.InvolvedQuests = ArrayList<Any?>()
        computer.AllowedNetworks = ArrayList<Any?>()
        computer.LogMessages = ArrayList<Any?>()
        computer.preferences = HashMap<Any?, Any?>()
        computer.Stats = HashMap<Any?, Any?>()
        computer.Globals = ArrayList<Any?>()
        repeat(20) {
            computer.Globals.add(null)
        }
        computer.MyFileSystem = FileSystem(computer)
        computer.MyMakeBounty = MakeBounty(computer.MyFileSystem)
        computer.MyWatchHandler = WatchHandler(computer.RawComputerHandler, computer)
        computer.MyEquipmentSheet = EquipmentSheet(computer)
        computer.MyDropTable = null
        computer.currentCPU = 0f
        computer.reportCPU = 0f
        computer.baseCPU = 0f
        computer.currentWatchCost = 0f
        computer.overheatStart = -1
        computer.sentOverHeatedMessage = false
        computer.healCounter = 0
    }

    private fun loadEquipment(computer: Computer, equipmentXml: String) {
        val loadXml = wrappedXml(equipmentXml)
        val root = loadXml.findNodeRecursive("root", 0) ?: return
        var index = 0
        var equipmentNode = loadXml.findNode(root, "equipment", index)
        while (equipmentNode != null) {
            val fileNode = loadXml.findNode(equipmentNode, "file", 0)
            if (fileNode != null) {
                computer.MyEquipmentSheet.equip(index, loadFile(fileNode, loadXml))
            }
            index += 1
            equipmentNode = loadXml.findNode(root, "equipment", index)
        }
    }

    private fun loadFileSystem(computer: Computer, fileSystemXml: String) {
        val loadXml = wrappedXml(fileSystemXml)
        val root = loadXml.findNodeRecursive("root", 0) ?: return
        val filesNode = loadXml.findNode(root, "files", 0) ?: return

        var index = 0
        var directoryNode = loadXml.findNode(filesNode, "directory", index)
        while (directoryNode != null) {
            text(directoryNode, loadXml)?.let(computer.MyFileSystem::addDirectory)
            index += 1
            directoryNode = loadXml.findNode(filesNode, "directory", index)
        }

        index = 0
        var fileNode = loadXml.findNode(filesNode, "file", index)
        while (fileNode != null) {
            val file = loadFile(fileNode, loadXml)
            if (file.type != HackerFile.FIREWALL) {
                computer.MyFileSystem.addFile(file, false)
            } else {
                val content = file.content
                val firewallLevel = (content["data"] as? String)?.toIntOrNull() ?: 0
                val quantity = file.quantity
                var generatedName = ""
                repeat(quantity) { offset ->
                    val generated = NewFireWall().updateFirewall(firewallLevel)
                    if (generatedName.isEmpty()) {
                        generatedName = generated.name
                    } else {
                        generated.setName(generatedName + offset)
                    }
                    computer.MyFileSystem.addFile(generated, false)
                }
            }
            index += 1
            fileNode = loadXml.findNode(filesNode, "file", index)
        }
    }

    private fun loadPorts(computer: Computer, portsXml: String) {
        val loadXml = wrappedXml(portsXml)
        val root = loadXml.findNodeRecursive("root", 0) ?: return
        val portsNode = loadXml.findNode(root, "ports", 0) ?: return

        var index = 0
        var portNode = loadXml.findNode(portsNode, "port", index)
        while (portNode != null) {
            val port = Port(computer, computer.MyComputerHandler)
            val number = text(loadXml.findNode(portNode, "number", 0), loadXml)?.toIntOrNull() ?: 0
            port.setNumber(number)
            port.setOn(text(loadXml.findNode(portNode, "onoff", 0), loadXml) != "0")
            port.setDummy(text(loadXml.findNode(portNode, "dummy", 0), loadXml) != "0")
            port.setCPUCost(text(loadXml.findNode(portNode, "cpu", 0), loadXml)?.toFloatOrNull() ?: 0f)
            text(loadXml.findNode(portNode, "note", 0), loadXml)?.let(port::setNote)
            text(loadXml.findNode(portNode, "malicioustarget", 0), loadXml)?.let(port::setMaliciousTarget)
            text(loadXml.findNode(portNode, "health", 0), loadXml)?.toFloatOrNull()?.let(port::setHealth)

            val fireWall = NewFireWall(computer.MyComputerHandler)
            try {
                val firewallType = text(loadXml.findNode(portNode, "firewall", 0), loadXml)?.toIntOrNull()
                if (firewallType != null) {
                    fireWall.loadHackerFile(NewFireWall().updateFirewall(firewallType))
                } else {
                    val legacyFirewallNode = loadXml.findNode(loadXml.findNode(portNode, "firewall", 0), "file", 0)
                    if (legacyFirewallNode != null) {
                        fireWall.loadHackerFile(loadFile(legacyFirewallNode, loadXml))
                    }
                }
            } catch (_: Exception) {
                val legacyFirewallNode = loadXml.findNode(loadXml.findNode(portNode, "firewall", 0), "file", 0)
                if (legacyFirewallNode != null) {
                    fireWall.loadHackerFile(loadFile(legacyFirewallNode, loadXml))
                }
            }
            fireWall.setParentPort(port)
            port.setFireWall(fireWall)

            val type = text(loadXml.findNode(portNode, "type", 0), loadXml)?.toIntOrNull() ?: 0
            port.setType(type)

            val program = createProgram(computer, port, type)
            if (program != null) {
                val script = HashMap<String, String>()
                val codeNode = loadXml.findNode(portNode, "code", 0)
                program.getTypeKeys().forEach { rawKey ->
                    val key = rawKey ?: return@forEach
                    val keyNode = if (type != Port.HTTP && type != Port.FTP) {
                        if (codeNode == null) null else loadXml.findNode(codeNode, key, 0)
                    } else {
                        loadXml.findNode(portNode, key, 0)
                    }
                    val value = if (keyNode == null) null else text(keyNode, loadXml)
                    if (value != null) {
                        script[key] = value
                    }
                }
                program.installScript(script)
                port.setProgram(program)
            }

            computer.Ports.put(number, port)
            index += 1
            portNode = loadXml.findNode(portsNode, "port", index)
        }
    }

    private fun loadWatches(computer: Computer, watchesXml: String) {
        val loadXml = wrappedXml(watchesXml)
        val root = loadXml.findNodeRecursive("root", 0) ?: return
        val watchesNode = loadXml.findNode(root, "watches", 0) ?: return

        var index = 0
        var watchNode = loadXml.findNode(watchesNode, "watch", index)
        while (watchNode != null) {
            val watch = Watch(computer)
            val type = text(loadXml.findNode(watchNode, "type", 0), loadXml)?.toIntOrNull() ?: 0
            watch.setType(type)
            watch.setSearchFireWall(text(loadXml.findNode(watchNode, "searchfirewall", 0), loadXml)?.toIntOrNull() ?: 0)
            watch.setCPUCost(text(loadXml.findNode(watchNode, "cpu", 0), loadXml)?.toFloatOrNull() ?: 0f)
            text(loadXml.findNode(watchNode, "installport", 0), loadXml)?.toIntOrNull()?.let(watch::setPort)
            text(loadXml.findNode(watchNode, "note", 0), loadXml)?.let(watch::setNote)
            watch.setOn(text(loadXml.findNode(watchNode, "on", 0), loadXml)?.toIntOrNull() == 1)

            var observedIndex = 0
            var observedNode = loadXml.findNode(watchNode, "observedport", observedIndex)
            while (observedNode != null) {
                text(observedNode, loadXml)?.toIntOrNull()?.let(watch::addObservedPort)
                observedIndex += 1
                observedNode = loadXml.findNode(watchNode, "observedport", observedIndex)
            }

            watch.setQuantity(text(loadXml.findNode(watchNode, "quantity", 0), loadXml)?.toFloatOrNull() ?: 0f)
            when (type) {
                Watch.PETTY_CASH -> watch.setInitialQuantity(computer.pettyCash)
                Watch.HEALTH -> watch.setInitialQuantity(100f)
            }

            val script = HashMap<String, String>()
            script["fire"] = text(loadXml.findNode(watchNode, "fire", 0), loadXml) ?: ""
            val program = WatchProgram(computer, computer.MyComputerHandler, watch)
            program.computerHandler = computer.MyComputerHandler
            program.installScript(script)
            watch.setProgram(program)

            computer.MyWatchHandler.addWatch(watch)
            index += 1
            watchNode = loadXml.findNode(watchesNode, "watch", index)
        }
    }

    private fun createProgram(computer: Computer, port: Port, type: Int): Program? {
        return when (type) {
            Port.BANKING -> Banking(computer, computer.MyComputerHandler, port)
            Port.ATTACK -> AttackProgram(computer, computer.MyComputerHandler, port, computer.Choices, computer.MyMakeBounty)
            Port.SHIPPING -> ShippingProgram(computer, computer.MyComputerHandler, port)
            Port.FTP -> FTPProgram(computer, computer.MyComputerHandler, computer.MyFileSystem, port)
            Port.HTTP -> HTTPProgram(computer, computer.MyComputerHandler)
            else -> null
        }
    }

    private fun wrappedXml(body: String): LoadXML {
        val loadXml = LoadXML()
        loadXml.loadString("<root>$body</root>")
        return loadXml
    }

    private fun text(node: Node?, loadXml: LoadXML): String? {
        if (node == null) {
            return null
        }
        val textNode = loadXml.findNode(node, "#text", 0) ?: return null
        return textNode.nodeValue
    }
}
