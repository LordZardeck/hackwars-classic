package game

import assignments.PacketNetwork
import util.sql

/**
 * Description: This is the Network singleton.  It loads all the networks into existence.  Sometimes it blows boiling hot lava all over your keyboard, forcing upgrades.
 */

class Network private constructor(private var computerHandler: NetworkSwitch?) : Runnable, Cloneable {
    companion object {
        //The root network.
        const val ROOT_NETWORK: String = "UGOPNet"
        const val JAIL_NETWORK: String = "JuniperPenetentiary"
        const val ATTACK_SLEEP: Long = 180000
        private var networkSingleton: Network? = null

        @JvmStatic
        @Synchronized
        fun getInstance(computerHandler: NetworkSwitch?): Network {
            if (networkSingleton == null) {
                networkSingleton = Network(computerHandler)
            }
            return networkSingleton!!
        }
    }

    //private Thread
    private var MyThread: Thread? = null

    //MYSQL INFO.
    private val Connection = "localhost"
    private val DB = "hackwars"
    private val Username = "root"
    private val Password = ""

    private val networkNodes = HashMap<Any?, Any?>()

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
        val Node = networkNodes[networkName] as HashMap<*, *>?
        if (Node != null) {
            val Players = Node["players"] as HashMap<*, *>?
            if (Players != null) {
                Players.remove(playerIP)
            }
        }
    }

    /**
     * Fetch the network information for this network.
     */
    @Synchronized
    fun getNetworkInformation(networkName: String?): PacketNetwork {
        val networkNode = networkNodes[networkName] as HashMap<*, *>? ?: networkNodes[ROOT_NETWORK] as HashMap<*, *>?

        val packetNetwork = PacketNetwork()
        packetNetwork.name = networkName
        packetNetwork.setAttackNPCs(networkNode?.get("attackNPCs") as ArrayList<*>? ?: ArrayList<Any?>())
        packetNetwork.questNPCs = networkNode?.get("questNPCs") as ArrayList<*>? ?: ArrayList<Any?>()
        packetNetwork.miningNPCs = networkNode?.get("miningNPCs") as ArrayList<*>? ?: ArrayList<Any?>()
        packetNetwork.storeNPCs = networkNode?.get("storeNPCs") as ArrayList<*>? ?: ArrayList<Any?>()
        packetNetwork.storeIP = networkNode?.get("storeNPC") as String? ?: ""
        return (packetNetwork)
    }

    /**
     * Request that a player be switched to another network.
     */
    @Synchronized
    fun switchNetwork(startNetwork: String, endNetwork: String?, playerIP: String?): String? {
        // this should never happen as the check now happens in Computer.java
        if (startNetwork == endNetwork)  //Check whether you are trying to switch to the same network.
            return ("You are already on $startNetwork.")

        var message: String? = "There is no connection between $startNetwork and $endNetwork."
        val StartNetwork = networkNodes[startNetwork] as HashMap<*, *>
        val AttachedNetworks = StartNetwork["attachedNetworks"] as HashMap<*, *>
        if (AttachedNetworks[endNetwork] != null) {
            message = AttachedNetworks[endNetwork] as String?
        }
        return (message)
    }

    init {
        loadNetworks()

        MyThread = Thread(this)
        MyThread!!.start()
    }

    @Throws(CloneNotSupportedException::class)
    public override fun clone(): Any {
        throw CloneNotSupportedException()
    }

    fun loadNetworks(): HashMap<*, *> {
        try {
            // load all the network nodes from the database
            val C = sql(Connection, DB, Username, Password)

            var result: ArrayList<*>? = null
            var result1: ArrayList<*>? = null

            val Q = "SELECT id, name, attack_probability FROM network"
            result = C.process(Q)
            if (result != null && result.size > 0) {
                var i = 0
                while (i < result.size) {
                    // networkInfo is the value of the networks hashmap, for a given network
                    // "storeNPC" returns a string ip
                    // "attackNPCs" returns an arraList of attack NPCs
                    // "miningNPCs" returns an arrayList (p, resource)
                    // "questNPCs" returns an arrayList of quest NPCs
                    // "attachedNetworks" returns an HashMap ( attached network name, entrance message)
                    val networkInfo = HashMap<Any?, Any?>()

                    val networkId = result[i] as String
                    val networkName = result[i + 1] as String?
                    val attackProbability = (result[i + 2] as String?) as Float

                    //String networkStoreIP = (String)result.get(i+2);
                    //networkInfo.put("storeNPC", networkStoreIP);

                    //get the attached networks and their entrance criteria
                    val Q1 =
                        "SELECT n.name,an.entranceMessage FROM network n INNER JOIN attached_networks an ON n.id = an.attached_network_id WHERE an.network_id = $networkId"
                    result1 = C.process(Q1)
                    val attachedNetworksArray = HashMap<Any?, Any?>()
                    if (result1 != null && result1.size > 0) {
                        var j = 0
                        while (j < result1.size) {
                            //  j = attachedNetworkName
                            //  j+1 = entranceMessage
                            attachedNetworksArray.put(result1[j] as String?, result1[j + 1] as String?)
                            j += 2
                        }
                        networkInfo.put("attachedNetworks", attachedNetworksArray)
                    }

                    // variables used in creating the arrayLists & HashMaps for the network NPCs
                    var npcIP: String? = ""
                    var resource: String? = ""
                    var npcName: String? = ""
                    var npcTitle: String? = ""

                    // get the Store NPCs
                    val Qstore =
                        "SELECT npc_ip, name, title FROM network_npc WHERE npc_type = 'store' AND network_id = $networkId"
                    result1 = C.process(Qstore)
                    val storeHashArray = ArrayList<Any?>()
                    if (result1 != null && result1.size > 0) {
                        var j = 0
                        while (j < result1.size) {
                            val storeNPCs = HashMap<Any?, Any?>()
                            npcIP = result1[j] as String?
                            npcName = result1[j + 1] as String?
                            npcTitle = result1[j + 2] as String?
                            storeNPCs.put("ip", npcIP)
                            storeNPCs.put("name", npcName)
                            storeNPCs.put("title", npcTitle)
                            storeHashArray.add(storeNPCs)
                            j += 3
                        }
                    }
                    networkInfo.put("storeNPCs", storeHashArray)
                    // because we have a link to the "Store" from the web browser, we need to set the storeIP
                    var networkStoreIP: String? = ""
                    if (storeHashArray.size > 0) {
                        networkStoreIP = ((storeHashArray[0]) as HashMap<*, *>)["ip"] as String?
                    }
                    println("network = $networkName, storeNPC = $networkStoreIP")
                    networkInfo.put("storeNPC", networkStoreIP)

                    // get all the mining NPCs for this network
                    val Qmining =
                        "SELECT npc_ip, resource, name, title FROM network_npc WHERE npc_type = 'mining' AND network_id = $networkId"
                    result1 = C.process(Qmining)
                    val miningHashArray = ArrayList<Any?>()
                    if (result1 != null && result1.size > 0) {
                        var j = 0
                        while (j < result1.size) {
                            val miningNPCs = HashMap<Any?, Any?>()
                            npcIP = result1[j] as String?
                            resource = result1[j + 1] as String?
                            npcName = result1[j + 2] as String?
                            npcTitle = result1[j + 3] as String?
                            miningNPCs.put("ip", npcIP)
                            miningNPCs.put("commodity", resource)
                            miningNPCs.put("name", npcName)
                            miningNPCs.put("title", npcTitle)
                            miningHashArray.add(miningNPCs)
                            j += 4
                        }
                    }
                    networkInfo.put("miningNPCs", miningHashArray)

                    // get all the attack NPCs for this network
                    val Qattack =
                        "SELECT npc_ip, name, title FROM network_npc WHERE npc_type = 'attack' AND network_id = $networkId"
                    result1 = C.process(Qattack)
                    val attackHashArray = ArrayList<Any?>()
                    if (result1 != null && result1.size > 0) {
                        var j = 0
                        while (j < result1.size) {
                            val attackNPCs = HashMap<Any?, Any?>()
                            npcIP = result1[j] as String?
                            npcName = result1[j + 1] as String?
                            npcTitle = result1[j + 2] as String?
                            attackNPCs.put("ip", npcIP)
                            attackNPCs.put("name", npcName)
                            attackNPCs.put("title", npcTitle)
                            attackHashArray.add(attackNPCs)
                            j += 3
                        }
                    }
                    networkInfo.put("attackNPCs", attackHashArray)

                    // get all the quest NPCs for this network
                    val Qquest =
                        "SELECT npc_ip, name, title FROM network_npc WHERE npc_type = 'quest' AND network_id = $networkId"
                    result1 = C.process(Qquest)
                    val questHashArray = ArrayList<Any?>()
                    if (result1 != null && result1.size > 0) {
                        var j = 0
                        while (j < result1.size) {
                            val questNPCs = HashMap<Any?, Any?>()
                            npcIP = result1[j] as String?
                            npcName = result1[j + 1] as String?
                            npcTitle = result1[j + 2] as String?
                            questNPCs.put("ip", npcIP)
                            questNPCs.put("name", npcName)
                            questNPCs.put("title", npcTitle)
                            questHashArray.add(questNPCs)
                            j += 3
                        }
                    }
                    networkInfo.put("questNPCs", questHashArray)
                    networkInfo.put("attackProbability", attackProbability)
                    networkNodes.put(networkName, networkInfo)
                    i += 3
                }
            }
            if (networkNodes[ROOT_NETWORK] == null) {
                val networkInfo = HashMap<Any?, Any?>()
                networkInfo.put("attachedNetworks", HashMap<Any?, Any?>())
                networkInfo.put("storeNPCs", ArrayList<Any?>())
                networkInfo.put("miningNPCs", ArrayList<Any?>())
                networkInfo.put("attackNPCs", ArrayList<Any?>())
                networkInfo.put("questNPCs", ArrayList<Any?>())
                networkInfo.put("attackProbability", 0)
                networkInfo.put("storeNPC", "")
                networkNodes.put(ROOT_NETWORK, networkInfo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return networkNodes
        // create a class for each
        // stuff them in the hashmap, name is the key, values are the arrayLists of the NPCs
    }

    //The thread for the network.
    override fun run() {
        while (true) {
            try {
                val c = networkNodes.values
                val itr: MutableIterator<*> = c.iterator()
                val attackRandomize = Math.random().toFloat()
                while (itr.hasNext()) {
                    val H = itr.next() as HashMap<*, *>
                    val attackProbability = H["attackProbability"] as Float
                    if (attackRandomize < attackProbability) {
                        var P: Array<Any?>? = null
                        if ((H["players"] as HashMap<*, *>?) != null) {
                            P = (H["players"] as HashMap<*, *>).values.toTypedArray()
                        }

                        var NPC: Array<Any?>? = null
                        if ((H["attackNPCs"] as ArrayList<*>?) != null) {
                            NPC = (H["attackNPCs"] as ArrayList<*>).toTypedArray()
                        }

                        if (P != null) {
                            val attackMe = (Math.random() * P.size).toInt()
                            val attackWithMe = (Math.random() * NPC!!.size).toInt()

                            val Parameter: Array<Any?>? =
                                arrayOf<Any?>((NPC[attackWithMe] as HashMap<*, *>)["ip"] as String?)

                            try {
                                computerHandler!!.addData(
                                    ApplicationData(
                                        "launchNetworkAttack",
                                        Parameter,
                                        0,
                                        P[attackMe] as String?
                                    ), P[attackMe] as String?
                                )
                            } catch (e: Exception) {
                                //No reason to print this.
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                Thread.sleep(ATTACK_SLEEP)
            } catch (e: Exception) {
            }
        }
    }
}
