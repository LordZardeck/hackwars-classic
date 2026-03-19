package game

import assignments.DamageAssignment
import assignments.LoginFailedAssignment
import assignments.LoginSuccessAssignment
import assignments.PacketAssignment
import com.hackwars.game.functions.*
import com.hackwars.game.functions.Function
import com.hackwars.game.program.AttackProgram
import com.hackwars.game.program.ShippingProgram
import game.computer.dispatch.CommandDispatcher
import game.computer.dispatch.CommandRegistry
import game.computer.packet.*
import game.computer.persistence.XmlComputerPersistence
import game.computer.runtime.*
import game.computer.runtime.RuntimeTickEventApplier.apply
import game.computer.session.ComputerSessionService
import game.computer.session.LoginRequest
import game.computer.session.PlayStatisticsRequest
import game.runchallenge.ChallengeRunner
import hackscript.model.Variable
import org.w3c.dom.Node
import util.LoadXML
import util.Time
import view.Task
import java.io.BufferedWriter
import java.io.FileWriter
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.math.max

/**
 * Computer.kt
 * 
 * Description: This class represents most of the data about a player, and handles loading/parsing/saving their XML file. It forks off incoming information
 * to other classes, such as ports, watches, and applications. It has the main threaded event loop that is
 * used to process incoming operations from an individual player/someone interacting with their account.
 * This class is a beast, know it well lest ye be bitten.
 */

open class Computer : Runnable {
    var MAX_OPS: Int = 4096

    @JvmField
    var FILE_SIZE_LIMIT: Int = 60000

    //heal limit
    @JvmField
    var HEAL_LIMIT: Int = 9

    //NOOB Safety.
    private val noobLevel = 30

    //SQL CONNECTION INFO.
    private val Connection = "127.0.0.1"
    private val DB = "hackerforum"
    private val Username = "root"
    private val Connection2 = "127.0.0.1"
    private val DB2 = "hackwars_drupal"
    private val Username2 = "root"
    private val Password2 = ""
    @JvmField
    var MyEquipmentSheet: EquipmentSheet =
        EquipmentSheet(this) //Keeps track of equipment currently installed and other such things.
    var MyNewFireWall: NewFireWall? =
        null //new NewFireWall(); // because I hate static variables, cause they hate me.  Used to generate firewalls.

    var xpTable: IntArray = IntArray(100) //Table of XP per level.
    @JvmField
    var lastChangeNetwork: Long = 0 //Keep track of the last time the player changed networks.
    var lastAttack: Long = 0 //At what time did an attack last take place.
    var healCounter: Long = 0 //Used to decide how frequently a port should heal based on Mod.
    var lastSent: Long = 0 //Last time that a packet was sent.
    @JvmField
    var lastPingTime: Long = 0
    var logInTime: Long = 0
    var lastClientPacketTime: Long = 0
    val COMPUTER_TIMEOUT: Long =
        (1400000 - (700000 * Math.random()).toInt() //How long before we re-write the computer to disk.
                ).toLong()

    val AUTO_SAVE: Long = (1200000 - (600000 * Math.random()).toInt() //How often should we save the profile?	
            ).toLong()

    @JvmField
    var type: Int = 0 //Is this an NPC or player?
    var dailyPaySize: Float = 1000f //How much do you make a day?
    @JvmField
    var dailyPayReduction: Float = 1.0f //Percent value that indicates how much daily pay should be reduced.
    @JvmField
    var respawnMoney: Float = 0f //How much money does an NPC get when they respawn.
    var maximumPettyCash: Float =
        0f //For some NPCs we want to limit the cash in their petty, so that they can't be robbed for tons.
    var loggedIn: Boolean = false //whether the player has logged in, or was accessed.
    var gateway: Boolean = false
    @get:JvmName("getFileIOValue")
    @set:JvmName("setFileIOValue")
    var FileIO: Boolean = true
    var upgradedAccount: Boolean = false
    var inactive: Boolean = false
    var RecentQuestFinishers: HashMap<Any?, Any?> = HashMap() //Players who've recently finished quests.

    var lastSave: Long = 0 //When was the last time that the profile was saved.
    @JvmField
    var systemChange: Boolean = true //Has the system changed since we last sent a packet.
    @JvmField
    var healthChange: Boolean = true //Should an update be given regarding the player's current port healths?
    var countDown: Boolean = false //Is a count down currently taking place?.
    var countDownStart: Long = 0 //When did the countdown start?

    //Used to manage errors when the occur during load time.
    var LOAD_FAILURE: Boolean = false //The XML file Failed To Load.
    var LOGOUT: Boolean = false //Has a player requested that they be logged out.
    var errorMessage: String = "" //An error message to report back to the player.
    @get:JvmName("getLoadRequesterValue")
    @set:JvmName("setLoadRequesterValue")
    var loadRequester: String = "" //The IP of the individual who requested that this computer be loaded.

    var lastAccessed: Long = 0 //When was the computer last accessed?
    var lastPaid: Long = 0 //When was the last time this player recieved their daily money.
    @get:JvmName("getOverheatStartValue")
    @set:JvmName("setOverheatStartValue")
    var overheatStart: Long = -1 //Keep track of when an overheat started.
    lateinit var MyTime: Time //Central time keeping thread.
    @JvmField
    var MyThread: Thread? = null //The thread associated with this class.

    var GUI_READY: Boolean =
        false //This variable is used by the 3D chat to determine whether the GUI is in a state ready to start receiving walking packets.
    @get:JvmName("getLoadingValue")
    @set:JvmName("setLoadingValue")
    var Loading: Boolean = false //Is the computer currently loading.
    @get:JvmName("getLoadedValue")
    @set:JvmName("setLoadedValue")
    var Loaded: Boolean = false //Has the computer started loading.
    @JvmField
    var run: Boolean = true //Used to set whether this computer's thread is running.
    var LOG_UPDATE: Boolean = false //Has the player's DB been updated?
    @JvmField
    var locked: Boolean = false //Has the account been locked down?
    @JvmField
    var lockCount: Int = 0
    @JvmField
    var unlockKey: String = "" //What key will unlock the account.

    //Ports on the computer.
    @JvmField
    var Ports: HashMap<Any?, Any?> = HashMap()

    //Information about Computer.
    @JvmField
    var ip: String = "" //IP Address of this computer.
    @JvmField
    var userName: String? = null //Username associated with this computer.
    @get:JvmName("getPasswordValue")
    @set:JvmName("setPasswordValue")
    var password: String? = null //FTP password for this computer.
    var successfulHacks: Int = 0 //Number of successful hacks that this player has performed.
    @JvmField
    var pageBody: String = "" //Body of personal webpage.
    @JvmField
    var pageTitle: String = "" //Title of personal webpage.
    @JvmField
    var adRevenueTarget: String = "" //Target that daily pay should be placed in (may be malicious).
    @JvmField
    var storeRevenueTarget: String = "" //Target that daily store revenue should be placed in (may be malicious).
    @JvmField
    var lastBountyHTTP: String = "" //Keeps track of the last person to take over the daily pay of this computer.

    @JvmField
    var pageChanged: Boolean = false //Has the page changed since last output to file system?
    var votes: Int = 0 //How many votes does the player currently have.
    var operationCount: Int = 0 //How many operations has a player performed since they last logged in?

    //Improved Network and Quest Functionality.
    @JvmField
    var CurrentQuests: HashMap<Any?, Any?> = HashMap()
    @JvmField
    var CompletedQuests: java.util.ArrayList<Any?> = java.util.ArrayList()
    @JvmField
    var InvolvedQuests: java.util.ArrayList<Any?> = java.util.ArrayList()
    @JvmField
    var network: String = Network.ROOT_NETWORK //Keeps track of the network that this NPC is currently on.
    @JvmField
    var AllowedNetworks: java.util.ArrayList<Any?> =
        java.util.ArrayList() //The networks a player is allowed access to.
    lateinit var MyFileSystem: FileSystem //The file system used for hack wars.
    var MyMakeClue: MakeClue? = null //The class for generating and checking clues.
    @JvmField
    var MyMakeBounty: MakeBounty? = null //Used for handling bounties.

    //Current tasks that have been sent in for this computer to perform.
    val available: Semaphore = Semaphore(1, true) //Make it thread safe.
    @JvmField
    var Tasks: java.util.ArrayList<Any?> = java.util.ArrayList() //The array of tasks.

    //Array of messages since last packet.
    @JvmField
    var Messages: java.util.ArrayList<Any?> = java.util.ArrayList()

    //Array list of damage updates.
    @JvmField
    var Damage: java.util.ArrayList<Any?> = java.util.ArrayList()

    //Array list of show choices requests from finalized attacks.
    @JvmField
    var Choices: java.util.ArrayList<Any?> = java.util.ArrayList()

    //An instance of the central server used for communicating with client.
    var MyHackerServer: HackerServerBridge? = null
    @JvmField
    var connectionID: Int = -1 //ID of this client connection.
    @JvmField
    var PA: PacketAssignment = PacketAssignment(0) //The current packet assignment we're building.
    var DA: DamageAssignment = DamageAssignment(0) //The current damage assignment we're building.

    //The parent Computer Handler that tasks can be dispatched to.
    lateinit var MyComputerHandler: NetworkSwitch
    var RawComputerHandler: ComputerHandler? = null

    //Handle the watches installed on this computer.
    lateinit var MyWatchHandler: WatchHandler

    @JvmField
    var Stats: HashMap<Any?, Any?> = HashMap() //Player statistics are stored in a hash map.
    var LogMessages: java.util.ArrayList<Any?> = java.util.ArrayList() //Allow players to save messages to their 'DB'.
    var Globals: java.util.ArrayList<Any?> = java.util.ArrayList() //Allow players to maintain global variables.

    @JvmField
    var cputype: Int = 0 //What type of CPU is installed on this computer.
    @JvmField
    var memorytype: Int = 0 //What type of Memory is installed on this computer.

    @JvmField
    var pettyCash: Float = 0.0f //Money in petty cash.
    @JvmField
    var bankMoney: Float = 0.0f //Money in bank.
    var currentCPU: Float = 0.0f //The current CPU load.
    var reportCPU: Float = 0.0f //The CPU load reported to the player.
    var baseCPU: Float = 0.0f
    var currentWatchCost: Float = 0.0f //The cost associated with the watches that are currently active.
    @JvmField
    var myVotes: Int = 0 //How many votes do you have to use on websites you like.
    @get:JvmName("getVoteCountValue")
    @set:JvmName("setVoteCountValue")
    var voteCount: Int = 0 //How many times has your site been voted for.

    //The new commodity banks and pettys.
    @JvmField
    var store: String = ""
    @JvmField
    var repairXP: FloatArray = floatArrayOf(15.0f, 30.0f, 60.0f, 120.0f, 240.0f)
    @JvmField
    var commodityAmount: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    var commodityRespawn: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)

    //Default ports.
    @JvmField
    var defaultBank: Int = 0
    @JvmField
    var defaultAttack: Int = 0
    @JvmField
    var defaultFTP: Int = 0
    @JvmField
    var defaultHTTP: Int = 0
    @JvmField
    var defaultShipping: Int = 0
    var profile: String? = null

    @get:JvmName("getRepairedValue")
    @set:JvmName("setRepairedValue")
    var repaired: Boolean = false

    //Drop Table info.
    @JvmField
    var MyDropTable: DropTable? = null
    @JvmField
    var dropTable: Int = 1
    var lastDrop: HackerFile? = null

    //This hashmap contains all the virtual functions run within the run function.
    var functions: HashMap<Any?, Any?> = HashMap()

    // this hashmap contains the user's preferences
    @JvmField
    var preferences: HashMap<Any?, Any?>? = null
    var sendPreferences: Boolean = false

    //MessageHandler
    @JvmField
    var messageHandler: MessageHandler = MessageHandler(this)

    @JvmField
    val sessionService: ComputerSessionService = ComputerSessionService()
    private val xmlComputerPersistence = XmlComputerPersistence()
    private val persistenceSupport = LegacyComputerPersistenceSupport(xmlComputerPersistence)
    private val loadCoordinator = ComputerLoadCoordinator(sessionService, xmlComputerPersistence, persistenceSupport)
    private val standardPacketBuilder = ComputerPacketBuilder()
    private val damagePacketBuilder = ComputerDamagePacketBuilder()
    private val runtimeCoordinator = ComputerRuntimeCoordinator()
    private var commandDispatcher: CommandDispatcher? = null

    var sentOverHeatedMessage: Boolean = false

    open val stats: HashMap<Any?, Any?>
        get() = Stats

    open val ports: HashMap<Any?, Any?>
        get() = Ports

    open val currentQuests: HashMap<Any?, Any?>
        get() = CurrentQuests

    open var cpuType: Int
        get() = cputype
        set(value) {
            cputype = value
        }

    open var cpuLoad: Float
        get() = currentCPU
        set(value) {
            currentCPU = value
        }

    /**
     * This function builds up the initial HashMap of functions.
     */
    fun buildFunctionHash() {
        val self = this
        functions = HashMap<Any?, Any?>()
        functions!!.put("deletelogs", DeleteLogs(self))
        functions!!.put("requestftpupdate", RequestFTPUpdate(self))
        functions!!.put("requestzombieattack", RequestZombieAttack(self))
        functions!!.put("requestattackdefault", RequestAttackDefault(self))
        functions!!.put("addshowchoices", AddShowChoices(self))
        functions!!.put("bankxp", BankXP(self))
        functions!!.put("deletefolder", DeleteFolder(self))
        functions!!.put("createfolder", CreateFolder(self))
        functions!!.put("code", SetCode(self))
        functions!!.put("dochallenge", DoChallenge(self))
        functions!!.put("redirectxp", RedirectXP(self))
        functions!!.put("repairxp", RepairXP(self))
        functions!!.put("watchxp", WatchXP(self))
        functions!!.put("httpxp", HttpXP(self))
        functions!!.put("scanxp", ScanXP(self))
        functions!!.put("setftppassword", SetFTPPassword(self))
        functions!!.put("setdefaultport", SetDefaultPort(self))
        functions!!.put("requesttrigger", RequestTrigger(self))
        functions!!.put("requesttriggernote", RequestTriggerNote(self))
        functions!!.put("requestsave", RequestSave(self))
        functions!!.put("requesttask", RequestTask(self))
        functions!!.put("setdummyport", SetDummyPort(self))
        functions!!.put("portonoff", PortOnOff(self))
        functions!!.put("saveportnote", SavePortNote(self))
        functions!!.put("uninstallport", UninstallPort(self))
        functions!!.put("changewatchport", ChangeWatchPort(self))
        functions!!.put("launchNetworkAttack", LaunchNetworkAttack(self))
        @Suppress("UNCHECKED_CAST")
        commandDispatcher = CommandRegistry.Companion.fromFunctions(functions as Map<String, Function>)
    }

    /**
     * Return the current daily pay reduction value.
     */
    fun getDailyPayReduction(): Float {
        return (dailyPayReduction)
    }

    fun setDailyPayReduction(dailyPayReduction: Float) {
        this.dailyPayReduction = dailyPayReduction
    }

    /**
     * Returns whether or not this account is an NPC.
     */
    fun isNPC(): Boolean {
        if (type == NPC) {
            return true
        }
        return false
    }

    /**
     * Add recent quest finishes.
     */
    fun addRecentQuestFinisher(ip: String?) {
        RecentQuestFinishers.put(ip, "true")
    }

    /**
     * Check whether a player has recently finished a quest.
     */
    fun checkRecentQuestFinisher(ip: String?): Boolean {
        if (RecentQuestFinishers.get(ip) == null) return (false)
        return (true)
    }

    val cPUType: Int
        /**
         * Get the type of cpu currently installed on this computer.
         */
        get() = (cputype)

    /**
     * Get the number of votes that a player currently has.
     */
    open fun getVoteCount(): Int {
        return (voteCount)
    }

    open fun setVoteCount(voteCount: Int) {
        this.voteCount = voteCount
    }

    val noobSafety: Int
        /**
         * Get the noob safety protection.
         */
        get() = (noobLevel)

    open val showChoicesArray: ArrayList<Any?>
        /**
         * Get the array that is used to indicate the showChoices boxes that should be shown.
         * There may be more than one if multiple attacks are running, hence the array.
         */
        get() = Choices

    /**
     * Set the ad revenue target.
     */
    fun setAdRevenueTarget(adRevenueTarget: String) {
        this.adRevenueTarget = adRevenueTarget
    }

    /**
     * Return the HashMap of quests a player is currently participating in.
     */
    /**
     * Get the current target of website revenue.
     */
    fun getAdRevenueTarget(): String? {
        return (adRevenueTarget)
    }

    open val packetAssignment: PacketAssignment
        /**
         * Get the main packet being built up since the last time a packet was sent updating the client.
         */
        get() = PA

    val isUnderAttack: Boolean
        /**
         * Whether the computer is under attack or not
         */
        get() {
            val ports = Ports.values.toTypedArray()
            for (i in ports.indices) {
                val port = ports[i] as Port
                val accessing = port.getAccessing()
                //System.out.println(port.getNumber()+": "+accessing);
                if (accessing != "") {
                    return (true)
                }
            }
            return (false)
        }

    val maximumWatches: Int
        /**
         * Get the maximum watches.
         */
        get() = (WATCH_CHART!![memorytype] + MyEquipmentSheet.getWatchBonus())

    val maximumWatchesNoBonus: Int
        get() = (WATCH_CHART!![memorytype])

    /**
     * Update a global variable.
     */
    fun setGlobal(index: Int, data: Any?) {
        if (index < 20) {
            Globals.set(index, data)
        }
    }

    /**
     * Is this player a member?
     */
    fun getFileIO(): Boolean {
        return (FileIO)
    }

    /**
     * Return a global variable.
     */
    fun getGlobal(index: Int): Variable? {
        if (index < 20) {
            return (Globals.get(index) as Variable?)
        }
        return (null)
    }

    /**
     * Reset the player's logs.
     */
    fun resetLogs() {
        LogMessages = java.util.ArrayList<Any?>()
        LogMessages!!.add(arrayOf<String>("", ""))
        LOG_UPDATE = true
    }

    val logs: String
        /**
         * Return a string representation of the logs.
         */
        get() {
            if (LogMessages == null) return ("")
            var returnMe = ""
            for (i in LogMessages!!.indices) {
                val temp = (LogMessages!!.get(i) as Array<String>?)!![0]
                if (temp != "null") {
                    returnMe += temp + "\n"
                }
            }
            return (returnMe)
        }

    val storeIP: String?
        /**
         * Get the current store IP for the network the player is on.
         */
        get() = (store)

    /**
     * Edit the logs associated with this account.
     */
    fun editLogs(data: String?, replace: String) {
        var data = data
        var replace = replace
        replace = replace.replace("\\\\".toRegex(), "\\\\\\\\")
        replace = replace.replace("\\$".toRegex(), "\\\\\\$")
        data = HackerLinker.regexEscape(data)

        for (i in LogMessages!!.indices) {
            val content = LogMessages!!.get(i) as Array<String?>?
            if (content != null) {
                content[0] = content[0]!!.replace(data.toRegex(), replace)
                LogMessages!!.set(i, content)
            }
        }
        LOG_UPDATE = true
        sendPacket()
    }

    /**
     * Allow player's to save messages to their 'DB'.
     */
    fun logMessage(message: String?, ip: String?, timestamp: Long) {
        val c = Calendar.getInstance()
        if (timestamp != -1L) {
            c.setTimeInMillis(timestamp)
        }
        val month = c.get(Calendar.MONTH)
        var monthString = "Dec"
        if (month == Calendar.JANUARY) monthString = "Jan"
        if (month == Calendar.FEBRUARY) monthString = "Feb"
        if (month == Calendar.MARCH) monthString = "Mar"
        if (month == Calendar.APRIL) monthString = "Apr"
        if (month == Calendar.MAY) monthString = "May"
        if (month == Calendar.JUNE) monthString = "Jun"
        if (month == Calendar.JULY) monthString = "Jul"
        if (month == Calendar.AUGUST) monthString = "Aug"
        if (month == Calendar.SEPTEMBER) monthString = "Sep"
        if (month == Calendar.OCTOBER) monthString = "Oct"
        if (month == Calendar.NOVEMBER) monthString = "Nov"
        if (month == Calendar.DECEMBER) monthString = "Dec"

        val dayOfMonth = c.get(Calendar.DAY_OF_MONTH)
        val year = c.get(Calendar.YEAR)

        var hour = c.get(Calendar.HOUR)
        val minute = c.get(Calendar.MINUTE)
        val seconds = c.get(Calendar.SECOND)
        val ampm = c.get(Calendar.AM_PM)
        var pm = "PM"
        val second: String?
        val minutes: String?
        if (seconds < 10) second = "0" + seconds
        else second = "" + seconds
        if (minute < 10) minutes = "0" + minute
        else minutes = "" + minute
        if (ampm == 0) pm = "AM"

        if (hour == 0) hour = 12

        val stamp =
            dayOfMonth.toString() + "-" + monthString + "-" + year + " (" + hour + ":" + minutes + ":" + second + " " + pm + ")"
        LogMessages!!.add(arrayOf<String?>(stamp + " " + message, ip))
        if (LogMessages!!.size > 50) LogMessages!!.removeAt(0)

        LOG_UPDATE = true
        sendPacket()
    }

    /**
     * Deletes all the logs corresponding to a specific IP.
     */
    fun deleteLogs(ip: String?) {
        val LogIterator = LogMessages!!.iterator()
        while (LogIterator.hasNext()) {
            val S = LogIterator.next() as Array<String?>
            if (S[1] == ip) LogIterator.remove()
        }

        try {
            val params = arrayOf<Any?>(ip, this.ip)
            sessionService.executeRemote("http://www.hackwars.net/xmlrpc/facebook.php", "deleteLogs", params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        LOG_UPDATE = true
        sendPacket()
    }

    val lastBountyHTTPIP: String?
        /**
         * Returns the IP of the last player ot take over this computer's HTTP as
         * part of a bounty.
         */
        get() = (lastBountyHTTP)

    open val equipmentSheet: EquipmentSheet
        /**
         * Return the equipment sheet for use by other aspects of the computer.
         */
        get() = MyEquipmentSheet

    val newFireWall: NewFireWall?
        get() = (MyNewFireWall)

    /**
     * Get whether or not the overheat timeout has finished.
     */
    fun getOverheatStart(): Long {
        return (overheatStart)
    }

    /**
     * Set the player's hash.
     */
    private var clientHash = ""
    fun setClientHash(clientHash: String) {
        this.clientHash = clientHash
    }

    fun getClientHash(): String? {
        return (clientHash)
    }

    /**
     * Set the player's public key.
     */
    @get:JvmName("getPublicKeyValue")
    @set:JvmName("setPublicKeyValue")
    var publicKey: ByteArray? = null
    fun setPublicKey(publicKey: ByteArray?) {
        this.publicKey = publicKey
    }

    fun getPassword(): String {
        return password ?: ""
    }

    fun setPassword(password: String?) {
        this.password = password
    }

    /**
     * Respawn the NPC character.
     */
    fun respawn(HackType: Int) {
        if (type == NPC) {
            if (MyDropTable == null) MyDropTable = DropTable(dropTable, this)

            if (HackType == Port.BANKING) {
                val rgen = Random()
                val respawnAmount =
                    respawnMoney + rgen.nextFloat() * (2 * (respawnMoney * 0.25f)) - (respawnMoney * 0.25f)
                pettyCash = respawnAmount
                //pettyCash=respawnMoney;
            } else if (HackType == Port.FTP) {
                val HF = MyDropTable!!.generateDrop()
                val Parameter: Array<Any?>? = arrayOf<Any?>("Public/", HF)
                HF.setLocation("Public/")
                MyComputerHandler!!.addData(ApplicationData("savefile", Parameter, 0, ip), ip)
            }
        }
    }

    /**
     * Return the last dropped file.
     */
    fun getDrop(): HackerFile? {
        return lastDrop
    }

    /**
     * Set the last dropped file.
     */
    fun setDrop(file: HackerFile?) {
        lastDrop = file
    }

    /**
     * returns the drop table associated with this computer.
     */
    fun getDropTable(): DropTable {
        if (MyDropTable == null) MyDropTable = DropTable(dropTable, this)
        return (MyDropTable!!)
    }

    /**
     * Return the computer handler associated with this computer.
     */
    open val computerHandler: NetworkSwitch
        get() = MyComputerHandler

    /**
     * Get the type of computer (NPC, or Player)
     */
    open fun getType(): Int {
        return (type)
    }

    /**
     * Start count down.
     */
    fun startCountDown() {
        systemChange = true
        countDown = true
        countDownStart = MyTime!!.getCurrentTime()
    }

    val currentTime: Long
        /**
         * Get the current time.
         */
        get() = (MyTime!!.getCurrentTime())

    val serverID: String?
        /**
         * Get the server id associated with this computer.
         */
        get() = (MyHackerServer!!.getServerID())

    val watchLevel: Float
        /**
         * Get the watch level of the player.
         */
        get() = (getLevel(getStatXP("Watch"))).toFloat()

    val attackLevel: Float
        /**
         * Get the attack level of the player.
         */
        get() = (getLevel(getStatXP("Attack"))).toFloat()

    val bankLevel: Float
        /**
         * Get the attack level of the player.
         */
        get() = (getLevel(getStatXP("Bank"))).toFloat()

    val scanningLevel: Float
        /**
         * Get the attack level of the player.
         */
        get() = (getLevel(getStatXP("Scanning"))).toFloat()

    val fireWallLevel: Float
        /**
         * Get the attack level of the player.
         */
        get() = (getLevel(getStatXP("FireWall"))).toFloat()

    val hTTPLevel: Float
        /**
         * Get the HTTP level of the player.
         */
        get() = (getLevel(getStatXP("Webdesign"))).toFloat()

    val redirectingLevel: Float
        /**
         * Get the Redirecting level of the player.
         */
        get() = (getLevel(getStatXP("Redirecting"))).toFloat()

    val repairLevel: Float
        /**
         * Get the repair level of the player.
         */
        get() = (getLevel(getStatXP("Repair"))).toFloat()

    private fun getStatXP(statName: String?): Float {
        val value = Stats.get(statName)
        if (value is Float) return value
        if (value is Number) return (value.toFloat())
        return (0.0f)
    }

    /**
     * Return the HashMap that is used to store a player's XP in various skills.
     */
    /**
     * Set the store revenue target.
     */
    fun setStoreRevenueTarget(storeRevenueTarget: String?) {
        this.storeRevenueTarget = storeRevenueTarget ?: ""
    }

    /**
     * Set the IP address of the individual requesting that this profile be loaded.
     */
    fun setLoadRequester(loadRequester: String) {
        this.loadRequester = loadRequester
    }

    open val watchHandler: WatchHandler
        /**
         * Get the watch handler attached to this computer.
         */
        get() = MyWatchHandler

    /**
     * Destroy all the watches installed on the given port number.
     */
    fun destroyWatches(port: Int) {
        MyWatchHandler!!.destroyWatches(port)
    }

    /**
     * Increment the number of successful hacks that a player has performed.
     */
    fun incrementSuccessfulHacks() {
        successfulHacks++
    }

    /**
     * Get the HashMap of ports installed on this computer.
     */
    val title: String
        /**
         * Get the title of the player's webpage.
         */
        get() = pageTitle

    val body: String
        /**
         * Get the body of the player's webpage.
         */
        get() = pageBody

    /**
     * Tell the computer to send a packet.
     */
    fun sendPacket() {
        systemChange = true
    }

    /**
     * Send a damage packet.
     */
    fun sendDamagePacket() {
        healthChange = true
    }

    /**
     * Get/Set whether or not a piece of hardware has been repaired during this session.
     */
    fun setRepaired(repaired: Boolean) {
        this.repaired = repaired
    }

    fun getRepaired(): Boolean {
        return (repaired)
    }

    /**
     * Get the default bank associated with this computer.
     */
    open fun getDefaultBank(): Int {
        return getPortOn(defaultBank, Port.BANKING)
    }

    /**
     * Return a port that is On if the default happens to be off.
     */
    fun getPortOn(defaultPort: Int, portType: Int): Int {
        //First use the default port value

        val checkPort = Ports.get(defaultPort) as Port?

        if (checkPort != null) {
            if (checkPort.getType() == portType && checkPort.getOn()) return defaultPort
        }

        val PortIterator = Ports.entries.iterator()
        var ii = 0
        while (PortIterator.hasNext()) {
            val TempPort = ((PortIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port
            if (TempPort.getType() == portType && TempPort.getOn() && !TempPort.getDummy()) {
                return TempPort.getNumber()
            }
            ii++
        }

        return 0
    }

    fun setDefaultBank(defaultBank: Int) {
        this.defaultBank = defaultBank
    }

    /**
     * Get the default attack port associated with this computer.
     */
    open fun getDefaultAttack(): Int {
        return getPortOn(defaultAttack, Port.ATTACK)
    }

    fun setDefaultAttack(defaultAttack: Int) {
        this.defaultAttack = defaultAttack
    }

    /**
     * Return the default port used for shipping commodities.
     */
    open fun getDefaultShipping(): Int {
        return getPortOn(defaultShipping, Port.SHIPPING)
    }

    fun setDefaultShipping(defaultShipping: Int) {
        this.defaultShipping = defaultShipping
    }

    /**
     * Get the default HTTP port associated with this computer.
     */
    open fun getDefaultHTTP(): Int {
        return getPortOn(defaultHTTP, Port.HTTP)
    }

    fun setDefaultHTTP(defaultHTTP: Int) {
        this.defaultHTTP = defaultHTTP
    }


    /**
     * Get the default FTP port associated with this computer.
     */
    open fun getDefaultFTP(): Int {
        return getPortOn(defaultFTP, Port.FTP)
    }

    fun setDefaultFTP(defaultFTP: Int) {
        this.defaultFTP = defaultFTP
    }

    /**
     * Add a message to be dispatched to the client.
     */
    fun addMessage(Message: String?) {
        if (connectionID != -1) {
            //Messages.add(Message);
            messageHandler.addMessage(arrayOf<Any?>(Message, MessageHandler.GAME_MESSAGE), null)
            systemChange = true
        }
    }

    fun addMessage(Message: Array<out Any?>?) {
        if (connectionID != -1) {
            //Messages.add(Message);
            messageHandler.addMessage(Message, null)
            systemChange = true
        }
    }

    fun addMessage(Message: String?, parameters: Array<Any?>?) {
        if (connectionID != -1) {
            //Messages.add(Message);
            messageHandler.addMessage(arrayOf<Any?>(Message, MessageHandler.GAME_MESSAGE), parameters)
            systemChange = true
        }
    }

    fun addMessage(Message: Array<out Any?>?, parameters: Array<Any?>?) {
        if (connectionID != -1) {
            //Messages.add(Message);
            messageHandler.addMessage(Message, parameters)
            systemChange = true
        }
    }

    fun addMessage(Message: Array<out Any?>, parameters: Array<Any?>?, portInfo: Array<Any?>?) {
        if (connectionID != -1) {
            //Messages.add(Message);
            messageHandler.addMessage(Message, parameters, portInfo)
            systemChange = true
        }
    }

    fun getMessages(): java.util.ArrayList<*>? {
        return (Messages)
    }

    /**
     * Returns the amount of money in the Player's petty cash.
     * This is the money that can be stollen.
     */
    open fun getPettyCash(): Float {
        return (pettyCash)
    }


    /**
     * Gets the current amount of a given commodity in this computers
     * commodity stores.
     */
    fun getCommodity(commodityType: Int): Float {
        return (commodityAmount!![commodityType])
    }

    /**
     * Respawn the commodity.
     */
    fun respawnCommodity(commodityType: Int) {
        if (type == NPC && commodityAmount!![commodityType] <= 0.0) commodityAmount!![commodityType] =
            commodityRespawn!![commodityType]
    }

    /**
     * Sets the amount of the given commodity.
     */
    fun setCommodityAmount(commodityType: Int, amount: Float) {
        this.commodityAmount!![commodityType] = amount

        if (type == NPC && amount <= 0) {
            this.commodityAmount!![commodityType] = commodityRespawn!![commodityType]
        }

        sendPacket()
    }

    /**
     * Set the amount of money in the player's petty cash.
     */
    fun setPettyCash(pettyCash: Float) {
        var pettyCash = pettyCash
        this.pettyCash = pettyCash
        if (maximumPettyCash > 0 && this.pettyCash > maximumPettyCash) this.pettyCash = maximumPettyCash
        if (pettyCash < 0) pettyCash = 0f
    }

    var bank: Float
        /**
         * Returns the amount of money in the Player's bank.
         * This is the money that can be stollen.
         */
        get() = (bankMoney)
        /**
         * Set the amount of money in the player's bank.
         */
        set(bankMoney) {
            this.bankMoney = bankMoney
        }

    val maximumCPULoad: Float
        /**
         * Return the maximum CPU load based on the current CPU installed.
         */
        get() = (CPU_CHART!![cputype] + MyEquipmentSheet.getCPUBonus())

    val maximumCPUNoBonus: Float
        get() = (CPU_CHART!![cputype])

    /**
     * Returns whether or not this is a gateway NPC.
     */
    fun isGateway(): Boolean {
        return (gateway)
    }

    /**
     * Get the network that this NPC is currently on.
     */
    fun getNetwork(): String? {
        return (network)
    }

    /**
     * Return the amount of money in the player's bank.
     * This money cannot be hacked.
     */
    fun getBankMoney(): Float {
        return (bankMoney)
    }

    val clueLevel: Int
        /**
         * Get the clue level of this computer.
         */
        get() = (0)
    //	return(clueLevel);

    val cPULoad: Float
        /**
         * Return the current CPU load of this computer.
         */
        get() = (reportCPU)

    val baseCPULoad: Float
        /**
         * Return the base CPU load before overheating is taken into account.
         */
        get() = (baseCPU)

    /**
     * Return the file system object.
     */
    open val fileSystem: FileSystem
        get() = MyFileSystem

    /**
     * Get the base amount of damage this player currently deals.
     * Type = "Attack" or "Redirecting"
     */
    fun getDamage(type: String?): Float {
        var damage = 2.0f
        val attackXP = getStatXP(type)
        var i = 0
        try {
            while ((attackXP.toInt() > xpTable!![i]) && i < 99) {
                i++
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
        }
        //damage+=(float)((i+1)/5);
        damage += ((i + 1) * 0.2f)
        return (damage)
    }

    /**
     * Get the current level of a stat based on the current XP.
     */
    fun getLevel(xp: Float): Int {
        var i = 0
        try {
            while ((xp.toInt() > xpTable!![i]) && i < 99) {
                i++
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
        }
        return (i + 1)
    }

    /**
     * Set whether this computer's thread should currently be running.
     */
    fun setRun(run: Boolean) {
        Network.getInstance(MyComputerHandler)
            .removeFromNetwork(network, ip) //Remove the player from their current network.
        this.run = run
        val thread = MyThread
        if (thread != null) {
            thread.interrupt()
        }
        MyThread = null
    }

    val makeClue: MakeClue?
        /**
         * Get the class for checking/generating clues.
         */
        get() = (MyMakeClue)

    fun getDamage(): java.util.ArrayList<Any?> {
        return (Damage)
    }

    fun returnPlayer(profile: String?) {
        this.profile = profile
    }

    /**
     * Constructor.
     */
    constructor(
        ip: String,
        MyComputerHandler: ComputerHandler?,
        MyTime: Time,
        connectionID: Int,
        MyHackerServer: HackerServerBridge?
    ) {
        val self = this
        for (i in 0..19) Globals.add(null)

        MyFileSystem = FileSystem(self) //The virtual file sytem.
        //	MyMakeClue=new MakeClue(MyFileSystem,this,clueLevel);//The clue generator/checker.
        MyMakeBounty = MakeBounty(MyFileSystem) //Used for generating bounties.


        //Create the experience table.
        var xp = 83
        var xpDiff = 83
        for (i in 0..99) {
            xpTable!![i] = xp
            xpDiff = (xpDiff + xpDiff / 9.525).toInt()
            xp += xpDiff
        }

        this.MyComputerHandler = NetworkSwitch(self, MyComputerHandler)
        this.RawComputerHandler = MyComputerHandler
        this.MyNewFireWall = NewFireWall(this.MyComputerHandler)
        this.ip = ip
        this.MyTime = MyTime
        this.connectionID = connectionID
        this.MyHackerServer = MyHackerServer

        MyWatchHandler = WatchHandler(MyComputerHandler, self)

        while (lastAccessed == 0L) this.lastAccessed = MyTime.getCurrentTime()
        MyThread = Thread(this, "Computer - " + ip)
        MyThread!!.start()
    }

    /**
     * Constructor.
     */
    constructor(
        userName: String?,
        ip: String,
        MyComputerHandler: ComputerHandler?,
        MyTime: Time,
        connectionID: Int,
        MyHackerServer: HackerServerBridge?,
        playerLogin: Boolean
    ) {
        val self = this
        for (i in 0..19) Globals.add(null)

        MyFileSystem = FileSystem(self) //The virtual file sytem.
        //	MyMakeClue=new MakeClue(MyFileSystem,this,clueLevel);//The clue generator/checker.
        MyMakeBounty = MakeBounty(MyFileSystem) //Used for generating bounties.

        this.ip = ip


        //Create the experience table.
        var xp = 83
        var xpDiff = 83
        for (i in 0..99) {
            xpTable!![i] = xp
            xpDiff = (xpDiff + xpDiff / 9.525).toInt()
            xp += xpDiff
        }

        this.MyComputerHandler = NetworkSwitch(self, MyComputerHandler)
        this.RawComputerHandler = MyComputerHandler

        this.userName = userName
        this.MyTime = MyTime
        this.connectionID = connectionID
        this.MyHackerServer = MyHackerServer

        MyWatchHandler = WatchHandler(MyComputerHandler, self)

        while (lastAccessed == 0L) this.lastAccessed = MyTime.getCurrentTime()
        MyThread = Thread(this, "Computer - " + ip)
        MyThread!!.start()
    }


    /**
     * Set the connection ID associated with this computer.
     * If a player is already logged in and reconnects this is necessary.
     */
    @JvmField
    var RESEND_CAPTCHA: Boolean = false
    fun setConnectionID(connectionID: Int, loginPassword: String) {
        try {
            available.acquire()
            RESEND_CAPTCHA = true
            this.lastAccessed = MyTime!!.getCurrentTime()
            loadRequester = ""
            Tasks.add(0, setConnectionIDTask(this, connectionID, crypt(loginPassword.toByteArray(), clientHash)))
            available.release()

            if (REMOTE_XMLRPC_ENABLED) {
                try {
                    sessionService.requestFunctionPacks(ip)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            FileIO = true
        } catch (e: Exception) {
            available.release()
            e.printStackTrace()
        }
    }

    fun setConnectionID(connectionID: Int) {
        try {
            available.acquire()
            RESEND_CAPTCHA = true
            this.lastAccessed = MyTime!!.getCurrentTime()
            loadRequester = ""
            Tasks.add(0, setConnectionIDTask(this, connectionID, null))
            available.release()
            FileIO = true
        } catch (e: Exception) {
            available.release()
            e.printStackTrace()
        }
    }

    /**
     * Set a connection ID in a threaded environment.
     */
    private inner class setConnectionIDTask(MyComputer: Computer?, connectionID: Int, loginPassword: String?) :
        Task {
        private var connectionID = -1
        private var loginPassword: String? = ""
        private var MyComputer: Computer? = null

        init {
            this.connectionID = connectionID
            this.loginPassword = loginPassword
            this.MyComputer = MyComputer
        }

        override fun execute() {
            if (loginPassword != null) {
                MyComputer!!.loginPassword = loginPassword
            }
            if (checkLogin()) {
                MyComputer!!.connectionID = connectionID //The ID used to relay data back to the client-side.

                MyHackerServer!!.removeRandomKey(ip)
                var O = MyHackerServer!!.getRandomKey(ip, clientHash, publicKey)
                val MyLoginSuccessAssignment = LoginSuccessAssignment(
                    0, ip, O!![0] as String?,
                    this@Computer.isNPC()
                )
                MyLoginSuccessAssignment.setPublicKey(O[1] as ByteArray?)
                O = arrayOf<Any>(MyLoginSuccessAssignment, connectionID)
                MyHackerServer!!.addData(O)
                //Make sure we resend the network information.
                if (network != null) {
                    Network.getInstance(MyComputerHandler).getNetworkInformation(network)
                    PA.setPacketNetwork(Network.getInstance(MyComputerHandler).getNetworkInformation(network))
                }


                //Remove and re-add a player to the 3D chat.
                GUI_READY =
                    false //Don't allow packets to send since we can't trust the GUI is ready, the first packet we receive GUI side will set this to true.

                //WorldSingleton.getInstance().invalidatePlayer("game",ip);//Add the player into the 3D chat.
                //WorldSingleton.getInstance().addPlayer("game",ip,userName,npc);//Add the player into the 3D chat.

                //Force a packet update.
                systemChange = true
                healthChange = true
                LOG_UPDATE = true
            } else {
                val O: Array<Any?>? = arrayOf<Any?>(LoginFailedAssignment(0), connectionID)
                MyHackerServer!!.addData(O)
            }
        }
    }

    /**
     * returns the ip of the computer.
     */
    open fun getIP(): String {
        return this.ip
    }

    /**
     * addData()
     * Adds a function to be processed using a semaphore into the
     * computer's processing stack.
     */
    fun addData(MyApplicationData: Any?) {
        try {
            available.acquire()
            if (Tasks.size < 50) {
                operationCount += 1 //Keep track of how many operations have been peformed while this player is logged in.
                val MAD = MyApplicationData as ApplicationData
                //We must make sure that the transactional data gets moved to the front of the list.
                var applicationData = false
                if (MyApplicationData is ApplicationData) applicationData = true


                //if(!applicationData||!locked||!((ApplicationData)MyApplicationData).getSourceIP().equals(ip)||((ApplicationData)MyApplicationData).getSource()!=ApplicationData.OUTSIDE||connectionID==-1){
                var add = true
                if (locked) {
                    val packet: Any? = clientPackets.get(MAD.getFunction())
                    if (packet != null) {
                        val count = packet as Int
                        if (count > 0) {
                            add = false
                        }
                    }
                }
                if (add) {
                    if (MAD.getFunction() == "bank" || MAD.getFunction() == "pettycash") Tasks.add(0, MyApplicationData)
                    else Tasks.add(MyApplicationData)
                }


                //}
            }
            available.release()
        } catch (e: Exception) {
            e.printStackTrace()
            available.release()
        }
    }

    /**
     * Is the computer currently loading.
     */
    fun getLoading(): Boolean {
        return (Loading)
    }

    /**
     * Is the computer completely loaded.
     */
    fun getLoaded(): Boolean {
        return (Loaded)
    }

    /**
     * Set the password associated with a player.
     */
    @get:JvmName("getLoginPasswordValue")
    @set:JvmName("setLoginPasswordValue")
    var loginPassword: String? = ""
    var playFabAuthenticated: Boolean = false
    fun setPlayFabAuthenticated(userName: String?) {
        playFabAuthenticated = true
        if (userName != null && userName.trim { it <= ' ' }.length > 0) {
            this.userName = userName
        }
    }

    fun setLoginPassword(loginPassword: String) {
        this.loginPassword = crypt(loginPassword.toByteArray(), clientHash)
    }

    /**
     * private void checkLogin(String userName,String loginPassword){
     * this.userName=userName;
     * this.loginPassword=loginPassword;
     * checkLogin();
     * } */
    /**
     * Check to make sure that username and password are correct.
     */
    fun checkLogin(): Boolean {
        val loginRequest = LoginRequest(
            ip,
            userName,
            loginPassword!!,
            playFabAuthenticated,
            ServerRuntimeState.isTesting()
        )
        val result = sessionService.authenticate(loginRequest)
        sendPreferences = result.sendPreferences
        return result.accepted
    }

    /**
     * Get the total level of a player.
     */
    fun getTotalLevel(): Int {
        var totalLevel = 0
        totalLevel += getLevel(getStatXP("Attack"))
        totalLevel += getLevel(getStatXP("Bank"))
        totalLevel += getLevel(getStatXP("Watch"))
        totalLevel += getLevel(getStatXP("Scanning"))
        totalLevel += getLevel(getStatXP("FireWall"))
        totalLevel += getLevel(getStatXP("Webdesign"))
        totalLevel += getLevel(getStatXP("Redirecting"))
        totalLevel += getLevel(getStatXP("Repair"))
        return totalLevel
    }

    /**
     * Performs a challenge XML-RPC call.
     */
    fun doChallengeRPC(challengeID: String, source: String?) {
        addMessage(MessageHandler.CHALLENGE_START, arrayOf<Any?>(challengeID))
        addMessage("-----------------------")


        //Fetch the HackerFile associated with this ID.
        var ChallengeFile: HackerFile? = null
        val ChallengeFiles = MyFileSystem!!.getFilesOfType(HackerFile.CHALLENGE)
        var Content: HashMap<*, *>? = null
        if (ChallengeFiles != null) for (i in ChallengeFiles.indices) {
            val TempFile = ChallengeFiles.get(i) as HackerFile
            Content = TempFile.getContent()
            val identifier = Content!!.get("identifier") as String?
            if (identifier != null) if (challengeID == identifier) {
                ChallengeFile = TempFile
                break
            }
        }

        if (ChallengeFile == null) { //Did we find this file.
            addMessage(MessageHandler.CHALLENGE_NO_FILE)
            return
        }

        var input = Content!!.get("input") as String
        var output = Content.get("output") as String
        val inputMultiple: Array<String>? = input.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val outputMultiple: Array<String>? = output.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val inputtype = Content.get("inputtype") as String
        val outputtype = Content.get("outputtype") as String
        val QuestID: Int =
            (Content.get("questid") as? String)?.toIntOrNull()
                ?: (Content.get("questid") as? Number)?.toInt()
                ?: -1
        val TaskName = Content.get("task") as String?

        var success = true
        var result: HashMap<*, *>? = null
        for (ii in inputMultiple!!.indices) {
            addMessage(MessageHandler.CHALLENGE_RUNNING_ATTEMPT, arrayOf<Any?>((ii + 1)))

            try {
                input = inputMultiple[ii]
                output = outputMultiple!![ii]

                var sendStringInput: Array<String> = emptyArray()
                var sendDoubleInput = arrayOfNulls<Double>(0)
                var sendIntegerInput = arrayOfNulls<Int>(0)
                var sendStringOutput: Array<String> = emptyArray()
                var sendDoubleOutput = arrayOfNulls<Double>(0)
                var sendIntegerOutput = arrayOfNulls<Int>(0)


                if (inputtype == "String") {
                    sendStringInput = input.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                }
                if (outputtype == "String") {
                    sendStringOutput = output.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                }


                if (inputtype == "float") {
                    val ss = input.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    sendDoubleInput = arrayOfNulls<Double>(ss.size)
                    for (i in ss.indices) {
                        sendDoubleInput[i] = ss[i].toDouble()
                    }
                }
                if (outputtype == "float") {
                    val ss = output.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    sendDoubleOutput = arrayOfNulls<Double>(ss.size)
                    for (i in ss.indices) {
                        sendDoubleOutput[i] = ss[i].toDouble()
                    }
                }

                if (inputtype == "int") {
                    val ss = input.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    sendIntegerInput = arrayOfNulls<Int>(ss.size)
                    for (i in ss.indices) {
                        sendIntegerInput[i] = ss[i].toInt()
                    }
                }
                if (outputtype == "int") {
                    val ss = output.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    sendIntegerOutput = arrayOfNulls<Int>(ss.size)
                    for (i in ss.indices) {
                        sendIntegerOutput[i] = ss[i].toInt()
                    }
                }

                result = ChallengeRunner.getInstance().runToyProblem(
                    source,
                    3000,
                    sendDoubleInput,
                    sendStringInput,
                    sendIntegerInput,
                    sendDoubleOutput,
                    sendStringOutput,
                    sendIntegerOutput
                )

                success = result!!.get("success") as Boolean? as Boolean

                val stringOut = result.get("outstring") as Array<Any?>
                val doubleOut = result.get("outdouble") as Array<Any?>
                val intOut = result.get("outint") as Array<Any?>

                for (i in stringOut.indices) {
                    addMessage("String Outputted: " + stringOut[i])
                }

                for (i in doubleOut.indices) {
                    addMessage("Float Outputted: " + doubleOut[i])
                }

                for (i in intOut.indices) {
                    addMessage("Int Outputted: " + intOut[i])
                }

                if (!success) {
                    addMessage(result.get("error") as String?)
                    break
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        addMessage("-----------------------")


        //Reward.
        if (success) {
            //We'd set a quest parameter to true here.
            val TaskLabel = ""

            if (!checkQuest(QuestID)) { //Make sure the quest isn't already complete.
                var CurrentQuest: HashMap<Any?, Any?>? = null
                var label: String? = ""
                if (CurrentQuests.get(QuestID) != null) {
                    CurrentQuest = (CurrentQuests.get(QuestID) as Array<Any?>?)!![0] as HashMap<Any?, Any?>?
                    label = (CurrentQuests.get(QuestID) as Array<Any?>?)!![1] as String?
                }

                if (CurrentQuest == null) {
                    CurrentQuest = HashMap<Any?, Any?>()
                    CurrentQuest.put(TaskName, arrayOf<Any>(true, TaskLabel))
                    CurrentQuests.put(QuestID, arrayOf<Any?>(CurrentQuest, label))
                } else {
                    CurrentQuest.put(TaskName, arrayOf<Any>(true, TaskLabel))
                }
            }

            addMessage(MessageHandler.CHALLENGE_COMPLETED)
        } else {
            addMessage(MessageHandler.CHALLENGE_FAILED)
        }
    }

    /**
     * Tells the computer to begin loading player data in its thread.
     */
    fun loadSave() {
        try {
            if (!Loading) {
                available.acquire()
                Loading = true
                Tasks.add(0, loadSaveTask(this))
                available.release()
            }
        } catch (e: Exception) {
            available.release()
            e.printStackTrace()
        }
    }


    /**
     * Tells the computer to begin writing its data to the DB/XML files.
     */
    fun writeSave() {
        try {
            Loaded = false
            //Write stuff to the database.
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Checks whether the banking port is on before an action can be performed.
     */
    open fun checkBank(): Boolean {
        val PortIterator = Ports.entries.iterator()
        var ii = 0
        var success = false //MAKE SURE THAT A BANKING PORT IS INSTALLED.


        while (PortIterator.hasNext()) {
            val TempPort = ((PortIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port
            if (TempPort.getType() == Port.BANKING && TempPort.getOn() && !TempPort.getDummy()) success = true
            ii++
        }
        return (success)
    }

    /**
     * Checks whether a fire wall is installed on any of their ports.
     */
    fun checkFirewall(): Boolean {
        val PortIterator = Ports.entries.iterator()
        var ii = 0
        var success = false //MAKE SURE THAT A BANKING PORT IS INSTALLED.

        while (PortIterator.hasNext()) {
            val TempPort = ((PortIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port
            if (TempPort.getFireWall() != null && (TempPort.getFireWall().getType().get("name") as String) != "None") {
                success = true
                break
            }
            ii++
        }
        return (success)
    }

    /**
     * Checks whether a mining port is currently on and ready to accept a transaction.
     */
    fun checkShipping(): Boolean {
        val PortIterator = Ports.entries.iterator()
        var ii = 0
        var success = false //MAKE SURE THAT A BANKING PORT IS INSTALLED.

        while (PortIterator.hasNext()) {
            val TempPort = ((PortIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port
            if (TempPort.getType() == Port.SHIPPING && TempPort.getOn() && !TempPort.getDummy()) success = true
            ii++
        }
        return (success)
    }

    /**
     * Checks whether the http port is on before an action can be performed.
     */
    open fun checkHTTP(): Boolean {
        val PortIterator = Ports.entries.iterator()
        var ii = 0
        var success = false //MAKE SURE THAT A BANKING PORT IS INSTALLED.
        while (PortIterator.hasNext()) {
            val TempPort = ((PortIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port
            if (TempPort.getType() == Port.HTTP && TempPort.getOn() && !TempPort.getDummy()) success = true
            ii++
        }
        return (success)
    }

    val attacking: Boolean
        /**
         * Checks whether or not any of the ports on this computer are attacking.
         */
        get() {
            val PortIterator = Ports.entries.iterator()
            var ii = 0
            val success = false //MAKE SURE THAT A BANKING PORT IS INSTALLED.
            while (PortIterator.hasNext()) {
                val TempPort =
                    ((PortIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port
                if (TempPort.getAttacking()) return (true)
                ii++
            }
            return (false)
        }

    /**
     * Checks whether the http port is on before an action can be performed.
     */
    fun checkFTP(): Boolean {
        val PortIterator = Ports.entries.iterator()
        var ii = 0
        var success = false //MAKE SURE THAT A BANKING PORT IS INSTALLED.
        while (PortIterator.hasNext()) {
            val TempPort = ((PortIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port
            if (TempPort.getType() == Port.FTP && TempPort.getOn() && !TempPort.getDummy()) success = true
            ii++
        }
        return (success)
    }

    /**
     * Check whether or not a quest is finished.
     */
    open fun checkQuest(ID: Int): Boolean {
        for (i in CompletedQuests.indices) {
            val check = (CompletedQuests.get(i) as Array<Any?>?)!![0] as Int
            if (check == ID) return (true)
        }
        return (false)
    }


    private fun getWindowHandle(P: Port?): Int {
        var windowHandle = 0
        if (P != null) {
            val program = P.getProgram()
            if (program is AttackProgram) {
                windowHandle = program.getWindowHandle()
            } else if (program is ShippingProgram) {
                windowHandle = program.getWindowHandle()
            }
        }
        return (windowHandle)
    }


    /**
     * Check whether the file needs to be renamed.
     * 
     */
    fun checkRename(HF: HackerFile, path: String): HackerFile {
        var HFCheck = MyFileSystem!!.getFile(path, HF.getName())
        if (HFCheck != null) {
            if (HFCheck.isStacking()) {
                if (HF.checkSumFailed(HFCheck) || (HF.getType() == HackerFile.BOUNTY && ip == store)) {
                    val nameCheck = HF.getName()
                    var i = 0
                    var name = nameCheck + i
                    var TF: HackerFile? = null
                    HF.setName(name)
                    //  System.out.println("Changing name to "+name);
                    if (HF.getType() != HackerFile.BOUNTY) { //Check for identical files, bounties are a special case.
                        while ((MyFileSystem!!.getFile(path, name).also { TF = it }) != null && HF.checkSumFailed(TF)) {
                            i++
                            //        System.out.println("Changing name to "+(nameCheck+i));
                            name = nameCheck + i
                            HF.setName(name)
                        }
                    } else {
                        while ((MyFileSystem!!.getFile(path, name).also { TF = it }) != null) {
                            i++
                            name = nameCheck + i
                            HF.setName(name)
                        }
                    }

                    HF.setName(name)
                    if (TF == null || HF.checkSumFailed(TF)) HFCheck = null
                    else HFCheck = TF
                }
            }
        }
        return (HF)
    }

    /**
     * Save the file to the file system after checking the checksum, etc.,
     */
    fun saveFile(HF: HackerFile, HFCheck: HackerFile?, path: String) {
        var HF = HF
        val nameCheck = HF.getName()
        HF = checkRename(HF, path)

        var quantity = HF.getQuantity()
        if (quantity == 0) quantity = 1

        val price = 0f
        if (HFCheck != null) { //Does the file already exist on disk?
            if (HFCheck.getQuantity() == -1) {
                quantity = -1
            } else if (HFCheck.isStacking() && HF.getName() == HFCheck.getName()) {
                quantity = HFCheck.getQuantity() + HF.getQuantity()
            }
        }
        HF.setQuantity(quantity)

        if (!MyFileSystem!!.addFile(HF, true)) { //Check whether the file system can accept a new file.
            addMessage(MessageHandler.HD_FULL)
        }

        if (path == "Public/" || path == "Store/") {
            PA.setRequestSecondary(true, 8)
            PA.setRequestPrimary(true, 8)
        } else {
            PA.setRequestPrimary(true, 1)
        }
        systemChange = true
    }

    /**
     * TTJ's method to bypass file quantity calculation and instead set it the the value passed in parameter
     * (fix for takeFile bug)
     * basically a clone of the saveFile() function
     */
    private fun saveFileTemp(HF: HackerFile, HFCheck: HackerFile?, path: String, newQuantity: Int) {
        var HF = HF
        val nameCheck = HF.getName()
        HF = checkRename(HF, path)

        var quantity = HF.getQuantity()
        if (quantity == 0) quantity = 1

        val price = 0f
        if (HFCheck != null) { //Does the file already exist on disk?
            if (HFCheck.getQuantity() == -1) {
                quantity = -1
            } else if (HFCheck.isStacking() && HF.getName() == HFCheck.getName()) {
                quantity = HFCheck.getQuantity() + HF.getQuantity()
            }
        }
        HF.setQuantity(newQuantity) // sorry, but i'm ignoring all that crap about quantity

        if (!MyFileSystem!!.addFile(HF, true)) { //Check whether the file system can accept a new file.
            addMessage(MessageHandler.HD_FULL)
        }

        if (path == "Public/" || path == "Store/") {
            PA.setRequestSecondary(true, 8)
            PA.setRequestPrimary(true, 8)
        } else {
            PA.setRequestPrimary(true, 1)
        }
        systemChange = true
    }

    /**
     * Fetch execution tasks from the stack.
     */
    private var iterationCount = 0
    private var cpuLoadCalculated = false

    @Synchronized
    override fun run() {
        while (run) {
            iterationCount++
            val startTime = MyTime!!.getCurrentTime()

            try {
                //LOCK OUR LIST AND POP ONE ENTRY.
                available.acquire()
                //Iterator MyIterator=Tasks.iterator();
                var o: Any? = null
                //if(MyIterator.hasNext()){
                if (Tasks.size > 0) {
                    //o=MyIterator.next();
                    o = Tasks.get(0)
                    if ((Loaded && !LOAD_FAILURE) || o !is ApplicationData) {
                        //MyIterator.remove();
                        Tasks.removeAt(0)
                    }
                }
                available.release()

                if (!countDown || MyTime!!.getCurrentTime() - countDownStart < COUNTDOWN_LENGTH)  //Has a coundown taken place and the server timed out?
                    if (o != null) {
                        processQueuedItem(o, startTime)
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            runLoopMaintenance(startTime)
        }
        println("Stopping thread" + ip)
    }

    private fun processQueuedItem(o: Any?, startTime: Long) {
        if (o is Task) {
            val T = o
            T.execute()
        } else if (o is ApplicationData && Loaded) {
            var checkedWatch = false
            val MyApplicationData = o
            val function = MyApplicationData.getFunction()
            var port = MyApplicationData.getPort()

            updateApplicationActivity(MyApplicationData, function)
            port = normalizeTransferPort(MyApplicationData, function, port)
            maybeLogIncomingMessage(MyApplicationData, function)

            if (commandDispatcher == null) buildFunctionHash()

            val handledByDispatcher = commandDispatcher != null && LegacyRunLoopApplicationDataRouter.dispatch(
                this,
                MyApplicationData,
                port,
                commandDispatcher!!
            )

            if (!handledByDispatcher) {
                checkedWatch = dispatchToPortOrFail(MyApplicationData, port)
            }

            finalizeProcessedApplicationData(MyApplicationData, function, startTime, checkedWatch)
        }
    }

    private fun updateApplicationActivity(applicationData: ApplicationData, function: String) {
        if (function == "ping") {
            lastPingTime = MyTime!!.getCurrentTime()
            if (logInTime == 0L) {
                logInTime = MyTime!!.getCurrentTime()
            }
        }

        if (clientPackets.containsKey(function)) {
            if (lastClientPacketTime == 0L || logInTime == 0L) {
                logInTime = MyTime!!.getCurrentTime()
            }
            lastClientPacketTime = MyTime!!.getCurrentTime()
            val lockCountAdd = clientPackets.get(function) as Int
            lockCount += lockCountAdd
        }

        if (applicationData.getSource() == ApplicationData.OUTSIDE) GUI_READY = true
    }

    private fun normalizeTransferPort(applicationData: ApplicationData, function: String, port: Int): Int {
        var port = port
        if (function == "requestsecondarydirectory" || function == "put" || function == "get" || function == "finalizeput") {
            val targetIP = (applicationData.getParameters() as Array<Any?>?)!![0] as String

            if (targetIP != ip) {
                port = defaultFTP
                val P = Ports.get(port) as Port?
                if (P != null) {
                    if (P.getType() != Port.FTP) {
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "message",
                                arrayOf<Any>(MessageHandler.PORT_WAS_NOT_FTP, arrayOf<Any?>(port, ip)),
                                0,
                                ip
                            ), targetIP
                        )
                        if (function == "finalizeput") P.friendlyPut(applicationData)
                    } else if (P.getDummy()) {
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "message",
                                arrayOf<Any>(
                                    MessageHandler.PORT_WAS_DUMMY,
                                    arrayOf<Any?>(port, ip),
                                    arrayOf<Any>(applicationData.getSourcePort(), targetIP)
                                ),
                                0,
                                ip
                            ), targetIP
                        )
                        if (function == "finalizeput") P.friendlyPut(applicationData)
                    } else if (!P.getOn()) {
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "message",
                                arrayOf<Any>(MessageHandler.PORT_NOT_ON, arrayOf<Any?>(port, ip)),
                                0,
                                ip
                            ), targetIP
                        )
                        if (function == "finalizeput") P.friendlyPut(applicationData)
                    }
                } else {
                    if (function == "finalizeput") P?.friendlyPut(applicationData)
                    MyComputerHandler!!.addData(
                        ApplicationData("message", MessageHandler.FTP_NOT_FOUND, 0, ip),
                        targetIP
                    )
                }
            }
        }
        return (port)
    }

    private fun maybeLogIncomingMessage(applicationData: ApplicationData, function: String) {
        if (function == "logmessage") {
            val message = (applicationData.getParameters() as Array<Any?>?)!![0] as String?
            val ip = (applicationData.getParameters() as Array<Any?>?)!![1] as String?
            val timestamp = (applicationData.getParameters() as Array<Any?>?)!![2] as Long
            logMessage(message, ip, timestamp)
        }
    }

    private fun dispatchToPortOrFail(applicationData: ApplicationData, port: Int): Boolean {
        if (Ports.get(port) != null) {
            val tempport = Ports.get(port) as Port
            tempport.setCurrentPacket(PA)
            tempport.addApplicationData(applicationData, MyTime!!.getCurrentTime())

            currentWatchCost = MyWatchHandler!!.checkWatches(applicationData, Ports, pettyCash)
            return (true)
        } else {
            if (applicationData.getFunction() == "damage") MyComputerHandler!!.addData(
                ApplicationData(
                    "requestcancelattack", null, applicationData.getSourcePort(),
                    this.ip
                ), applicationData.getSourceIP()
            )

            if (applicationData.getSourceIP() != ip) MyComputerHandler!!.addData(
                ApplicationData(
                    "message", arrayOf<Any>(
                        MessageHandler.PORT_NOT_ON, arrayOf<Any?>(port, ip)
                    ), 0, ip
                ), applicationData.getSourceIP()
            )
            addMessage(MessageHandler.COULD_NOT_EXECUTE_APPLICATION, arrayOf<Any?>(port))
            systemChange = true
            return (false)
        }
    }

    private fun finalizeProcessedApplicationData(
        applicationData: ApplicationData?,
        function: String,
        startTime: Long,
        checkedWatch: Boolean
    ) {
        if (function != "requestequipment") {
            lastAccessed = startTime
        }
        if (!checkedWatch) currentWatchCost = MyWatchHandler!!.checkWatches(applicationData, Ports, pettyCash)
    }

    private fun runLoopMaintenance(startTime: Long) {
        //Check whether or not a packet should currently be sent.
        sendStandardPacket()
        runRuntimeCoordinatorTick()


        //Macro Protection.
        if (operationCount > 6000 && !this.isNPC()) {
            RawComputerHandler!!.broadcast(
                ApplicationData(
                    "message",
                    arrayOf<Any>(MessageHandler.PLAYER_BUSY, arrayOf<Any?>(ip)),
                    0,
                    ""
                )
            )
            operationCount = 0
        }


        //Sleep to cut down on processor load.
        if (Tasks.size == 0) {
            try {
                val endTime = MyTime!!.getCurrentTime()
                if (SLEEP_TIME - (endTime - startTime) > 0) Thread.sleep(SLEEP_TIME - (endTime - startTime))
            } catch (e: InterruptedException) {
                if (run) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            if (Tasks.size > 10) {
                CentralLogging.getInstance().addOutput("Username: " + userName + " IP:" + ip + " Spamming?\n")
            }
        }


        //Dispatch a 3D chat update.
        if (getLoaded() && !getLoading() && type != NPC && GUI_READY) sendChatPacket()

        try {
            Thread.sleep(50)
        } catch (e: InterruptedException) {
            if (run) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runRuntimeCoordinatorTick() {
        val now = MyTime!!.getCurrentTime()
        checkRuntimePortTimeouts(now)
        val runtimeState = buildRuntimeTickState(now)
        apply(runtimeCoordinator.tick(runtimeState), buildRuntimeTickEventSink(now))
        applyRuntimeTickState(runtimeState)
    }

    private fun checkRuntimePortTimeouts(now: Long) {
        val portIterator = Ports.entries.iterator()
        while (portIterator.hasNext()) {
            val tempPort = ((portIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port
            tempPort.checkTimeOut(now)
        }
    }

    private fun buildRuntimeTickState(now: Long): RuntimeTickState {
        val state = RuntimeTickState()
        state.now = now
        state.ip = ip
        state.loaded = Loaded
        state.loading = Loading
        state.loggedIn = loggedIn
        state.loadFailure = LOAD_FAILURE
        state.logoutRequested = LOGOUT
        state.countDown = countDown
        state.countDownStart = countDownStart
        state.countDownLengthMs = COUNTDOWN_LENGTH
        state.lastAccessed = lastAccessed
        state.computerTimeoutMs = COMPUTER_TIMEOUT
        state.lastSave = lastSave
        state.autoSaveMs = AUTO_SAVE
        state.loadRequester = loadRequester
        state.errorMessage = errorMessage
        state.pendingTasks.addAll(buildRuntimeQueuedTasks())
        state.lastPingTime = lastPingTime
        state.logInTime = logInTime
        state.lastClientPacketTime = lastClientPacketTime
        state.pingTimeoutMs = PING_TIMEOUT
        state.clientPacketTimeoutMs = CLIENT_PACKET_TIMEOUT
        state.lastPaid = lastPaid
        state.payPeriodMs = PAY_PERIOD
        state.dailyPaySize = dailyPaySize
        state.dailyPayReduction = dailyPayReduction
        state.inactive = inactive
        state.npc = this.isNPC()
        state.type = type
        state.httpActive = checkHTTP()
        state.httpLevel = this.hTTPLevel
        state.pettyCash = pettyCash
        state.bankMoney = bankMoney
        state.myVotes = myVotes
        state.adRevenueTarget = adRevenueTarget
        state.currentCPU = currentCPU
        state.reportCPU = reportCPU
        state.baseCPU = baseCPU
        state.currentWatchCost = currentWatchCost
        state.cpuLoadCalculated = cpuLoadCalculated
        state.lastAttack = lastAttack
        state.attackRateMs = ATTACK_RATE
        state.healCounter = healCounter
        state.healMod = MyEquipmentSheet.getHealMod()
        state.overheatStart = overheatStart
        state.overHeatTimeMs = OVER_HEAT_TIME
        state.sentOverHeatedMessage = sentOverHeatedMessage
        state.cpuMaximum = CPU_CHART!![cputype] + MyEquipmentSheet.getCPUBonus()
        state.lockCount = lockCount
        state.locked = locked
        state.resendCaptcha = RESEND_CAPTCHA
        state.captchaThreshold = CAPTCHA_COUNT
        state.unlockKey = unlockKey
        state.operationCount = operationCount
        state.grantFilesInterval = 150
        state.grantFilesCounter = max(0, iterationCount - 1)
        state.captchaGenerator = {
            val generated: Array<Any?> = generateImage()
            val pixels = if (generated[0] is IntArray) generated[0] as IntArray else IntArray(0)
            RuntimeCaptchaPayload((generated[1] as kotlin.String?)!!, pixels)
        }
        state.watchCostSupplier =
            { MyWatchHandler!!.checkWatches(ApplicationData("null", null, 0, ip), Ports, pettyCash) }
        state.ports.addAll(buildRuntimePortSnapshots())
        return (state)
    }

    private fun buildRuntimeQueuedTasks(): MutableList<RuntimeQueuedTask> {
        val runtimeTasks = java.util.ArrayList<RuntimeQueuedTask>()
        val queuedItems = java.util.ArrayList<Any?>(Tasks)
        val iterator: MutableIterator<Any?> = queuedItems.iterator()
        while (iterator.hasNext()) {
            val queued = iterator.next()
            if (queued is ApplicationData) {
                val applicationData = queued
                runtimeTasks.add(
                    RuntimeQueuedTask(
                        applicationData.getFunction(),
                        applicationData.getSourceIP(),
                        applicationData.getParameters(),
                        applicationData.getPort(),
                        applicationData.getSourcePort(),
                        applicationData.getSource()
                    )
                )
            }
        }
        return (runtimeTasks)
    }

    private fun buildRuntimePortSnapshots(): MutableList<RuntimePortSnapshot> {
        val snapshots = java.util.ArrayList<RuntimePortSnapshot>()
        val portIterator = Ports.entries.iterator()
        while (portIterator.hasNext()) {
            val tempPort = ((portIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port
            snapshots.add(
                RuntimePortSnapshot(
                    tempPort.getNumber(),
                    tempPort.getType(),
                    tempPort.getOn(),
                    tempPort.getDummy(),
                    tempPort.getAttacking(),
                    tempPort.getOverHeated(),
                    tempPort.getHealth(),
                    tempPort.getCPUCost(),
                    tempPort.getBaseCPUCostTotal(),
                    tempPort.getLastDamageWindowHandle(),
                    tempPort.getAccessing(),
                    getRuntimeTargetPort(tempPort),
                    getRuntimeTargetIP(tempPort),
                    getRuntimeMaliciousTarget(tempPort)!!,
                    isRuntimeZombie(tempPort)
                )
            )
        }
        return (snapshots)
    }

    private fun getRuntimeTargetPort(tempPort: Port): Int {
        if (tempPort.getProgram() is AttackProgram) {
            return (tempPort.getProgram() as AttackProgram).getTargetPort()
        } else if (tempPort.getProgram() is ShippingProgram) {
            return (tempPort.getProgram() as ShippingProgram).getTargetPort()
        }
        return (-1)
    }

    private fun getRuntimeTargetIP(tempPort: Port): String {
        if (tempPort.getProgram() is AttackProgram) {
            val targetIP = (tempPort.getProgram() as AttackProgram).getTargetIP()
            return (if (targetIP == null) "" else targetIP)
        } else if (tempPort.getProgram() is ShippingProgram) {
            val targetIP = (tempPort.getProgram() as ShippingProgram).getTargetIP()
            return (if (targetIP == null) "" else targetIP)
        }
        return ("")
    }

    private fun getRuntimeMaliciousTarget(tempPort: Port): String? {
        if (tempPort.getProgram() is AttackProgram) {
            val maliciousIP = (tempPort.getProgram() as AttackProgram).getMaliciousIP()
            if (maliciousIP != null) return (maliciousIP)
        }
        return (tempPort.getMaliciousTarget())
    }

    private fun isRuntimeZombie(tempPort: Port): Boolean {
        if (tempPort.getProgram() is AttackProgram) {
            return ((tempPort.getProgram() as AttackProgram).isZombie())
        }
        return (false)
    }

    private fun buildRuntimeTickEventSink(now: Long): RuntimeTickEventSink {
        return object : RuntimeTickEventSink {
            override fun persistRequested(autoSave: Boolean) {
                if (autoSave) {
                    MyEquipmentSheet.degradeEquipment()
                }
                try {
                    MysqlHandler.addWork(
                        arrayOf<Any?>(
                            ip,
                            this@Computer,
                            "asdbas0d98a0sd9fa8sasdlbo",
                            pageChanged,
                            pageTitle,
                            pageBody
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if (autoSave) {
                    MyComputerHandler!!.addData(ApplicationData("requestequipment", 13, 0, ip), ip)
                }
            }

            override fun unloadRequested() {
                Loaded = false
                RawComputerHandler!!.addData(null, ip)
            }

            override fun playerCountDecrementRequested() {
                RawComputerHandler!!.decrementPlayers()
            }

            override fun applicationDataDispatchRequested(
                runtimeApplicationData: RuntimeApplicationDataDispatch,
                targetIp: String
            ) {
                val applicationData = ApplicationData(
                    runtimeApplicationData.function,
                    runtimeApplicationData.parameters,
                    runtimeApplicationData.port,
                    runtimeApplicationData.sourceIp
                )
                applicationData.setSourcePort(runtimeApplicationData.sourcePort)
                applicationData.setSource(runtimeApplicationData.source)
                MyComputerHandler!!.addData(applicationData, targetIp)
            }

            override fun logEntry(message: String, ip: String, timestamp: Long) {
                logMessage(message, ip, timestamp)
            }

            override fun playSessionRecorded(targetIP: String, startedAt: Long, endedAt: Long) {
                sessionService.recordPlayWindow(targetIP, startedAt, endedAt)
            }

            override fun dailyPayIssued(amount: Float, targetIP: String) {
                MyComputerHandler!!.addData(ApplicationData("pettycash", arrayOf<Any>(amount, false), 0, ip), targetIP)
            }

            override fun httpXpIssued(amount: Float, targetIP: String) {
                MyComputerHandler!!.addData(ApplicationData("httpxp", amount, 0, ip), targetIP)
            }

            override fun bankMoneyAdded(amount: Float) {
            }

            override fun pettyCashAdded(amount: Float) {
            }

            override fun attackContinueRequested(portNumber: Int, targetPort: Int) {
                val tempPort = Ports.get(portNumber) as Port?
                if (tempPort != null) {
                    tempPort.addApplicationData(ApplicationData("attackcontinue", null, targetPort, ""), now)
                }
            }

            override fun overheatAnnounced(ip: String) {
                addMessage(MessageHandler.COMPUTER_OVERHEATED)
            }

            override fun opponentOverheated(targetIp: String, windowHandle: Int, accessing: String) {
                MyComputerHandler!!.addData(
                    ApplicationData(
                        "message",
                        arrayOf<Any>(
                            MessageHandler.OVERHEATED_OPPONENT,
                            arrayOf<Any?>(ip),
                            arrayOf<Any?>(windowHandle, accessing)
                        ),
                        0,
                        ip
                    ), targetIp
                )
                MyComputerHandler!!.addData(
                    ApplicationData(
                        "message",
                        arrayOf<Any>(MessageHandler.OVERHEATED_OPPONENT_GAME, arrayOf<Any?>(ip)),
                        0,
                        ip
                    ), targetIp
                )
            }

            override fun zombieOverheated(targetIp: String, maliciousIp: String) {
                MyComputerHandler!!.addData(
                    ApplicationData(
                        "message",
                        arrayOf<Any>(MessageHandler.ZOMBIE_OVERHEATED, arrayOf<Any?>(targetIp)),
                        0,
                        targetIp
                    ), maliciousIp
                )
            }

            override fun captchaRequested(payload: RuntimeCaptchaPayload) {
                PA.setCAPTCHA(payload.image)
                unlockKey = payload.unlockKey
                sendPacket()
            }

            override fun grantFilesRequested() {
                GiveItemsSingleton.getInstance().giveFiles(this@Computer, RawComputerHandler)
            }

            override fun equipmentRefreshRequested() {
                healthChange = true
            }
        }
    }

    private fun applyRuntimeTickState(runtimeState: RuntimeTickState) {
        applyRuntimePortSnapshots(runtimeState)
        Loaded = runtimeState.loaded
        lastSave = runtimeState.lastSave
        lastPingTime = runtimeState.lastPingTime
        logInTime = runtimeState.logInTime
        lastClientPacketTime = runtimeState.lastClientPacketTime
        lastPaid = runtimeState.lastPaid
        inactive = runtimeState.inactive
        pettyCash = runtimeState.pettyCash
        bankMoney = runtimeState.bankMoney
        myVotes = runtimeState.myVotes
        currentCPU = runtimeState.currentCPU
        reportCPU = runtimeState.reportCPU
        baseCPU = runtimeState.baseCPU
        currentWatchCost = runtimeState.currentWatchCost
        cpuLoadCalculated = runtimeState.cpuLoadCalculated
        lastAttack = runtimeState.lastAttack
        healCounter = runtimeState.healCounter
        overheatStart = runtimeState.overheatStart
        sentOverHeatedMessage = runtimeState.sentOverHeatedMessage
        lockCount = runtimeState.lockCount
        locked = runtimeState.locked
        RESEND_CAPTCHA = runtimeState.resendCaptcha
        unlockKey = runtimeState.unlockKey
        iterationCount = runtimeState.grantFilesCounter
    }

    private fun applyRuntimePortSnapshots(runtimeState: RuntimeTickState) {
        val iterator: MutableIterator<*> = runtimeState.ports.iterator()
        while (iterator.hasNext()) {
            val runtimePort = iterator.next() as RuntimePortSnapshot
            val tempPort = Ports.get(runtimePort.number) as Port?
            if (tempPort == null) continue

            val oldHealth = tempPort.getHealth()
            val newHealth = runtimePort.health
            if (newHealth != oldHealth) {
                if (tempPort.damagePort(oldHealth - newHealth)) {
                    healthChange = true
                    MyWatchHandler!!.updateInitialHealthQuanity(tempPort.getNumber(), tempPort.getHealth())
                }
            }

            if (tempPort.getOverHeated() != runtimePort.overHeated) {
                tempPort.setOverHeated(runtimePort.overHeated)
            }

            if (tempPort.getAttacking() != runtimePort.attacking) {
                tempPort.setAttacking(runtimePort.attacking)
            }
        }
    }

    /**
     * Run the logic used to perform the saving process of accounts.
     */
    fun runSavingLogic() {
        //System.out.println("Running Saving Logic");
        //WRITE THE COMPUTER BACK TO DISK WHEN A TIMEOUT IS REACHED.
        //System.out.println("Computer Timeout: "+COMPUTER_TIMEOUT+" Logged Time:"+(MyTime.getCurrentTime()-lastAccessed));
        if ((MyTime!!.getCurrentTime() - lastAccessed > COMPUTER_TIMEOUT || LOGOUT || LOAD_FAILURE || (countDown && MyTime!!.getCurrentTime() - countDownStart > COUNTDOWN_LENGTH)) && Loaded) {
            //Message the player who requested this load with the error message.
            if ((loadRequester != ip) && LOAD_FAILURE && (loadRequester != "")) {
                val MyIterator = Tasks.iterator()
                var o: Any? = null
                if (MyIterator.hasNext()) {
                    o = MyIterator.next()
                    if (o is ApplicationData) {
                        val AD = o
                        if (AD.getFunction() == "pettycash") {
                            MyComputerHandler!!.addData(AD, AD.getSourceIP())
                        }

                        if (AD.getFunction() == "requestwebpage") {
                            val PageTitle = "Server Not Found"
                            val PageBody =
                                "<html><head><title>Hack Wars - Error report</title><style><!--H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;color:white} H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} BODY {background-color:rgb(0,0,0);font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;color:white;} B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;color:white;} P {color:white;font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}A {color : black;}A.name {color : black;}HR {color : #525D76;}--></style> </head><body><h1 style=\"width:100%\">HTTP Status 408</h1><HR size=\"1\" noshade=\"noshade\"><p style=\"background-color:black;\"><b>type</b> HTTP Error</p><p style=\"background-color:black;\"><b>message</b> <u>Resource not found.</u></p><p style=\"background-color:black\"><b>description</b> <u>The HTTP server of the player you attempted to connect to does not seem to be on.</u></p><HR size=\"1\" noshade=\"noshade\"><h3>&copy; Hack Wars</h3></body></html>"
                            val Files: Array<Any?>? = null
                            val O: Array<Any?>? = arrayOf<Any?>(PageTitle, PageBody, Files, 0)
                            MyComputerHandler!!.addData(ApplicationData("webpage", O, 0, ip), AD.getSourceIP())
                        }
                    }
                    MyIterator.remove()
                }


                MyComputerHandler!!.addData(ApplicationData("message", errorMessage, 0, ip), loadRequester)
            }

            if (!LOAD_FAILURE) { //Only write to disk if the file didn't fail to load.
                try {
                    MysqlHandler.addWork(
                        arrayOf<Any?>(
                            ip,
                            this,
                            "asdbas0d98a0sd9fa8sasdlbo",
                            pageChanged,
                            pageTitle,
                            pageBody
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Loaded = false
            RawComputerHandler!!.addData(null, ip)
            if (type != NPC) RawComputerHandler!!.decrementPlayers()
        } else if (!LOAD_FAILURE) { //Perform an auto-save every 10 minutes or so.
            if (lastSave == 0L) lastSave = MyTime!!.getCurrentTime()
            if (MyTime!!.getCurrentTime() - lastSave > AUTO_SAVE) {
                MyEquipmentSheet.degradeEquipment() //This is a good time to check whether or not equipment has degraded.

                lastSave = MyTime!!.getCurrentTime()
                try {
                    MysqlHandler.addWork(
                        arrayOf<Any?>(
                            ip,
                            this,
                            "asdbas0d98a0sd9fa8sasdlbo",
                            pageChanged,
                            pageTitle,
                            pageBody
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                MyComputerHandler!!.addData(ApplicationData("requestequipment", 13, 0, ip), ip)
            }
        }
    }

    /**
     * Checks wheter or not we have stopped getting a ping and therefore need to write to a db how long the player has been playing.
     */
    fun checkPingTime() {
        val result = sessionService.recordPlayStatistics(
            PlayStatisticsRequest(
                loggedIn,
                ip,
                MyTime!!.getCurrentTime(),
                logInTime,
                lastPingTime,
                lastClientPacketTime
            )
        )
        lastPingTime = result.lastPingTimeMillis
        logInTime = result.logInTimeMillis
        lastClientPacketTime = result.lastClientPacketTimeMillis
    }

    /**
     * Checks whether or not the player should be given their daily pay and provides it.
     */
    fun checkDailyPay() {
        /**
         * CHECK WHETHER IT IS TIME FOR DAILY PAY AND PROVIDE IT TO THE AD REVENUE TARGET.
         */
        if (lastPaid <= 100)  //Make sure that the first time the player plays they don't get paid.
            lastPaid = MyTime!!.getCurrentTime()

        if (MyTime!!.getCurrentTime() - lastPaid > PAY_PERIOD && Loaded && !inactive) {
            val TempPort: Port? = null

            myVotes += 1 //Get some more votes.
            if (myVotes > 4) myVotes = 4

            if (!checkHTTP()) {
                logMessage(
                    "Did not receive income from website because HTTP is not installed.",
                    ip,
                    lastPaid + PAY_PERIOD
                )
                lastPaid = MyTime!!.getCurrentTime()
            } else {
                var mod: Float = (this.hTTPLevel - 1.0f) * 50.0f
                if (type == NPC) mod = 0.0f

                val amount = (dailyPaySize + mod) * 0.75f * dailyPayReduction
                val extra = (dailyPaySize + mod) * 0.75f - amount
                if (extra > 0.0f) {
                    pettyCash += extra
                    val message = "Transferred " + NumberFormat.getCurrencyInstance()
                        .format(extra.toDouble()) + " of daily pay from " + ip + "."
                    logMessage(message, ip, lastPaid + PAY_PERIOD)
                }

                var MyPettyCash = ApplicationData("pettycash", arrayOf<Any>(amount, false), 0, ip)
                MyComputerHandler!!.addData(MyPettyCash, adRevenueTarget)
                val MyHTTPXP = ApplicationData("httpxp", this.hTTPLevel * 10.0f, 0, ip)
                MyComputerHandler!!.addData(MyHTTPXP, adRevenueTarget)
                var message = "Transferred " + NumberFormat.getCurrencyInstance()
                    .format(amount.toDouble()) + " of daily pay from " + ip + "."
                var logMessageParameters = arrayOf<Any?>(message, ip, lastPaid + PAY_PERIOD)
                MyPettyCash = ApplicationData("logmessage", logMessageParameters, 0, ip)
                MyComputerHandler!!.addData(MyPettyCash, adRevenueTarget)
                message = "Received $" + (dailyPaySize + mod) * 0.25 + " in guaranteed income to bank."
                logMessageParameters = arrayOf<Any?>(message, ip, lastPaid + PAY_PERIOD)
                MyPettyCash = ApplicationData("logmessage", logMessageParameters, 0, ip)
                MyComputerHandler!!.addData(MyPettyCash, ip)
                bankMoney += ((dailyPaySize + mod) * 0.25).toFloat()
                lastPaid += PAY_PERIOD
            }
        } else if (inactive) {
            logMessage("Did not receive income because you were inactive.", ip, MyTime!!.getCurrentTime())
            lastPaid = MyTime!!.getCurrentTime()
            inactive = false
        }
    }

    /**
     * This function runs the attack logic, this takes care of dealing with overheating, applying hardware, and calculating
     * current CPU costs.
     */
    fun runAttackLogic() {
        /**
         * CHECK FOR ATTACKS/PERFORM HEALING AT THE GIVEN RATE/CALCULATE CPU LOAD.
         * Deals with: Attacking, Healing, Over Heating.
         */
        if (getLoaded() && !getLoading() && MyTime!!.getCurrentTime() - lastAttack > ATTACK_RATE) {
            val startReportCPU = reportCPU

            if (!cpuLoadCalculated)  //Check the current watch cost.
                currentWatchCost = MyWatchHandler!!.checkWatches(ApplicationData("null", null, 0, ip), Ports, pettyCash)
            cpuLoadCalculated = true

            val startCPU = currentCPU //What was the CPU cost at the start?


            //Heal the port at a given rate -- at this time once every 6 seconds.
            var heal = false
            var overHeated = false
            if (healCounter % MyEquipmentSheet.getHealMod() == 0L) heal = true
            if (currentCPU > CPU_CHART!![cputype] + MyEquipmentSheet.getCPUBonus()) {
                overHeated = true
                if (overheatStart == -1L) overheatStart = MyTime!!.getCurrentTime()
            } else if (MyTime!!.getCurrentTime() - overheatStart > OVER_HEAT_TIME && overheatStart != -1L) {
                overheatStart = -1
            } else if (overheatStart != -1L) {
                overHeated = true
            }

            var PortIterator = Ports.entries.iterator()
            var ii = 0
            var tempCPULoad = 0.0f
            var tempBaseCPULoad = 0.0f
            while (PortIterator.hasNext()) {
                val TempPort = ((PortIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port


                //If heal true heal the port.
                if (heal) {
                    if (TempPort.damagePort(-1.0f)) {
                        healthChange = true
                        MyWatchHandler!!.updateInitialHealthQuanity(TempPort.getNumber(), TempPort.getHealth())
                    }
                }
                //Put the ports into over-heat mode.
                if (overHeated) {
                    if (!TempPort.getOverHeated() && TempPort.getHealth() != 100f) {
                        if (TempPort.getType() == Port.ATTACK || TempPort.getType() == Port.SHIPPING) {
                            if (TempPort.getProgram() is AttackProgram) {
                                val TempProgram = TempPort.getProgram() as AttackProgram
                                if (TempProgram.isZombie()) {
                                    MyComputerHandler!!.addData(
                                        ApplicationData(
                                            "message",
                                            arrayOf<Any>(
                                                MessageHandler.ZOMBIE_OVERHEATED,
                                                arrayOf<Any?>(ip)
                                            ),
                                            0,
                                            ip
                                        ),
                                        TempProgram.getMaliciousIP()
                                    )
                                }
                                //send a message to the other player that they are overheated.
                                MyComputerHandler!!.addData(
                                    ApplicationData(
                                        "message",
                                        arrayOf<Any>(
                                            MessageHandler.OVERHEATED_OPPONENT,
                                            arrayOf<Any?>(ip),
                                            arrayOf<Any?>(TempPort.getLastDamageWindowHandle(), TempPort.getAccessing())
                                        ),
                                        0,
                                        ip
                                    ), TempProgram.getTargetIP()
                                )
                                MyComputerHandler!!.addData(
                                    ApplicationData(
                                        "message",
                                        arrayOf<Any>(MessageHandler.OVERHEATED_OPPONENT_GAME, arrayOf<Any?>(ip)),
                                        0,
                                        ip
                                    ), TempProgram.getTargetIP()
                                )
                            }
                        }
                        if (!sentOverHeatedMessage) {
                            addMessage(MessageHandler.COMPUTER_OVERHEATED)
                            sentOverHeatedMessage = true
                        }
                    }

                    TempPort.setOverHeated(true) //Put the port in an overheated state.
                }

                TempPort.checkTimeOut(MyTime!!.getCurrentTime()) //Has the port timed out since it was attacked.

                tempCPULoad += TempPort.getCPUCost()
                tempBaseCPULoad += TempPort.getBaseCPUCostTotal()

                if (TempPort.getOn() == false) { //For test.
                    TempPort.setAttacking(false)
                }

                if ((TempPort.getType() == Port.ATTACK || TempPort.getType() == Port.SHIPPING) && TempPort.getAttacking()) { //If a port is attacking force the attack to continue.
                    if (TempPort.getProgram() is AttackProgram) {
                        val AP = TempPort.getProgram() as AttackProgram
                        TempPort.addApplicationData(
                            ApplicationData("attackcontinue", null, AP.getTargetPort(), ""),
                            MyTime!!.getCurrentTime()
                        )
                    } else {
                        val SP = TempPort.getProgram() as ShippingProgram
                        TempPort.addApplicationData(
                            ApplicationData("attackcontinue", null, SP.getTargetPort(), ""),
                            MyTime!!.getCurrentTime()
                        )
                    }
                }
                ii++
            }

            baseCPU = tempBaseCPULoad + currentWatchCost //The base CPU prior to overheating.
            currentCPU = tempCPULoad + currentWatchCost
            lastAttack = MyTime!!.getCurrentTime()
            healCounter++

            reportCPU = currentCPU
            if (overHeated && currentCPU <= CPU_CHART[cputype] + MyEquipmentSheet.getCPUBonus()) {
                reportCPU = CPU_CHART[cputype] + MyEquipmentSheet.getCPUBonus() + 1
            } else if (!overHeated) { //Make sure the ports do not think they're overheated.
                PortIterator = Ports.entries.iterator()
                while (PortIterator.hasNext()) {
                    val TempPort = (PortIterator.next().value) as Port
                    TempPort.setOverHeated(false)
                    sentOverHeatedMessage = false
                }
            }

            if (currentCPU != startCPU || startReportCPU != reportCPU)  //The CPU Load has Changed.
                healthChange = true
        } else { //Otherwise make sure we still calculate the CPU load.
            currentCPU = 0.0f
            val PortIterator = Ports.entries.iterator()
            var overHeated = false
            while (PortIterator.hasNext()) {
                val TempPort = ((PortIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port?
                if (TempPort != null) {
                    currentCPU += TempPort.getCPUCost()
                    if (TempPort.getOverHeated()) {
                        overHeated = true
                    }
                }
            }
            currentCPU += currentWatchCost

            if (overHeated && currentCPU <= CPU_CHART!![cputype] + MyEquipmentSheet.getCPUBonus()) reportCPU =
                CPU_CHART[cputype] + MyEquipmentSheet.getCPUBonus() + 1
            else reportCPU = currentCPU
        }
    }

    /**
     * This function checks whether or not a packet should currently be sent, be it a damage packet or a standard packet.
     */
    fun sendStandardPacket() {
        /**
         * AT THE END OF THE PACKET TIMEOUT DISPATCH A PACKET TO THE CLIENT.
         */
        if (systemChange || healthChange) if (cpuLoadCalculated && getLoaded() && !getLoading() && MyTime!!.getCurrentTime() - lastSent > PACKET_TIMEOUT && connectionID >= 0) {
            if (systemChange) {
                systemChange = false
                standardPacketBuilder.populate(PA, buildStandardPacketSnapshot())
                Messages.clear()
                Choices.clear()
                if (LOG_UPDATE) {
                    LOG_UPDATE = false
                }
                if (sendPreferences) {
                    sendPreferences = false
                }

                val O: Array<Any?>? = arrayOf<Any?>(PA, connectionID)

                MyHackerServer!!.addData(O)
                lastSent = MyTime!!.getCurrentTime()
                PA = PacketAssignment(0)
            }

            if (healthChange) {
                healthChange = false
                damagePacketBuilder.populate(DA, buildDamagePacketSnapshot())
                Damage.clear()

                val O: Array<Any?>? = arrayOf<Any?>(DA, connectionID)
                MyHackerServer!!.addData(O)
                lastSent = MyTime!!.getCurrentTime()
                DA = DamageAssignment(0)
            }
        }
    }

    private fun buildStandardPacketSnapshot(): ComputerStandardPacketSnapshot {
        val messageArray = Messages.toTypedArray()
        val choices = java.util.ArrayList<Array<Any?>?>()
        val choiceIterator = Choices.iterator()
        while (choiceIterator.hasNext()) {
            choices.add(choiceIterator.next() as Array<Any?>?)
        }

        var logMessages: java.util.ArrayList<Array<String?>?>? = null
        if (LOG_UPDATE) {
            logMessages = java.util.ArrayList<Array<String?>?>()
            val logIterator = LogMessages!!.iterator()
            while (logIterator.hasNext()) {
                logMessages.add(logIterator.next() as Array<String?>?)
            }
        }

        var preferenceCopy: HashMap<String, Any?>? = null
        if (sendPreferences && preferences != null) {
            preferenceCopy = HashMap()
            preferences!!.forEach { (key, value) ->
                if (key != null) {
                    preferenceCopy[key.toString()] = value
                }
            }
        }

        var countDownSeconds: Int? = null
        if (countDown) {
            countDownSeconds = ((COUNTDOWN_LENGTH - (MyTime!!.getCurrentTime() - countDownStart)) / 1000).toInt()
        }

        return ComputerStandardPacketSnapshot(
            pettyCash,
            bankMoney,
            CPU_CHART!![cputype] + MyEquipmentSheet.getCPUBonus(),
            cputype,
            memorytype,
            defaultBank,
            defaultAttack,
            defaultHTTP,
            defaultFTP,
            defaultShipping,
            successfulHacks,
            voteCount,
            reportCPU,
            MyFileSystem!!.getHDType(),
            MyFileSystem!!.getQuantity() - 2,
            MyFileSystem!!.getMaximumSpace(),
            RawComputerHandler!!.getPlayers(),
            commodityAmount!!,
            MyEquipmentSheet.getHealBonus(),
            myVotes,
            messageArray,
            choices.filterNotNull(),
            logMessages?.filterNotNull()?.map { it.filterNotNull().toTypedArray() },
            countDownSeconds,
            preferenceCopy
        )
    }

    private fun buildDamagePacketSnapshot(): ComputerDamagePacketSnapshot {
        val healthUpdates = java.util.ArrayList<PortHealthSnapshot>()
        val portIterator = Ports.entries.iterator()
        while (portIterator.hasNext()) {
            val tempPort = ((portIterator.next() as MutableMap.MutableEntry<*, *>).value) as Port
            healthUpdates.add(
                PortHealthSnapshot(
                    tempPort.getNumber(),
                    tempPort.getHealth(),
                    tempPort.getCPUCost(),
                    tempPort.getFireWall().getType(),
                    tempPort.getHealCount(),
                    tempPort.getBaseCPUCostAndFirewall(),
                    getWindowHandle(tempPort)
                )
            )
        }

        val damageEntries = java.util.ArrayList<Array<Any?>>()
        val damageIterator = Damage.iterator()
        while (damageIterator.hasNext()) {
            val damageEntry = damageIterator.next() as Array<Any?>?
            if (damageEntry != null) {
                damageEntries.add(damageEntry)
            }
        }

        return ComputerDamagePacketSnapshot(
            getStatXP("Attack"),
            getStatXP("Bank"),
            getStatXP("FireWall"),
            getStatXP("Watch"),
            getStatXP("Scanning"),
            getStatXP("Webdesign"),
            getStatXP("Redirecting"),
            getStatXP("Repair"),
            reportCPU,
            healthUpdates,
            damageEntries
        )
    }

    /**
     * This function takes care of creating a 3D chat packet and dispatching it.
     */
    fun sendChatPacket() {
        /*HacktendoPacket HP=WorldSingleton.getInstance().getPacket("game",ip);
		if(HP.getSpriteEvents().size()>0){
			Object O[]=new Object[]{HP,new Integer(connectionID)};
			MyHackerServer.addData(O);
		}*/
    }

    //
    //
    /**
     * What follows is the loading and saving steps exclusively.
     * This task loads the save file representing the computer from disk.
     */
    private inner class loadSaveTask(MyComputer: Computer?) : Task {
        private var MyComputer: Computer? = null
        private var run = false

        init {
            this.MyComputer = MyComputer
        }

        override fun execute() {
            if (!run) {
                run = true
                loadCoordinator.execute((MyComputer as game.Computer?)!!)
            }
        }
    }


    /**
     * Output the contents of this class as an XML string.
     */
    @Throws(Exception::class)
    fun outputXML(): String {
        var returnMe = ""
        try {
            returnMe = persistenceSupport.outputXml(this)
        } catch (e: Exception) {
            try {
                val Out = BufferedWriter(FileWriter("saveerror.txt", true))
                Out.write(returnMe)
                Out.write(e.toString())
                Out.close()
            } catch (e2: Exception) {
                e.printStackTrace()
            }
            throw (e)
        }
        return (returnMe)
    }

    /**
     * This function encapsulates the loading of the file data-structure.
     */
    fun loadFile(N: Node, LX: LoadXML): HackerFile {
        return persistenceSupport.loadFile(N, LX)
    }

    companion object {
        //Runnable is an interface that allows us to make this class be a thread.
        private fun getPropertySafe(key: String, fallback: String?): String? {
            try {
                return System.getProperty(key, fallback)
            } catch (e: SecurityException) {
                return fallback
            }
        }

        //max ops values.
        const val FREE_MAX_OPS: Int = 4096
        const val PAY_MAX_OPS: Int = 16384
        private val REMOTE_XMLRPC_ENABLED = getPropertySafe("hackwars.remoteXmlRpc", "false").toBoolean()

        //file size limits.
        const val FREE_FILE_SIZE_LIMIT: Int = 60000
        const val PAY_FILE_SIZE_LIMIT: Int = 240000

        // makers (allows for selling)
        @JvmField
        val makers: HashMap<Any?, Any?> = HashMap()

        init {
            makers.put("Alexander", 15.0f)
            makers.put("Low", 30.0f)
            makers.put("Medium", 150.0f)
            makers.put("High", 1500.0f)
            makers.put("Rare", 15000.0f)
            makers.put("Holiday", 250.0f)
            makers.put("I", 15.0f)
            makers.put("II", 23.0f)
            makers.put("III", 36.0f)
            makers.put("IV", 56.0f)
            makers.put("V", 87.0f)
            makers.put("VI", 134.0f)
            makers.put("VII", 208.0f)
            makers.put("VIII", 322.0f)
            makers.put("IX", 500.0f)
            makers.put("X", 775.0f)
            makers.put("XI", 1201.0f)
            makers.put("XII", 1861.0f)
            makers.put("XIII", 2885.0f)
            makers.put("XIV", 4471.0f)
            makers.put("XV", 6930.0f)
            makers.put("XVI", 10742.0f)
            makers.put("XVII", 16649.0f)
            makers.put("XVIII", 25807.0f)
            makers.put("XIX", 40000.0f)
            makers.put("XX", 62000.0f)
            makers.put("Trash.I", 15.0f)
            makers.put("Trash.II", 17.0f)
            makers.put("Trash.III", 19.0f)
            makers.put("Trash.IV", 21.0f)
            makers.put("Trash.V", 24.0f)
            makers.put("Trash.VI", 27.0f)
            makers.put("Trash.VII", 30.0f)
            makers.put("Trash.VIII", 34.0f)
            makers.put("Trash.IX", 38.0f)
            makers.put("Trash.X", 43.0f)
            makers.put("Trash.XI", 49.0f)
            makers.put("Trash.XII", 55.0f)
            makers.put("Trash.XIII", 62.0f)
            makers.put("Trash.XIV", 69.0f)
            makers.put("Trash.XV", 78.0f)
            makers.put("Trash.XVI", 88.0f)
            makers.put("Trash.XVII", 99.0f)
            makers.put("Trash.XVIII", 111.0f)
            makers.put("Trash.XIX", 125.0f)
            makers.put("Trash.XX", 141.0f)
            makers.put("Trash.XXI", 158.0f)
            makers.put("Trash.XXII", 178.0f)
            makers.put("Trash.XXIII", 200.0f)
            makers.put("Trash.XXIV", 225.0f)
            makers.put("Trash.XXV", 253.0f)
            makers.put("Trash.XXVI", 285.0f)
            makers.put("Trash.XXVII", 321.0f)
            makers.put("Trash.XXVIII", 361.0f)
            makers.put("Trash.XXIX", 406.0f)
            makers.put("Trash.XXX", 457.0f)
            makers.put("Trash.XXXI", 514.0f)
            makers.put("Trash.XXXII", 578.0f)
            makers.put("Trash.XXXIII", 650.0f)
            makers.put("Trash.XXXIV", 731.0f)
            makers.put("Trash.XXXV", 823.0f)
            makers.put("Trash.XXXVI", 926.0f)
            makers.put("Trash.XXXVII", 1041.0f)
            makers.put("Trash.XXXVIII", 1171.0f)
            makers.put("Trash.XXXIX", 1318.0f)
            makers.put("Trash.XL", 1483.0f)
            makers.put("Token.Silverlight", 500.0f)
            makers.put("Token.Draconis", 250.0f)
            makers.put("Xyphex.I", 15.0f)
            makers.put("Xyphex.II", 23.0f)
            makers.put("Xyphex.III", 36.0f)
            makers.put("Xyphex.IV", 56.0f)
            makers.put("Xyphex.V", 87.0f)
            makers.put("Xyphex.VI", 134.0f)
            makers.put("Xyphex.VII", 208.0f)
            makers.put("Xyphex.VIII", 322.0f)
            makers.put("Xyphex.IX", 500.0f)
            makers.put("Xyphex.X", 775.0f)
            makers.put("Xyphex.XI", 1201.0f)
            makers.put("Xyphex.XII", 1861.0f)
            makers.put("Xyphex.XIII", 2885.0f)
            makers.put("Xyphex.XIV", 4471.0f)
            makers.put("Xyphex.XV", 6930.0f)
            makers.put("Xyphex.XVI", 10742.0f)
            makers.put("Xyphex.XVII", 16649.0f)
            makers.put("Xyphex.XVIII", 25807.0f)
            makers.put("Xyphex.XIX", 40000.0f)
            makers.put("Xyphex.XX", 62000.0f)
        }

        //packets that originate from the client.
        private val clientPackets: HashMap<Any?, Any?> = HashMap()

        init {
            clientPackets.put("fetchports", 0)
            clientPackets.put("setdefaultport", 0)
            clientPackets.put("changenetwork", 0)
            clientPackets.put("healport", 0)
            clientPackets.put("requestequipment", 0)
            clientPackets.put("installequipment", 0)
            clientPackets.put("repairequipment", 1)
            clientPackets.put("fetchwatches", 0)
            clientPackets.put("requestpage", 1)
            clientPackets.put("requestpurchase", 0)
            clientPackets.put("requesttrigger", 0)
            clientPackets.put("requestsave", 0)
            clientPackets.put("requesttask", 0)
            clientPackets.put("requestwebpage", 1)
            clientPackets.put("submit", 0)
            clientPackets.put("makebounty", 0)
            clientPackets.put("exit", 0)
            clientPackets.put("vote", 0)
            clientPackets.put("savepage", 0)
            clientPackets.put("withdraw", 0)
            clientPackets.put("requestdirectory", 0)
            clientPackets.put("unlock", 0)
            clientPackets.put("setftppassword", 0)
            clientPackets.put("requestsecondarydirectory", 0)
            clientPackets.put("requestcancelattack", 0)
            clientPackets.put("cluedata", 0)
            clientPackets.put("requestzombiecancelattack", 0)
            clientPackets.put("installapplication", 0)
            clientPackets.put("installwatch", 0)
            clientPackets.put("setwatchobservedports", 0)
            clientPackets.put("installfirewall", 0)
            clientPackets.put("replaceapplication", 0)
            clientPackets.put("uninstallport", 0)
            clientPackets.put("portonoff", 0)
            clientPackets.put("peekcode", 0)
            clientPackets.put("peeklogs", 0)
            clientPackets.put("saveportnote", 0)
            clientPackets.put("setwatchquantity", 0)
            clientPackets.put("setwatchonoff", 0)
            clientPackets.put("setwatchnote", 0)
            clientPackets.put("setwatchsearchfirewall", 0)
            clientPackets.put("deletewatch", 0)
            clientPackets.put("deletefirewall", 0)
            clientPackets.put("changewatchport", 0)
            clientPackets.put("changewatchtype", 0)
            clientPackets.put("deletefolder", 0)
            clientPackets.put("setdummyport", 0)
            clientPackets.put("changedailypay", 0)
            clientPackets.put("deletelogs", 0)
            clientPackets.put("createfolder", 0)
            clientPackets.put("put", 0)
            clientPackets.put("get", 0)
            clientPackets.put("malget", 0)
            clientPackets.put("requestfile", 0)
            clientPackets.put("requestgame", 0)
            clientPackets.put("requestscan", 1)
            clientPackets.put("savefile", 0)
            clientPackets.put("compilefile", 0)
            clientPackets.put("deletemulti", 0)
            clientPackets.put("deletefile", 0)
            clientPackets.put("setfiledescription", 0)
            clientPackets.put("setfileprice", 0)
            clientPackets.put("emptypettycash", 0)
            clientPackets.put("finalizecancelled", 0)
            clientPackets.put("requestattack", 1)
            clientPackets.put("requestzombieattack", 1)
            clientPackets.put("transfer", 0)
            clientPackets.put("deposit", 0)
            clientPackets.put("dochallenge", 0)
            clientPackets.put("sellfile", 0)
            clientPackets.put("sellfilemulti", 0)
            clientPackets.put("decompilefile", 0)
        }

        val LOCAL_AUTH_FALLBACK: Boolean =
            !"false".equals(getPropertySafe("hackwars.localAuthFallback", "true"), ignoreCase = true)

        const val ATTACK_RATE: Long = 2000 //How frequently should an attack tack place.
        const val CHANGE_NETWORKS: Long = 180000 //How often can the player change networks?

        const val PACKET_TIMEOUT: Long = 500 //How frequently should we generate a packet?
        const val PING_TIMEOUT: Long =
            20000 //how long should we wait after a ping to determine whether the player has closed the client or not.
        const val CLIENT_PACKET_TIMEOUT: Long = 600000
        const val CAPTCHA_COUNT: Int = 200

        //private static final long COMPUTER_TIMEOUT=60000;//-(int)(1000*Math.random());//How long before we re-write the computer to disk.
        const val PAY_PERIOD: Long = 43200000 //How often should we be paid. (Per Day)
        const val PLAYER: Int = 0
        const val NPC: Int = 1
        const val COUNTDOWN_LENGTH: Long = 180000 //How long should a countdown take?

        const val SLEEP_TIME: Long = 50 //How often can we process a remote call?
        const val OVER_HEAT_TIME: Long = 60000 //How long should an overheat take place for.

        const val DEDRICKS_QUEST: Int = 10

        //Player's experience in the various skills.
        @JvmField
        val CPU_CHART: FloatArray =
            floatArrayOf(50.0f, 100.0f, 150.0f, 200.0f, 250.0f, 300.0f, 75.0f) //Maximum Loads of various CPUs.
        @JvmField
        val MEMORY_CHART: FloatArray = floatArrayOf(8.0f, 16.0f, 24.0f, 32.0f, 8.0f) //Maximum Port Count.
        @JvmField
        val WATCH_CHART: IntArray = intArrayOf(4, 6, 8, 12, 5)
        const val Plutonium: Int = 4
        const val YBCO: Int = 3
        const val Silicon: Int = 2
        const val Germanium: Int = 1
        const val DuctTape: Int = 0
        @JvmField
        var commodityString: Array<String> = arrayOf("Duct Tape", "Germanium", "Silicon", "YBCO", "Plutonium")
        @JvmField
        var requiredRepairLevel: IntArray = intArrayOf(0, 15, 45, 75, 90)
        @JvmField
        var commodityXP: FloatArray = floatArrayOf(20.0f, 40.0f, 100.0f, 400.0f, 1000.0f)
        const val MAX_PORT: Int = 32

        /**
         * Used to encrypt and decrypt XOR encrypted data.
         */
        fun crypt(data: ByteArray, key: String): String {
            for (ii in data.indices) {
                data[ii] = (data[ii].toInt() xor key.toByteArray()[ii % key.length].toInt()).toByte()
            }
            return (String(data))
        }

        /**
         * Generate the CAPTCHA image. (100x20)
         */
        fun generateImage(): Array<Any?> {
            val challenge = ComputerSessionService().generateCaptcha()
            return arrayOf<Any?>(challenge.pixels, challenge.key)
        }


        //Testing main.
        @JvmStatic
        fun main(args: Array<String>) {
        }
    }
}
internal class ComputerLoadCoordinator(
    private val sessionService: ComputerSessionService,
    private val xmlComputerPersistence: XmlComputerPersistence,
    private val persistenceSupport: LegacyComputerPersistenceSupport,
    private val postLoadBootstrap: ComputerPostLoadBootstrap = ComputerPostLoadBootstrap()
) {
    constructor(
        sessionService: ComputerSessionService,
        xmlComputerPersistence: XmlComputerPersistence,
        persistenceSupport: LegacyComputerPersistenceSupport
    ) : this(sessionService, xmlComputerPersistence, persistenceSupport, ComputerPostLoadBootstrap())

    fun execute(computer: Computer) {
        try {
            authenticatePendingConnection(computer)

            val activeLoad = !computer.loadRequester.isNullOrEmpty()
            if (!activeLoad) {
                computer.loggedIn = true
                computer.logInTime = computer.MyTime.currentTime
            }

            computer.upgradedAccount = false
            computer.inactive = false
            computer.MAX_OPS = Computer.FREE_MAX_OPS
            computer.FILE_SIZE_LIMIT = Computer.FREE_FILE_SIZE_LIMIT

            val functionPackResult = sessionService.requestFunctionPacks(computer.ip)
            computer.upgradedAccount = functionPackResult.upgradedAccount
            computer.inactive = functionPackResult.inactive
            computer.MAX_OPS = functionPackResult.maxOps
            computer.FILE_SIZE_LIMIT = functionPackResult.fileSizeLimit

            val xml = sessionService.loadLocalSaveXml(computer.ip, activeLoad)
            val snapshot = xmlComputerPersistence.parse(xml)
            persistenceSupport.restoreSnapshot(computer, snapshot)
        } catch (e: Exception) {
            computer.errorMessage = e.message?.takeIf { it.isNotEmpty() }
                ?: "Unable to load local account data for ip=${computer.ip}."
            e.printStackTrace()
            computer.LOAD_FAILURE = true
        }

        computer.Loaded = true
        computer.Loading = false
        postLoadBootstrap.apply(computer)
    }

    private fun authenticatePendingConnection(computer: Computer) {
        if (computer.connectionID == -1) {
            return
        }

        if (!computer.checkLogin()) {
            computer.MyHackerServer!!.addData(arrayOf(LoginFailedAssignment(0), computer.connectionID))
            computer.connectionID = -1
            return
        }

        val randomKey = computer.MyHackerServer!!.getRandomKey(computer.ip, computer.getClientHash(), computer.publicKey)
        val loginSuccessAssignment = LoginSuccessAssignment(0, computer.ip, randomKey[0] as String, computer.isNPC())
        loginSuccessAssignment.setPublicKey(randomKey[1] as ByteArray)
        computer.MyHackerServer!!.addData(arrayOf(loginSuccessAssignment, computer.connectionID))
    }
}

internal class ComputerPostLoadBootstrap {
    fun apply(computer: Computer) {
        if (computer.connectionID == -1) {
            return
        }

        if (computer.LOAD_FAILURE) {
            computer.MyHackerServer!!.addData(arrayOf(LoginFailedAssignment(0), computer.connectionID))
            computer.connectionID = -1
            return
        }

        computer.MyComputerHandler.getMyComputerHandler().addComputer(computer)
        computer.PA.setPacketNetwork(Network.getInstance(computer.MyComputerHandler).getNetworkInformation(computer.network))
        computer.sendPreferences = true
    }
}
