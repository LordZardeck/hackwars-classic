package game

import org.w3c.dom.Node
import util.LoadXML
import java.util.LinkedHashMap

object LegacyHackerFileCodec {
    private val legacyKindRegistry: Map<Int, HackerFileKind> = mapOf(
        0 to ProgramKind(ProgramFamily.BANKING, ProgramForm.COMPILED),
        1 to ProgramKind(ProgramFamily.BANKING, ProgramForm.SOURCE),
        2 to ProgramKind(ProgramFamily.ATTACK, ProgramForm.COMPILED),
        3 to ProgramKind(ProgramFamily.ATTACK, ProgramForm.SOURCE),
        4 to ProgramKind(ProgramFamily.WATCH, ProgramForm.COMPILED),
        5 to ProgramKind(ProgramFamily.WATCH, ProgramForm.SOURCE),
        6 to ProgramKind(ProgramFamily.FTP, ProgramForm.COMPILED),
        7 to ProgramKind(ProgramFamily.FTP, ProgramForm.SOURCE),
        8 to LegacyLevelKind(LegacyLevelFamily.FIREWALL),
        9 to TextFileKind,
        10 to LegacyLevelKind(LegacyLevelFamily.CPU),
        11 to LegacyLevelKind(LegacyLevelFamily.HD),
        12 to ProgramKind(ProgramFamily.HTTP, ProgramForm.COMPILED),
        13 to LegacyLevelKind(LegacyLevelFamily.MEMORY),
        14 to ImageFileKind,
        15 to ProgramKind(ProgramFamily.HTTP, ProgramForm.SOURCE),
        16 to ClueFileKind,
        17 to BountyFileKind,
        18 to EquipmentLicenseKind(EquipmentSlotType.AGP),
        19 to EquipmentLicenseKind(EquipmentSlotType.PCI),
        20 to GameProjectFileKind,
        21 to GameFileKind,
        22 to QuestGameFileKind,
        23 to ProgramKind(ProgramFamily.SHIPPING, ProgramForm.COMPILED),
        24 to ProgramKind(ProgramFamily.SHIPPING, ProgramForm.SOURCE),
        25 to QuestItemFileKind,
        26 to CommoditySlipFileKind,
        27 to ChallengeFileKind,
        28 to NewFirewallFileKind,
        29 to TrashFileKind,
    )

    private val firewallLegacySpecialKeys = listOf("name", "long_desc", "short_desc", "value")

    @JvmStatic
    fun kindForLegacyId(id: Int): HackerFileKind = legacyKindRegistry[id] ?: legacyKindRegistry.getValue(0)

    @JvmStatic
    fun parseLegacyXml(node: Node, loadXml: LoadXML): HackerFile {
        val kind = kindForLegacyId(text(loadXml.findNode(node, "type", 0), loadXml)?.toIntOrNull() ?: 0)
        val contentNode = loadXml.findNode(node, "content", 0)
        val file = HackerFile(kind)
        file.name = text(loadXml.findNode(node, "name", 0), loadXml) ?: "CORRUPT(DELETE)"
        file.location = text(loadXml.findNode(node, "location", 0), loadXml)
        text(loadXml.findNode(node, "description", 0), loadXml)?.let(file::setDescription)
        file.price = text(loadXml.findNode(node, "price", 0), loadXml)?.toFloatOrNull() ?: 0f
        file.quantity = text(loadXml.findNode(node, "quantity", 0), loadXml)?.toIntOrNull() ?: 0
        file.cPUCost = text(loadXml.findNode(node, "cpu", 0), loadXml)?.toFloatOrNull() ?: 0f
        file.maker = text(loadXml.findNode(node, "maker", 0), loadXml)
        file.content = if (contentNode == null) kind.defaultContent() else parseLegacyContent(kind, loadXml, contentNode)
        return file
    }

    @JvmStatic
    fun parseLegacyContent(kind: HackerFileKind, rawContent: Map<*, *>?): HackerFileContent {
        val content = rawContent ?: emptyMap<Any?, Any?>()
        fun value(key: String): String = content[key]?.toString() ?: ""
        return when (kind) {
            is ProgramKind -> ProgramContent(
                family = kind.family,
                scripts = LinkedHashMap<ProgramScriptSlot, String>().apply {
                    programScriptSlots(kind.family).forEach { put(it, value(it.legacyKey)) }
                },
            )

            TextFileKind -> TextContent(data = value("data"), level = value("level"))
            ClueFileKind -> ClueContent(
                currentStep = value("currentstep"),
                clueLevel = value("cluelevel"),
                steps = List(6) { index -> value("step$index") },
            )

            BountyFileKind -> BountyContent(
                count = value("count"),
                script = value("script"),
                maker = value("maker"),
                targetType = value("type"),
                reward = value("reward"),
                target = value("target"),
                bountyIp = value("bountyip"),
                timeout = value("timeout"),
            )

            is EquipmentLicenseKind -> EquipmentLicenseContent(
                attribute0 = value("attribute0"),
                attribute1 = value("attribute1"),
                attribute2 = value("attribute2"),
                quality0 = value("quality0"),
                quality1 = value("quality1"),
                quality2 = value("quality2"),
                timeout = value("timeout"),
                maxQuality = value("maxquality"),
                currentQuality = value("currentquality"),
                lastDegrade = value("lastdegrade"),
                bonusData = content["bonusdata"]?.toString(),
            )

            NewFirewallFileKind -> NewFirewallContent(
                bankDamageModifier = value("bank_damage_modifier"),
                attackDamageModifier = value("attack_damage_modifier"),
                redirectDamageModifier = value("redirect_damage_modifier"),
                ftpDamageModifier = value("ftp_damage_modifier"),
                httpDamageModifier = value("http_damage_modifier"),
                specialAttribute1 = toFirewallSpecialAttribute(content["specialAttribute1"]),
                specialAttribute2 = toFirewallSpecialAttribute(content["specialAttribute2"]),
                attackDamage = value("attack_damage"),
                equipLevel = value("equip_level"),
                name = value("name"),
                storePrice = value("store_price"),
            )

            ChallengeFileKind -> ChallengeContent(
                input = value("input"),
                output = value("output"),
                inputType = value("inputtype"),
                outputType = value("outputtype"),
                task = value("task"),
                questId = value("questid"),
                identifier = value("identifier"),
            )

            QuestGameFileKind -> QuestGameContent(
                data = value("data"),
                level = value("level"),
                questId = value("questid"),
                task = value("task"),
            )

            QuestItemFileKind -> VisualItemContent(
                itemName = value("itemname"),
                imageId = value("imageid"),
                questId = value("questid"),
            )

            TrashFileKind -> VisualItemContent(
                itemName = value("itemname"),
                imageId = value("imageid"),
            )

            is LegacyLevelKind, ImageFileKind, GameProjectFileKind, GameFileKind, CommoditySlipFileKind ->
                LegacyLevelContent(data = value("data"), level = value("level"))
        }
    }

    fun parseLegacyContent(kind: HackerFileKind, loadXml: LoadXML, contentNode: Node): HackerFileContent {
        val raw = LinkedHashMap<String, Any?>()
        typeKeys(kind).forEach { key ->
            val keyNode = loadXml.findNode(contentNode, key, 0)
            if (key == "specialAttribute1" || key == "specialAttribute2") {
                raw[key] = if (keyNode == null) {
                    blankSpecialAttributeMap()
                } else {
                    LinkedHashMap<String, String>().apply {
                        firewallLegacySpecialKeys.forEach { nestedKey ->
                            put(nestedKey, text(loadXml.findNode(keyNode, nestedKey, 0), loadXml) ?: "")
                        }
                    }
                }
            } else {
                raw[key] = text(keyNode, loadXml) ?: ""
            }
        }
        return parseLegacyContent(kind, raw)
    }

    @JvmStatic
    fun toLegacyContentMap(kind: HackerFileKind, content: HackerFileContent): LinkedHashMap<String, Any?> {
        if (content === EmptyContent) {
            return linkedMapOf()
        }
        return when (val typedContent = ensureContentKind(kind, content)) {
            is ProgramContent -> LinkedHashMap<String, Any?>().apply {
                programScriptSlots(typedContent.family).forEach { slot ->
                    put(slot.legacyKey, typedContent.script(slot))
                }
            }

            is TextContent -> linkedMapOf(
                "data" to typedContent.data,
                "level" to typedContent.level,
            )

            is ClueContent -> linkedMapOf<String, Any?>(
                "currentstep" to typedContent.currentStep,
                "cluelevel" to typedContent.clueLevel,
            ).apply {
                typedContent.steps.forEachIndexed { index, value ->
                    put("step$index", value)
                }
            }

            is BountyContent -> linkedMapOf(
                "count" to typedContent.count,
                "script" to typedContent.script,
                "maker" to typedContent.maker,
                "type" to typedContent.targetType,
                "reward" to typedContent.reward,
                "target" to typedContent.target,
                "bountyip" to typedContent.bountyIp,
                "timeout" to typedContent.timeout,
            )

            is EquipmentLicenseContent -> linkedMapOf<String, Any?>(
                "attribute0" to typedContent.attribute0,
                "attribute1" to typedContent.attribute1,
                "attribute2" to typedContent.attribute2,
                "quality0" to typedContent.quality0,
                "quality1" to typedContent.quality1,
                "quality2" to typedContent.quality2,
                "timeout" to typedContent.timeout,
                "maxquality" to typedContent.maxQuality,
                "currentquality" to typedContent.currentQuality,
                "lastdegrade" to typedContent.lastDegrade,
            ).apply {
                typedContent.bonusData?.let { put("bonusdata", it) }
            }

            is NewFirewallContent -> linkedMapOf(
                "bank_damage_modifier" to typedContent.bankDamageModifier,
                "attack_damage_modifier" to typedContent.attackDamageModifier,
                "redirect_damage_modifier" to typedContent.redirectDamageModifier,
                "ftp_damage_modifier" to typedContent.ftpDamageModifier,
                "http_damage_modifier" to typedContent.httpDamageModifier,
                "specialAttribute1" to toLegacySpecialAttributeMap(typedContent.specialAttribute1),
                "specialAttribute2" to toLegacySpecialAttributeMap(typedContent.specialAttribute2),
                "attack_damage" to typedContent.attackDamage,
                "equip_level" to typedContent.equipLevel,
                "name" to typedContent.name,
                "store_price" to typedContent.storePrice,
            )

            is ChallengeContent -> linkedMapOf(
                "input" to typedContent.input,
                "output" to typedContent.output,
                "inputtype" to typedContent.inputType,
                "outputtype" to typedContent.outputType,
                "task" to typedContent.task,
                "questid" to typedContent.questId,
                "identifier" to typedContent.identifier,
            )

            is QuestGameContent -> linkedMapOf(
                "data" to typedContent.data,
                "level" to typedContent.level,
                "questid" to typedContent.questId,
                "task" to typedContent.task,
            )

            is VisualItemContent -> when (kind) {
                QuestItemFileKind -> linkedMapOf(
                    "questid" to typedContent.questId.orEmpty(),
                    "itemname" to typedContent.itemName,
                    "imageid" to typedContent.imageId,
                )

                TrashFileKind -> linkedMapOf(
                    "itemname" to typedContent.itemName,
                    "imageid" to typedContent.imageId,
                )

                else -> linkedMapOf()
            }

            is LegacyLevelContent -> linkedMapOf(
                "data" to typedContent.data,
                "level" to typedContent.level,
            )

            EmptyContent -> linkedMapOf()
        }
    }

    fun serializeXml(file: HackerFile): String = buildString {
        append("<file>\n")
        appendCdataTag("name", file.name)
        appendCdataTag("maker", file.maker)
        appendCdataTag("location", file.location)
        appendCdataTag("description", file.rawDescription())
        appendTag("quantity", file.quantity.toString())
        appendTag("type", file.kind.legacyId.toString())
        appendTag("price", file.price.toString())
        appendTag("cpu", file.cPUCost.toString())
        append("<content>\n")
        val contentMap = toLegacyContentMap(file.kind, file.content)
        typeKeys(file.kind).forEach { key ->
            if (key == "specialAttribute1" || key == "specialAttribute2") {
                append("<").append(key).append(">\n")
                val specialMap = contentMap[key] as? Map<*, *> ?: blankSpecialAttributeMap()
                firewallLegacySpecialKeys.forEach { nestedKey ->
                    appendCdataTag(nestedKey, specialMap[nestedKey]?.toString())
                }
                append("</").append(key).append(">\n")
            } else {
                appendCdataTag(key, contentMap[key]?.toString())
            }
        }
        append("</content>\n")
        append("</file>\n")
    }

    fun checksumFailed(left: HackerFile, right: HackerFile): Boolean {
        if (checksumString(left) != checksumString(right)) {
            return true
        }
        if (left.cPUCost != right.cPUCost) {
            return true
        }
        return left.name != right.name
    }

    fun checksumString(file: HackerFile): String = buildString {
        val contentMap = toLegacyContentMap(file.kind, file.content)
        typeKeys(file.kind).forEach { key ->
            if (key == "specialAttribute1" || key == "specialAttribute2") {
                val specialMap = contentMap[key] as? Map<*, *> ?: blankSpecialAttributeMap()
                firewallLegacySpecialKeys.forEach { nestedKey ->
                    append(specialMap[nestedKey]?.toString())
                }
            } else {
                append(contentMap[key]?.toString())
            }
        }
    }

    fun typeKeys(kind: HackerFileKind): List<String> = when (kind) {
        is ProgramKind -> programKeys(kind)
        ClueFileKind -> listOf("currentstep", "cluelevel", "step0", "step1", "step2", "step3", "step4", "step5")
        BountyFileKind -> listOf("count", "script", "maker", "type", "reward", "target", "bountyip", "timeout")
        is EquipmentLicenseKind -> listOf(
            "attribute0",
            "attribute1",
            "attribute2",
            "quality0",
            "quality1",
            "quality2",
            "timeout",
            "maxquality",
            "currentquality",
            "lastdegrade",
        )

        QuestItemFileKind -> listOf("questid", "itemname", "imageid")
        ChallengeFileKind -> listOf("input", "output", "inputtype", "outputtype", "task", "questid", "identifier")
        QuestGameFileKind -> listOf("data", "level", "questid", "task")
        TrashFileKind -> listOf("itemname", "imageid")
        NewFirewallFileKind -> listOf(
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
            "store_price",
        )

        TextFileKind, is LegacyLevelKind, ImageFileKind, GameProjectFileKind, GameFileKind, CommoditySlipFileKind ->
            listOf("data", "level")
    }

    fun specialKeys(kind: HackerFileKind): List<String> =
        if (kind == NewFirewallFileKind) firewallLegacySpecialKeys else emptyList()

    fun programKeys(kind: HackerFileKind): List<String> =
        if (kind is ProgramKind) {
            programScriptSlots(kind.family).map { it.legacyKey }
        } else {
            emptyList()
        }

    fun programScriptSlots(family: ProgramFamily): List<ProgramScriptSlot> = when (family) {
        ProgramFamily.BANKING -> listOf(ProgramScriptSlot.DEPOSIT, ProgramScriptSlot.WITHDRAW, ProgramScriptSlot.TRANSFER)
        ProgramFamily.ATTACK, ProgramFamily.SHIPPING -> listOf(
            ProgramScriptSlot.INITIALIZE,
            ProgramScriptSlot.FINALIZE,
            ProgramScriptSlot.CONTINUE,
        )

        ProgramFamily.WATCH -> listOf(ProgramScriptSlot.FIRE)
        ProgramFamily.FTP -> listOf(ProgramScriptSlot.PUT, ProgramScriptSlot.GET)
        ProgramFamily.HTTP -> listOf(ProgramScriptSlot.ENTER, ProgramScriptSlot.EXIT, ProgramScriptSlot.SUBMIT)
    }

    fun retargetContent(kind: HackerFileKind, existingContent: HackerFileContent): HackerFileContent =
        parseLegacyContent(kind, toLegacyContentMap(inferKind(existingContent, kind), existingContent))

    private fun inferKind(content: HackerFileContent, fallback: HackerFileKind): HackerFileKind = when (content) {
        is ProgramContent -> ProgramKind(content.family, (fallback as? ProgramKind)?.form ?: ProgramForm.SOURCE)
        is TextContent -> TextFileKind
        is ClueContent -> ClueFileKind
        is BountyContent -> BountyFileKind
        is EquipmentLicenseContent -> fallback
        is NewFirewallContent -> NewFirewallFileKind
        is ChallengeContent -> ChallengeFileKind
        is QuestGameContent -> QuestGameFileKind
        is VisualItemContent -> fallback
        is LegacyLevelContent -> fallback
        EmptyContent -> fallback
    }

    private fun ensureContentKind(kind: HackerFileKind, content: HackerFileContent): HackerFileContent =
        if (content === EmptyContent || matches(kind, content)) content else retargetContent(kind, content)

    private fun matches(kind: HackerFileKind, content: HackerFileContent): Boolean = when (kind) {
        is ProgramKind -> content is ProgramContent && content.family == kind.family
        TextFileKind -> content is TextContent
        ClueFileKind -> content is ClueContent
        BountyFileKind -> content is BountyContent
        is EquipmentLicenseKind -> content is EquipmentLicenseContent
        NewFirewallFileKind -> content is NewFirewallContent
        ChallengeFileKind -> content is ChallengeContent
        QuestGameFileKind -> content is QuestGameContent
        QuestItemFileKind, TrashFileKind -> content is VisualItemContent
        is LegacyLevelKind, ImageFileKind, GameProjectFileKind, GameFileKind, CommoditySlipFileKind -> content is LegacyLevelContent
    }

    private fun toFirewallSpecialAttribute(value: Any?): FirewallSpecialAttribute {
        val map = value as? Map<*, *> ?: emptyMap<Any?, Any?>()
        return FirewallSpecialAttribute(
            name = map["name"]?.toString() ?: "",
            longDescription = map["long_desc"]?.toString() ?: "",
            shortDescription = map["short_desc"]?.toString() ?: "",
            value = map["value"]?.toString() ?: "",
        )
    }

    private fun toLegacySpecialAttributeMap(attribute: FirewallSpecialAttribute): LinkedHashMap<String, String> =
        linkedMapOf(
            "name" to attribute.name,
            "long_desc" to attribute.longDescription,
            "short_desc" to attribute.shortDescription,
            "value" to attribute.value,
        )

    private fun blankSpecialAttributeMap(): LinkedHashMap<String, String> =
        linkedMapOf(
            "name" to "",
            "long_desc" to "",
            "short_desc" to "",
            "value" to "",
        )

    private fun text(node: Node?, loadXml: LoadXML): String? {
        if (node == null) {
            return null
        }
        val textNode = loadXml.findNode(node, "#text", 0) ?: return null
        return textNode.nodeValue
    }

    private fun StringBuilder.appendTag(name: String, value: String) {
        append("<").append(name).append(">")
        append(escapeXmlText(value))
        append("</").append(name).append(">\n")
    }

    private fun StringBuilder.appendCdataTag(name: String, value: String?) {
        append("<").append(name).append("><![CDATA[")
        append(escapeCdata(value ?: ""))
        append("]]></").append(name).append(">\n")
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
}
