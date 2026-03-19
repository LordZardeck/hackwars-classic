package game.computer.persistence

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class XmlComputerPersistence : ComputerPersistence {
    override fun parse(xml: String): ComputerSnapshot = parseDocument(parseXml(xml))

    override fun serialize(snapshot: ComputerSnapshot): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n")
        append("<save>\n")
        appendTag("ip", snapshot.ip)
        appendCdataTag("name", snapshot.name ?: "null")
        appendTag("cputype", snapshot.cpuType)
        appendTag("memorytype", snapshot.memoryType)
        appendCdataTag("password", snapshot.password ?: "null")
        appendTag("hackcount", snapshot.hackCount)
        appendTag("votecount", snapshot.voteCount)
        appendTag("playertype", snapshot.playerType)
        if (snapshot.playerType == NPC_TYPE) {
            appendTag("network", snapshot.network ?: "")
        }
        appendTag("dailypaysize", snapshot.dailyPaySize)
        appendTag("dailyPayReduction", snapshot.dailyPayReduction)
        appendTag("respawnmoney", snapshot.respawnMoney)
        appendTag("maximumpettycash", snapshot.maximumPettyCash)
        appendTag("dropTable", snapshot.dropTable)
        appendCurrentQuests(snapshot.currentQuests)
        appendInvolvedQuests(snapshot.involvedQuests)
        appendCompletedQuests(snapshot.completedQuests)
        snapshot.allowedNetworks.forEach { appendTag("allowedNetwork", it) }
        appendLogEntries(snapshot.logEntries)
        appendGlobals(snapshot.globals)
        appendTag("hdtype", snapshot.hdType)
        appendTag("lastpaid", snapshot.lastPaid)
        appendTag("pettycash", snapshot.pettyCash)
        appendTag("bank", snapshot.bank)
        appendTag("defaultattack", snapshot.defaultAttack)
        appendTag("defaultbank", snapshot.defaultBank)
        appendTag("defaultftp", snapshot.defaultFtp)
        appendTag("defaulthttp", snapshot.defaultHttp)
        appendTag("defaultshipping", snapshot.defaultShipping)
        appendStats(snapshot.stats)
        appendFloatSeries("commodity", snapshot.commodityAmount)
        appendFloatSeries("commodityrespawn", snapshot.commodityRespawn)
        append(snapshot.portsXml.ifBlank { defaultPortsXml() })
        append(snapshot.watchesXml.ifBlank { defaultWatchesXml() })
        append(snapshot.fileSystemXml.ifBlank { defaultFilesXml() })
        appendWebsite(snapshot.website)
        append(snapshot.equipmentXml.ifBlank { defaultEquipmentXml() })
        appendPreferences(snapshot.preferences)
        append("</save>")
    }

    private fun parseDocument(document: Document): ComputerSnapshot {
        val root = document.documentElement
        require(root != null && root.tagName == "save") { "Expected <save> document root" }

        return ComputerSnapshot(
            ip = text(root, "ip"),
            name = nullableText(root, "name"),
            cpuType = intText(root, "cputype"),
            memoryType = intText(root, "memorytype"),
            password = nullableText(root, "password"),
            hackCount = intText(root, "hackcount"),
            voteCount = intText(root, "votecount"),
            playerType = intText(root, "playertype"),
            network = nullableText(root, "network"),
            dailyPaySize = floatText(root, "dailypaysize"),
            dailyPayReduction = floatText(root, "dailyPayReduction", 1f),
            respawnMoney = floatText(root, "respawnmoney"),
            maximumPettyCash = floatText(root, "maximumpettycash"),
            dropTable = intText(root, "dropTable"),
            currentQuests = readCurrentQuests(root),
            involvedQuests = readInvolvedQuests(root),
            completedQuests = readCompletedQuests(root),
            allowedNetworks = readTextList(root, "allowedNetwork"),
            logEntries = readLogEntries(root),
            globals = readGlobals(root),
            hdType = intText(root, "hdtype"),
            lastPaid = longText(root, "lastpaid"),
            pettyCash = floatText(root, "pettycash"),
            bank = floatText(root, "bank"),
            defaultAttack = intText(root, "defaultattack"),
            defaultBank = intText(root, "defaultbank"),
            defaultFtp = intText(root, "defaultftp"),
            defaultHttp = intText(root, "defaulthttp"),
            defaultShipping = intText(root, "defaultshipping"),
            stats = readStats(root),
            commodityAmount = readFloatSeries(root, "commodity"),
            commodityRespawn = readFloatSeries(root, "commodityrespawn"),
            portsXml = outerXml(root, "ports").ifBlank { defaultPortsXml() },
            watchesXml = outerXml(root, "watches").ifBlank { defaultWatchesXml() },
            fileSystemXml = outerXml(root, "files").ifBlank { defaultFilesXml() },
            website = readWebsite(root),
            equipmentXml = outerXmls(root, "equipment").ifBlank { defaultEquipmentXml() },
            preferences = readPreferences(root)
        )
    }

    private fun readWebsite(root: Element): ComputerWebsiteSnapshot {
        val website = child(root, "website")
        if (website == null) {
            return ComputerWebsiteSnapshot()
        }

        return ComputerWebsiteSnapshot(
            myVotes = intText(website, "myvotes"),
            storeRevenueTarget = text(website, "storerevenue"),
            adRevenueTarget = nullableText(website, "adrevenue"),
            title = nullableText(website, "title"),
            body = nullableText(website, "body")
        )
    }

    private fun readStats(root: Element): ComputerStatsSnapshot {
        val stats = child(root, "stats") ?: return ComputerStatsSnapshot()
        return ComputerStatsSnapshot(
            attackXp = floatText(stats, "attackxp"),
            merchantingXp = floatText(stats, "merchantingxp"),
            firewallXp = floatText(stats, "firewallxp"),
            watchXp = floatText(stats, "watchxp"),
            scanningXp = floatText(stats, "scanningxp"),
            webDesignXp = floatText(stats, "webdesignxp"),
            redirectingXp = floatText(stats, "redirectingxp"),
            repairXp = floatText(stats, "repairxp")
        )
    }

    private fun readCurrentQuests(root: Element): List<CurrentQuestSnapshot> {
        return children(root, "currentquest").map { quest ->
            CurrentQuestSnapshot(
                id = intText(quest, "id"),
                label = text(quest, "label"),
                tasks = children(quest, "task").map { task ->
                    CurrentQuestTaskSnapshot(
                        name = text(task, "name"),
                        complete = booleanText(task, "complete"),
                        label = text(task, "label")
                    )
                }
            )
        }
    }

    private fun readCompletedQuests(root: Element): List<CompletedQuestSnapshot> {
        return children(root, "completedquest").map { quest ->
            CompletedQuestSnapshot(
                id = intText(quest, "id"),
                label = text(quest, "label")
            )
        }
    }

    private fun readInvolvedQuests(root: Element): List<Int> {
        return children(root, "involvedquest").map { quest ->
            intText(quest, "id")
        }
    }

    private fun readLogEntries(root: Element): List<LogEntrySnapshot> {
        return children(root, "logentry").map { entry ->
            LogEntrySnapshot(
                ip = entry.getAttribute("ip"),
                message = nullableText(entry)
            )
        }
    }

    private fun readGlobals(root: Element): List<GlobalSnapshot> {
        return children(root, "global").map { global ->
            GlobalSnapshot(
                type = global.getAttribute("type").ifBlank { "TYPE" },
                value = nullableText(global)
            )
        }
    }

    private fun readPreferences(root: Element): Map<String, String> {
        val preferences = linkedMapOf<String, String>()
        val prefs = child(root, "preferences") ?: return preferences
        children(prefs, "preference").forEach { pref ->
            val name = text(pref, "name")
            val value = text(pref, "value")
            if (name.isNotBlank()) {
                preferences[name] = value
            }
        }
        return preferences
    }

    private fun readFloatSeries(root: Element, sectionName: String): List<Float> {
        val section = child(root, sectionName) ?: return defaultCommodityValues()
        val values = children(section, "value").map { floatText(it) }
        return if (values.size >= 5) values.take(5) else values + List(5 - values.size) { 0f }
    }

    private fun StringBuilder.appendCurrentQuests(currentQuests: List<CurrentQuestSnapshot>) {
        currentQuests.forEach { quest ->
            append("<currentquest>\n")
            appendTag("id", quest.id, indent = "    ")
            appendTag("label", quest.label, indent = "    ")
            quest.tasks.forEach { task ->
                append("    <task>\n")
                appendTag("name", task.name, indent = "      ")
                appendTag("complete", task.complete, indent = "      ")
                appendTag("label", task.label, indent = "      ")
                append("    </task>\n")
            }
            append("</currentquest>\n")
        }
    }

    private fun StringBuilder.appendInvolvedQuests(involvedQuests: List<Int>) {
        involvedQuests.forEach { questId ->
            append("<involvedquest>\n")
            appendTag("id", questId, indent = "   ")
            append("</involvedquest>\n")
        }
    }

    private fun StringBuilder.appendCompletedQuests(completedQuests: List<CompletedQuestSnapshot>) {
        completedQuests.forEach { quest ->
            append("<completedquest>\n")
            appendTag("id", quest.id, indent = "   ")
            appendTag("label", quest.label, indent = "   ")
            append("</completedquest>\n")
        }
    }

    private fun StringBuilder.appendLogEntries(logEntries: List<LogEntrySnapshot>) {
        logEntries.forEach { entry ->
            append("<logentry ip=\"")
            append(escapeXmlAttribute(entry.ip))
            append("\"><![CDATA[")
            append(escapeCdata(entry.message ?: "null"))
            append("]]></logentry>")
        }
    }

    private fun StringBuilder.appendGlobals(globals: List<GlobalSnapshot>) {
        globals.take(20).forEach { global ->
            append("<global type=\"")
            append(escapeXmlAttribute(global.type))
            append("\"><![CDATA[")
            append(escapeCdata(global.value ?: "null"))
            append("]]></global>")
        }
    }

    private fun StringBuilder.appendStats(stats: ComputerStatsSnapshot) {
        append("<stats>\n")
        appendTag("attackxp", stats.attackXp)
        appendTag("merchantingxp", stats.merchantingXp)
        appendTag("firewallxp", stats.firewallXp)
        appendTag("watchxp", stats.watchXp)
        appendTag("scanningxp", stats.scanningXp)
        appendTag("webdesignxp", stats.webDesignXp)
        appendTag("redirectingxp", stats.redirectingXp)
        appendTag("repairxp", stats.repairXp)
        append("</stats>\n")
    }

    private fun StringBuilder.appendFloatSeries(sectionName: String, values: List<Float>) {
        append("<")
        append(sectionName)
        append(">\n")
        values.take(5).forEach { value ->
            append("     <value>")
            append(value)
            append("</value>\n")
        }
        append("</")
        append(sectionName)
        append(">\n")
    }

    private fun StringBuilder.appendWebsite(website: ComputerWebsiteSnapshot) {
        append("<website>\n")
        appendTag("myvotes", website.myVotes)
        appendTag("storerevenue", website.storeRevenueTarget)
        appendCdataTag("adrevenue", website.adRevenueTarget ?: "null")
        appendCdataTag("title", website.title ?: "null")
        appendCdataTag("body", website.body ?: "null")
        append("</website>\n")
    }

    private fun StringBuilder.appendPreferences(preferences: Map<String, String>) {
        if (preferences.isEmpty()) {
            return
        }
        append("<preferences>\n")
        preferences.forEach { (name, value) ->
            append("   <preference>\n")
            appendTag("name", name, indent = "      ")
            appendTag("value", value, indent = "      ")
            append("   </preference>\n")
        }
        append("</preferences>\n")
    }

    private fun StringBuilder.appendTag(name: String, value: String, indent: String = "") {
        append(indent)
        append("<")
        append(name)
        append(">")
        append(escapeXmlText(value))
        append("</")
        append(name)
        append(">\n")
    }

    private fun StringBuilder.appendTag(name: String, value: Int, indent: String = "") = appendTag(name, value.toString(), indent)

    private fun StringBuilder.appendTag(name: String, value: Long, indent: String = "") = appendTag(name, value.toString(), indent)

    private fun StringBuilder.appendTag(name: String, value: Float, indent: String = "") = appendTag(name, value.toString(), indent)

    private fun StringBuilder.appendTag(name: String, value: Boolean, indent: String = "") = appendTag(name, if (value) "true" else "false", indent)

    private fun StringBuilder.appendCdataTag(name: String, value: String, indent: String = "") {
        append(indent)
        append("<")
        append(name)
        append("><![CDATA[")
        append(escapeCdata(value))
        append("]]></")
        append(name)
        append(">\n")
    }

    private fun text(parent: Element, childName: String): String {
        return child(parent, childName)?.let { text(it) } ?: ""
    }

    private fun text(parent: Element): String {
        return nodeText(parent)
    }

    private fun nullableText(parent: Element, childName: String): String? {
        val value = text(parent, childName)
        return if (value.isBlank() || value == "null") null else value
    }

    private fun nullableText(element: Element): String? {
        val value = text(element)
        return if (value.isBlank() || value == "null") null else value
    }

    private fun intText(parent: Element, childName: String, default: Int = 0): Int {
        return text(parent, childName).trim().toIntOrNull() ?: default
    }

    private fun longText(parent: Element, childName: String, default: Long = 0L): Long {
        return text(parent, childName).trim().toLongOrNull() ?: default
    }

    private fun floatText(parent: Element, childName: String, default: Float = 0f): Float {
        return text(parent, childName).trim().toFloatOrNull() ?: default
    }

    private fun floatText(element: Element, default: Float = 0f): Float {
        return text(element).trim().toFloatOrNull() ?: default
    }

    private fun booleanText(parent: Element, childName: String): Boolean {
        return text(parent, childName).trim().equals("true", ignoreCase = true) || text(parent, childName).trim() == "1"
    }

    private fun readTextList(root: Element, childName: String): List<String> {
        return children(root, childName).map { text(it) }
    }

    private fun child(parent: Element, childName: String): Element? {
        return children(parent, childName).firstOrNull()
    }

    private fun children(parent: Element, childName: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == childName) {
                result += node as Element
            }
        }
        return result
    }

    private fun outerXml(parent: Element, childName: String): String {
        return child(parent, childName)?.let { nodeToString(it) } ?: ""
    }

    private fun outerXmls(parent: Element, childName: String): String {
        return children(parent, childName).joinToString(separator = "") { nodeToString(it) }
    }

    private fun nodeToString(node: Node): String {
        val transformerFactory = TransformerFactory.newInstance()
        val transformer = transformerFactory.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.INDENT, "no")
        }
        val writer = StringWriter()
        transformer.transform(DOMSource(node), StreamResult(writer))
        return writer.toString()
    }

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isCoalescing = true
            isNamespaceAware = false
            isIgnoringComments = true
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun escapeXmlText(value: String): String {
        return buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }

    private fun escapeXmlAttribute(value: String): String = escapeXmlText(value)

    private fun escapeCdata(value: String): String = value.replace("]]>", "]]&gt;")

    private fun nodeText(node: Node): String {
        return when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> node.nodeValue ?: ""
            Node.ELEMENT_NODE -> buildString {
                val nodes = node.childNodes
                for (i in 0 until nodes.length) {
                    val child = nodes.item(i)
                    if (child.nodeType == Node.TEXT_NODE && child.nodeValue.isNullOrBlank()) {
                        continue
                    }
                    append(nodeText(child))
                }
            }
            else -> ""
        }
    }

    companion object {
        private const val NPC_TYPE = 1
    }
}
