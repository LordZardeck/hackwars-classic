package game.computer.persistence

import game.HackerFile
import game.LegacyComputerPersistenceSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonComputerPersistenceSupportTest {
    private val xmlPersistence = XmlComputerPersistence()
    private val jsonPersistence = JsonComputerPersistence()
    private val support = JsonComputerPersistenceSupport(jsonPersistence, LegacyComputerPersistenceSupport(xmlPersistence))

    @Test
    fun `export snapshot writes website and script text to blob references and restores losslessly`() {
        val snapshot = xmlPersistence.parse(sampleSaveXml())

        val write = support.exportSnapshot(snapshot)
        val manifest = jsonPersistence.parse(write.manifestJson)
        val restored = support.toSnapshot(manifest, write.blobs)

        assertEquals(1, write.version)
        assertTrue(write.blobs.any { it.path == "website/body" && it.textContent == "Body text" })
        assertTrue(write.blobs.any { it.path == "ports/3/program/enter" && it.textContent == "enter-script" })
        assertTrue(write.blobs.any { it.path == "watches/0/program/fire" && it.textContent == "watch-fire" })
        assertTrue(write.blobs.any { it.path == "files/0/content/data" && it.textContent == "notes here" })

        val websiteBodyRef = manifest.website.body?.blobRef
        assertNotNull(websiteBodyRef)
        assertEquals("website/body", websiteBodyRef.path)
        assertEquals("Body text", restored.website.body)
        assertTrue(restored.portsXml.contains("<number>3</number>"))
        assertTrue(restored.portsXml.contains("<enter><![CDATA[enter-script]]></enter>"))
        assertTrue(restored.watchesXml.contains("<fire><![CDATA[watch-fire]]></fire>"))
        assertTrue(restored.fileSystemXml.contains("<data><![CDATA[notes here]]></data>"))
        assertTrue(restored.equipmentXml.contains("<name><![CDATA[gear-a]]></name>"))
    }

    @Test
    fun `restore tolerates missing firewall special attributes`() {
        val manifest = JsonComputerSaveManifest(
            ip = "10.0.0.12",
            ports = listOf(
                PortSave(
                    number = 3,
                    type = 3,
                    firewall = HackerFileSave(
                        type = HackerFile.NEW_FIREWALL,
                        name = "Firewall",
                        content = mapOf(
                            "name" to TextFieldSave(inlineValue = "fw"),
                            "attack_damage" to TextFieldSave(inlineValue = "10"),
                            "equip_level" to TextFieldSave(inlineValue = "1"),
                            "store_price" to TextFieldSave(inlineValue = "20"),
                            "bank_damage_modifier" to TextFieldSave(inlineValue = "1"),
                            "attack_damage_modifier" to TextFieldSave(inlineValue = "1"),
                            "redirect_damage_modifier" to TextFieldSave(inlineValue = "1"),
                            "ftp_damage_modifier" to TextFieldSave(inlineValue = "1"),
                            "http_damage_modifier" to TextFieldSave(inlineValue = "1"),
                        ),
                    ),
                ),
            ),
        )

        val restored = support.toSnapshot(manifest, emptyList())

        assertTrue(restored.portsXml.contains("<specialAttribute1>"))
        assertTrue(restored.portsXml.contains("<specialAttribute2>"))
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
                <number>3</number>
                <type>3</type>
                <health>100.0</health>
                <onoff>1</onoff>
                <cpu>3.5</cpu>
                <note><![CDATA[watch the port]]></note>
                <firewall>
                    <file>
                        <type>28</type>
                        <name><![CDATA[Firewall]]></name>
                        <location><![CDATA[]]></location>
                        <description><![CDATA[]]></description>
                        <price>0.0</price>
                        <quantity>1</quantity>
                        <cpu>0.0</cpu>
                        <maker><![CDATA[]]></maker>
                        <content>
                            <name><![CDATA[fw]]></name>
                            <specialAttribute1>
                                <name><![CDATA[bonus]]></name>
                                <value><![CDATA[5]]></value>
                                <long_desc><![CDATA[Long firewall description]]></long_desc>
                                <short_desc><![CDATA[Short firewall description]]></short_desc>
                            </specialAttribute1>
                            <specialAttribute2>
                                <name><![CDATA[bonus2]]></name>
                                <value><![CDATA[6]]></value>
                                <long_desc><![CDATA[Alt long description]]></long_desc>
                                <short_desc><![CDATA[Alt short description]]></short_desc>
                            </specialAttribute2>
                            <attack_damage><![CDATA[10]]></attack_damage>
                            <equip_level><![CDATA[1]]></equip_level>
                            <store_price><![CDATA[20]]></store_price>
                            <bank_damage_modifier><![CDATA[1]]></bank_damage_modifier>
                            <attack_damage_modifier><![CDATA[1]]></attack_damage_modifier>
                            <redirect_damage_modifier><![CDATA[1]]></redirect_damage_modifier>
                            <ftp_damage_modifier><![CDATA[1]]></ftp_damage_modifier>
                            <http_damage_modifier><![CDATA[1]]></http_damage_modifier>
                        </content>
                    </file>
                </firewall>
                <dummy>0</dummy>
                <malicioustarget><![CDATA[]]></malicioustarget>
                <enter><![CDATA[enter-script]]></enter>
                <exit><![CDATA[exit-script]]></exit>
                <submit><![CDATA[submit-script]]></submit>
            </port>
        </ports>
        <watches>
            <watch>
                <cpu>1.0</cpu>
                <on>1</on>
                <type>0</type>
                <note><![CDATA[health watch]]></note>
                <observedport>3</observedport>
                <installport>3</installport>
                <searchfirewall>0</searchfirewall>
                <quantity>5.0</quantity>
                <fire><![CDATA[watch-fire]]></fire>
            </watch>
        </watches>
        <files>
            <directory><![CDATA[Public/]]></directory>
            <file>
                <type>9</type>
                <name><![CDATA[notes]]></name>
                <location><![CDATA[Public/]]></location>
                <description><![CDATA[Demo file]]></description>
                <price>12.5</price>
                <quantity>2</quantity>
                <cpu>0.5</cpu>
                <maker><![CDATA[Maker]]></maker>
                <content>
                    <data><![CDATA[notes here]]></data>
                    <level><![CDATA[1]]></level>
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
                <type>18</type>
                <name><![CDATA[gear-a]]></name>
                <location><![CDATA[]]></location>
                <description><![CDATA[]]></description>
                <price>1.0</price>
                <quantity>1</quantity>
                <cpu>0.1</cpu>
                <maker><![CDATA[]]></maker>
                <content>
                    <attribute0><![CDATA[1]]></attribute0>
                    <attribute1><![CDATA[2]]></attribute1>
                    <attribute2><![CDATA[3]]></attribute2>
                    <quality0><![CDATA[4]]></quality0>
                    <quality1><![CDATA[5]]></quality1>
                    <quality2><![CDATA[6]]></quality2>
                    <timeout><![CDATA[7]]></timeout>
                    <maxquality><![CDATA[8]]></maxquality>
                    <currentquality><![CDATA[9]]></currentquality>
                    <lastdegrade><![CDATA[10]]></lastdegrade>
                </content>
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
}
