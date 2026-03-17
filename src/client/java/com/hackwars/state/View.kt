package com.hackwars.state

import assignments.*
import chat.messages.ArrayMessageOut
import client.Launcher
import com.hackwars.net.DamageEvent
import com.hackwars.net.decodeDamageEvent
import com.plink.dolphinnet.Assignment
import com.plink.dolphinnet.DataHandler
import com.plink.dolphinnet.HashSingleton
import com.plink.dolphinnet.Reporter
import com.plink.dolphinnet.assignments.ZippedAssignment
import gui.Hacker
import gui.MacroDialog
import gui.Tutorial
import util.Encryption
import util.Time
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.Semaphore
import javax.swing.*

/**
 * (C) Ben Coe 2007 <br></br>
 * The main controller for Coezilla.
 */

class View(private val ip: String, MyLoad: Launcher?) : DataHandler, Runnable {
    var reconnect: Boolean = false
    private var MyThread: Thread? = null
    private var R: Reporter? = null
    private var chatR: Reporter? = null
    private var user: String? = null
    private var pass: String? = null
    private var lastAccessed: Long = 0
    private var lastPing: Long = 0
    private val MyTime = Time()
    private val TIME_OUT: Long = 70000
    private val CHAT_TIME_OUT: Long = 70000
    private val available = Semaphore(1, true)
    private var MyHacker: Hacker? = null
    private val Tasks = ArrayList<Any?>()
    private val LoginFrame: JFrame? = null
    private val usernameField: JTextField? = null
    private val ipField: JTextField? = null
    private var username: String? = null
    private val message: JLabel? = null
    private val packets = ArrayList<Any?>()
    private val loaded = false
    var load: Launcher? = null
        private set
    private var encryptedIP: String? = null
    var tutorial: Tutorial? = null
        private set
    private var function: String? = ""
    private var open = false
    private var run = false
    private val lastPacketSent: Long = 0
    private var offline = false

    /**
     * This is run from the main(), when the View is first loaded. Also called from Launcher.java.
     */
    fun loginToServer(username: String?, password: String?, ip: String?) {
        // A login from the launcher login form should always start a fresh UI session.
        if (MyHacker != null) {
            try {
                val frame = MyHacker!!.getFrame()
                if (frame != null) {
                    frame.dispose()
                }
            } catch (e: Exception) {
            }
            MyHacker = null
        }
        packets.clear()
        open = false
        reconnect = false
        this.username = username
        this.pass = password
        this.user = ip
        println("Trying to login " + username + " with password " + password)
        run = true
        println("BEFORE RECONNECT()")
        reconnect()
        println("DONE RECONNECT()")
        MyThread = Thread(this)
        MyThread!!.start()
        if (LOGIN_FALLBACK_START) {
            val fallbackThread = Thread(object : Runnable {
                override fun run() {
                    try {
                        Thread.sleep(2500)
                    } catch (e: Exception) {
                    }
                    if (run && MyHacker == null) {
                        println("Login response timeout, starting local UI fallback.")
                        startProgram(this@View.username, this@View.user, false, this@View.user)
                    }
                }
            }, "Login Fallback")
            fallbackThread.setDaemon(true)
            fallbackThread.start()
        }
        //        addFinishedAssignment(new LoginAssignment(0, username, password, user));
    }

    fun startProgram(username: String?, ip: String?, npc: Boolean, encryptedIP: String?) {
        //System.out.println("Starting Program");
        reconnect = false
        /*this.username=username;
		loadUser(ip);

		reconnect();*/
        //user=ip;
        this.encryptedIP = encryptedIP
        /*MyThread=new Thread(this);
		MyThread.start();*/
        if (MyHacker == null) MyHacker = Hacker(this, username, ip, npc, encryptedIP, offline)
        else MyHacker!!.update(this, username, ip, npc, encryptedIP)
    }

    val iP: String?
        get() = (ip)

    /**
     * Load in username and password information.
     */
    fun loadUser(ip: String?): Boolean {
        user = ip
        //pass="Blah";
        //System.out.println("Loading User");
        return (true)
    }

    fun setFunction(function: String?) {
        this.function = function
    }

    /** ////////////////////////// */ //DataHandler implementation of abstract methods..
    override fun addFinishedAssignment(A: Assignment?) {
        if (A is RemoteFunctionCall) {
            val RFC = A

            val o = RFC.getParameters() as Array<Any?>
            val next = tutorial!!.checkStep(function, arrayOf<Any?>(o[0]))
            //System.out.println("Checking Tutorial Step "+tutorial.getFunction()+"  "+tutorial.getCheck()+":"+next+": "+function+" "+o[0]);
            if (next) tutorial!!.nextStep()
        }
        R!!.addFinishedAssignment(ZippedAssignment(0, A))
        try {
            Thread.sleep(250)
        } catch (e: Exception) {
        }
    }

    fun addFinishedAssignment(A: MessageInPacket?) {
        //System.out.println("Sending Chat Assignment");
        if (!offline) {
            chatR!!.addFinishedAssignment(ZippedAssignment(0, A))
        }
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

    //Constructor.
    init {
        //try{
        this.load = MyLoad
        //Encryption.getInstance().init();
        tutorial = Tutorial()

        //Sound.startSound();
    }

    @Synchronized
    override fun addData(o: Any?) {
        if (MyHacker != null && !reconnect) {
            if (o is PingAssignment) {
                lastPingSuccess = MyTime.getCurrentTime()
            }

            if (o is PacketAssignment || o is DamageAssignment) {
                packets.add(o)
            } else if (o is ArrayMessageOut) {
                //System.out.println("Got Array Message Out");
                try {
                    MyHacker!!.getChatController().processMessage(o)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (o is LoginSuccessAssignment) {
                val LSA = o
                if (LSA.getPublicKey() != null) {
                    Encryption.getInstance().finalize(LSA.getPublicKey())
                }
                startProgram(username, LSA.getIP(), LSA.isNPC(), LSA.getEncryptedIP())
            } else if (o is LoginFailedAssignment) {
                //	MyLoad.setMessage("Didn't Work");
                //System.out.println("Login Failed");
            }
            lastAccessed = MyTime.getCurrentTime() //Prevent timeout.
        } else {
            if (o is PacketAssignment) {
                if (o != null) {
                    packets.add(o)
                    println("Received a packet before finished creating")
                }
            } else if (o is DamageAssignment) {
                if (o != null) {
                    packets.add(o)
                }
            } else if (o is ArrayMessageOut) {
                packets.add(o)
                println("Got Array Message Out Before Loaded")
            } else if (o is LoginSuccessAssignment) {
                println("Login Successful")
                val LSA = o
                if (LSA.getPublicKey() != null) {
                    Encryption.getInstance().finalize(LSA.getPublicKey())
                }
                startProgram(username, LSA.getIP(), LSA.isNPC(), LSA.getEncryptedIP())
            } else if (o is LoginFailedAssignment) {
                val LFA = o
                //System.out.println("Login Failed "+LFA.getMessage());
                if (this.load != null) {
                    load!!.setMessage("Login failed, check your username<br> and password.")
                    //MyLoad.setMessage(LFA.getMessage());
                }
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
    fun reconnect() {
        println("Connecting")
        println("ABOUT TO CREATE REPORTER")
        R = Reporter(ip, 200000, 10021, 10020)
        println("ABOUT TO CREATE CHAT REPORTER")
        chatR = Reporter("localhost", 200000, 10026, 10025)

        R!!.setDataHandler(this)
        if (!offline) {
            chatR = Reporter(ip, 200000, 10026, 10025)
            chatR!!.setDataHandler(this)
        }
        println("CREATED REPORTER & CHAT REPORTER")

        //Wait for handshake from server.
        var success = true
        var startTime = MyTime.getCurrentTime()
        println("Attempting to Connect to Server")
        if (ip != "none") {
            while (R!!.getID() == -1) {
                if (MyTime.getCurrentTime() - startTime > TIME_OUT) {
                    success = false
                    break
                }
            }
            startTime = MyTime.getCurrentTime()
            println("Connection ID: " + R!!.getID())
            println("Connecting to Chat")
            if (!offline) {
                while (chatR!!.getID() == -1) {
                    if (MyTime.getCurrentTime() - startTime > CHAT_TIME_OUT) {
                        success = false
                        break
                    }
                    try {
                        Thread.sleep(10)
                    } catch (e: Exception) {
                    }
                }
            }
            //System.out.println("Connection ID: "+chatR.getID());
        }
        if (success && R!!.getID() == -1) {
            success = false
        }
        if (success && !offline && chatR!!.getID() == -1) {
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
        MyLoginAssignment.setPublicKey(Encryption.getInstance().getEncodedKey())
        R!!.addFinishedAssignment(MyLoginAssignment)
        if (!offline) {
            val MyChatLoginAssignment = LoginAssignment(0, username, crypt(pass!!.toByteArray(), key), user)
            MyChatLoginAssignment.setPublicKey(Encryption.getInstance().getEncodedKey())
            chatR!!.addFinishedAssignment(MyChatLoginAssignment)
        }
        //System.out.println("YAR");
    }

    fun addFunctionCall(RFC: RemoteFunctionCall?) {
        try {
            available.acquire()
            Tasks.add(RFC)
            available.release()
        } catch (e: Exception) {
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
        //	System.out.println("---------------------CLEANING----------------------------");
        MyHacker = null
        this.load = null
        MyTime.clean()
        open = false
        System.gc()
        run = false
        lastPingSuccess = 0
        try {
            R!!.clean()
        } catch (e: Exception) {
        }
        try {
            if (!offline) {
                chatR!!.clean()
            }
        } catch (e: Exception) {
        }
    }

    fun setOfflineMode(offline: Boolean) {
        this.offline = offline
    }


    override fun run() {
        val i = 0
        while (lastPingSuccess == 0L) lastPingSuccess = MyTime.getCurrentTime()
        while (run) {
            try {
                available.acquire()
                val MyIterator: MutableIterator<*> = Tasks.iterator()

                while (MyIterator.hasNext()) {
                    val o = MyIterator.next()
                    if (o is RemoteFunctionCall) {
                        addFinishedAssignment(o as Assignment)
                    }
                    MyIterator.remove()
                }

                available.release()
                Thread.sleep(150)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (MyHacker != null) {
                if (!MyHacker!!.getLoading()) {
                    for (j in packets.indices) {
                        if (packets.get(j) is PacketAssignment) {
                            val PA = packets.get(j) as PacketAssignment
                            packets.set(j, null)

                            //System.out.println("Received a packet after finished loading");
                            //System.out.println("Test");
                            //System.out.println("Received Packet");
                            MyHacker!!.setPettyCash(PA.getPettyCash())
                            //System.out.println("PETTY CASH: "+PA.getPettyCash());
                            MyHacker!!.setLoadFile(PA.getLoadFile())
                            MyHacker!!.setLoadFile(PA.getLoadFile())
                            MyHacker!!.setBankMoney(PA.getBankMoney())
                            MyHacker!!.setDefaultBank(PA.getDefaultBank())
                            MyHacker!!.setDefaultAttack(PA.getDefaultAttack())
                            MyHacker!!.setDefaultFTP(PA.getDefaultFTP())
                            MyHacker!!.setDefaultHTTP(PA.getDefaultHTTP())
                            MyHacker!!.setDefaultRedirect(PA.getDefaultShipping())
                            MyHacker!!.setCPUType(PA.getCPUType())
                            MyHacker!!.setHDType(PA.getHDType())
                            MyHacker!!.setHDMax(PA.getHDMaximum())
                            MyHacker!!.setHDQuantity(PA.getHDQuantity())
                            MyHacker!!.setMemoryType(PA.getMemoryType())
                            MyHacker!!.setHackOMeter(PA.getHackCount())
                            MyHacker!!.setVoteOMeter(PA.getVoteCount())
                            MyHacker!!.setServerLoad(PA.getServerLoad())
                            MyHacker!!.setCommodities(PA.getCommodities())
                            MyHacker!!.getStatsPanel().getCPULoadIcon().setLoad(PA.getCPUCost().toInt().toFloat())
                            MyHacker!!.getStatsPanel().getCPULoadIcon().setTotalLoad(PA.getCPUMax().toInt().toFloat())
                            //System.out.println("Heal Discount: "+PA.getHealDiscount());
                            MyHacker!!.setHealDiscount(PA.getHealDiscount())
                            MyHacker!!.setVotesLeft(PA.getVotesLeft())

                            if (PA.getPreferences() != null) {
                                MyHacker!!.setPreferences(PA.getPreferences())
                            }

                            if (PA.getCountDown() != -1) {
                                //System.out.println("Start Countdown -- View");
                                MyHacker!!.setCountDown(PA.getCountDown())
                            }
                            if (PA.getMessages().size != 0) {
                                val messages = PA.getMessages()
                                //System.out.println("Message Received");
                                for (ii in messages!!.indices) MyHacker!!.showMessage(messages[ii])
                            }
                            if (PA.getPacketPorts() != null) {
                                //System.out.println("GOT PORTS?");
                                MyHacker!!.setPorts(PA.getPacketPorts())
                            }

                            if (PA.getDirectory() != null) {
                                //System.out.println(MyHacker.getCurrentFolder());
                                //System.out.println("received primary");
                                val dir = PA.getDirectory()
                                MyHacker!!.receivedDirectory(dir)
                            }
                            if (PA.getSecondaryDirectory() != null) {
                                //System.out.println("received secondary directory");
                                MyHacker!!.receivedSecondaryDirectory(PA.getSecondaryDirectory(), PA.getAllowedDir())
                            }
                            if (PA.getFile() != null) {
                                val HF = PA.getFile()
                                //System.out.println("Received File from server: "+HF.getName());
                                MyHacker!!.receivedFile(HF)
                            }
                            if (PA.getBody() != null) {
                                //System.out.println("Title not null");
                                MyHacker!!.receivedPage(PA.getTitle(), PA.getBody())
                            }
                            if (PA.getScannedPorts() != null) {
                                //System.out.println("Received Scan");
                                MyHacker!!.receivedScan(PA.getScannedPorts())
                            }
                            if (PA.getPacketWatches() != null) {
                                MyHacker!!.receivedWatches(PA.getPacketWatches())
                            }
                            if (PA.requestPrimary()) {
                                val reqDir = MyHacker!!.getRequestedDirectory()
                                val objects: Array<Any?>? =
                                    arrayOf<Any?>(MyHacker!!.getEncryptedIP(), MyHacker!!.getCurrentFolder())
                                if (reqDir != Hacker.BROWSER && reqDir != Hacker.EQUIPMENT) {
                                    addFunctionCall(
                                        RemoteFunctionCall(
                                            PA.getRequestPrimaryID(),
                                            "requestdirectory",
                                            objects
                                        )
                                    )
                                } else if (reqDir == Hacker.EQUIPMENT) {
                                    addFunctionCall(RemoteFunctionCall(Hacker.EQUIPMENT, "requestequipment", objects))
                                }
                            }
                            if (PA.getRequestHardware()) {
                                val objects: Array<Any?>? = arrayOf<Any?>(MyHacker!!.getEncryptedIP())
                                MyHacker!!.setRequestedDirectory(Hacker.EQUIPMENT)
                                addFunctionCall(RemoteFunctionCall(Hacker.EQUIPMENT, "requestequipment", objects))
                            }
                            if (PA.requestSecondary()) {
                                //System.out.println("Request Secondary - "+MyHacker.getFTPIP());
                                val objects: Array<Any?>? = arrayOf<Any?>(
                                    MyHacker!!.getFTPIP(),
                                    MyHacker!!.getSecondaryFolder(),
                                    MyHacker!!.getEncryptedIP(),
                                    MyHacker!!.getFTPPort()
                                )
                                addFunctionCall(RemoteFunctionCall(Hacker.FTP, "requestsecondarydirectory", objects))
                            }
                            if (PA.getChoices().size != 0) {
                                MyHacker!!.showChoices(PA.getChoices())
                            }
                            if (PA.getPeakCode() != null) {
                                //System.out.println("Peeking at Code");
                                MyHacker!!.showCode(PA.getPeakCode())
                            }
                            if (PA.getLogUpdate() != null) {
                                MyHacker!!.receivedLogUpdate(PA.getLogUpdate())
                            }
                            if (PA.getCAPTCHA() != null) {
                                //System.out.println("Captcha!");
                                val BI = BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)
                                BI.setRGB(0, 0, 175, 45, PA.getCAPTCHA() as IntArray?, 0, 175)
                                val MD = MacroDialog(BI, MyHacker)
                            }

                            if (PA.getPacketNetwork() != null) {
                                MyHacker!!.setNetwork(PA.getPacketNetwork())
                            }
                        } else if (packets[j] is DamageAssignment) {
                            val DA = packets.get(j) as DamageAssignment
                            packets.set(j, null)

                            MyHacker!!.setAttackXP(DA.getAttackXP())
                            MyHacker!!.setMerchantingXP(DA.getMerchantXP())
                            MyHacker!!.setWatchXP(DA.getWatchXP())
                            MyHacker!!.setScanXP(DA.getScanningXP())
                            MyHacker!!.setFirewallXP(DA.getFireWallXP())
                            MyHacker!!.setHTTPXP(DA.getHTTPXP())
                            MyHacker!!.setRedirectXP(DA.getRedirectXP())
                            MyHacker!!.setRepairXP(DA.getRepairXP())
                            MyHacker!!.getStatsPanel().getCPULoadIcon().setLoad(DA.getCPUCost().toInt().toFloat())
                            //System.out.println("DAMAGE CPU COST: "+DA.getCPUCost());
                            MyHacker!!.setHealth(DA.getHealthUpdates())
                            DA.damage?.forEach {
                                decodeDamageEvent(it)?.let {
                                    when (it) {
                                        is DamageEvent.Attack -> MyHacker?.showAttackMessage(
                                            it.amount,
                                            it.windowHandle,
                                            it.firewall,
                                            it.mining
                                        )

                                        is DamageEvent.Zombie -> MyHacker?.showZombieMessage(
                                            it.amount,
                                            it.windowHandle,
                                            it.ip
                                        )

                                        is DamageEvent.Attacking -> MyHacker?.setAttacking(it.windowHandle, it.attacking)
                                    }
                                }
                            }
                        }
                    }

                    //Remove processed packets.
                    val PacketIterator: MutableIterator<*> = packets.iterator()
                    while (PacketIterator.hasNext()) {
                        if (PacketIterator.next() == null) PacketIterator.remove()
                    }
                }
            }
            if (MyHacker != null) {
                try {
                    val AMI = MyHacker!!.getChatController().popMessages()
                    if (AMI != null) {
                        //System.out.println("Sending Message");
                        if (!offline) {
                            chatR!!.addFinishedAssignment(MessageInPacket(AMI))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (MyTime.getCurrentTime() - lastPing > PINGTIME) {
                    lastPing = MyTime.getCurrentTime()
                    //System.out.println("PINGING - "+user);
                    if (user != null) {
                        R!!.addFinishedAssignment(PingAssignment(0, user))
                        if (!offline) {
                            chatR!!.addFinishedAssignment(PingAssignment(0, username!!.lowercase(Locale.getDefault())))
                        }
                    }
                    //System.out.println("PINGING");
                }
            }
            if (MyTime.getCurrentTime() - lastPingSuccess > PINGTIMEOUT) {
                if (open) {
                    println("Reconnecting")
                    lastPingSuccess = MyTime.getCurrentTime()
                    try {
                        println("Attempt reconnect.")
                        if (R != null) {
                            println("Calling R.clean()")
                            R!!.clean()
                        }
                    } catch (e: Exception) {
                    }
                    try {
                        if (chatR != null) chatR!!.clean()
                    } catch (e: Exception) {
                        //	e.printStackTrace();
                    }

                    reconnect = true
                    reconnect()
                }
            }
        }
    }

    companion object {
        /** ///////////////////// */ // Data.
        private val LOGIN_FALLBACK_START =
            !"false".equals(getPropertySafe("hackwars.loginFallbackStart", "true"), ignoreCase = true)
        var lastPingSuccess: Long = 0
        const val PINGTIMEOUT: Int = 40000
        const val PINGTIME: Int = 15000
        var instrument: Int = 0
        private fun getPropertySafe(key: String, fallback: String?): String? {
            try {
                return System.getProperty(key, fallback)
            } catch (e: SecurityException) {
                return fallback
            }
        }

        //Testing main.
        private fun getLaunchArguments(args: Array<String?>?): Array<String?>? {
            if (args != null && args.size >= 4) {
                return (arrayOf(args[0], args[1], args[2], args[3]))
            }
            if (GraphicsEnvironment.isHeadless()) {
                println("Usage: java View/View <server-host> <username> <password> <player-ip> [offline]")
                return (null)
            }
            val hostField = JTextField("127.0.0.1")
            val userField = JTextField("localuser")
            val passField = JPasswordField("localpass")
            val ipField = JTextField("192.168.2.002")
            val message = arrayOf<Any>(
                "Server Host:", hostField,
                "Username:", userField,
                "Password:", passField,
                "Player IP:", ipField
            )
            val option = JOptionPane.showConfirmDialog(
                null,
                message,
                "HackWars Launcher",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            )
            if (option != JOptionPane.OK_OPTION) {
                return (null)
            }
            val host = hostField.getText().trim { it <= ' ' }
            val user = userField.getText().trim { it <= ' ' }
            val pass = String(passField.getPassword())
            val playerIP = ipField.getText().trim { it <= ' ' }
            if (host == "" || user == "" || playerIP == "") {
                JOptionPane.showMessageDialog(null, "Server host, username, and player IP are required.")
                return (null)
            }
            return (arrayOf(host, user, pass, playerIP))
        }
    }
}