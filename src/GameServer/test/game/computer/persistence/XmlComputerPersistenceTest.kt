package game.computer.persistence

import org.junit.Test
import org.w3c.dom.CDATASection
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlComputerPersistenceTest {
    private val persistence = XmlComputerPersistence()

    @Test
    fun `parse extracts the expected values`() {
        val snapshot = persistence.parse(sampleSaveXml())

        assertEquals("10.0.0.12", snapshot.ip)
        assertEquals("alice", snapshot.name)
        assertEquals(2, snapshot.cpuType)
        assertEquals(3, snapshot.memoryType)
        assertEquals("secret", snapshot.password)
        assertEquals(7, snapshot.hackCount)
        assertEquals(4, snapshot.voteCount)
        assertEquals(1, snapshot.playerType)
        assertEquals("root", snapshot.network)
        assertEquals(1500.5f, snapshot.dailyPaySize)
        assertEquals(0.75f, snapshot.dailyPayReduction)
        assertEquals(250.0f, snapshot.respawnMoney)
        assertEquals(999.0f, snapshot.maximumPettyCash)
        assertEquals(12, snapshot.dropTable)
        assertEquals(1, snapshot.currentQuests.size)
        assertEquals(1, snapshot.completedQuests.size)
        assertEquals(1, snapshot.involvedQuests.size)
        assertEquals(1, snapshot.allowedNetworks.size)
        assertEquals(1, snapshot.logEntries.size)
        assertEquals(2, snapshot.globals.size)
        assertEquals(2, snapshot.hdType)
        assertEquals(123456789L, snapshot.lastPaid)
        assertEquals(42.5f, snapshot.pettyCash)
        assertEquals(100.0f, snapshot.bank)
        assertEquals(3, snapshot.defaultAttack)
        assertEquals(4, snapshot.defaultBank)
        assertEquals(5, snapshot.defaultFtp)
        assertEquals(6, snapshot.defaultHttp)
        assertEquals(7, snapshot.defaultShipping)
        assertEquals(111.0f, snapshot.stats.attackXp)
        assertEquals(222.0f, snapshot.stats.merchantingXp)
        assertEquals(333.0f, snapshot.stats.firewallXp)
        assertEquals(444.0f, snapshot.stats.watchXp)
        assertEquals(555.0f, snapshot.stats.scanningXp)
        assertEquals(666.0f, snapshot.stats.webDesignXp)
        assertEquals(777.0f, snapshot.stats.redirectingXp)
        assertEquals(888.0f, snapshot.stats.repairXp)
        assertEquals(5, snapshot.commodityAmount.size)
        assertEquals(5, snapshot.commodityRespawn.size)
        assertTrue(snapshot.portsXml.contains("<port>"))
        assertTrue(snapshot.watchesXml.contains("<watch>"))
        assertTrue(snapshot.fileSystemXml.contains("<files>"))
        assertTrue(snapshot.equipmentXml.contains("<equipment>"))
        assertEquals(1, snapshot.preferences.size)
    }

    @Test
    fun `serialize round trips the save structure`() {
        val snapshot = persistence.parse(sampleSaveXml())
        val serialized = persistence.serialize(snapshot)

        assertEquals(normalizeXml(sampleSaveXml()), normalizeXml(serialized))
    }

    private fun sampleSaveXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="no" ?>
        <save>
        <ip>10.0.0.12</ip>
        <name><![CDATA[alice]]></name>
        <cputype>2</cputype>
        <memorytype>3</memorytype>
        <password><![CDATA[secret]]></password>
        <hackcount>7</hackcount>
        <votecount>4</votecount>
        <playertype>1</playertype>
        <network>root</network>
        <dailypaysize>1500.5</dailypaysize>
        <dailyPayReduction>0.75</dailyPayReduction>
        <respawnmoney>250.0</respawnmoney>
        <maximumpettycash>999.0</maximumpettycash>
        <dropTable>12</dropTable>
        <currentquest>
            <id>99</id>
            <label><![CDATA[Main Quest]]></label>
            <task>
                <name>step-1</name>
                <complete>true</complete>
                <label><![CDATA[Open the vault]]></label>
            </task>
        </currentquest>
        <involvedquest>
            <id>101</id>
        </involvedquest>
        <completedquest>
            <id>98</id>
            <label><![CDATA[Done Already]]></label>
        </completedquest>
        <allowedNetwork>alpha</allowedNetwork>
        <logentry ip="192.168.0.2"><![CDATA[log body]]></logentry>
        <global type="STRING"><![CDATA[value]]></global>
        <global type="TYPE"><![CDATA[null]]></global>
        <hdtype>2</hdtype>
        <lastpaid>123456789</lastpaid>
        <pettycash>42.5</pettycash>
        <bank>100.0</bank>
        <defaultattack>3</defaultattack>
        <defaultbank>4</defaultbank>
        <defaultftp>5</defaultftp>
        <defaulthttp>6</defaulthttp>
        <defaultshipping>7</defaultshipping>
        <stats>
        <attackxp>111.0</attackxp>
        <merchantingxp>222.0</merchantingxp>
        <firewallxp>333.0</firewallxp>
        <watchxp>444.0</watchxp>
        <scanningxp>555.0</scanningxp>
        <webdesignxp>666.0</webdesignxp>
        <redirectingxp>777.0</redirectingxp>
        <repairxp>888.0</repairxp>
        </stats>
        <commodity>
             <value>1.0</value>
             <value>2.0</value>
             <value>3.0</value>
             <value>4.0</value>
             <value>5.0</value>
        </commodity>
        <commodityrespawn>
             <value>5.0</value>
             <value>4.0</value>
             <value>3.0</value>
             <value>2.0</value>
             <value>1.0</value>
        </commodityrespawn>
        <ports>
            <port>
                <number>1</number>
                <type>0</type>
                <health>100.0</health>
                <onoff>1</onoff>
                <cpu>3.5</cpu>
                <note><![CDATA[watch the port]]></note>
                <firewall>
                    <file>
                        <type>0</type>
                        <name><![CDATA[None]]></name>
                        <location><![CDATA[]]></location>
                        <description><![CDATA[]]></description>
                        <price>0.0</price>
                        <quantity>1</quantity>
                        <cpu>0.0</cpu>
                        <maker><![CDATA[]]></maker>
                        <content/>
                    </file>
                </firewall>
                <dummy>0</dummy>
                <malicioustarget><![CDATA[]]></malicioustarget>
            </port>
        </ports>
        <watches>
            <watch>
                <cpu>1.0</cpu>
                <on>1</on>
                <type>0</type>
                <note><![CDATA[health watch]]></note>
                <observedport>1</observedport>
                <installport>1</installport>
                <searchfirewall>0</searchfirewall>
                <quantity>5.0</quantity>
                <fire><![CDATA[fire]]></fire>
            </watch>
        </watches>
        <files>
            <directory><![CDATA[Public/]]></directory>
            <file>
                <type>1</type>
                <name><![CDATA[demo]]></name>
                <location><![CDATA[Public/]]></location>
                <description><![CDATA[Demo file]]></description>
                <price>12.5</price>
                <quantity>2</quantity>
                <cpu>0.5</cpu>
                <maker><![CDATA[Maker]]></maker>
                <content>
                    <data><![CDATA[data]]></data>
                </content>
            </file>
        </files>
        <website>
        <myvotes>3</myvotes><storerevenue>store-ip</storerevenue>
        <adrevenue><![CDATA[ad-ip]]></adrevenue>
        <title><![CDATA[Home]]></title>
        <body><![CDATA[Body text]]></body>
        </website>
        <equipment>
            <file>
                <type>1</type>
                <name><![CDATA[gear-a]]></name>
                <location><![CDATA[]]></location>
                <description><![CDATA[]]></description>
                <price>1.0</price>
                <quantity>1</quantity>
                <cpu>0.1</cpu>
                <maker><![CDATA[]]></maker>
                <content/>
            </file>
        </equipment>
        <equipment>
        </equipment>
        <equipment>
        </equipment>
        <preferences>
           <preference>
              <name>theme</name>
              <value>dark</value>
           </preference>
        </preferences>
        </save>
    """.trimIndent()

    private fun normalizeXml(xml: String): String {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isCoalescing = true
            isNamespaceAware = false
            isIgnoringComments = true
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)))
        return normalizeNode(document.documentElement)
    }

    private fun normalizeNode(node: Node): String {
        return when (node.nodeType) {
            Node.ELEMENT_NODE -> normalizeElement(node as Element)
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> escape(node.nodeValue ?: "")
            else -> ""
        }
    }

    private fun normalizeElement(element: Element): String {
        val attrs = buildString {
            val namedNodeMap = element.attributes
            val items = mutableListOf<Pair<String, String>>()
            for (i in 0 until namedNodeMap.length) {
                val attr = namedNodeMap.item(i)
                items += attr.nodeName to (attr.nodeValue ?: "")
            }
            items.sortedBy { it.first }.forEach { (name, value) ->
                append(" ")
                append(name)
                append("=\"")
                append(escape(value))
                append("\"")
            }
        }

        val children = mutableListOf<String>()
        val nodes = element.childNodes
        for (i in 0 until nodes.length) {
            val child = nodes.item(i)
            if (child.nodeType == Node.TEXT_NODE && child.nodeValue.isNullOrBlank()) {
                continue
            }
            if (child.nodeType == Node.ELEMENT_NODE || child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                children += normalizeNode(child)
            }
        }

        return if (children.isEmpty()) {
            "<${element.tagName}$attrs/>"
        } else {
            "<${element.tagName}$attrs>${children.joinToString("")}</${element.tagName}>"
        }
    }

    private fun escape(value: String): String {
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
}
