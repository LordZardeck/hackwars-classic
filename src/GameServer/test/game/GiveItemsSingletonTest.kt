package game

import com.hackwars.data.model.AttachedNetworkLink
import com.hackwars.data.model.DropItemData
import com.hackwars.data.model.NetworkDefinition
import com.hackwars.data.model.NetworkNpcView
import com.hackwars.data.model.PendingPurchase
import com.hackwars.data.model.SearchBootstrapRow
import com.hackwars.data.service.GameWorldDataService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GiveItemsSingletonTest {
    @After
    fun tearDown() {
        GiveItemsSingleton.resetWorldDataServiceForTests()
    }

    @Test
    fun giveFiles_marksFulfilledPurchasesAfterSuccessfulGrant() {
        val service = FakeWorldDataService(
            purchases = listOf(PendingPurchase(storeItemId = 1, boughtItemId = 99L))
        )
        GiveItemsSingleton.installWorldDataServiceForTests(service)

        val computer = mock<Computer>()
        val fileSystem = mock<FileSystem>()
        val handler = mock<ComputerHandler>()
        whenever(computer.getIP()).thenReturn("10.0.0.1")
        whenever(computer.getFileSystem()).thenReturn(fileSystem)
        whenever(fileSystem.getSpaceLeft()).thenReturn(99)

        GiveItemsSingleton.getInstance().giveFiles(computer, handler)

        assertEquals(listOf(99L), service.markedPurchases)
    }

    private class FakeWorldDataService(
        private val purchases: List<PendingPurchase>,
    ) : GameWorldDataService {
        val markedPurchases = mutableListOf<Long>()

        override fun loadNetworkDefinitions(): List<NetworkDefinition> = emptyList()

        override fun findDropItems(dropId: Int): List<DropItemData> = emptyList()

        override fun findDomainByIp(ip: String): String? = null

        override fun findPendingPurchasesByIp(ip: String): List<PendingPurchase> = purchases

        override fun markPurchasesGiven(ids: Collection<Long>) {
            markedPurchases += ids
        }
    }
}
