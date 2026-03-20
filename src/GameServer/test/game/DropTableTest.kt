package game

import com.hackwars.data.model.AttachedNetworkLink
import com.hackwars.data.model.DropItemData
import com.hackwars.data.model.NetworkDefinition
import com.hackwars.data.model.NetworkNpcView
import com.hackwars.data.model.PendingPurchase
import com.hackwars.data.model.SearchBootstrapRow
import com.hackwars.data.service.GameWorldDataService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.HashMap

class DropTableTest {
    @Test
    fun generateDrop_usesDropTableDataFromWorldService() {
        val xml = """
            <file>
                <name><![CDATA[QuestNote]]></name>
                <maker><![CDATA[Maker]]></maker>
                <location><![CDATA[Inbox]]></location>
                <description><![CDATA[Quest item]]></description>
                <quantity>1</quantity>
                <type>9</type>
                <price>0</price>
                <cpu>0</cpu>
                <content>
                    <data><![CDATA[alpha beta]]></data>
                    <level><![CDATA[1]]></level>
                </content>
            </file>
        """.trimIndent()
        val service = FakeWorldDataService(listOf(DropItemData(1, xml)))
        val dropTable = DropTable(7, mock(), worldDataService = service)

        val result = dropTable.generateDrop()

        assertNotNull(result)
        assertEquals("QuestNote", result.getName())
        assertEquals(7, service.requestedDropId)
    }

    private class FakeWorldDataService(
        private val rows: List<DropItemData>,
    ) : GameWorldDataService {
        var requestedDropId: Int = -1

        override fun loadNetworkDefinitions(): List<NetworkDefinition> = emptyList()

        override fun findDropItems(dropId: Int): List<DropItemData> {
            requestedDropId = dropId
            return rows
        }

        override fun findDomainByIp(ip: String): String? = null

        override fun findPendingPurchasesByIp(ip: String): List<PendingPurchase> = emptyList()

        override fun markPurchasesGiven(ids: Collection<Long>) {
        }
    }
}
