package game

import com.hackwars.data.model.AttachedNetworkLink
import com.hackwars.data.model.NetworkDefinition
import com.hackwars.data.model.NetworkNpcView
import com.hackwars.data.model.PendingPurchase
import com.hackwars.data.model.DropItemData
import com.hackwars.data.model.SearchBootstrapRow
import com.hackwars.data.service.GameWorldDataService
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayList
import java.util.HashMap

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTest {
    @Test
    fun schedulerDispatchesAttacksAndRespectsTheConfiguredDelay() = runTest {
        Network.resetForTests()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = server.runtime.GameServerRuntime(dispatcher)
        val switch = CapturingNetworkSwitch()
        val network = Network(
            switch,
            runtime = runtime,
            attackSleepMs = 1_000L,
            bootstrapNetworks = false
        )

        network.clearNetworksForTests()
        network.putNetworkForTests(Network.ROOT_NETWORK, rootNetwork(attackProbability = 1.0f))

        network.start()
        runCurrent()
        assertEquals(1, switch.events.size)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, switch.events.size)

        network.shutdownAndJoin()
    }

    @Test
    fun loadNetworks_populatesRegistryFromDataService() {
        val service = FakeWorldDataService(
            definitions = listOf(
                NetworkDefinition(
                    id = 1,
                    name = "Alpha",
                    attackProbability = 0.75f,
                    attachedNetworks = listOf(AttachedNetworkLink("Beta", "Go beta")),
                    storeNpcs = listOf(NetworkNpcView("store-ip", "store", "", "Store", "Store Title")),
                    miningNpcs = listOf(NetworkNpcView("mine-ip", "mining", "ore", "Mine", "Mine Title")),
                    attackNpcs = listOf(NetworkNpcView("attack-ip", "attack", "", "Attack", "Attack Title")),
                    questNpcs = listOf(NetworkNpcView("quest-ip", "quest", "", "Quest", "Quest Title")),
                )
            )
        )
        val network = Network(
            null,
            runtime = server.runtime.GameServerRuntime(),
            bootstrapNetworks = false,
            worldDataService = service,
        )

        network.loadNetworks()
        val info = network.getNetworkInformation("Alpha")

        assertEquals("Alpha", info.name)
        assertEquals("store-ip", info.storeIP)
        assertEquals(1, info.storeNPCs.size)
        assertEquals(1, info.getRegularNPCs().size)
        assertEquals(1, info.getMiningNPCs().size)
        assertEquals(1, info.getQuestNPCs().size)
        assertTrue(service.loaded)
    }

    private fun rootNetwork(attackProbability: Float): HashMap<Any?, Any?> {
        val network = HashMap<Any?, Any?>()
        val players = HashMap<Any?, Any?>()
        players["player-ip"] = "player-ip"

        val attackNpc = HashMap<Any?, Any?>()
        attackNpc["ip"] = "npc-ip"

        val attackNpcs = ArrayList<Any?>()
        attackNpcs.add(attackNpc)

        network["attachedNetworks"] = HashMap<Any?, Any?>()
        network["storeNPCs"] = ArrayList<Any?>()
        network["miningNPCs"] = ArrayList<Any?>()
        network["attackNPCs"] = attackNpcs
        network["questNPCs"] = ArrayList<Any?>()
        network["attackProbability"] = attackProbability
        network["storeNPC"] = ""
        network["players"] = players
        return network
    }

    private class CapturingNetworkSwitch : NetworkSwitch(null, null) {
        val events = mutableListOf<Pair<ApplicationData, String?>>()

        override fun addData(AD: ApplicationData, ip: String?) {
            events += AD to ip
        }
    }

    private class FakeWorldDataService(
        private val definitions: List<NetworkDefinition>,
    ) : GameWorldDataService {
        var loaded = false

        override fun loadNetworkDefinitions(): List<NetworkDefinition> {
            loaded = true
            return definitions
        }

        override fun findDropItems(dropId: Int): List<DropItemData> = emptyList()

        override fun findDomainByIp(ip: String): String? = null

        override fun findPendingPurchasesByIp(ip: String): List<PendingPurchase> = emptyList()

        override fun markPurchasesGiven(ids: Collection<Long>) {
        }
    }
}
