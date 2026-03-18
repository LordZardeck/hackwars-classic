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
import rpc.*
import rpc.MakeBounty
import server.remote.RemoteCallContext
import server.remote.invokeOnServer
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

                    val remoteCallContext = RemoteCallContext(MyComputerHandler!!) { ip ->
                        crypt(ip, clientKey)
                    }
                    if (RFC.invokeOnServer(remoteCallContext)) {
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
                    }
                    //Set the default port that an application will execute on.
                    else if (RFC.getFunction() == "setdefaultport") {
                        val setDefaultPortCall = SetDefaultPort.fromRpc(RFC)
                        val ip = crypt(setDefaultPortCall.encryptedIp, clientKey)
                        MyComputerHandler!!.addData(
                            ApplicationData("setdefaultport", setDefaultPortCall.type, setDefaultPortCall.port, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    }
                    //RETURN TO THE ROOT NETWORK.
                    else if (RFC.getFunction() == "changenetwork") {
                        val changeNetworkCall = ChangeNetwork.fromRpc(RFC)
                        val ip = crypt(changeNetworkCall.encryptedIp, clientKey)
                        MyComputerHandler!!.addData(
                            ApplicationData("changenetwork", changeNetworkCall.network, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    }
                    //Heal a specific port.
                    else if (RFC.getFunction() == "healport") {
                        val healPortCall = HealPort.fromRpc(RFC)
                        val ip = crypt(healPortCall.encryptedIp, clientKey)
                        MyComputerHandler!!.addData(
                            ApplicationData("heal", null, healPortCall.port, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    }
                    //Allow hacktendo to activate a sprite.
                    else if (RFC.getFunction() == "hacktendoActivate") {
                        val parsedCall = HacktendoActivate.fromRpc(RFC)
                        val activateID = parsedCall.activateID
                        val activateType = parsedCall.activateType
                        val ip = parsedCall.ip

                        val O = arrayOf<Any>(activateID, activateType)
                        MyComputerHandler!!.addData(
                            ApplicationData("hacktendoActivate", O, 0, ip),
                            ip,
                            ApplicationData.INSIDE
                        )
                    }
                    //Allow Hacktendo to move objects through space.
                    else if (RFC.getFunction() == "hacktendoTarget") {
                        val parsedCall = HacktendoTarget.fromRpc(RFC)
                        val targetX = parsedCall.targetX
                        val targetY = parsedCall.targetY
                        val ip = parsedCall.ip
                        val currentX = parsedCall.currentX
                        val currentY = parsedCall.currentY

                        val O = arrayOf<Any>(targetX, targetY, currentX, currentY)
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "hacktendoTarget",
                                O,
                                0,
                                ip
                            ), ip, ApplicationData.INSIDE
                        )
                    }
                    //Request a listing of equipment from a player.
                    else if (RFC.getFunction() == "requestequipment") {
                        val parsedCall = RequestEquipment.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "requestequipment",
                                RFC.getID(),
                                0,
                                ip
                            ), ip, ApplicationData.OUTSIDE
                        )
                    }
                    //Install equipment for a player.
                    else if (RFC.getFunction() == "installequipment") {
                        val parsedCall = InstallEquipment.fromRpc(RFC)
                        var ip = parsedCall.ip
                        val position =
                            parsedCall.position
                        val name = parsedCall.name
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
                    }
                    //Repair equipment that's currently installed.
                    else if (RFC.getFunction() == "repairequipment") {
                        val parsedCall = RepairEquipment.fromRpc(RFC)
                        var ip = parsedCall.ip
                        val position =
                            parsedCall.position
                        val name =
                            parsedCall.name
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
                    }
                    //Fetch the watches and return them to the client.
                    else if (RFC.getFunction() == "fetchwatches") {
                        val parsedCall = FetchWatches.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "fetchwatches",
                                null,
                                0,
                                ip
                            ), ip, ApplicationData.OUTSIDE
                        )
                    }
                    //Request your own webpage.
                    else if (RFC.getFunction() == "requestpage") {
                        val parsedCall = RequestPage.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "requestpage",
                                null,
                                0,
                                ip
                            ), ip, ApplicationData.OUTSIDE
                        )
                    }
                    //Used whn a player wishes to peform a purchase with another player.
                    else if (RFC.getFunction() == "requestpurchase") {
                        val parsedCall = RequestPurchase.fromRpc(RFC)
                        var target_ip = parsedCall.targetIp
                        var source_ip =
                            parsedCall.sourceIp
                        source_ip = crypt(source_ip, clientKey)

                        val file_name =
                            parsedCall.fileName
                        val quantity =
                            parsedCall.quantity
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
                    }
                    //POST INFORMATION FROM A GAME.
                    else if (RFC.getFunction() == "requesttrigger") {
                        val parsedCall = RequestTrigger.fromRpc(RFC)
                        val watchNote = parsedCall.watchNote
                        val TriggerParam =
                            parsedCall.triggerParam
                        val sourceIP =
                            parsedCall.sourceIP
                        val targetIP =
                            parsedCall.targetIP
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
                    }
                    //SAVE INFORMATION FROM A GAME.
                    else if (RFC.getFunction() == "requestsave") {
                        val parsedCall = RequestSave.fromRpc(RFC)
                        val fileName = parsedCall.fileName
                        val TriggerParam =
                            parsedCall.triggerParam
                        val targetIP =
                            parsedCall.targetIP
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
                    }
                    //LET A GAME FINISH A TASK IN A QUEST.
                    else if (RFC.getFunction() == "requesttask") {
                        val parsedCall =
                            RequestTask.fromRpc(RFC)
                        val fileName = parsedCall.fileName
                        val questID =
                            parsedCall.questID
                        val taskName =
                            parsedCall.taskName
                        val targetIP =
                            parsedCall.targetIP
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
                    }
                    //Request another player's webpage.
                    else if (RFC.getFunction() == "requestwebpage") {
                        val requestWebpageCall =
                            RequestWebpage.fromRpc(RFC)
                        var target_ip =
                            requestWebpageCall.targetIp
                        var source_ip =
                            requestWebpageCall.sourceIp

                        if (!(source_ip == "062.153.7.142"))  //This is the IP used to hook-in and make requests externally.
                            source_ip = crypt(
                                source_ip,
                                clientKey
                            )

                        val parameters =
                            HashMap(requestWebpageCall.parameters)
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
                    }
                    //Send a form submission to another player.
                    else if (RFC.getFunction() == "submit") {
                        val submitCall =
                            Submit.fromRpc(RFC)
                        val target_ip =
                            submitCall.targetIp
                        var source_ip =
                            submitCall.sourceIp
                        val parameters =
                            HashMap(submitCall.parameters)

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
                    }
                    //Create a bounty.
                    else if (RFC.getFunction() == "makebounty") {
                        val parsedCall =
                            MakeBounty.fromRpc(RFC)
                        var source_ip =
                            parsedCall.sourceIp
                        source_ip = crypt(
                            source_ip,
                            clientKey
                        )
                        val anonymous =
                            parsedCall.anonymous
                        val target =
                            parsedCall.target
                        val type =
                            parsedCall.type
                        val fname =
                            parsedCall.fname
                        val folder =
                            parsedCall.folder
                        val iterations =
                            parsedCall.iterations
                        val reward =
                            parsedCall.reward
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
                    }
                    //Exit a player's webpage.
                    else if (RFC.getFunction() == "exit") {
                        val parsedCall =
                            Exit.fromRpc(RFC)
                        val target_ip =
                            parsedCall.targetIp
                        var source_ip =
                            parsedCall.sourceIp

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
                    }
                    //Vote for a player's webpage.
                    else if (RFC.getFunction() == "vote") {
                        val parsedCall =
                            Vote.fromRpc(RFC)
                        val target_ip =
                            parsedCall.targetIp
                        var source_ip =
                            parsedCall.sourceIp

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
                        val parsedCall =
                            SavePage.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val title =
                            parsedCall.title
                        val body =
                            parsedCall.body
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
                        val parsedCall =
                            Withdraw.fromRpc(
                                RFC
                            )
                        val amount =
                            parsedCall.amount
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val port =
                            parsedCall.port
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
                        val parsedCall =
                            RequestDirectory.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val path =
                            parsedCall.path
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
                        val parsedCall =
                            Unlock.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val code =
                            parsedCall.code
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
                        val parsedCall =
                            SetFtpPassword.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val password =
                            parsedCall.password
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
                        val parsedCall =
                            RequestSecondaryDirectory.fromRpc(
                                RFC
                            )
                        val ip =
                            parsedCall.ip
                        val path =
                            parsedCall.path
                        var targetIP =
                            parsedCall.targetIP

                        targetIP =
                            crypt(
                                targetIP,
                                clientKey
                            )

                        val port =
                            parsedCall.port
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
                        val parsedCall =
                            RequestCancelAttack.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val port =
                            parsedCall.port
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
                        val parsedCall =
                            ClueData.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val data =
                            parsedCall.data
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
                        val parsedCall =
                            RequestZombieCancelAttack.fromRpc(
                                RFC
                            )
                        val ip =
                            parsedCall.ip
                        val port =
                            parsedCall.port
                        var targetIP =
                            parsedCall.targetIP
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
                        val parsedCall =
                            InstallApplication.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val port =
                            parsedCall.port
                        val path =
                            parsedCall.path
                        val name =
                            parsedCall.name
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
                        val parsedCall =
                            InstallWatch.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val path =
                            parsedCall.path
                        val name =
                            parsedCall.name
                        val type =
                            parsedCall.type
                        val port =
                            parsedCall.port
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
                        val parsedCall =
                            SetWatchObservedPorts.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val watchID =
                            parsedCall.watchID
                        val ObservedPorts =
                            parsedCall.observedPorts
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
                        val parsedCall =
                            InstallFirewall.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val port =
                            parsedCall.port
                        val path =
                            parsedCall.path
                        val name =
                            parsedCall.name
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
                        val parsedCall =
                            ReplaceApplication.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val port =
                            parsedCall.port
                        val path =
                            parsedCall.path
                        val name =
                            parsedCall.name
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
                        val parsedCall =
                            UninstallPort.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val port =
                            parsedCall.port
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
                        val parsedCall =
                            PortOnOff.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val port =
                            parsedCall.port
                        val on =
                            parsedCall.on
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
                        val parsedCall =
                            PeekCode.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val targetIP =
                            parsedCall.targetIP
                        val port =
                            parsedCall.port
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
                        val parsedCall =
                            PeekLogs.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val targetIP =
                            parsedCall.targetIP
                        val port =
                            parsedCall.port
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
                        val parsedCall =
                            SavePortNote.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val port =
                            parsedCall.port
                        val note =
                            parsedCall.note
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
                        val parsedCall =
                            SetWatchQuantity.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val watchID =
                            parsedCall.watchID
                        val quantity =
                            parsedCall.quantity
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
                        val parsedCall =
                            SetWatchOnOff.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val watchID =
                            parsedCall.watchID
                        val state =
                            parsedCall.state
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
                        val parsedCall =
                            SetWatchNote.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val watchID =
                            parsedCall.watchID
                        val note =
                            parsedCall.note
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
                        val parsedCall =
                            SetWatchSearchFirewall.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val watchID =
                            parsedCall.watchID
                        val searchFireWall =
                            parsedCall.searchFireWall
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
                        val parsedCall =
                            DeleteWatch.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val watchID =
                            parsedCall.watchID
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
                        val parsedCall =
                            DeleteFirewall.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val portID =
                            parsedCall.portID
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
                        val parsedCall =
                            ChangeWatchPort.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val WatchID =
                            parsedCall.watchId
                        val PortID =
                            parsedCall.portId
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
                        val parsedCall =
                            ChangeWatchType.fromRpc(
                                RFC
                            )
                        var ip =
                            parsedCall.ip
                        ip = crypt(
                            ip,
                            clientKey
                        )
                        val WatchID =
                            parsedCall.watchID
                        val PortID =
                            parsedCall.portID
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
                        val parsedCall = DeleteFolder.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val directory = parsedCall.directory
                        MyComputerHandler!!.addData(
                            ApplicationData("deletefolder", directory, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "setdummyport") {
                        val parsedCall = SetDummyPort.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val port = parsedCall.port
                        val dummy = parsedCall.dummy
                        MyComputerHandler!!.addData(
                            ApplicationData("setdummyport", dummy, port, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "changedailypay") {
                        val parsedCall = ChangeDailyPay.fromRpc(RFC)
                        val ip = parsedCall.ip
                        val port = parsedCall.port
                        val change = parsedCall.change
                        var finalizeIP = parsedCall.finalizeIP
                        val attackPort = parsedCall.attackPort
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
                        val parsedCall = DeleteLogs.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        MyComputerHandler!!.addData(
                            ApplicationData("deletelogs", null, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "createfolder") {
                        val parsedCall = CreateFolder.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val directory = parsedCall.directory
                        MyComputerHandler!!.addData(
                            ApplicationData("createfolder", directory, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "put") {
                        val parsedCall = Put.fromRpc(RFC)
                        val ip = parsedCall.ip
                        val port = parsedCall.port
                        val name = parsedCall.name
                        val fetch_path = parsedCall.fetchPath
                        val put_path = parsedCall.putPath
                        var targetIP = parsedCall.targetIP
                        targetIP = crypt(targetIP, clientKey)
                        val password = parsedCall.password
                        val quantity = parsedCall.quantity
                        val Parameter: Array<Any?>? =
                            arrayOf<Any?>(ip, name, fetch_path, put_path, password, quantity)
                        MyComputerHandler!!.addData(
                            ApplicationData("put", Parameter, port, targetIP),
                            targetIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "get") {
                        val parsedCall = Get.fromRpc(RFC)
                        val ip = parsedCall.ip
                        val port = parsedCall.port
                        val name = parsedCall.name
                        val fetch_path = parsedCall.fetchPath
                        val put_path = parsedCall.putPath
                        var targetIP = parsedCall.targetIP
                        targetIP = crypt(targetIP, clientKey)
                        val password = parsedCall.password
                        val quantity = parsedCall.quantity
                        val Parameter: Array<Any?>? =
                            arrayOf<Any?>(targetIP, name, fetch_path, put_path, password, quantity)
                        MyComputerHandler!!.addData(
                            ApplicationData("get", Parameter, port, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "malget") {
                        val parsedCall = MalGet.fromRpc(RFC)
                        val ip = parsedCall.ip
                        val port = parsedCall.port
                        val name = parsedCall.name
                        val fetch_path = parsedCall.fetchPath
                        val put_path = parsedCall.putPath
                        var targetIP = parsedCall.targetIP
                        val attackPort = parsedCall.attackPort
                        targetIP = crypt(targetIP, clientKey)
                        val Parameter: Array<Any?>? =
                            arrayOf<Any?>(targetIP, name, fetch_path, put_path, "", port, attackPort)
                        MyComputerHandler!!.addData(
                            ApplicationData("malget", Parameter, port, targetIP),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "requestfile") {
                        val parsedCall = RequestFile.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val path = parsedCall.path
                        val name = parsedCall.name
                        val Parameter: Array<String?>? = arrayOf<String?>(path, name)
                        MyComputerHandler!!.addData(
                            ApplicationData("requestfile", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "requestgame") {
                        val parsedCall = RequestGame.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val path = parsedCall.path
                        val name = parsedCall.name
                        val Parameter: Array<String?>? = arrayOf<String?>(path, name)
                        MyComputerHandler!!.addData(
                            ApplicationData("requestgame", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "requestscan") {
                        val parsedCall = RequestScan.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val targetIP = parsedCall.targetIP
                        if (ip != targetIP) MyComputerHandler!!.addData(
                            ApplicationData("requestscan", ip, 0, ip),
                            targetIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "savefile") {
                        val parsedCall = SaveFile.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val path = parsedCall.path
                        val name = parsedCall.name
                        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name)
                        MyComputerHandler!!.addData(
                            ApplicationData("savefile", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "compilefile") {
                        val parsedCall = CompileFile.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val path = parsedCall.path
                        val name = parsedCall.name
                        val price = parsedCall.price
                        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name, price)
                        MyComputerHandler!!.addData(
                            ApplicationData("compilefile", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "deletemulti") {
                        val parsedCall = DeleteMulti.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)

                        val allFiles = parsedCall.allFiles
                        val parameters: Array<Any?>? = arrayOf<Any?>(allFiles)
                        MyComputerHandler!!.addData(
                            ApplicationData("deletemulti", parameters, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "deletefile") {
                        val parsedCall = DeleteFile.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val path = parsedCall.path
                        val name = parsedCall.name
                        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name)
                        MyComputerHandler!!.addData(
                            ApplicationData("deletefile", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "setfiledescription") {
                        val parsedCall = SetFileDescription.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val path = parsedCall.path
                        val name = parsedCall.name
                        val description = parsedCall.description
                        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name, description)
                        MyComputerHandler!!.addData(
                            ApplicationData("setfiledescription", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "setfileprice") {
                        val parsedCall = SetFilePrice.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val path = parsedCall.path
                        val name = parsedCall.name
                        val price = parsedCall.price
                        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name, price)
                        MyComputerHandler!!.addData(
                            ApplicationData("setfileprice", Parameter, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "emptypettycash") {
                        val parsedCall = EmptyPettyCash.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val targetIP = parsedCall.targetIP
                        val targetPort = parsedCall.targetPort
                        val windowHandle = parsedCall.windowHandle
                        MyComputerHandler!!.addData(
                            ApplicationData("emptyPettyCash", windowHandle, targetPort, ip),
                            targetIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "finalizecancelled") {
                        val parsedCall = FinalizeCancelled.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val targetIP = parsedCall.targetIP
                        val targetPort = parsedCall.targetPort
                        MyComputerHandler!!.addData(
                            ApplicationData("finalizecancelled", null, targetPort, ip),
                            targetIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "requestattack") {
                        val parsedCall = RequestAttack.fromRpc(RFC)
                        val targetIP = parsedCall.targetIP
                        val targetPort = parsedCall.targetPort
                        var sourceIP = parsedCall.sourceIP
                        sourceIP = crypt(sourceIP, clientKey)

                        val sourcePort = parsedCall.sourcePort

                        val secondaryPorts = parsedCall.secondaryPorts
                        val scripts = parsedCall.scripts
                        val extraInfo = parsedCall.extraInfo
                        val windowHandle = parsedCall.windowHandle

                        val Parameters: Array<Any?>? =
                            arrayOf<Any?>(targetIP, targetPort, secondaryPorts, scripts, extraInfo, windowHandle)
                        val AD = ApplicationData("requestattack", Parameters, sourcePort, sourceIP)

                        if (targetIP != sourceIP && targetIP.indexOf("store") == -1) MyComputerHandler!!.addData(
                            AD,
                            sourceIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "requestzombieattack") {
                        val parsedCall = RequestZombieAttack.fromRpc(RFC)
                        val targetIP = parsedCall.targetIP
                        val targetPort = parsedCall.targetPort
                        val sourceIP = parsedCall.sourceIP
                        val sourcePort = parsedCall.sourcePort

                        val I = parsedCall.I
                        val S = parsedCall.S
                        val O = parsedCall.O
                        var parentIP = parsedCall.parentIP
                        parentIP = crypt(parentIP, clientKey)

                        val Parameters: Array<Any?>? = arrayOf<Any?>(targetIP, targetPort, I, S, O, sourceIP)
                        val AD = ApplicationData("requestzombieattack", Parameters, sourcePort, parentIP)

                        if (targetIP != sourceIP && targetIP.indexOf("store") == -1) MyComputerHandler!!.addData(
                            AD,
                            parentIP,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "transfer") {
                        val parsedCall = Transfer.fromRpc(RFC)
                        val amount = parsedCall.amount
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val target_ip = parsedCall.targetIp
                        val port = parsedCall.port
                        val tO: Array<Any?>? = arrayOf<Any?>(target_ip, amount)
                        MyComputerHandler!!.addData(
                            ApplicationData("transfer", tO, port, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "deposit") {
                        val parsedCall = Deposit.fromRpc(RFC)
                        val amount = parsedCall.amount
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val port = parsedCall.port
                        MyComputerHandler!!.addData(
                            ApplicationData("deposit", amount, port, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "dochallenge") {
                        val parsedCall = DoChallenge.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val code = parsedCall.code
                        val challengeID = parsedCall.challengeID

                        val O: Array<Any?>? = arrayOf<Any?>(code, challengeID)
                        MyComputerHandler!!.addData(
                            ApplicationData("dochallenge", O, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    }
                    //Allows a player to sell a file back to the game store.
                    else if (RFC.getFunction() == "sellfile") {
                        val parsedCall = SellFile.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val location = parsedCall.location
                        val fileName = parsedCall.fileName
                        val compileCost = parsedCall.compileCost
                        val quantity = parsedCall.quantity
                        val O: Array<Any?>? = arrayOf<Any?>(location, fileName, compileCost, ip, quantity)
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "requestsellfile",
                                O,
                                0,
                                "store" + serverID
                            ), ip, ApplicationData.OUTSIDE
                        )
                    }
                    // Sell multiple files at once from the client
                    else if (RFC.getFunction() == "sellfilemulti") {
                        val parsedCall = SellFileMulti.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val allFiles = parsedCall.allFiles
                        val O: Array<Any?>? = arrayOf<Any?>(allFiles, ip)
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "sellfilemulti",
                                O,
                                0,
                                "store" + serverID
                            ), ip, ApplicationData.OUTSIDE
                        )
                    }
                    //Allows a player to sell a file back to the game store.
                    else if (RFC.getFunction() == "decompilefile") {
                        val parsedCall = DecompileFile.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val location = parsedCall.location
                        val fileName = parsedCall.fileName
                        val compileCost = parsedCall.compileCost
                        val O: Array<Any?>? = arrayOf<Any?>(location, fileName, compileCost, ip)
                        MyComputerHandler!!.addData(
                            ApplicationData("decompilefile", O, 0, ip),
                            ip,
                            ApplicationData.OUTSIDE
                        )
                    }
                    //A deposit requested from facebook.
                    else if (RFC.getFunction() == "facebookdeposit") {
                        val parsedCall = FacebookDeposit.fromRpc(RFC)
                        val ip = parsedCall.ip
                        val amount = parsedCall.amount
                        val defaultPort = parsedCall.defaultPort

                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "deposit",
                                amount,
                                defaultPort,
                                ip
                            ), ip, ApplicationData.OUTSIDE
                        )
                    }
                    //A withdraw requested from facebook.
                    else if (RFC.getFunction() == "facebookwithdraw") {
                        val parsedCall = FacebookWithdraw.fromRpc(RFC)
                        val ip = parsedCall.ip
                        val amount = parsedCall.amount
                        val defaultPort = parsedCall.defaultPort

                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "withdraw",
                                amount,
                                defaultPort,
                                ip
                            ), ip, ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "facebooktransfer") {
                        val parsedCall = FacebookTransfer.fromRpc(RFC)
                        val ip = parsedCall.ip
                        val ip2 = parsedCall.ip2

                        val amount = parsedCall.amount
                        val defaultPort = parsedCall.defaultPort

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
                        val parsedCall = FacebookUpdate.fromRpc(RFC)
                        val ip = parsedCall.ip
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "facebookupdate",
                                null,
                                0,
                                ip
                            ), ip, ApplicationData.OUTSIDE
                        )
                    } else if (RFC.getFunction() == "setpreferences") {
                        val parsedCall = SetPreferences.fromRpc(RFC)
                        var ip = parsedCall.ip
                        ip = crypt(ip, clientKey)
                        val preferences =
                            parsedCall.preferences
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
