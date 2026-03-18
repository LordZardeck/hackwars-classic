package server

import assignments.LoginAssignment
import assignments.LoginFailedAssignment
import assignments.PingAssignment
import assignments.RemoteFunctionCall
import com.plink.dolphinnet.Assignment
import com.plink.dolphinnet.ClientData
import com.plink.dolphinnet.Editor
import com.plink.dolphinnet.IParty
import com.plink.dolphinnet.assignments.ZippedAssignment
import game.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rpc.FetchPorts
import util.Encryption
import util.PlayFabTokenVerifier
import util.PlayFabTokenVerifier.AuthResult
import util.Time
import java.util.*

/**
 * (c) Hack Wars 2008
 *
 *
 * Description: This is the main Hack Wars server bridge. It creates the underlying connections and routes packets
 * to actual players of the game.
 */

class HackerServer(e: Editor, serverID: String) : IParty(e), HackerServerBridge {
    companion object {
        private val Logger: Logger = LoggerFactory.getLogger(HackerServer::class.java)

        var MyTime: Time? = null
        var on: Boolean = true
        var SHUTDOWN_AT: Long = 0
    }

    //Data.
    private val Keys = HashMap<Any?, Any?>()
    private val IPs = HashMap<Any?, Any?>()
    private var MyComputerHandler: ComputerHandler? = null
    private val taskQueue = Channel<Any?>(Channel.UNLIMITED)
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var serverJob: Job? = null
    private var serverID = ""
    private val MyEncryption = Encryption()

    /**
     * Get the ID associated with this server.
     */
    override fun getServerID(): String {
        return serverID
    }

    /** Receive a failed assignment. */
    override fun failedAssignment(a: Assignment?) {
    }

    override fun addData(o: Any?) {
        taskQueue.trySend(o).onFailure {
            Logger.error("Failed to enqueue task in addData", it)
        }
    }

    /**
     * Dispatch a packet assignment.
     */
    fun dispatchPacket(assignment: Assignment?, connectionID: Int) {
        (editor.clients.get(connectionID) as ClientData?)?.addJob(assignment)
    }

    /** Receive a completed assignment. */
    @Synchronized
    override fun returnAssignment(assignment: Assignment?) {
        taskQueue.trySend(assignment).onFailure {
            Logger.error("Failed to enqueue task in returnAssignment", it)
        }
    }


    /**
     * Grabs work from the server and dispatches it via the computer handler to
     * individual 'PCs' playing the game.
     */
    private fun processTasks() {
        Logger.info("Game Server Started")

        var clientKey: String?
        while (true) {
            val o = runBlocking { taskQueue.receive() } ?: continue
            try {
                val MyAssignment: Any? = o
                clientKey = ""
                if (o is Assignment) {
                    clientKey = o.getHash()
                }

                //Is somone trying to login?
                if (MyAssignment is LoginAssignment) {
                    if (on) {
                        val MyLoginAssignment = MyAssignment
                        val accessToken = MyLoginAssignment.getAccessToken()
                        var authResult: AuthResult? = null
                        try {
                            authResult = PlayFabTokenVerifier.verify(accessToken)
                        } catch (authError: Exception) {
                            Logger.error("HackerServer: PlayFab authentication failed", authError)
                        }

                        if (authResult == null) {
                            dispatchPacket(LoginFailedAssignment(0), (MyAssignment as Assignment).getReporterID())
                            continue
                        }

                        val user = authResult.getPlayFabId()
                        val ip = authResult.getPlayerIp()

                        if (MyComputerHandler!!.getComputer(ip) != null) {
                            val C = MyComputerHandler!!.getComputer(ip)
                            C.setClientHash(clientKey)
                            C.setPublicKey(MyLoginAssignment.getPublicKey())
                            C.setPlayFabAuthenticated(user)
                            C.setConnectionID(MyLoginAssignment.getReporterID())
                        } else {
                            val C = Computer(
                                user,
                                ip,
                                MyComputerHandler,
                                MyTime,
                                MyLoginAssignment.getReporterID(),
                                this,
                                true
                            )
                            C.setClientHash(clientKey)
                            C.setPublicKey(MyLoginAssignment.getPublicKey())
                            C.setPlayFabAuthenticated(user)
                            MyComputerHandler!!.addComputer(C)
                            C.loadSave()
                        }
                    } else {
                        dispatchPacket(LoginFailedAssignment(0), (MyAssignment as Assignment).getReporterID())
                    }
                } else if (MyAssignment is PingAssignment) {
                    val PA = MyAssignment
                    MyComputerHandler!!.addData(ApplicationData("ping", null, 0, PA.getUser()), PA.getUser())

                    //Return a packet to the server.
                    this.addData(arrayOf<Any>(PingAssignment(0, "bcoe"), (MyAssignment as Assignment).reporterID))

                    if (PA.id == 850335 && PA.user == "bcoe") { //Start booting players.
                        SHUTDOWN_AT = MyTime!!.getCurrentTime()
                        on = false
                        ServerRuntimeState.setShutdownAt(SHUTDOWN_AT)
                        ServerRuntimeState.setRunning(on)
                        MyComputerHandler!!.startCountDown()
                    }
                } else if (MyAssignment is RemoteFunctionCall) {
                    val RFC = MyAssignment

                    try {
                        RFC.decryptFunction(MyEncryption, clientKey)
                    } catch (e: Exception) {
                    }

                    if (RFC.getFunction() == null) {
                        continue
                    }

                    //Sent when you want an array of ports to be updated client side.
                    if (RFC.function == "fetchports") {
                        val fetchPortsCall = FetchPorts.fromRpc(RFC)
                        val ip = crypt(fetchPortsCall.encryptedIp, clientKey)
                        MyComputerHandler!!.addData(
                            ApplicationData("fetchports", null, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else  //Set the default port that an application will execute on.
                        if (RFC.getFunction() == "setdefaultport") {
                            var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                            val port = (RFC.getParameters() as Array<Any?>?)!![1] as Int
                            val type = (RFC.getParameters() as Array<Any?>?)!![2] as Int?
                            ip = crypt(ip, clientKey)
                            MyComputerHandler!!.addData(
                                ApplicationData("setdefaultport", type, port, ip),
                                ip,
                                ApplicationData.OUTSIDE
                            )
                        } else  //RETURN TO THE ROOT NETWORK.
                            if (RFC.getFunction() == "changenetwork") {
                                var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                                ip = crypt(ip, clientKey)
                                val network = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                MyComputerHandler!!.addData(
                                    ApplicationData("changenetwork", network, 0, ip),
                                    ip,
                                    ApplicationData.OUTSIDE
                                )
                            } else  //Heal a specific port.
                                if (RFC.getFunction() == "healport") {
                                    var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                                    val port = (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                    ip = crypt(ip, clientKey)
                                    MyComputerHandler!!.addData(
                                        ApplicationData("heal", null, port, ip),
                                        ip,
                                        ApplicationData.OUTSIDE
                                    )
                                } else  //Allow hacktendo to activate a sprite.
                                    if (RFC.getFunction() == "hacktendoActivate") {
                                        val activateID = (RFC.getParameters() as Array<Any?>?)!![0] as Int
                                        val activateType = (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                        val ip = (RFC.getParameters() as Array<Any?>?)!![2] as String?

                                        val O = arrayOf<Any>(activateID, activateType)
                                        MyComputerHandler!!.addData(
                                            ApplicationData("hacktendoActivate", O, 0, ip),
                                            ip,
                                            ApplicationData.INSIDE
                                        )
                                    } else  //Allow Hacktendo to move objects through space.
                                        if (RFC.getFunction() == "hacktendoTarget") {
                                            val targetX = (RFC.getParameters() as Array<Any?>?)!![0] as Int
                                            val targetY = (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                            val ip = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                            val currentX = (RFC.getParameters() as Array<Any?>?)!![3] as Int
                                            val currentY = (RFC.getParameters() as Array<Any?>?)!![4] as Int

                                            val O = arrayOf<Any>(targetX, targetY, currentX, currentY)
                                            MyComputerHandler!!.addData(
                                                ApplicationData(
                                                    "hacktendoTarget",
                                                    O,
                                                    0,
                                                    ip
                                                ), ip, ApplicationData.INSIDE
                                            )
                                        } else  //Request a listing of equipment from a player.
                                            if (RFC.getFunction() == "requestequipment") {
                                                var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                ip = crypt(ip, clientKey)
                                                MyComputerHandler!!.addData(
                                                    ApplicationData(
                                                        "requestequipment",
                                                        RFC.getID(),
                                                        0,
                                                        ip
                                                    ), ip, ApplicationData.OUTSIDE
                                                )
                                            } else  //Install equipment for a player.
                                                if (RFC.getFunction() == "installequipment") {
                                                    var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                    val position =
                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                    val name = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                    ip = crypt(ip, clientKey)
                                                    val O: Array<Any?>? = arrayOf<Any?>(position, name, RFC.getID())
                                                    MyComputerHandler!!.addData(
                                                        ApplicationData(
                                                            "installequipment",
                                                            O,
                                                            0,
                                                            ip
                                                        ), ip, ApplicationData.OUTSIDE
                                                    )
                                                } else  //Repair equipment that's currently installed.
                                                    if (RFC.getFunction() == "repairequipment") {
                                                        var ip =
                                                            (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                        val position =
                                                            (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                        val name =
                                                            (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                        ip = crypt(ip, clientKey)
                                                        val O: Array<Any?>? =
                                                            arrayOf<Any?>(position, name, RFC.getID())
                                                        MyComputerHandler!!.addData(
                                                            ApplicationData(
                                                                "repairequipment",
                                                                O,
                                                                0,
                                                                ip
                                                            ), ip, ApplicationData.OUTSIDE
                                                        )
                                                    } else  //Fetch the watches and return them to the client.
                                                        if (RFC.getFunction() == "fetchwatches") {
                                                            var ip =
                                                                (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                            ip = crypt(ip, clientKey)
                                                            MyComputerHandler!!.addData(
                                                                ApplicationData(
                                                                    "fetchwatches",
                                                                    null,
                                                                    0,
                                                                    ip
                                                                ), ip, ApplicationData.OUTSIDE
                                                            )
                                                        } else  //Request your own webpage.
                                                            if (RFC.getFunction() == "requestpage") {
                                                                var ip =
                                                                    (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                ip = crypt(ip, clientKey)
                                                                MyComputerHandler!!.addData(
                                                                    ApplicationData(
                                                                        "requestpage",
                                                                        null,
                                                                        0,
                                                                        ip
                                                                    ), ip, ApplicationData.OUTSIDE
                                                                )
                                                            } else  //Used whn a player wishes to peform a purchase with another player.
                                                                if (RFC.getFunction() == "requestpurchase") {
                                                                    var target_ip =
                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                    var source_ip =
                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String
                                                                    source_ip = crypt(source_ip, clientKey)

                                                                    val file_name =
                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                    val quantity =
                                                                        (RFC.getParameters() as Array<Any?>?)!![3] as Int?
                                                                    val O: Array<Any?>? =
                                                                        arrayOf<Any?>(file_name, quantity)

                                                                    if (target_ip.length >= 5) if (target_ip.substring(
                                                                            0,
                                                                            5
                                                                        ).lowercase(Locale.getDefault()) == "store"
                                                                    ) target_ip = "store" + serverID

                                                                    MyComputerHandler!!.addData(
                                                                        ApplicationData(
                                                                            "requestpurchase",
                                                                            O,
                                                                            0,
                                                                            source_ip
                                                                        ), target_ip, ApplicationData.OUTSIDE
                                                                    )
                                                                } else  //POST INFORMATION FROM A GAME.
                                                                    if (RFC.getFunction() == "requesttrigger") {
                                                                        val watchNote =
                                                                            (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                                                        val TriggerParam =
                                                                            (RFC.getParameters() as Array<Any?>?)!![1] as HashMap<*, *>?
                                                                        val sourceIP =
                                                                            (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                        val targetIP =
                                                                            (RFC.getParameters() as Array<Any?>?)!![3] as String?
                                                                        val O: Any = arrayOf<Any?>(
                                                                            watchNote,
                                                                            TriggerParam,
                                                                            sourceIP
                                                                        )
                                                                        MyComputerHandler!!.addData(
                                                                            ApplicationData(
                                                                                "requesttriggernote",
                                                                                O,
                                                                                0,
                                                                                sourceIP
                                                                            ), targetIP, ApplicationData.OUTSIDE
                                                                        )
                                                                    } else  //SAVE INFORMATION FROM A GAME.
                                                                        if (RFC.getFunction() == "requestsave") {
                                                                            val fileName =
                                                                                (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                                                            val TriggerParam =
                                                                                (RFC.getParameters() as Array<Any?>?)!![1] as HashMap<*, *>?
                                                                            val targetIP =
                                                                                (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                            val O: Any = arrayOf<Any?>(
                                                                                fileName,
                                                                                TriggerParam
                                                                            )

                                                                            MyComputerHandler!!.addData(
                                                                                ApplicationData(
                                                                                    "requestsave",
                                                                                    O,
                                                                                    0,
                                                                                    targetIP
                                                                                ),
                                                                                targetIP,
                                                                                ApplicationData.OUTSIDE
                                                                            )
                                                                        } else  //LET A GAME FINISH A TASK IN A QUEST.
                                                                            if (RFC.getFunction() == "requesttask") {
                                                                                val fileName =
                                                                                    (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                                                                val questID =
                                                                                    (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                                                                val taskName =
                                                                                    (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                                val targetIP =
                                                                                    (RFC.getParameters() as Array<Any?>?)!![3] as String?
                                                                                val O: Any = arrayOf<Any?>(
                                                                                    fileName,
                                                                                    questID,
                                                                                    taskName
                                                                                )
                                                                                MyComputerHandler!!.addData(
                                                                                    ApplicationData(
                                                                                        "requesttask",
                                                                                        O,
                                                                                        0,
                                                                                        targetIP
                                                                                    ),
                                                                                    targetIP,
                                                                                    ApplicationData.OUTSIDE
                                                                                )
                                                                            } else  //Request another player's webpage.
                                                                                if (RFC.getFunction() == "requestwebpage") {
                                                                                    var target_ip =
                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                    var source_ip =
                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String

                                                                                    if (!(source_ip == "062.153.7.142"))  //This is the IP used to hook-in and make requests externally.
                                                                                        source_ip = crypt(
                                                                                            source_ip,
                                                                                            clientKey
                                                                                        )

                                                                                    val parameters =
                                                                                        (RFC.parameters as? Array<*>?)
                                                                                            ?.getOrNull(2)
                                                                                            ?.let {
                                                                                                @Suppress("UNCHECKED_CAST")
                                                                                                it as? HashMap<Any?, Any?>
                                                                                            }
                                                                                            ?: HashMap<Any?, Any?>()

                                                                                    parameters["packetid"] = RFC.id

                                                                                    if (target_ip.length >= 5) if (target_ip.substring(
                                                                                            0,
                                                                                            5
                                                                                        )
                                                                                            .lowercase(Locale.getDefault()) == "store"
                                                                                    ) target_ip = "store" + serverID

                                                                                    MyComputerHandler!!.addData(
                                                                                        ApplicationData(
                                                                                            "requestwebpage",
                                                                                            parameters,
                                                                                            0,
                                                                                            source_ip
                                                                                        ),
                                                                                        target_ip,
                                                                                        ApplicationData.OUTSIDE
                                                                                    )
                                                                                } else  //Send a form submission to another player.
                                                                                    if (RFC.getFunction() == "submit") {
                                                                                        val target_ip =
                                                                                            (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                                                                        var source_ip =
                                                                                            (RFC.getParameters() as Array<Any?>?)!![1] as String

                                                                                        val parameters =
                                                                                            (RFC.parameters as? Array<*>?)
                                                                                                ?.getOrNull(2)
                                                                                                ?.let {
                                                                                                    @Suppress("UNCHECKED_CAST")
                                                                                                    it as? HashMap<Any?, Any?>
                                                                                                }
                                                                                                ?: HashMap<Any?, Any?>()

                                                                                        parameters["packetid"] =
                                                                                            RFC.id

                                                                                        if (!(source_ip == "062.153.7.142"))  //This is the IP used to hook-in and make requests externally.
                                                                                            source_ip = crypt(
                                                                                                source_ip,
                                                                                                clientKey
                                                                                            )

                                                                                        MyComputerHandler!!.addData(
                                                                                            ApplicationData(
                                                                                                "submit",
                                                                                                parameters,
                                                                                                0,
                                                                                                source_ip
                                                                                            ),
                                                                                            target_ip,
                                                                                            ApplicationData.OUTSIDE
                                                                                        )
                                                                                    } else  //Create a bounty.
                                                                                        if (RFC.getFunction() == "makebounty") {
                                                                                            var source_ip =
                                                                                                (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                            source_ip = crypt(
                                                                                                source_ip,
                                                                                                clientKey
                                                                                            )
                                                                                            val anonymous =
                                                                                                (RFC.getParameters() as Array<Any?>?)!![1] as Boolean?
                                                                                            val target =
                                                                                                (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                                            val type =
                                                                                                (RFC.getParameters() as Array<Any?>?)!![3] as Int?
                                                                                            val fname =
                                                                                                (RFC.getParameters() as Array<Any?>?)!![4] as String?
                                                                                            val folder =
                                                                                                (RFC.getParameters() as Array<Any?>?)!![5] as String?
                                                                                            val iterations =
                                                                                                (RFC.getParameters() as Array<Any?>?)!![6] as Int?
                                                                                            val reward =
                                                                                                (RFC.getParameters() as Array<Any?>?)!![7] as Float?
                                                                                            val O: Array<Any?>? =
                                                                                                arrayOf<Any?>(
                                                                                                    anonymous,
                                                                                                    target,
                                                                                                    type,
                                                                                                    fname,
                                                                                                    folder,
                                                                                                    iterations,
                                                                                                    reward
                                                                                                )
                                                                                            MyComputerHandler!!.addData(
                                                                                                ApplicationData(
                                                                                                    "makebounty",
                                                                                                    O,
                                                                                                    0,
                                                                                                    source_ip
                                                                                                ),
                                                                                                source_ip,
                                                                                                ApplicationData.OUTSIDE
                                                                                            )
                                                                                        } else  //Exit a player's webpage.
                                                                                            if (RFC.getFunction() == "exit") {
                                                                                                val target_ip =
                                                                                                    (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                                                                                var source_ip =
                                                                                                    (RFC.getParameters() as Array<Any?>?)!![1] as String

                                                                                                source_ip = crypt(
                                                                                                    source_ip,
                                                                                                    clientKey
                                                                                                )

                                                                                                MyComputerHandler!!.addData(
                                                                                                    ApplicationData(
                                                                                                        "exit",
                                                                                                        null,
                                                                                                        0,
                                                                                                        source_ip
                                                                                                    ),
                                                                                                    target_ip,
                                                                                                    ApplicationData.OUTSIDE
                                                                                                )
                                                                                            } else  //Vote for a player's webpage.
                                                                                                if (RFC.getFunction() == "vote") {
                                                                                                    val target_ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                                                                                    var source_ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String

                                                                                                    source_ip =
                                                                                                        crypt(
                                                                                                            source_ip,
                                                                                                            clientKey
                                                                                                        )

                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "vote",
                                                                                                            null,
                                                                                                            0,
                                                                                                            target_ip
                                                                                                        ),
                                                                                                        source_ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "savepage") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val title =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                                                                                    val body =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                                                    val O: Array<Any?>? =
                                                                                                        arrayOf<Any?>(
                                                                                                            title,
                                                                                                            body
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "savepage",
                                                                                                            O,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "withdraw") {
                                                                                                    val amount =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as Float
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as Int
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "withdraw",
                                                                                                            amount,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "requestdirectory") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val path =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                                                                                    val O: Array<Any?>? =
                                                                                                        arrayOf<Any?>(
                                                                                                            path,
                                                                                                            RFC.getID()
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "requestdirectory",
                                                                                                            O,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "unlock") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val code =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "unlock",
                                                                                                            code,
                                                                                                            0,
                                                                                                            ""
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "setftppassword") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val password =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "setftppassword",
                                                                                                            password,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "requestsecondarydirectory") {
                                                                                                    val ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                                                                                    val path =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                                                                                    var targetIP =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as String

                                                                                                    targetIP =
                                                                                                        crypt(
                                                                                                            targetIP,
                                                                                                            clientKey
                                                                                                        )

                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![3] as Int
                                                                                                    val Parameter: Array<Any?>? =
                                                                                                        arrayOf<Any?>(
                                                                                                            targetIP,
                                                                                                            path,
                                                                                                            RFC.getID()
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "requestsecondarydirectory",
                                                                                                            Parameter,
                                                                                                            port,
                                                                                                            targetIP
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "requestcancelattack") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "requestcancelattack",
                                                                                                            null,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "cluedata") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val data =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "cluedata",
                                                                                                            data,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "requestzombiecancelattack") {
                                                                                                    val ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                                                                                    var targetIP =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as String
                                                                                                    targetIP =
                                                                                                        crypt(
                                                                                                            targetIP,
                                                                                                            clientKey
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "requestcancelattack",
                                                                                                            null,
                                                                                                            port,
                                                                                                            targetIP
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "installapplication") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                                                                                    val path =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                                                    val name =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![3] as String?
                                                                                                    val Parameter: Array<String?>? =
                                                                                                        arrayOf<String?>(
                                                                                                            path,
                                                                                                            name
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "installapplication",
                                                                                                            Parameter,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "installwatch") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val path =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                                                                                    val name =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                                                    val type =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![3] as Int
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![4] as Int
                                                                                                    val Parameter: Array<Any?>? =
                                                                                                        arrayOf<Any?>(
                                                                                                            path,
                                                                                                            name,
                                                                                                            type
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "installwatch",
                                                                                                            Parameter,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "setwatchobservedports") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val watchID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                                                                    val ObservedPorts =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as Array<Int?>?
                                                                                                    val Parameter: Array<Any?>? =
                                                                                                        arrayOf<Any?>(
                                                                                                            watchID,
                                                                                                            ObservedPorts
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "setwatchobservedports",
                                                                                                            Parameter,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "installfirewall") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                                                                                    val path =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                                                    val name =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![3] as String?
                                                                                                    val Parameter: Array<String?>? =
                                                                                                        arrayOf<String?>(
                                                                                                            path,
                                                                                                            name
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "installfirewall",
                                                                                                            Parameter,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "replaceapplication") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                                                                                    val path =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                                                    val name =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![3] as String?
                                                                                                    val Parameter: Array<String?>? =
                                                                                                        arrayOf<String?>(
                                                                                                            path,
                                                                                                            name
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "replaceapplication",
                                                                                                            Parameter,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "uninstallport") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "uninstallport",
                                                                                                            port,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "portonoff") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                                                                                    val on =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as Boolean?
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "portonoff",
                                                                                                            on,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "peekcode") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val targetIP =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as Int
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "peekcode",
                                                                                                            null,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        targetIP,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "peeklogs") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val targetIP =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as Int
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "peeklogs",
                                                                                                            null,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        targetIP,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "saveportnote") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val port =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int
                                                                                                    val note =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "saveportnote",
                                                                                                            note,
                                                                                                            port,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "setwatchquantity") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val watchID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                                                                    val quantity =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as Float?
                                                                                                    val O: Any =
                                                                                                        arrayOf<Any?>(
                                                                                                            watchID,
                                                                                                            quantity
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "setwatchquantity",
                                                                                                            O,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "setwatchonoff") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val watchID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                                                                    val state =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as Boolean?
                                                                                                    val O: Any =
                                                                                                        arrayOf<Any?>(
                                                                                                            watchID,
                                                                                                            state
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "setwatchonoff",
                                                                                                            O,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "setwatchnote") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val watchID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                                                                    val note =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                                                                                    val O: Any =
                                                                                                        arrayOf<Any?>(
                                                                                                            watchID,
                                                                                                            note
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "setwatchnote",
                                                                                                            O,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "setwatchsearchfirewall") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val watchID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                                                                    val searchFireWall =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as Int?
                                                                                                    val O: Any =
                                                                                                        arrayOf<Any?>(
                                                                                                            watchID,
                                                                                                            searchFireWall
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "setwatchsearchfirewall",
                                                                                                            O,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "deletewatch") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val watchID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                                                                    val O: Any =
                                                                                                        arrayOf<Any?>(
                                                                                                            watchID
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "deletewatch",
                                                                                                            O,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "deletefirewall") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val portID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "deletefirewall",
                                                                                                            portID,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "changewatchport") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val WatchID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                                                                    val PortID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as Int?
                                                                                                    val I: Array<Int?>? =
                                                                                                        arrayOf<Int?>(
                                                                                                            WatchID,
                                                                                                            PortID
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "changewatchport",
                                                                                                            I,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                } else if (RFC.getFunction() == "changewatchtype") {
                                                                                                    var ip =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![0] as String
                                                                                                    ip = crypt(
                                                                                                        ip,
                                                                                                        clientKey
                                                                                                    )
                                                                                                    val WatchID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![1] as Int?
                                                                                                    val PortID =
                                                                                                        (RFC.getParameters() as Array<Any?>?)!![2] as Int?
                                                                                                    val I: Array<Int?>? =
                                                                                                        arrayOf<Int?>(
                                                                                                            WatchID,
                                                                                                            PortID
                                                                                                        )
                                                                                                    MyComputerHandler!!.addData(
                                                                                                        ApplicationData(
                                                                                                            "changewatchtype",
                                                                                                            I,
                                                                                                            0,
                                                                                                            ip
                                                                                                        ),
                                                                                                        ip,
                                                                                                        ApplicationData.OUTSIDE
                                                                                                    )
                                                                                                }

                    if (RFC.getFunction() == "deletefolder") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val directory = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        MyComputerHandler!!.addData(
                            ApplicationData("deletefolder", directory, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "setdummyport") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val port = (RFC.getParameters() as Array<Any?>?)!![1] as Int
                        val dummy = (RFC.getParameters() as Array<Any?>?)!![2] as Boolean?
                        MyComputerHandler!!.addData(
                            ApplicationData("setdummyport", dummy, port, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "changedailypay") {
                        val parameters = RFC.getParameters() as Array<Any?>
                        val ip = parameters[0] as String?
                        val port = parameters[1] as Int
                        val change = parameters[2] as String?
                        var finalizeIP = parameters[3] as String
                        val attackPort = parameters[4] as Int
                        finalizeIP = crypt(finalizeIP, clientKey)

                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "changedailypay",
                                arrayOf<Any?>(change, attackPort),
                                port,
                                finalizeIP
                            ), ip, ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "deletelogs") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        MyComputerHandler!!.addData(
                            ApplicationData("deletelogs", null, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "createfolder") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val directory = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        MyComputerHandler!!.addData(
                            ApplicationData("createfolder", directory, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "put") {
                        val ip = (RFC.getParameters() as Array<Any?>?)!![0] as String?
                        val port = (RFC.getParameters() as Array<Any?>?)!![1] as Int
                        val name = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                        val fetch_path = (RFC.getParameters() as Array<Any?>?)!![3] as String?
                        val put_path = (RFC.getParameters() as Array<Any?>?)!![4] as String?
                        var targetIP = (RFC.getParameters() as Array<Any?>?)!![5] as String
                        targetIP = crypt(targetIP, clientKey)
                        val password = (RFC.getParameters() as Array<Any?>?)!![6] as String?
                        val quantity = (RFC.getParameters() as Array<Any?>?)!![7] as Int?
                        val Parameter: Array<Any?>? =
                            arrayOf<Any?>(ip, name, fetch_path, put_path, password, quantity)
                        MyComputerHandler!!.addData(
                            ApplicationData("put", Parameter, port, targetIP),
                            targetIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "get") {
                        val ip = (RFC.getParameters() as Array<Any?>?)!![0] as String?
                        val port = (RFC.getParameters() as Array<Any?>?)!![1] as Int
                        val name = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                        val fetch_path = (RFC.getParameters() as Array<Any?>?)!![3] as String?
                        val put_path = (RFC.getParameters() as Array<Any?>?)!![4] as String?
                        var targetIP = (RFC.getParameters() as Array<Any?>?)!![5] as String
                        targetIP = crypt(targetIP, clientKey)
                        val password = (RFC.getParameters() as Array<Any?>?)!![6] as String?
                        val quantity = (RFC.getParameters() as Array<Any?>?)!![7] as Int?
                        val Parameter: Array<Any?>? =
                            arrayOf<Any?>(targetIP, name, fetch_path, put_path, password, quantity)
                        MyComputerHandler!!.addData(
                            ApplicationData("get", Parameter, port, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "malget") {
                        val parameters = RFC.getParameters() as Array<Any?>
                        val ip = parameters[0] as String?
                        val port = parameters[1] as Int
                        val name = parameters[2] as String?
                        val fetch_path = parameters[3] as String?
                        val put_path = parameters[4] as String?
                        var targetIP = parameters[5] as String
                        val attackPort = parameters[6] as Int
                        targetIP = crypt(targetIP, clientKey)
                        val Parameter: Array<Any?>? =
                            arrayOf<Any?>(targetIP, name, fetch_path, put_path, "", port, attackPort)
                        MyComputerHandler!!.addData(
                            ApplicationData("malget", Parameter, port, targetIP),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "requestfile") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val path = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        val name = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                        val Parameter: Array<String?>? = arrayOf<String?>(path, name)
                        MyComputerHandler!!.addData(
                            ApplicationData("requestfile", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "requestgame") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val path = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        val name = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                        val Parameter: Array<String?>? = arrayOf<String?>(path, name)
                        MyComputerHandler!!.addData(
                            ApplicationData("requestgame", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "requestscan") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val targetIP = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        if (ip != targetIP) MyComputerHandler!!.addData(
                            ApplicationData("requestscan", ip, 0, ip),
                            targetIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "savefile") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val path = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        val name = (RFC.getParameters() as Array<Any?>?)!![2] as HackerFile?
                        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name)
                        MyComputerHandler!!.addData(
                            ApplicationData("savefile", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "compilefile") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val path = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        val name = (RFC.getParameters() as Array<Any?>?)!![2] as HackerFile?
                        val price = (RFC.getParameters() as Array<Any?>?)!![3] as Float?
                        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name, price)
                        MyComputerHandler!!.addData(
                            ApplicationData("compilefile", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "deletemulti") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)

                        val allFiles = (RFC.getParameters() as Array<Any?>?)!![1] as Array<Any?>?
                        val parameters: Array<Any?>? = arrayOf<Any?>(allFiles)
                        MyComputerHandler!!.addData(
                            ApplicationData("deletemulti", parameters, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "deletefile") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val path = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        val name = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name)
                        MyComputerHandler!!.addData(
                            ApplicationData("deletefile", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "setfiledescription") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val path = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        val name = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                        val description = (RFC.getParameters() as Array<Any?>?)!![3] as String?
                        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name, description)
                        MyComputerHandler!!.addData(
                            ApplicationData("setfiledescription", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "setfileprice") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val path = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        val name = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                        val price = (RFC.getParameters() as Array<Any?>?)!![3] as Float?
                        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name, price)
                        MyComputerHandler!!.addData(
                            ApplicationData("setfileprice", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "emptypettycash") {
                        val parameters = RFC.getParameters() as Array<Any?>
                        var ip = parameters[0] as String
                        ip = crypt(ip, clientKey)
                        val targetIP = parameters[1] as String?
                        val targetPort = parameters[2] as Int
                        val windowHandle = parameters[3] as Int
                        MyComputerHandler!!.addData(
                            ApplicationData("emptyPettyCash", windowHandle, targetPort, ip),
                            targetIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "finalizecancelled") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val targetIP = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        val targetPort = (RFC.getParameters() as Array<Any?>?)!![2] as Int
                        MyComputerHandler!!.addData(
                            ApplicationData("finalizecancelled", null, targetPort, ip),
                            targetIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "requestattack") {
                        val parameters = RFC.getParameters() as Array<Any?>
                        val targetIP = parameters[0] as String
                        val targetPort = parameters[1] as Int
                        var sourceIP = parameters[2] as String
                        sourceIP = crypt(sourceIP, clientKey)

                        val sourcePort = parameters[3] as Int

                        val secondaryPorts = parameters[4] as Array<Int?>?
                        val scripts = parameters[5] as Array<Array<String?>?>?
                        val extraInfo = parameters[6] as Array<Any?>?
                        val windowHandle = parameters[7] as Int?

                        val Parameters: Array<Any?>? =
                            arrayOf<Any?>(targetIP, targetPort, secondaryPorts, scripts, extraInfo, windowHandle)
                        val AD = ApplicationData("requestattack", Parameters, sourcePort, sourceIP)

                        if (targetIP != sourceIP && targetIP.indexOf("store") == -1) MyComputerHandler!!.addData(
                            AD,
                            sourceIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "requestzombieattack") {
                        val targetIP = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        val targetPort = (RFC.getParameters() as Array<Any?>?)!![1] as Int
                        val sourceIP = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                        val sourcePort = (RFC.getParameters() as Array<Any?>?)!![3] as Int

                        val I = (RFC.getParameters() as Array<Any?>?)!![4] as Array<Int?>?
                        val S = (RFC.getParameters() as Array<Any?>?)!![5] as Array<Array<String?>?>?
                        val O = (RFC.getParameters() as Array<Any?>?)!![6] as Array<Any?>?
                        var parentIP = (RFC.getParameters() as Array<Any?>?)!![7] as String
                        parentIP = crypt(parentIP, clientKey)

                        val Parameters: Array<Any?>? = arrayOf<Any?>(targetIP, targetPort, I, S, O, sourceIP)
                        val AD = ApplicationData("requestzombieattack", Parameters, sourcePort, parentIP)

                        if (targetIP != sourceIP && targetIP.indexOf("store") == -1) MyComputerHandler!!.addData(
                            AD,
                            parentIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "transfer") {
                        val amount = (RFC.getParameters() as Array<Any?>?)!![0] as Float
                        var ip = (RFC.getParameters() as Array<Any?>?)!![1] as String
                        ip = crypt(ip, clientKey)
                        val target_ip = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                        val port = (RFC.getParameters() as Array<Any?>?)!![3] as Int
                        val tO: Array<Any?>? = arrayOf<Any?>(target_ip, amount)
                        MyComputerHandler!!.addData(
                            ApplicationData("transfer", tO, port, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "deposit") {
                        val amount = (RFC.getParameters() as Array<Any?>?)!![0] as Float
                        var ip = (RFC.getParameters() as Array<Any?>?)!![1] as String
                        ip = crypt(ip, clientKey)
                        val port = (RFC.getParameters() as Array<Any?>?)!![2] as Int
                        MyComputerHandler!!.addData(
                            ApplicationData("deposit", amount, port, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "dochallenge") {
                        var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                        ip = crypt(ip, clientKey)
                        val code = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                        val challengeID = (RFC.getParameters() as Array<Any?>?)!![2] as String?

                        val O: Array<Any?>? = arrayOf<Any?>(code, challengeID)
                        MyComputerHandler!!.addData(
                            ApplicationData("dochallenge", O, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else  //Allows a player to sell a file back to the game store.
                        if (RFC.getFunction() == "sellfile") {
                            var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                            ip = crypt(ip, clientKey)
                            val location = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                            val fileName = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                            val compileCost = (RFC.getParameters() as Array<Any?>?)!![3] as Float?
                            val quantity = (RFC.getParameters() as Array<Any?>?)!![4] as Int?
                            val O: Array<Any?>? = arrayOf<Any?>(location, fileName, compileCost, ip, quantity)
                            MyComputerHandler!!.addData(
                                ApplicationData(
                                    "requestsellfile",
                                    O,
                                    0,
                                    "store" + serverID
                                ), ip, ApplicationData.OUTSIDE
                            )
                        } else  // Sell multiple files at once from the client
                            if (RFC.getFunction() == "sellfilemulti") {
                                var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                                ip = crypt(ip, clientKey)
                                val allFiles = (RFC.getParameters() as Array<Any?>?)!![1] as Array<Any?>?
                                val O: Array<Any?>? = arrayOf<Any?>(allFiles, ip)
                                MyComputerHandler!!.addData(
                                    ApplicationData(
                                        "sellfilemulti",
                                        O,
                                        0,
                                        "store" + serverID
                                    ), ip, ApplicationData.OUTSIDE
                                )
                            } else  //Allows a player to sell a file back to the game store.
                                if (RFC.getFunction() == "decompilefile") {
                                    var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                                    ip = crypt(ip, clientKey)
                                    val location = (RFC.getParameters() as Array<Any?>?)!![1] as String?
                                    val fileName = (RFC.getParameters() as Array<Any?>?)!![2] as String?
                                    val compileCost = (RFC.getParameters() as Array<Any?>?)!![3] as Float?
                                    val O: Array<Any?>? = arrayOf<Any?>(location, fileName, compileCost, ip)
                                    MyComputerHandler!!.addData(
                                        ApplicationData("decompilefile", O, 0, ip),
                                        ip,
                                        ApplicationData.OUTSIDE
                                    )
                                } else  //A deposit requested from facebook.
                                    if (RFC.getFunction() == "facebookdeposit") {
                                        val ip = (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                        val amount = (RFC.getParameters() as Array<Any?>?)!![1] as Float?
                                        val defaultPort = (RFC.getParameters() as Array<Any?>?)!![2] as Int

                                        MyComputerHandler!!.addData(
                                            ApplicationData(
                                                "deposit",
                                                amount,
                                                defaultPort,
                                                ip
                                            ), ip, ApplicationData.OUTSIDE
                                        )
                                    } else  //A withdraw requested from facebook.
                                        if (RFC.getFunction() == "facebookwithdraw") {
                                            val ip = (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                            val amount = (RFC.getParameters() as Array<Any?>?)!![1] as Float?
                                            val defaultPort = (RFC.getParameters() as Array<Any?>?)!![2] as Int

                                            MyComputerHandler!!.addData(
                                                ApplicationData(
                                                    "withdraw",
                                                    amount,
                                                    defaultPort,
                                                    ip
                                                ), ip, ApplicationData.OUTSIDE
                                            )
                                        } else if (RFC.getFunction() == "facebooktransfer") {
                                            val ip = (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                            val ip2 = (RFC.getParameters() as Array<Any?>?)!![1] as String?

                                            val amount = (RFC.getParameters() as Array<Any?>?)!![2] as Float
                                            val defaultPort = (RFC.getParameters() as Array<Any?>?)!![3] as Int

                                            val tO: Array<Any?>? = arrayOf<Any?>(ip2, amount)
                                            MyComputerHandler!!.addData(
                                                ApplicationData(
                                                    "transfer",
                                                    tO,
                                                    defaultPort,
                                                    ip
                                                ), ip, ApplicationData.OUTSIDE
                                            )
                                        } else if (RFC.getFunction() == "facebookupdate") {
                                            val ip = (RFC.getParameters() as Array<Any?>?)!![0] as String?
                                            MyComputerHandler!!.addData(
                                                ApplicationData(
                                                    "facebookupdate",
                                                    null,
                                                    0,
                                                    ip
                                                ), ip, ApplicationData.OUTSIDE
                                            )
                                        } else if (RFC.getFunction() == "setpreferences") {
                                            var ip = (RFC.getParameters() as Array<Any?>?)!![0] as String
                                            ip = crypt(ip, clientKey)
                                            val preferences =
                                                (RFC.getParameters() as Array<Any?>?)!![1] as HashMap<*, *>?
                                            val O = arrayOf<Any?>(ip, preferences)
                                            MyComputerHandler!!.addData(
                                                ApplicationData("setpreferences", O, 0, ip),
                                                ip,
                                                ApplicationData.OUTSIDE
                                            )
                                        }
                } else if (MyAssignment is Array<*>) {
                    val AssignmentData = MyAssignment as Array<Any?>
                    if (AssignmentData.size >= 2 && AssignmentData[0] is Assignment && AssignmentData[1] is Int) {
                        val DispatchMe = AssignmentData[0] as Assignment
                        val connectionID = AssignmentData[1] as Int
                        val MyClientBinaryList = this.getEditor().getClients()
                        val MyClientData = MyClientBinaryList.get(connectionID) as ClientData?
                        if (MyClientData != null) {
                            MyClientData.addJob(ZippedAssignment(0, DispatchMe))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init { //Used for constructor just keep this here in IPartys.
        this.serverID = serverID
        MyTime = Time()
        ServerRuntimeState.setClock(MyTime)
        ServerRuntimeState.setRunning(on)
        ServerRuntimeState.setShutdownAt(SHUTDOWN_AT)
        MyComputerHandler = ComputerHandler(MyTime, this)
        serverJob = serverScope.launch(CoroutineName("HackerServer")) { processTasks() }
    }

    /**
     * De/encrypts binary data in a given byte array. Calling the method again
     * reverses the encryption.
     */
    fun crypt(ip: String, clientHash: String?): String {
        return Keys[ip + clientHash] as? String? ?: return ip
    }


    /**
     * Remove a key from our encryption system based on an IP provided.
     */
    @Synchronized
    override fun removeRandomKey(ip: String?) {
        val hash = IPs[ip] as String?
        if (ip != null) Keys.remove(hash)
        IPs.remove(ip)
        MyEncryption.remove(ip)
    }


    /**
     * Generates a random key for use with our encryption algorithm.
     */
    @Synchronized
    override fun getRandomKey(ip: String?, clientHash: String?, publicKey: ByteArray?): Array<Any?> {
        var key = ""
        for (i in 0..9) {
            key += ('a'.code + (Math.random() * 26).toInt()).toChar()
        }

        Keys[key + clientHash] = ip
        IPs[ip] = key + clientHash
        val myPublicKey: ByteArray? = publicKey?.let { MyEncryption.init(publicKey, clientHash, ip) }

        return arrayOf(key, myPublicKey)
    }
}

fun main(args: Array<String>) {
    try {
        val editor = Editor(2048, 1000, 10020, 10021) //Creates a new server for distributing tasks.
        editor.setClientJobSize(4)
        HackerServer(editor, args[0])
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
