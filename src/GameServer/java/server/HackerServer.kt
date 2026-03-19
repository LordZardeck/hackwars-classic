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
import server.remote.RemoteCallContext
import server.remote.invokeOnServer
import util.Encryption
import util.PlayFabTokenVerifier.AuthResult
import util.SessionTokenVerifiers
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

    private fun processLoginAssignment(assignment: LoginAssignment) {
        if (!on) return dispatchPacket(LoginFailedAssignment(0), assignment.reporterID)

        val clientKey = assignment.hash
        val accessToken = assignment.accessToken
        val authResult: AuthResult = runCatching { SessionTokenVerifiers.active().verify(accessToken) }
            .onFailure {
                Logger.error("HackerServer: PlayFab authentication failed", it)
            }
            .getOrNull()
            ?: return dispatchPacket(LoginFailedAssignment(0), assignment.reporterID)


        MyComputerHandler?.getComputer(authResult.playerIp)?.let { computer ->
            computer.setClientHash(clientKey)
            computer.setPublicKey(assignment.publicKey)
            computer.setPlayFabAuthenticated(authResult.playFabId)
            computer.setConnectionID(assignment.reporterID)
            return
        }

        val computer = Computer(
            authResult.playFabId,
            authResult.playerIp,
            MyComputerHandler,
            MyTime,
            assignment.reporterID,
            this,
            true
        ).also { MyComputerHandler?.addComputer(it) }

        computer.setClientHash(clientKey)
        computer.setPublicKey(assignment.publicKey)
        computer.setPlayFabAuthenticated(authResult.playFabId)
        computer.loadSave()
    }

    private fun processPingAssignment(assignment: PingAssignment) {
        MyComputerHandler?.addData(ApplicationData("ping", null, 0, assignment.user), assignment.user)

        //Return a packet to the server.
        this.addData(arrayOf<Any>(PingAssignment(0, "bcoe"), assignment.reporterID))

        if (assignment.id == 850335 && assignment.user == "bcoe") { //Start booting players.
            SHUTDOWN_AT = MyTime!!.currentTime
            on = false
            ServerRuntimeState.setShutdownAt(SHUTDOWN_AT)
            ServerRuntimeState.setRunning(on)
            MyComputerHandler!!.startCountDown()
        }
    }

    private fun processRemoteFunctionCall(assignment: RemoteFunctionCall) {
        runCatching { assignment.decryptFunction(MyEncryption, assignment.hash) }
            .onFailure { Logger.error("Failed to decrypt function", it) }

        if (assignment.function == null) {
            return
        }

        val remoteCallContext = RemoteCallContext(MyComputerHandler!!, serverID) { ip ->
            crypt(ip, assignment.hash)
        }
        if (!assignment.invokeOnServer(remoteCallContext)) {
            Logger.warn("Unhandled remote function: {}", assignment.function)
        }
    }

    private fun processGenericJob(job: Array<*>) {
        val (assignment, connectionID) = job
        if (job.size >= 2 && assignment is Assignment && connectionID is Int) {
            (editor.clients.get(connectionID) as? ClientData)?.run {
                addJob(ZippedAssignment(0, assignment))
            }
        }
    }

    /**
     * Grabs work from the server and dispatches it via the computer handler to
     * individual 'PCs' playing the game.
     */
    private fun processTasks() {
        Logger.info("Game Server Started")

        while (true) {
            val task = runBlocking { taskQueue.receive() } ?: continue

            runCatching {
                when (task) {
                    is LoginAssignment -> processLoginAssignment(task)
                    is PingAssignment -> processPingAssignment(task)
                    is RemoteFunctionCall -> processRemoteFunctionCall(task)
                    is Array<*> -> processGenericJob(task)
                }
            }.onFailure { Logger.error("Error while processing task", it) }
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
