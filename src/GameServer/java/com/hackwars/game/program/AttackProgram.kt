/**
 * AttackProgram.java
 * 
 * 
 * A class for housing attack scripts and storing various state information.
 */
package com.hackwars.game.program

import com.hackwars.game.program.attack.AttackContinueHandler
import com.hackwars.game.program.attack.AttackFinalizeHandler
import com.hackwars.game.program.attack.AttackFunctionHandler
import com.hackwars.game.program.attack.AttackInitializeHandler
import com.hackwars.game.program.attack.RequestAttackHandler
import com.hackwars.game.program.attack.RequestCancelAttackHandler
import com.hackwars.game.program.attack.ZombieAttackHandler
import game.*
import hackscript.model.RunFactory
import org.slf4j.LoggerFactory

class AttackProgram(
    MyComputer: Computer?,
    ParentPort: Port?,
    MyComputerHandler: NetworkSwitch?,
    Choices: ArrayList<Any?>?,
    MyMakeBounty: MakeBounty?
) : Program() {
    companion object {
        //TIMEOUT FOR HOW LONG A SINGLE ATTACK IS ALLOWED TO CONTINUE.
        private const val ATTACK_TIMEOUT: Long = 450000
        private const val PWN_PERCENT = 0.005
        private val Logger = LoggerFactory.getLogger(AttackProgram::class.java)
    }

    internal var attackStart: Long = 0

    //Installed Scripts.
    internal var continueScript: String? = "" //Script that runs during each iteration of an attack.
    internal var finalizeScript: String? = ""
    internal var initializeScript: String? = ""

    private var ParentPort: Port? = null //The Port that this program is installed on.

    internal var SecondaryTargets: ArrayList<Any?> = ArrayList() //An array of secondary targets to attack.
    internal var currentTarget = 0

    private var MyComputer: Computer? = null //The computer that the parent port is attached to.
    private var MyComputerHandler: NetworkSwitch? = null //Computer handler for dispatching messages to other computers.
    private var MyMakeBounty: MakeBounty? = null //Used for checking bounties as an attack takes place.

    //State information.
    internal var targetIP = "" //IP address being targeted with attack.
    internal var maliciousIP = "" //The IP of a player who has maliciously hooked into this attack.
    internal var zombieIP = "" //The IP of the individual allowed to hook into this attack(if anyone).
    internal var zombie = false //Is the port currently in zombie mode?
    internal var choicesShown = false //Has the show choices dialogue been shown yet for this attack?
    internal var isNPC = false //Is the target of theh attack an NPC.
    internal var switching = false //Is the attack switching?
    internal var dealDamage = true //Should damage be dealth in this iteration?
    internal var targetPort = 0 //Port being targeted with attack.
    internal var targetPortType = 0 //Type of port being attacked.
    internal var iterations = 0 //Iterations of attack to date.
    private val targetPortPettyCash = 0.0f //Amount of cash in target's petty cash.
    internal var pettyCashTarget = 0.0f //Amount in opponent's petty cash that should promote stealing.

    internal var MaliciousCode: Array<Array<String?>?>? =
        Array<Array<String?>?>(4) { arrayOfNulls<String>(2) }  //Potential malicious code to install.
    internal var MaliciousParameters: Array<Any?>? = null //Parameters to initialize malicious code with.

    //Bank Malicious IP : Bank Petty Cash Target : FTP Malicious IP : Petty Cash Target
    //This array is used to add choices requests into the choices array.
    private var Choices: ArrayList<Any?>? = null

    private val functionHandlers = linkedMapOf<String, AttackFunctionHandler>()

    //reference to the window open in the client running this attack.
    @JvmField
    var windowHandle: Int = 0

    /**
     * Get the computer that this attack program is attached to.
     */
    override fun getComputer(): Computer? {
        return (MyComputer)
    }

    /**
     * Set whether or not the attack is currently switching.
     */
    fun setSwitching(switching: Boolean) {
        this.switching = switching
    }

    /**
     * Set whether or not this damage should be dealt in this iteration of attck.
     */
    fun setDamage(dealDamage: Boolean) {
        this.dealDamage = dealDamage
    }

    val iP: String?
        /**
         * Get the IP of the computer associated with this program.
         */
        get() = (MyComputer!!.ip)

    /**
     * Return the malicious IP currently associated with this attack if it is
     * being used remotely.
     */
    fun getMaliciousIP(): String? {
        return (maliciousIP)
    }

    val port: Int
        /**
         * Get the port number of the port that this program is installed on.
         */
        get() = (ParentPort!!.number)

    val savedMaliciousIP: String?
        /**
         * Get the malicious IP associated with the parent port.
         */
        get() = (ParentPort!!.maliciousTarget)

    fun getWindowHandle(): Int {
        return (windowHandle)
    }

    /**
     * Cancel an attack.
     */
    fun cancelAttack(heal: Boolean) {
        if (this.getTargetIP() != "") {
            if (!zombie) {
                MyComputerHandler!!.addData(
                    ApplicationData("cancelattack", heal, this.getTargetPort(), this.iP),
                    this.getTargetIP()
                )
            } else {
                MyComputerHandler!!.addData(
                    ApplicationData("cancelattack", heal, this.getTargetPort(), maliciousIP),
                    this.getTargetIP()
                )
            }
        }


        this.attacking = false
    }

    /**
     * Return whether or not the attack currently taking place is in zombie mode.
     */
    fun isZombie(): Boolean {
        return (zombie)
    }

    /**
     * Checks whether a condition has been met in a bounty.
     */
    fun checkBounty(InstallFile: HackerFile?, BountyType: Int) {
        MyMakeBounty!!.checkBounty(MyComputer, InstallFile, BountyType, targetIP, isNPC, "")
    }

    /**
     * Berserk!!!!!!
     */
    fun berserk() {
        //Damage your opponent.
        var damage = MyComputer!!.getDamage("Attack")

        if (!zombie) {
            val O: Array<Any?>? = arrayOf<Any?>(
                damage + MyComputer!!.equipmentSheet.getDamageBonus(),
                ParentPort!!.ip,
                ParentPort!!.number,
                false,
                null,
                windowHandle,
                -1
            )
            val AD = ApplicationData("damage", O, targetPort, MyComputer!!.ip)
            AD.sourcePort = ParentPort!!.number
            MyComputerHandler!!.addData(AD, targetIP)
        } else {
            val O: Array<Any?>? = arrayOf<Any?>(
                damage + MyComputer!!.equipmentSheet.getDamageBonus(),
                ParentPort!!.ip,
                ParentPort!!.number,
                false,
                ParentPort!!.ip,
                windowHandle,
                -1
            )
            val AD = ApplicationData("damage", O, targetPort, maliciousIP)
            AD.sourcePort = ParentPort!!.number
            MyComputerHandler!!.addData(AD, targetIP)
        }

        //Damage yourself.
        damage /= 2.0f
        ParentPort!!.setLastAccessed(MyComputer!!.currentTime - (Port.timeOut - 15000))
        ParentPort!!.damagePort(damage)
    }

    val targetHP: Float
        /**
         * Get the HP of the port being targeted with this attack.
         */
        get() = (ParentPort!!.targetHP)

    val targetPettyCash: Float
        /**
         * Return the value o the petty cash in the target port.
         */
        get() = (ParentPort!!.targetPettyCash)

    /**
     * Set the target port of this attack.
     */
    fun setTargetPort(targetPort: Int) {
        this.targetPort = targetPort
    }

    val cPULoad: Float
        /**
         * Get the CPU load on the computer that this attack is associated with.
         */
        get() = (MyComputer!!.cpuLoad)

    val maximumCPULoad: Float
        /**
         * Get the maximum CPU load of the current CPU installed
         */
        get() = (MyComputer!!.maximumCPULoad)

    var attacking: Boolean
        /**
         * Get whether the parent port is currently in an attacking state.
         */
        get() = (ParentPort!!.attacking)
        /**
         * Set whether or not this attacks' parent port is still attacking.
         */
        set(attacking) {
            ParentPort!!.setAttacking(attacking)
            if (!attacking) { //If the attack is being canceled reset the variables.
                zombie = false
                maliciousIP = ""
                zombieIP = ""
                windowHandle = 0
            }
        }

    /**
     * Get the port that this attack is targeting.
     */
    fun getTargetPort(): Int {
        return (targetPort)
    }

    val sourcePort: Int
        /**
         * Get the parent port number that this attack is attached to.
         */
        get() {
            if (ParentPort != null) {
                return (ParentPort!!.number)
            }
            return (0)
        }

    val hP: Float
        /**
         * Get the HP of the port that this program is attached to.
         */
        get() = (ParentPort!!.health)

    /**
     * Get the target IP of the current attack taking place.
     */
    fun getTargetIP(): String? {
        return (targetIP)
    }

    val targetCPUCost: Float
        /**
         * Get the CPU cost of the port being attacked.
         */
        get() = (ParentPort!!.targetCPUCost)

    val targetWatch: Boolean
        /**
         * Get whether the port being targeted with an attack has a watch installed.
         */
        get() = (ParentPort!!.targetWatch)

    /**
     * Sets the string of the player who is allowed to run this port remotely.
     */
    fun zombie(zombieIP: String) {
        this.zombieIP = zombieIP
    }

    /**
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    override fun installScript(Script: HashMap<*, *>) {
        continueScript = Script.get("continue") as String?
        initializeScript = Script.get("initialize") as String?
        finalizeScript = Script.get("finalize") as String?
    }

    /**
     * Return how many times the current attack has iterated.
     */
    fun getIterations(): Int {
        return (iterations)
    }

    override fun execute(MyApplicationData: ApplicationData) {
        if (hasAttackTimedOut()) {
            handleAttackTimeout()
            return
        }

        functionHandlers[MyApplicationData.function]?.execute(this, MyApplicationData)
    }

    /**
     * Return the array list of secondary attack targets.
     */
    fun nextTarget(): Int {
        if (SecondaryTargets.size == 0) return (-1)
        currentTarget = (1 + currentTarget) % SecondaryTargets.size
        val returnMe = SecondaryTargets.get(currentTarget) as Int

        return (returnMe)
    }

    /**
     * Returns the last file that was accessed via getMaliciousCode()
     */
    @JvmField
    var LastFile: HackerFile? = null

    //Constructor.
    init {
        super.setComputerHandler(MyComputerHandler)
        super.setComputer(MyComputer)
        if (MyComputer != null) this.attackStart = MyComputer.currentTime
        this.MyComputer = MyComputer
        this.ParentPort = ParentPort
        this.MyComputerHandler = MyComputerHandler
        this.Choices = Choices
        this.MyMakeBounty = MyMakeBounty
        registerDefaultFunctionHandlers()
    }

    fun getLastFile(): HackerFile? {
        return (LastFile)
    }


    /**
     * Return the program that should be installed based on type and malicious code.
     */
    fun getMaliciousCode(): HashMap<*, *>? {
        if (MaliciousCode!![0] == null) return (null)
        if (MaliciousCode!![0]!![0] == null) {
            MyComputer!!.addMessage(
                MessageHandler.INSTALL_SCRIPT_FAIL_NO_FILE,
                arrayOf<Any?>(),
                arrayOf<Any?>(windowHandle, MyComputer!!.ip)
            )
            MyComputer!!.addMessage(MessageHandler.INSTALL_SCRIPT_FAIL_NO_FILE_GAME)
            return (null)
        }
        val HF = MyComputer!!.fileSystem.getFile(MaliciousCode!![0]!![0], MaliciousCode!![0]!![1])
        if (HF == null) return (null)

        val fileType = HF.type
        var wrongType = true
        if (targetPortType == Port.BANKING && fileType == HackerFile.BANKING_COMPILED) {
            wrongType = false
        } else if (targetPortType == Port.ATTACK && fileType == HackerFile.ATTACKING_COMPILED) {
            wrongType = false
        } else if (targetPortType == Port.SHIPPING && fileType == HackerFile.SHIPPING_COMPILED) {
            wrongType = false
        } else if (targetPortType == Port.FTP && fileType == HackerFile.FTP_COMPILED) {
            wrongType = false
        }
        if (wrongType) {
            MyComputer!!.addMessage(
                MessageHandler.INSTALL_SCRIPT_FAIL_WRONG_TYPE,
                arrayOf<Any?>(),
                arrayOf<Any?>(windowHandle, MyComputer!!.ip)
            )
            MyComputer!!.addMessage(MessageHandler.INSTALL_SCRIPT_FAIL_WRONG_TYPE_GAME)
            return (null)
        }
        LastFile = HF

        HF.quantity = HF.quantity - 1
        if (HF.quantity <= 0) {
            MyComputer!!.fileSystem.deleteFile(MaliciousCode!![0]!![0], MaliciousCode!![0]!![1])
        }

        return (HF.content)
    }

    /**
     * Returns the keys associated with this program type.
     */
    override fun getTypeKeys(): Array<String?> {
        val returnMe: Array<String?>? = arrayOf<String?>("continue", "initialize", "finalize")
        return (returnMe!!)
    }


    /**
     * Return a hash map representation of the program currently installed on this port.
     */
    override fun getContent(): HashMap<*, *> {
        val returnMe: HashMap<Any?, Any?> = HashMap()
        returnMe.put("continue", continueScript)
        returnMe.put("initialize", initializeScript)
        returnMe.put("finalize", finalizeScript)
        return (returnMe)
    }

    /**
     * Get the parameters provided for installing malicious scripts.
     */
    fun getMaliciousParameters(): Array<Any?>? {
        return (MaliciousParameters)
    }

    /**
     * Set the petty cash amount used to decide when stealing should take place.
     */
    fun setPettyCashTarget(pettyCashTarget: Float) {
        this.pettyCashTarget = pettyCashTarget
    }

    /**
     * Get the petty cash amount used to decide when stealing should take place.
     */
    fun getPettyCashTarget(): Float {
        return (pettyCashTarget)
    }

    /**
     * Put the request to show choices into the show choices array.
     */
    fun showChoices() {
        if (!choicesShown) {
            val o: Array<Any?> = arrayOf(targetIP, targetPort, targetPortType, windowHandle)
            if (!zombie) {
                Choices!!.add(o)
                MyComputer!!.sendPacket()
            } else {
                MyComputerHandler!!.addData(
                    ApplicationData("addshowchoices", o, this.getTargetPort(), this.iP),
                    maliciousIP
                )
                MyComputer!!.sendPacket()
            }
        }
        choicesShown = true
    }

    /**
     * Output the class data in XML format.
     */
    override fun outputXML(): String {
        var returnMe: String? = "<code>\n"
        returnMe += "<initialize><![CDATA["
        if (initializeScript != null) returnMe += initializeScript!!.replace("]]>".toRegex(), "]]&gt;")
        else returnMe += initializeScript
        returnMe += "]]></initialize>\n"
        returnMe += "<continue><![CDATA["
        if (continueScript != null) returnMe += continueScript!!.replace("]]>".toRegex(), "]]&gt;")
        else returnMe += continueScript
        returnMe += "]]></continue>\n"
        returnMe += "<finalize><![CDATA["
        if (finalizeScript != null) returnMe += finalizeScript!!.replace("]]>".toRegex(), "]]&gt;")
        else returnMe += finalizeScript
        returnMe += "]]></finalize>\n"
        returnMe += "</code>\n"
        return (returnMe)
    }

    internal val computer: Computer
        get() = MyComputer!!

    internal val computerHandler: NetworkSwitch
        get() = MyComputerHandler!!

    internal val parentPort: Port
        get() = ParentPort!!

    internal val sourceIP: String
        get() = iP!!

    internal fun hasAttackTimedOut(): Boolean =
        this.attacking && computer.currentTime - attackStart > ATTACK_TIMEOUT

    internal fun handleAttackTimeout() {
        if (!zombie) {
            computerHandler.addData(
                ApplicationData("cancelattack", true, this.getTargetPort(), this.iP),
                this.getTargetIP()
            )
            computerHandler.addData(
                ApplicationData(
                    "message",
                    arrayOf<Any>(
                        MessageHandler.ATTACK_EXCEEDED_TIMEOUT,
                        arrayOf<Any?>(),
                        arrayOf<Any?>(windowHandle, this.iP)
                    ),
                    0,
                    this.iP
                ),
                this.iP
            )
        } else {
            computerHandler.addData(
                ApplicationData(
                    "message",
                    arrayOf<Any>(
                        MessageHandler.ATTACK_EXCEEDED_TIMEOUT,
                        arrayOf<Any?>(),
                        arrayOf<Any?>(windowHandle, maliciousIP)
                    ),
                    0,
                    this.iP
                ),
                maliciousIP
            )
            computerHandler.addData(
                ApplicationData("cancelattack", true, this.getTargetPort(), maliciousIP),
                this.getTargetIP()
            )
        }

        this.attacking = false
    }

    internal fun runScript(script: String) {
        runCatching {
            RunFactory.runCode(script, HackerLinker(this, MyComputerHandler), computer.MAX_OPS)
        }.onFailure { Logger.error("Error running script: $script", it) }
    }

    internal fun resetSecondaryTargets(targetPort: Int, targets: Array<Int?>) {
        SecondaryTargets = ArrayList()
        SecondaryTargets.add(targetPort)
        currentTarget = 0
        for (index in targets.indices) {
            SecondaryTargets.add(targets[index])
        }
    }

    internal fun removeSecondaryTarget(targetPort: Int) {
        val iterator = SecondaryTargets.iterator()
        while (iterator.hasNext()) {
            val tempPort = iterator.next() as Int
            if (tempPort == targetPort) {
                iterator.remove()
            }
        }
    }

    internal fun registerFunctionHandler(handler: AttackFunctionHandler) {
        functionHandlers[handler.functionName] = handler
    }

    private fun registerDefaultFunctionHandlers() {
        registerFunctionHandler(AttackContinueHandler())
        registerFunctionHandler(AttackInitializeHandler())
        registerFunctionHandler(AttackFinalizeHandler())
        registerFunctionHandler(ZombieAttackHandler())
        registerFunctionHandler(RequestAttackHandler())
        registerFunctionHandler(RequestCancelAttackHandler())
    }
}
