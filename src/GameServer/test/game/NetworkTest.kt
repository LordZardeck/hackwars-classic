package game

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayList
import java.util.HashMap

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
}
