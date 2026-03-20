package game

import assignments.PacketNetwork
import com.hackwars.data.service.GameWorldDataService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import server.runtime.GameServerRuntime
import server.runtime.GameServerService
import game.data.GameServerDataLocator

/**
 * Description: This is the Network singleton. It loads all the networks into existence.
 * The background scheduler is coroutine-driven.
 */
class Network internal constructor(
    private var computerHandler: NetworkSwitch?,
    private val runtime: GameServerRuntime = GameServerRuntime(),
    private val attackSleepMs: Long = ATTACK_SLEEP,
    private val bootstrapNetworks: Boolean = true,
    private val worldDataService: GameWorldDataService? = null
) : GameServerService, Cloneable {
    companion object {
        private val Logger = LoggerFactory.getLogger(Network::class.java)
        //The root network.
        const val ROOT_NETWORK: String = "UGOPNet"
        const val JAIL_NETWORK: String = "JuniperPenetentiary"
        const val ATTACK_SLEEP: Long = 180000
        private var networkSingleton: Network? = null

        @JvmStatic
        @Synchronized
        fun getInstance(computerHandler: NetworkSwitch?): Network {
            if (networkSingleton == null) {
                networkSingleton = Network(computerHandler).also { it.start() }
            } else if (computerHandler != null) {
                networkSingleton!!.computerHandler = computerHandler
            }
            return networkSingleton!!
        }

        @JvmStatic
        @Synchronized
        fun resetForTests() {
            networkSingleton?.shutdown()
            networkSingleton = null
        }

        private fun coerceFloat(value: Any?, defaultValue: Float = 0.0f): Float {
            return when (value) {
                is Float -> value
                is Double -> value.toFloat()
                is Int -> value.toFloat()
                is Long -> value.toFloat()
                is Number -> value.toFloat()
                is String -> value.toFloatOrNull() ?: defaultValue
                else -> defaultValue
            }
        }
    }

    private val networkNodes = HashMap<Any?, Any?>()
    private var schedulerJob: Job? = null
    @Volatile
    private var started = false

    /**
     * Add a player to a certain network. (Used primarily when logging on to set a player to root.)
     */
    @Synchronized
    fun addToNetwork(networkName: String?, playerIP: String?) {
        val node = networkNodes[networkName] as HashMap<Any?, Any?>?
        if (node != null) {
            val players = (node["players"] as HashMap<Any?, Any?>?) ?: HashMap()
            players[playerIP] = playerIP
            node["players"] = players
        }
    }

    /**
     * Add a player to a certain network. (Used primarily when logging on to set a player to root.)
     */
    @Synchronized
    fun removeFromNetwork(networkName: String?, playerIP: String?) {
        val node = networkNodes[networkName] as HashMap<*, *>?
        if (node != null) {
            val players = node["players"] as HashMap<*, *>?
            players?.remove(playerIP)
        }
    }

    /**
     * Fetch the network information for this network.
     */
    @Synchronized
    fun getNetworkInformation(networkName: String?): PacketNetwork {
        ensureRootNetwork()
        val networkNode = networkNodes[networkName] as HashMap<*, *>? ?: networkNodes[ROOT_NETWORK] as HashMap<*, *>?

        val packetNetwork = PacketNetwork()
        packetNetwork.name = networkName
        packetNetwork.setAttackNPCs(networkNode?.get("attackNPCs") as ArrayList<*>? ?: ArrayList<Any?>())
        packetNetwork.questNPCs = networkNode?.get("questNPCs") as ArrayList<*>? ?: ArrayList<Any?>()
        packetNetwork.miningNPCs = networkNode?.get("miningNPCs") as ArrayList<*>? ?: ArrayList<Any?>()
        packetNetwork.storeNPCs = networkNode?.get("storeNPCs") as ArrayList<*>? ?: ArrayList<Any?>()
        packetNetwork.storeIP = networkNode?.get("storeNPC") as String? ?: ""
        return packetNetwork
    }

    /**
     * Request that a player be switched to another network.
     */
    @Synchronized
    fun switchNetwork(startNetwork: String, endNetwork: String?, playerIP: String?): String? {
        if (startNetwork == endNetwork) {
            return "You are already on $startNetwork."
        }

        var message: String? = "There is no connection between $startNetwork and $endNetwork."
        val startNode = networkNodes[startNetwork] as HashMap<*, *>
        val attachedNetworks = startNode["attachedNetworks"] as HashMap<*, *>
        if (attachedNetworks[endNetwork] != null) {
            message = attachedNetworks[endNetwork] as String?
        }
        return message
    }

    override fun start() {
        if (started) {
            return
        }
        if (bootstrapNetworks) {
            Logger.info("Bootstrapping network registry")
            loadNetworks()
        } else {
            ensureRootNetwork()
        }
        synchronized(this) {
            if (started) {
                return
            }
            schedulerJob = runtime.scope.launch(CoroutineName("NetworkScheduler")) {
                runScheduler()
            }
            started = true
        }
        Logger.info("Network scheduler started")
    }

    override fun shutdown() {
        synchronized(this) {
            schedulerJob?.cancel()
            schedulerJob = null
            started = false
            runtime.close()
        }
    }

    override suspend fun join() {
        schedulerJob?.join()
    }

    suspend fun shutdownAndJoin() {
        shutdown()
        join()
    }

    @Throws(CloneNotSupportedException::class)
    public override fun clone(): Any {
        throw CloneNotSupportedException()
    }

    @Synchronized
    fun loadNetworks(): HashMap<*, *> {
        try {
            Logger.info("Loading networks from database")
            val service = worldDataService ?: GameServerDataLocator.worldService()
            service.loadNetworkDefinitions().forEach { definition ->
                val networkInfo = HashMap<Any?, Any?>()
                networkInfo["attachedNetworks"] = HashMap<Any?, Any?>().apply {
                    definition.attachedNetworks.forEach { link ->
                        put(link.attachedNetworkName, link.entranceMessage)
                    }
                }
                networkInfo["storeNPCs"] = definition.storeNpcs.map { npc ->
                    hashMapOf<Any?, Any?>(
                        "ip" to npc.ip,
                        "name" to npc.name,
                        "title" to npc.title,
                    )
                }.toCollection(ArrayList())
                networkInfo["storeNPC"] = definition.storeNpcIp
                networkInfo["miningNPCs"] = definition.miningNpcs.map { npc ->
                    hashMapOf<Any?, Any?>(
                        "ip" to npc.ip,
                        "commodity" to npc.resource,
                        "name" to npc.name,
                        "title" to npc.title,
                    )
                }.toCollection(ArrayList())
                networkInfo["attackNPCs"] = definition.attackNpcs.map { npc ->
                    hashMapOf<Any?, Any?>(
                        "ip" to npc.ip,
                        "name" to npc.name,
                        "title" to npc.title,
                    )
                }.toCollection(ArrayList())
                networkInfo["questNPCs"] = definition.questNpcs.map { npc ->
                    hashMapOf<Any?, Any?>(
                        "ip" to npc.ip,
                        "name" to npc.name,
                        "title" to npc.title,
                    )
                }.toCollection(ArrayList())
                networkInfo["attackProbability"] = definition.attackProbability
                println("network = ${definition.name}, storeNPC = ${definition.storeNpcIp}")
                networkNodes[definition.name] = networkInfo
            }
            ensureRootNetwork()
            Logger.info("Loaded {} networks", networkNodes.size)
        } catch (e: Exception) {
            Logger.error("Failed to load networks", e)
            e.printStackTrace()
        }

        return networkNodes
    }

    internal fun clearNetworksForTests() {
        synchronized(this) {
            networkNodes.clear()
        }
    }

    internal fun putNetworkForTests(name: String, networkInfo: HashMap<Any?, Any?>) {
        synchronized(this) {
            networkNodes[name] = networkInfo
        }
    }

    private fun ensureRootNetwork() {
        if (networkNodes[ROOT_NETWORK] == null) {
            val networkInfo = HashMap<Any?, Any?>()
            networkInfo["attachedNetworks"] = HashMap<Any?, Any?>()
            networkInfo["storeNPCs"] = ArrayList<Any?>()
            networkInfo["miningNPCs"] = ArrayList<Any?>()
            networkInfo["attackNPCs"] = ArrayList<Any?>()
            networkInfo["questNPCs"] = ArrayList<Any?>()
            networkInfo["attackProbability"] = 0.0f
            networkInfo["storeNPC"] = ""
            networkNodes[ROOT_NETWORK] = networkInfo
        }
    }

    private suspend fun runScheduler() {
        while (runtime.scope.isActive) {
            try {
                dispatchAttackTick()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            delay(attackSleepMs)
        }
    }

    private fun dispatchAttackTick() {
        val snapshots = synchronized(this) {
            networkNodes.values.mapNotNull { it as? HashMap<*, *> }
        }

        val attackRandomize = Math.random().toFloat()
        for (network in snapshots) {
            val attackProbability = coerceFloat(network["attackProbability"])
            if (attackRandomize < attackProbability) {
                val players = (network["players"] as HashMap<*, *>?)?.values?.toTypedArray()
                val attackNpcs = (network["attackNPCs"] as ArrayList<*>?)?.toTypedArray()

                if (players != null && attackNpcs != null && players.isNotEmpty() && attackNpcs.isNotEmpty()) {
                    val attackMe = (Math.random() * players.size).toInt()
                    val attackWithMe = (Math.random() * attackNpcs.size).toInt()

                    val parameter: Array<Any?> = arrayOf((attackNpcs[attackWithMe] as HashMap<*, *>)["ip"] as String?)

                    try {
                        computerHandler?.addData(
                            ApplicationData(
                                "launchNetworkAttack",
                                parameter,
                                0,
                                players[attackMe] as String?
                            ),
                            players[attackMe] as String?
                        )
                    } catch (e: Exception) {
                        // No reason to print this.
                    }
                }
            }
        }
    }
}
