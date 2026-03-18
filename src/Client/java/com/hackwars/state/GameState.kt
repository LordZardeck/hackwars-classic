package com.hackwars.state

import assignments.*
import chat.messages.ArrayMessageOut
import com.hackwars.assignments.AssignmentEvent
import com.hackwars.assignments.HackerDamageListener
import com.hackwars.assignments.HackerPacketListener
import com.hackwars.client.ConfigurationState
import com.plink.dolphinnet.Assignment
import com.plink.dolphinnet.DataHandler
import com.plink.dolphinnet.Reporter
import com.plink.dolphinnet.assignments.ZippedAssignment
import gui.Hacker
import kotlinx.coroutines.*
import util.Encryption
import util.Time
import java.lang.Runnable
import java.util.*
import javax.swing.SwingUtilities
import javax.swing.event.EventListenerList

/**
 * (C) Ben Coe 2007 <br></br>
 * The main controller for Coezilla.
 */

class GameState : DataHandler, Runnable {
    companion object {
        private val LOGIN_FALLBACK_START =
            !"false".equals(System.getProperty("hackwars.loginFallbackStart", "true"), ignoreCase = true)
        var lastPingSuccess: Long = 0
        const val PINGTIMEOUT: Int = 40000
        const val PINGTIME: Int = 15000
    }

    interface GameStateListener : EventListener
    class MessageEvent(source: Any, @JvmField val message: String) : EventObject(source)
    fun interface MessageEventListener : GameStateListener {
        fun onMessage(event: MessageEvent)
    }

    class FinishLoadingEvent(source: Any) : EventObject(source)
    fun interface FinishLoadingEventListener : GameStateListener {
        fun onFinishLoading(event: FinishLoadingEvent)
    }

    class ExitProgramEvent(source: Any) : EventObject(source)
    fun interface ExitProgramEventListener : GameStateListener {
        fun onExitProgram(event: ExitProgramEvent)
    }

    val listeners: EventListenerList = EventListenerList()

    fun <T : GameStateListener> addEventListener(c: Class<T>, l: T) = listeners.add(c, l)
    fun <T : GameStateListener> removeEventListener(c: Class<T>, l: T) = listeners.remove(c, l)

    private fun fireMessageEvent(message: String) {
        val event = MessageEvent(this, message)
        val invoke = { listeners.getListeners(MessageEventListener::class.java).forEach { it.onMessage(event) } };

        if (SwingUtilities.isEventDispatchThread()) invoke() else SwingUtilities.invokeLater(invoke)
    }
    private fun fireFinishedLoadingEvent() {
        val event = FinishLoadingEvent(this)
        val invoke = { listeners.getListeners(FinishLoadingEventListener::class.java).forEach { it.onFinishLoading(event) } };

        if (SwingUtilities.isEventDispatchThread()) invoke() else SwingUtilities.invokeLater(invoke)
    }
    private fun fireExitProgramEvent() {
        val event = ExitProgramEvent(this)
        val invoke = { listeners.getListeners(ExitProgramEventListener::class.java).forEach { it.onExitProgram(event) } };

        if (SwingUtilities.isEventDispatchThread()) invoke() else SwingUtilities.invokeLater(invoke)
    }

    var reconnect: Boolean = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var fallbackJob: Job? = null
    private var gameServerReporter: Reporter? = null
    private var chatServerReporter: Reporter? = null
    private var user: String? = null
    private var accessToken: String? = null
    private var lastAccessed: Long = 0
    private var lastPing: Long = 0
    private val MyTime = Time()
    private val TIME_OUT: Long = 70000
    private val CHAT_TIME_OUT: Long = 70000
    private val Tasks = ArrayList<Any?>()
    private var username: String? = null
    private val packets = ArrayList<Any?>()
    private var encryptedIP: String? = null
    private var function: String? = ""
    private var open = false
    private var run = false

    private val hackerPacketListener = HackerPacketListener(::addFunctionCall)
    private val hackerDamageListener = HackerDamageListener()
    private var hackerState: Hacker? = null
        set(value) {
            field = value
            hackerPacketListener.receiver = value
            hackerDamageListener.receiver = value
        }

    /**
     * This is run from the main(), when the View is first loaded. Also called from Launcher.java.
     */
    fun loginToServer(username: String?, password: String?, ip: String?) {
        loginToServer(username, password)
    }

    fun loginToServer(playFabId: String?, sessionTicket: String?) {
        // A login from the launcher login form should always start a fresh UI session.
        hackerState = hackerState?.apply { frame?.dispose() }.let { null }
        packets.clear()
        open = false
        reconnect = false
        this.username = playFabId
        this.accessToken = sessionTicket
        this.user = null
        println("Trying to login $playFabId with PlayFab access token")
        run = true
        loopJob?.cancel()
        fallbackJob?.cancel()
        loopJob = scope.launch {
            println("BEFORE RECONNECT()")
            reconnect()
            println("DONE RECONNECT()")
            runLoop()
        }
        if (LOGIN_FALLBACK_START) {
            fallbackJob = scope.launch {
                delay(2500)
                if (run && hackerState == null) {
                    println("Login response timeout, starting local UI fallback.")
                    fireMessageEvent("Login timed out. Please try again.")
                }
            }
        }
    }

    fun startProgram(username: String?, ip: String?, npc: Boolean, encryptedIP: String?) {
        reconnect = false
        this.encryptedIP = encryptedIP
        if (hackerState == null) hackerState = Hacker(this, username, ip, npc, encryptedIP, false)
        else hackerState!!.update(this, username, ip, npc, encryptedIP)
    }

    fun setFunction(function: String?) {
        this.function = function
    }

    override fun addFinishedAssignment(assignment: Assignment?) {
        gameServerReporter?.addFinishedAssignment(ZippedAssignment(0, assignment))
        runBlocking { delay(250) }
    }

    fun addFinishedAssignment(assignment: MessageInPacket?) {
        chatServerReporter?.addFinishedAssignment(ZippedAssignment(0, assignment))
    }

    override fun getData(i: Int): Any? {
        return (null)
    }

    override fun resetData() {
    }

    /**
     * Called by Data handler to return assignments to front-end.
     */
    var connected: Boolean = false

    @Synchronized
    override fun addData(o: Any?) {
        if (hackerState != null && !reconnect) {
            if (o is PingAssignment) {
                lastPingSuccess = MyTime.currentTime
            }

            when (o) {
                is PacketAssignment, is DamageAssignment -> packets.add(o)
                is ArrayMessageOut -> runCatching { hackerState?.chatController?.processMessage(o) }.onFailure {
                    it.printStackTrace()
                }

                is LoginSuccessAssignment -> {
                    o.publicKey?.let {
                        Encryption.getInstance().finalize(it)
                    }
                    user = o.ip
                    startProgram(username, o.ip, o.isNPC, o.encryptedIP)
                }
            }

            lastAccessed = MyTime.currentTime //Prevent timeout.
            return
        }

        when (o) {
            is PacketAssignment -> {
                packets.add(o)
                println("Received a packet before finished creating")
            }

            is DamageAssignment -> packets.add(o)
            is ArrayMessageOut -> {
                packets.add(o)
                println("Got Array Message Out Before Loaded")
            }

            is LoginSuccessAssignment -> {
                println("Login Successful")
                o.publicKey?.let {
                    Encryption.getInstance().finalize(it)
                }
                user = o.ip
                startProgram(username, o.ip, o.isNPC, o.encryptedIP)
            }

            is LoginFailedAssignment -> {
                fireMessageEvent("Login failed. Please authenticate again with PlayFab.")
            }
        }
    }


    fun crypt(data: ByteArray, key: String): String {
        for (ii in data.indices) {
            data[ii] = (data[ii].toInt() xor (key.toByteArray())[ii % key.length].toInt()).toByte()
        }
        return (String(data))
    }

    /**
     * Retry connecting.
     */
    suspend fun reconnect() {
        println("Connecting")
        println("ABOUT TO CREATE REPORTER")
        gameServerReporter = Reporter(
            ConfigurationState.GameServer.Address,
            200000,
            ConfigurationState.GameServer.InPort,
            ConfigurationState.GameServer.OutPort,
        )
        gameServerReporter?.setDataHandler(this)

        println("ABOUT TO CREATE CHAT REPORTER")
        chatServerReporter = Reporter(
            ConfigurationState.ChatServer.Address,
            200000,
            ConfigurationState.ChatServer.InPort,
            ConfigurationState.ChatServer.OutPort,
        )
        chatServerReporter?.setDataHandler(this)
        println("CREATED REPORTER & CHAT REPORTER")

        //Wait for handshake from server.
        var success = true
        var startTime = MyTime.currentTime
        println("Attempting to Connect to Server")
        while (gameServerReporter!!.id == -1) {
            if (MyTime.currentTime - startTime > TIME_OUT) {
                success = false
                break
            }
            delay(10)
        }
        startTime = MyTime.currentTime
        println("Connection ID: " + gameServerReporter!!.id)
        println("Connecting to Chat")
        while (chatServerReporter!!.id == -1) {
            if (MyTime.currentTime - startTime > CHAT_TIME_OUT) {
                success = false
                break
            }
            delay(10)
        }
        if (success && gameServerReporter?.id == -1) {
            success = false
        }
        if (success && chatServerReporter?.id == -1) {
            success = false
        }
        if (!success) {
            fireMessageEvent("Connection failed. Make sure local services are running.")
            run = false
            return
        }
        if (accessToken.isNullOrBlank()) {
            fireMessageEvent("Missing PlayFab access token.")
            run = false
            return
        }
        println("ZING")
        println("Connected --- " + username)

        Encryption.getInstance().init()
        val MyLoginAssignment = LoginAssignment(0, accessToken)
        MyLoginAssignment.publicKey = Encryption.getInstance().encodedKey
        gameServerReporter!!.addFinishedAssignment(MyLoginAssignment)
        val MyChatLoginAssignment = LoginAssignment(0, accessToken)
        MyChatLoginAssignment.publicKey = Encryption.getInstance().encodedKey
        chatServerReporter!!.addFinishedAssignment(MyChatLoginAssignment)
    }

    fun addFunctionCall(remoteFunctionCall: RemoteFunctionCall?) {
        synchronized(Tasks) {
            Tasks.add(remoteFunctionCall)
        }
    }

    fun exitProgram() {
        fireExitProgramEvent()
    }

    fun finishedLoading() {
        open = true
        fireFinishedLoadingEvent()
    }

    fun clean() {
        loopJob?.cancel()
        fallbackJob?.cancel()
        hackerState = null
        MyTime.clean()
        open = false
        System.gc()
        run = false
        lastPingSuccess = 0
        gameServerReporter?.clean()
        chatServerReporter?.clean()
    }

    override fun run() {
        runBlocking {
            runLoop()
        }
    }

    private suspend fun runLoop() {
        while (lastPingSuccess == 0L) lastPingSuccess = MyTime.currentTime
        while (run) {
            try {
                synchronized(Tasks) {
                    val myIterator: MutableIterator<*> = Tasks.iterator()
                    while (myIterator.hasNext()) {
                        val o = myIterator.next()
                        if (o is RemoteFunctionCall) {
                            addFinishedAssignment(o as Assignment)
                        }
                        myIterator.remove()
                    }
                }
                delay(150)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (hackerState?.loading != null) {
                for (j in packets.indices) {
                    packets[j] = when (val packet = packets[j]) {
                        is PacketAssignment -> hackerPacketListener.onPacketAssignment(
                            AssignmentEvent(this, packet)
                        )

                        is DamageAssignment -> hackerDamageListener.onDamageAssignment(
                            AssignmentEvent(this, packet)
                        )

                        else -> packet
                    }.takeUnless { it is Unit }
                }

                //Remove processed packets.
                packets.iterator().run { while (hasNext()) next() ?: remove() }
            }
            if (hackerState != null) {
                runCatching {
                    hackerState?.chatController?.popMessages()?.let {
                        chatServerReporter?.addFinishedAssignment(MessageInPacket(it))
                    }
                }.onFailure { it.printStackTrace() }

                if (MyTime.currentTime - lastPing > PINGTIME) {
                    lastPing = MyTime.currentTime
                    if (user != null) {
                        gameServerReporter?.addFinishedAssignment(PingAssignment(0, user))
                        chatServerReporter?.addFinishedAssignment(
                            PingAssignment(
                                0,
                                username!!.lowercase(Locale.getDefault())
                            )
                        )
                    }
                }
            }
            if (MyTime.currentTime - lastPingSuccess > PINGTIMEOUT) {
                if (open) {
                    println("Reconnecting")
                    lastPingSuccess = MyTime.currentTime
                    println("Attempt reconnect.")
                    gameServerReporter?.clean()
                    chatServerReporter?.clean()

                    reconnect = true
                    reconnect()
                }
            }
        }
    }
}
