package com.hackwars.state

import assignments.*
import chat.messages.ArrayMessageOut
import client.Launcher
import com.hackwars.assignments.AssignmentEvent
import com.hackwars.assignments.HackerDamageListener
import com.hackwars.assignments.HackerPacketListener
import com.plink.dolphinnet.Assignment
import com.plink.dolphinnet.DataHandler
import com.plink.dolphinnet.HashSingleton
import com.plink.dolphinnet.Reporter
import com.plink.dolphinnet.assignments.ZippedAssignment
import gui.Hacker
import gui.Tutorial
import kotlinx.coroutines.*
import util.Encryption
import util.Time
import java.awt.GraphicsEnvironment
import java.lang.Runnable
import java.util.*
import javax.swing.JOptionPane

/**
 * (C) Ben Coe 2007 <br></br>
 * The main controller for Coezilla.
 */

class GameState(private val ip: String, MyLoad: Launcher?) : DataHandler, Runnable {
    companion object {
        private val LOGIN_FALLBACK_START =
            !"false".equals(System.getProperty("hackwars.loginFallbackStart", "true"), ignoreCase = true)
        var lastPingSuccess: Long = 0
        const val PINGTIMEOUT: Int = 40000
        const val PINGTIME: Int = 15000
    }

    var reconnect: Boolean = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var fallbackJob: Job? = null
    private var gameServerReporter: Reporter? = null
    private var chatServerReporter: Reporter? = null
    private var user: String? = null
    private var pass: String? = null
    private var lastAccessed: Long = 0
    private var lastPing: Long = 0
    private val MyTime = Time()
    private val TIME_OUT: Long = 70000
    private val CHAT_TIME_OUT: Long = 70000
    private val Tasks = ArrayList<Any?>()
    private var username: String? = null
    private val packets = ArrayList<Any?>()
    var load: Launcher? = null
        private set
    private var encryptedIP: String? = null
    var tutorial: Tutorial? = null
        private set
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
        // A login from the launcher login form should always start a fresh UI session.
        hackerState = hackerState?.apply { frame?.dispose() }.let { null }
        packets.clear()
        open = false
        reconnect = false
        this.username = username
        this.pass = password
        this.user = ip
        println("Trying to login $username with password $password")
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
                    startProgram(this@GameState.username, this@GameState.user, false, this@GameState.user)
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

    val iP: String
        get() = (ip)

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

    init {
        this.load = MyLoad
        tutorial = Tutorial()
    }

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
                startProgram(username, o.ip, o.isNPC, o.encryptedIP)
            }

            is LoginFailedAssignment -> {
                load?.setMessage("Login failed, check your username<br> and password.")
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
        gameServerReporter = Reporter(ip, 200000, 10021, 10020)
        gameServerReporter?.setDataHandler(this)

        println("ABOUT TO CREATE CHAT REPORTER")
        chatServerReporter = Reporter(ip, 200000, 10026, 10025)
        chatServerReporter?.setDataHandler(this)
        println("CREATED REPORTER & CHAT REPORTER")

        //Wait for handshake from server.
        var success = true
        var startTime = MyTime.currentTime
        println("Attempting to Connect to Server")
        if (ip != "none") {
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
        }
        if (success && gameServerReporter?.id == -1) {
            success = false
        }
        if (success && chatServerReporter?.id == -1) {
            success = false
        }
        if (!success) {
            if (this.load != null) {
                load!!.setMessage("Connection failed. Make sure local services are running.")
            } else if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(
                    null,
                    "Connection failed. Start Tomcat, HackerServer, and ChatServer first."
                )
            }
            run = false
            return
        }
        println("ZING")
        println("Connected --- " + username + "   " + pass + "   " + user)

        Encryption.getInstance().init()
        val key = HashSingleton.getHash()
        val MyLoginAssignment = LoginAssignment(0, username, crypt(pass!!.toByteArray(), key), user)
        MyLoginAssignment.publicKey = Encryption.getInstance().encodedKey
        gameServerReporter!!.addFinishedAssignment(MyLoginAssignment)
        val MyChatLoginAssignment = LoginAssignment(0, username, crypt(pass!!.toByteArray(), key), user)
        MyChatLoginAssignment.publicKey = Encryption.getInstance().encodedKey
        chatServerReporter!!.addFinishedAssignment(MyChatLoginAssignment)
    }

    fun addFunctionCall(remoteFunctionCall: RemoteFunctionCall?) {
        synchronized(Tasks) {
            Tasks.add(remoteFunctionCall)
        }
    }

    fun exitProgram() {
        if (this.load != null) load!!.exitProgram()
    }

    fun finishedLoading() {
        open = true
        if (this.load != null) {
            load!!.finishedLoading()
        }
    }

    fun clean() {
        loopJob?.cancel()
        fallbackJob?.cancel()
        hackerState = null
        this.load = null
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
