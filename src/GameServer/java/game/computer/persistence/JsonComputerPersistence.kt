package game.computer.persistence

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.hackwars.data.model.JsonProfileWrite
import com.hackwars.data.model.PersistedTextBlob
import game.Computer
import game.HackerFile
import game.LegacyComputerPersistenceSupport
import game.Port
import org.w3c.dom.Node
import util.LoadXML
import java.time.LocalDateTime
import java.util.HashMap
import java.util.LinkedHashMap

class JsonComputerPersistence(
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .serializeNulls()
        .setPrettyPrinting()
        .create(),
) {
    fun parse(json: String): JsonComputerSaveManifest = gson.fromJson(json, JsonComputerSaveManifest::class.java)

    fun serialize(manifest: JsonComputerSaveManifest): String = gson.toJson(manifest)

    companion object {
        const val CURRENT_VERSION = 1
    }
}

class JsonComputerPersistenceSupport(
    private val jsonPersistence: JsonComputerPersistence,
    private val legacySupport: LegacyComputerPersistenceSupport,
) {
    fun captureWrite(computer: Computer): JsonProfileWrite {
        return exportSnapshot(
            snapshot = legacySupport.captureSnapshot(computer),
            legacyExtras = computer.legacySaveExtras,
        )
    }

    fun exportSnapshot(
        snapshot: ComputerSnapshot,
        legacyExtras: Map<String, String> = emptyMap(),
        migratedAt: LocalDateTime = LocalDateTime.now(),
    ): JsonProfileWrite {
        val blobs = mutableListOf<PersistedTextBlob>()
        val manifest = JsonComputerSaveManifest(
            schemaVersion = JsonComputerPersistence.CURRENT_VERSION,
            ip = snapshot.ip,
            name = snapshot.name,
            cpuType = snapshot.cpuType,
            memoryType = snapshot.memoryType,
            password = snapshot.password,
            hackCount = snapshot.hackCount,
            voteCount = snapshot.voteCount,
            playerType = snapshot.playerType,
            network = snapshot.network,
            dailyPaySize = snapshot.dailyPaySize,
            dailyPayReduction = snapshot.dailyPayReduction,
            respawnMoney = snapshot.respawnMoney,
            maximumPettyCash = snapshot.maximumPettyCash,
            dropTable = snapshot.dropTable,
            currentQuests = snapshot.currentQuests,
            involvedQuests = snapshot.involvedQuests,
            completedQuests = snapshot.completedQuests,
            allowedNetworks = snapshot.allowedNetworks,
            logEntries = snapshot.logEntries,
            globals = snapshot.globals,
            hdType = snapshot.hdType,
            lastPaid = snapshot.lastPaid,
            pettyCash = snapshot.pettyCash,
            bank = snapshot.bank,
            defaultAttack = snapshot.defaultAttack,
            defaultBank = snapshot.defaultBank,
            defaultFtp = snapshot.defaultFtp,
            defaultHttp = snapshot.defaultHttp,
            defaultShipping = snapshot.defaultShipping,
            stats = snapshot.stats,
            commodityAmount = snapshot.commodityAmount,
            commodityRespawn = snapshot.commodityRespawn,
            ports = parsePorts(snapshot.portsXml, blobs),
            watches = parseWatches(snapshot.watchesXml, blobs),
            fileSystem = parseFileSystem(snapshot.fileSystemXml, blobs),
            website = JsonComputerWebsiteSave(
                myVotes = snapshot.website.myVotes,
                storeRevenueTarget = snapshot.website.storeRevenueTarget,
                adRevenueTarget = snapshot.website.adRevenueTarget,
                title = snapshot.website.title,
                body = blobText("website/body", "website-body", snapshot.website.body, blobs),
            ),
            equipmentSlots = parseEquipment(snapshot.equipmentXml, blobs),
            preferences = snapshot.preferences,
            legacyExtras = LinkedHashMap(legacyExtras),
        )

        return JsonProfileWrite(
            manifestJson = jsonPersistence.serialize(manifest),
            version = JsonComputerPersistence.CURRENT_VERSION,
            migratedAt = migratedAt,
            blobs = blobs,
        )
    }

    fun restore(computer: Computer, manifestJson: String, blobs: List<PersistedTextBlob>) {
        val manifest = jsonPersistence.parse(manifestJson)
        computer.legacySaveExtras = LinkedHashMap(manifest.legacyExtras)
        legacySupport.restoreSnapshot(computer, toSnapshot(manifest, blobs))
    }

    fun toSnapshot(manifest: JsonComputerSaveManifest, blobs: List<PersistedTextBlob>): ComputerSnapshot {
        val blobMap = blobs.associate { it.path to it.textContent }
        return ComputerSnapshot(
            ip = manifest.ip,
            name = manifest.name,
            cpuType = manifest.cpuType,
            memoryType = manifest.memoryType,
            password = manifest.password,
            hackCount = manifest.hackCount,
            voteCount = manifest.voteCount,
            playerType = manifest.playerType,
            network = manifest.network,
            dailyPaySize = manifest.dailyPaySize,
            dailyPayReduction = manifest.dailyPayReduction,
            respawnMoney = manifest.respawnMoney,
            maximumPettyCash = manifest.maximumPettyCash,
            dropTable = manifest.dropTable,
            currentQuests = manifest.currentQuests,
            involvedQuests = manifest.involvedQuests,
            completedQuests = manifest.completedQuests,
            allowedNetworks = manifest.allowedNetworks,
            logEntries = manifest.logEntries,
            globals = manifest.globals,
            hdType = manifest.hdType,
            lastPaid = manifest.lastPaid,
            pettyCash = manifest.pettyCash,
            bank = manifest.bank,
            defaultAttack = manifest.defaultAttack,
            defaultBank = manifest.defaultBank,
            defaultFtp = manifest.defaultFtp,
            defaultHttp = manifest.defaultHttp,
            defaultShipping = manifest.defaultShipping,
            stats = manifest.stats,
            commodityAmount = manifest.commodityAmount,
            commodityRespawn = manifest.commodityRespawn,
            portsXml = writePortsXml(manifest.ports, blobMap),
            watchesXml = writeWatchesXml(manifest.watches, blobMap),
            fileSystemXml = writeFileSystemXml(manifest.fileSystem, blobMap),
            website = ComputerWebsiteSnapshot(
                myVotes = manifest.website.myVotes,
                storeRevenueTarget = manifest.website.storeRevenueTarget,
                adRevenueTarget = manifest.website.adRevenueTarget,
                title = manifest.website.title,
                body = resolveText(manifest.website.body, blobMap),
            ),
            equipmentXml = writeEquipmentXml(manifest.equipmentSlots, blobMap),
            preferences = manifest.preferences,
        )
    }

    private fun parsePorts(portsXml: String, blobs: MutableList<PersistedTextBlob>): List<PortSave> {
        val loadXml = wrappedXml(portsXml)
        val root = loadXml.findNodeRecursive("root", 0) ?: return emptyList()
        val portsNode = loadXml.findNode(root, "ports", 0) ?: return emptyList()
        val ports = mutableListOf<PortSave>()
        var index = 0
        var portNode = loadXml.findNode(portsNode, "port", index)
        while (portNode != null) {
            val type = text(loadXml.findNode(portNode, "type", 0), loadXml)?.toIntOrNull() ?: 0
            val number = text(loadXml.findNode(portNode, "number", 0), loadXml)?.toIntOrNull() ?: 0
            val firewallNode = loadXml.findNode(loadXml.findNode(portNode, "firewall", 0), "file", 0)
            val scripts = LinkedHashMap<String, TextFieldSave>()
            programKeysForPortType(type).forEach { key ->
                val keyNode = if (type == Port.HTTP || type == Port.FTP) {
                    loadXml.findNode(portNode, key, 0)
                } else {
                    val codeNode = loadXml.findNode(portNode, "code", 0)
                    if (codeNode == null) null else loadXml.findNode(codeNode, key, 0)
                }
                text(keyNode, loadXml)?.let { value ->
                    scripts[key] = blobText("ports/$number/program/$key", "program-script", value, blobs)
                        ?: TextFieldSave()
                }
            }
            ports += PortSave(
                number = number,
                type = type,
                health = text(loadXml.findNode(portNode, "health", 0), loadXml)?.toFloatOrNull() ?: 0f,
                on = text(loadXml.findNode(portNode, "onoff", 0), loadXml)?.toIntOrNull() == 1,
                cpuCost = text(loadXml.findNode(portNode, "cpu", 0), loadXml)?.toFloatOrNull() ?: 0f,
                note = text(loadXml.findNode(portNode, "note", 0), loadXml),
                firewall = firewallNode?.let { toHackerFileSave(legacySupport.loadFile(it, loadXml), "ports/$number/firewall", blobs) },
                dummy = text(loadXml.findNode(portNode, "dummy", 0), loadXml)?.toIntOrNull() == 1,
                maliciousTarget = text(loadXml.findNode(portNode, "malicioustarget", 0), loadXml),
                scripts = scripts,
            )
            index += 1
            portNode = loadXml.findNode(portsNode, "port", index)
        }
        return ports
    }

    private fun parseWatches(watchesXml: String, blobs: MutableList<PersistedTextBlob>): List<WatchSave> {
        val loadXml = wrappedXml(watchesXml)
        val root = loadXml.findNodeRecursive("root", 0) ?: return emptyList()
        val watchesNode = loadXml.findNode(root, "watches", 0) ?: return emptyList()
        val watches = mutableListOf<WatchSave>()
        var index = 0
        var watchNode = loadXml.findNode(watchesNode, "watch", index)
        while (watchNode != null) {
            val observedPorts = mutableListOf<Int>()
            var observedIndex = 0
            var observedNode = loadXml.findNode(watchNode, "observedport", observedIndex)
            while (observedNode != null) {
                text(observedNode, loadXml)?.toIntOrNull()?.let(observedPorts::add)
                observedIndex += 1
                observedNode = loadXml.findNode(watchNode, "observedport", observedIndex)
            }
            val scripts = linkedMapOf(
                "fire" to (blobText(
                    "watches/$index/program/fire",
                    "watch-script",
                    text(loadXml.findNode(watchNode, "fire", 0), loadXml),
                    blobs,
                ) ?: TextFieldSave(inlineValue = ""))
            )
            watches += WatchSave(
                type = text(loadXml.findNode(watchNode, "type", 0), loadXml)?.toIntOrNull() ?: 0,
                searchFireWall = text(loadXml.findNode(watchNode, "searchfirewall", 0), loadXml)?.toIntOrNull() ?: 0,
                cpuCost = text(loadXml.findNode(watchNode, "cpu", 0), loadXml)?.toFloatOrNull() ?: 0f,
                installPort = text(loadXml.findNode(watchNode, "installport", 0), loadXml)?.toIntOrNull() ?: 0,
                note = text(loadXml.findNode(watchNode, "note", 0), loadXml),
                on = text(loadXml.findNode(watchNode, "on", 0), loadXml)?.toIntOrNull() == 1,
                observedPorts = observedPorts,
                quantity = text(loadXml.findNode(watchNode, "quantity", 0), loadXml)?.toFloatOrNull() ?: 0f,
                scripts = scripts,
            )
            index += 1
            watchNode = loadXml.findNode(watchesNode, "watch", index)
        }
        return watches
    }

    private fun parseFileSystem(fileSystemXml: String, blobs: MutableList<PersistedTextBlob>): FileSystemSave {
        val loadXml = wrappedXml(fileSystemXml)
        val root = loadXml.findNodeRecursive("root", 0) ?: return FileSystemSave()
        val filesNode = loadXml.findNode(root, "files", 0) ?: return FileSystemSave()

        val directories = mutableListOf<String>()
        var directoryIndex = 0
        var directoryNode = loadXml.findNode(filesNode, "directory", directoryIndex)
        while (directoryNode != null) {
            text(directoryNode, loadXml)?.let(directories::add)
            directoryIndex += 1
            directoryNode = loadXml.findNode(filesNode, "directory", directoryIndex)
        }

        val files = mutableListOf<HackerFileSave>()
        var fileIndex = 0
        var fileNode = loadXml.findNode(filesNode, "file", fileIndex)
        while (fileNode != null) {
            files += toHackerFileSave(legacySupport.loadFile(fileNode, loadXml), "files/$fileIndex", blobs)
            fileIndex += 1
            fileNode = loadXml.findNode(filesNode, "file", fileIndex)
        }

        return FileSystemSave(directories = directories, files = files)
    }

    private fun parseEquipment(equipmentXml: String, blobs: MutableList<PersistedTextBlob>): List<EquipmentSlotSave> {
        val loadXml = wrappedXml(equipmentXml)
        val root = loadXml.findNodeRecursive("root", 0) ?: return emptyList()
        val equipment = mutableListOf<EquipmentSlotSave>()
        var index = 0
        var equipmentNode = loadXml.findNode(root, "equipment", index)
        while (equipmentNode != null) {
            val fileNode = loadXml.findNode(equipmentNode, "file", 0)
            equipment += EquipmentSlotSave(
                file = fileNode?.let { toHackerFileSave(legacySupport.loadFile(it, loadXml), "equipment/$index", blobs) },
            )
            index += 1
            equipmentNode = loadXml.findNode(root, "equipment", index)
        }
        return equipment
    }

    private fun toHackerFileSave(file: HackerFile, basePath: String, blobs: MutableList<PersistedTextBlob>): HackerFileSave {
        val content = LinkedHashMap<String, TextFieldSave>()
        val specialAttributes = LinkedHashMap<String, Map<String, TextFieldSave>>()
        val rawContent = file.content as? Map<*, *> ?: emptyMap<Any?, Any?>()

        file.typeKeys.forEach { rawKey ->
            val key = rawKey ?: return@forEach
            if (key == "specialAttribute1" || key == "specialAttribute2") {
                val rawSpecial = rawContent[key] as? Map<*, *> ?: emptyMap<Any?, Any?>()
                val fields = LinkedHashMap<String, TextFieldSave>()
                file.specialKeys.forEach { specialKey ->
                    val rawValue = rawSpecial[specialKey]?.toString()
                    val textField = captureFileText(
                        file.type,
                        key,
                        rawValue,
                        "$basePath/content/$key/$specialKey",
                        blobs,
                        specialKey = specialKey,
                    )
                    if (textField != null) {
                        fields[specialKey] = textField
                    }
                }
                if (fields.isNotEmpty()) {
                    specialAttributes[key] = fields
                } else if (file.type == HackerFile.NEW_FIREWALL) {
                    specialAttributes[key] = blankSpecialAttributes()
                }
            } else {
                val textField = captureFileText(
                    file.type,
                    key,
                    rawContent[key]?.toString(),
                    "$basePath/content/$key",
                    blobs,
                )
                if (textField != null) {
                    content[key] = textField
                }
            }
        }

        return HackerFileSave(
            type = file.type,
            name = file.name ?: "",
            location = file.location ?: "",
            description = file.publicDescription ?: "",
            price = file.price,
            quantity = file.quantity,
            cpuCost = file.cpuCost,
            maker = file.maker ?: "",
            content = content,
            specialAttributes = specialAttributes,
        )
    }

    private fun writePortsXml(ports: List<PortSave>, blobMap: Map<String, String>): String = buildString {
        append("<ports>\n")
        ports.forEach { port ->
            append("<port>\n")
            appendTag("number", port.number)
            appendTag("type", port.type)
            appendTag("health", port.health)
            appendTag("onoff", if (port.on) 1 else 0)
            appendTag("cpu", port.cpuCost)
            appendCdataTag("note", port.note)
            append("<firewall>")
            append(port.firewall?.let { toHackerFile(it, blobMap).outputXML() } ?: defaultFirewallXml())
            append("</firewall>\n")
            appendTag("dummy", if (port.dummy) 1 else 0)
            appendCdataTag("malicioustarget", port.maliciousTarget)
            appendProgramScripts(port.type, port.scripts, blobMap)
            append("</port>\n")
        }
        append("</ports>\n")
    }

    private fun writeWatchesXml(watches: List<WatchSave>, blobMap: Map<String, String>): String = buildString {
        append("<watches>\n")
        watches.forEach { watch ->
            append("<watch>\n")
            appendTag("cpu", watch.cpuCost)
            appendTag("on", if (watch.on) 1 else 0)
            appendTag("type", watch.type)
            appendCdataTag("note", watch.note)
            watch.observedPorts.forEach { appendTag("observedport", it) }
            appendTag("installport", watch.installPort)
            appendTag("searchfirewall", watch.searchFireWall)
            appendTag("quantity", watch.quantity)
            appendCdataTag("fire", resolveText(watch.scripts["fire"], blobMap))
            append("</watch>\n")
        }
        append("</watches>\n")
    }

    private fun writeFileSystemXml(fileSystem: FileSystemSave, blobMap: Map<String, String>): String = buildString {
        append("<files>\n")
        fileSystem.directories.forEach { appendCdataTag("directory", it) }
        fileSystem.files.forEach { append(toHackerFile(it, blobMap).outputXML()) }
        append("</files>\n")
    }

    private fun writeEquipmentXml(equipmentSlots: List<EquipmentSlotSave>, blobMap: Map<String, String>): String = buildString {
        val slots = if (equipmentSlots.isEmpty()) {
            List(3) { EquipmentSlotSave() }
        } else {
            equipmentSlots
        }
        slots.forEach { slot ->
            append("<equipment>\n")
            slot.file?.let { append(toHackerFile(it, blobMap).outputXML()) }
            append("</equipment>\n")
        }
    }

    private fun StringBuilder.appendProgramScripts(
        portType: Int,
        scripts: Map<String, TextFieldSave>,
        blobMap: Map<String, String>,
    ) {
        val keys = programKeysForPortType(portType)
        if (portType == Port.HTTP || portType == Port.FTP) {
            keys.forEach { key ->
                appendCdataTag(key, resolveText(scripts[key], blobMap))
            }
            return
        }

        append("<code>\n")
        keys.forEach { key ->
            appendCdataTag(key, resolveText(scripts[key], blobMap))
        }
        append("</code>\n")
    }

    private fun toHackerFile(file: HackerFileSave, blobMap: Map<String, String>): HackerFile {
        val result = HackerFile(file.type)
        result.name = file.name
        result.location = file.location
        result.setDescription(file.description)
        result.price = file.price
        result.quantity = file.quantity
        result.cpuCost = file.cpuCost
        result.maker = file.maker

        val content = HashMap<String, Any?>()
        file.content.forEach { (key, value) ->
            content[key] = resolveText(value, blobMap)
        }
        file.specialAttributes.forEach { (key, fields) ->
            val nested = HashMap<String, String?>()
            fields.forEach { (fieldName, value) ->
                nested[fieldName] = resolveText(value, blobMap)
            }
            content[key] = nested
        }
        if (file.type == HackerFile.NEW_FIREWALL) {
            listOf("specialAttribute1", "specialAttribute2").forEach { key ->
                if (!content.containsKey(key)) {
                    content[key] = HashMap(blankSpecialAttributeValues())
                }
            }
        }
        result.content = content
        return result
    }

    private fun captureFileText(
        fileType: Int,
        key: String,
        value: String?,
        path: String,
        blobs: MutableList<PersistedTextBlob>,
        specialKey: String? = null,
    ): TextFieldSave? {
        return if (shouldBlobFileContent(fileType, key, specialKey) && !value.isNullOrEmpty()) {
            blobText(path, fileBlobKind(fileType, key, specialKey), value, blobs)
        } else if (value != null) {
            TextFieldSave(inlineValue = value)
        } else {
            null
        }
    }

    private fun blobText(
        path: String,
        kind: String,
        value: String?,
        blobs: MutableList<PersistedTextBlob>,
    ): TextFieldSave? {
        if (value == null) {
            return null
        }
        blobs += PersistedTextBlob(path = path, kind = kind, textContent = value)
        return TextFieldSave(blobRef = BlobRef(path = path, kind = kind))
    }

    private fun resolveText(value: TextFieldSave?, blobMap: Map<String, String>): String? {
        val blobRef = value?.blobRef
        if (blobRef != null) {
            return blobMap[blobRef.path]
        }
        return value?.inlineValue
    }

    private fun blankSpecialAttributes(): Map<String, TextFieldSave> = blankSpecialAttributeValues()
        .mapValues { TextFieldSave(inlineValue = it.value) }

    private fun blankSpecialAttributeValues(): Map<String, String> = linkedMapOf(
        "name" to "",
        "long_desc" to "",
        "short_desc" to "",
        "value" to "",
    )

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

    private fun StringBuilder.appendTag(name: String, value: Int) = appendTag(name, value.toString())

    private fun StringBuilder.appendTag(name: String, value: Float) = appendTag(name, value.toString())

    private fun StringBuilder.appendTag(name: String, value: String) {
        append("<")
        append(name)
        append(">")
        append(escapeXmlText(value))
        append("</")
        append(name)
        append(">\n")
    }

    private fun StringBuilder.appendCdataTag(name: String, value: String?) {
        append("<")
        append(name)
        append("><![CDATA[")
        append(escapeCdata(value ?: ""))
        append("]]></")
        append(name)
        append(">\n")
    }

    private fun escapeXmlText(value: String): String = buildString {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(ch)
            }
        }
    }

    private fun escapeCdata(value: String): String = value.replace("]]>", "]]]]><![CDATA[>")

    private fun defaultFirewallXml(): String = buildString {
        append("<file>\n")
        append("<name><![CDATA[None]]></name>\n")
        append("<maker><![CDATA[]]></maker>\n")
        append("<location><![CDATA[]]></location>\n")
        append("<description><![CDATA[]]></description>\n")
        append("<quantity>1</quantity>\n")
        append("<type>0</type>\n")
        append("<price>0.0</price>\n")
        append("<cpu>0.0</cpu>\n")
        append("<content>\n</content>\n")
        append("</file>\n")
    }

    private fun programKeysForPortType(type: Int): List<String> {
        return when (type) {
            Port.BANKING -> listOf("deposit", "withdraw", "transfer")
            Port.ATTACK, Port.SHIPPING -> listOf("initialize", "continue", "finalize")
            Port.FTP -> listOf("get", "put")
            Port.HTTP -> listOf("enter", "exit", "submit")
            else -> emptyList()
        }
    }

    private fun shouldBlobFileContent(fileType: Int, key: String, specialKey: String? = null): Boolean {
        return when {
            fileType in setOf(
                HackerFile.BANKING_COMPILED,
                HackerFile.BANKING_SCRIPT,
                HackerFile.ATTACKING_COMPILED,
                HackerFile.ATTACKING_SCRIPT,
                HackerFile.WATCH_COMPILED,
                HackerFile.WATCH_SCRIPT,
                HackerFile.FTP_COMPILED,
                HackerFile.FTP_SCRIPT,
                HackerFile.HTTP,
                HackerFile.HTTP_SCRIPT,
                HackerFile.SHIPPING_COMPILED,
                HackerFile.SHIPPING_SCRIPT,
            ) -> true

            fileType == HackerFile.TEXT && key == "data" -> true
            fileType == HackerFile.CLUE && key.startsWith("step") -> true
            fileType == HackerFile.BOUNTY && key == "script" -> true
            fileType == HackerFile.CHALLENGE && key in setOf("input", "output", "task") -> true
            fileType == HackerFile.QUEST_GAME && key in setOf("data", "task") -> true
            fileType == HackerFile.NEW_FIREWALL &&
                key in setOf("specialAttribute1", "specialAttribute2") &&
                specialKey in setOf("long_desc", "short_desc") -> true

            else -> false
        }
    }

    private fun fileBlobKind(fileType: Int, key: String, specialKey: String?): String {
        return when {
            fileType == HackerFile.TEXT && key == "data" -> "file-text"
            fileType == HackerFile.CLUE && key.startsWith("step") -> "clue-text"
            fileType == HackerFile.BOUNTY && key == "script" -> "bounty-script"
            fileType == HackerFile.CHALLENGE && key in setOf("input", "output", "task") -> "challenge-text"
            fileType == HackerFile.QUEST_GAME && key in setOf("data", "task") -> "quest-game-text"
            fileType == HackerFile.NEW_FIREWALL && specialKey in setOf("long_desc", "short_desc") -> "firewall-description"
            else -> "file-content"
        }
    }
}
