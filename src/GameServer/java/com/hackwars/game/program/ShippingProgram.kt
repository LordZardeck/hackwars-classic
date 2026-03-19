/**
 * AttackProgram.java
 * 
 * 
 * A mining program is almost exactly like an attack with a couple minor differences:
 * 
 * 
 * 1) The damage is determined based on the mining level.
 * 2) You can't use the zombie play mechanic.
 */
package com.hackwars.game.program

import game.*
import hackscript.model.RunFactory

class ShippingProgram(computer: Computer?, computerHandler: NetworkSwitch?, private var parentPort: Port?) :
    Program(computer, computerHandler) {
    private var attackStart: Long = computer?.currentTime ?: 0

    /**
     * Sets the current commodity being mined for.
     */
    var currentCommodity: Int = 0 //The current commodity being mined for.
        /**
         * Returns the current commodity being mined.
         */
        get() = (field)

    //Installed Scripts.
    private var continueScript: String? = "" //Script that runs during each iteration of an attack.
    private var finalizeScript: String? = ""
    private var initializeScript: String? = ""

    private var SecondaryTargets: ArrayList<Any?> = ArrayList() //An array of secondary targets to attack.
    private var currentTarget = 0

    //State information.
    private var targetIP = "" //IP address being targeted with attack.
    private var choicesShown = false //Has the show choices dialogue been shown yet for this attack?
    private var isNPC = false //Is the target of theh attack an NPC.
    private var switching = false //Is the attack switching?
    private var dealDamage = true //Should damage be dealth in this iteration?
    private var targetPort = 0 //Port being targeted with attack.
    private var targetPortType = 0 //Type of port being attacked.
    private var iterations = 0 //Iterations of attack to date.

    private var MaliciousCode: Array<Array<String?>?>? =
        Array<Array<String?>?>(4) { arrayOfNulls<String>(2) }  //Potential malicious code to install.
    val maliciousParameters: Array<Any?>? = null //Parameters to initialize malicious code with.
        /**
         * Get the parameters provided for installing malicious scripts.
         */
        get() = (field)

    //Bank Malicious IP : Bank Petty Cash Target : FTP Malicious IP : Petty Cash Target
    private var windowHandle = 0

    /**
     * Cancel an attack.
     */
    fun cancelAttack(heal: Boolean) {
        if (getTargetIP() != "") {
            computerHandler!!.addData(
                ApplicationData("cancelattack", heal, this.getTargetPort(), this.iP),
                this.getTargetIP()
            )
            this.attacking = false
        }
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
        get() = (computer!!.getIP())

    val port: Int
        /**
         * Get the port number of the port that this program is installed on.
         */
        get() = (parentPort!!.getNumber())

    val savedMaliciousIP: String?
        /**
         * Get the malicious IP associated with the parent port.
         */
        get() = (parentPort!!.getMaliciousTarget())

    val targetHP: Float
        /**
         * Get the HP of the port being targeted with this attack.
         */
        get() = (parentPort!!.getTargetHP())

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
        get() = (computer!!.getCPULoad())

    val maximumCPULoad: Float
        /**
         * Get the maximum CPU load of the current CPU installed
         */
        get() = (computer!!.getMaximumCPULoad())

    var attacking: Boolean
        /**
         * Get whether the parent port is currently in an attacking state.
         */
        get() = (parentPort!!.getAttacking())
        /**
         * Set whether or not this attacks' parent port is still attacking.
         */
        set(attacking) {
            parentPort!!.setAttacking(attacking)
            if (!attacking) {
                windowHandle = 0
            }
        }

    /**
     * Get the port that this attack is targeting.
     */
    fun getTargetPort(): Int {
        return (targetPort)
    }

    val hP: Float
        /**
         * Get the HP of the port that this program is attached to.
         */
        get() = (parentPort!!.getHealth())

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
        get() = (parentPort!!.getTargetCPUCost())

    val targetWatch: Boolean
        /**
         * Get whether the port being targeted with an attack has a watch installed.
         */
        get() = (parentPort!!.getTargetWatch())

    /**
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    override fun installScript(script: HashMap<*, *>) {
        continueScript = script.get("continue") as String?
        initializeScript = script.get("initialize") as String?
        finalizeScript = script.get("finalize") as String?
    }

    /**
     * Return how many times the current attack has iterated.
     */
    fun getIterations(): Int {
        return (iterations)
    }

    fun getWindowHandle(): Int {
        return (windowHandle)
    }

    override fun execute(applicationData: ApplicationData) {
        if (this.attacking && computer!!.getCurrentTime() - attackStart > ATTACK_TIMEOUT) { //Attacks can only take up to 5 minutes.

            computerHandler!!.addData(
                ApplicationData("cancelattack", null, this.getTargetPort(), this.iP),
                this.getTargetIP()
            )
            computerHandler!!.addData(
                ApplicationData(
                    "message", MessageHandler.REDIRECT_EXCEEDED_MAXIMUM_TIMEOUT, 0,
                    this.iP
                ), this.iP
            )

            this.attacking = false
            return
        }

        var execute: String? = null
        if (applicationData.getFunction() == "attackcontinue") {
            iterations++
            execute = continueScript
            val damage = computer!!.getDamage("Redirecting")

            try {
                val HL = HackerLinker(this, computerHandler)
                RunFactory.runCode(execute, HL, computer!!.MAX_OPS)
            } catch (e: Exception) {
            }

            if (dealDamage) { //Should the port deal damage this iteration.
                val O: Array<Any?>? = arrayOf<Any?>(
                    damage + computer!!.getEquipmentSheet().getMiningBonus(),
                    parentPort!!.getIP(),
                    parentPort!!.getNumber(),
                    false,
                    null,
                    windowHandle,
                    this.currentCommodity
                )
                val AD = ApplicationData("damage", O, targetPort, computer!!.getIP())
                AD.setSourcePort(parentPort!!.getNumber())
                computerHandler!!.addData(AD, targetIP)
            }
            dealDamage = true

            return
        } else if (applicationData.getFunction() == "attackinitialize" && !switching && !parentPort!!.getAttacking()) {
            computerHandler!!.addData(
                ApplicationData("pettycash", -10.0f, 0, computer!!.getIP()),
                computer!!.getIP()
            )
            choicesShown = false
            attackStart = computer!!.getCurrentTime()

            iterations = 0

            val parameters = applicationData.getParameters() as Array<Any?>
            val F = parameters[0] as Array<Float?>
            val targetWatch = parameters[1] as Boolean
            if (parameters.size > 2) isNPC = (parameters[2] as Boolean?)!!

            parentPort!!.setTargetHP(F!![1]!!)
            parentPort!!.setTargetPettyCash(F[2]!!)
            parentPort!!.setTargetCPUCost(F[3]!!)
            parentPort!!.setTargetWatch(targetWatch)

            targetIP = applicationData.getSourceIP()
            targetPort = applicationData.getSourcePort()
            execute = initializeScript

            this.attacking = true

            try {
                val HL = HackerLinker(this, computerHandler)
                RunFactory.runCode(execute, HL, computer!!.MAX_OPS)
            } catch (e: Exception) {
            }

            if (!computer!!.checkBank()) {
                computerHandler!!.addData(
                    ApplicationData("cancelattack", null, this.getTargetPort(), this.iP),
                    this.getTargetIP()
                )
                this.attacking = false
                return
            }
        } else if (applicationData.getFunction() == "attackfinalize" && parentPort!!.getAttacking()) {
            targetPortType = (applicationData.getParameters() as Int?)!!
            targetPort = applicationData.getSourcePort()

            val MyIterator = SecondaryTargets.iterator()
            while (MyIterator.hasNext()) { //Remove this port from our list of secondary targets.
                val tempPort = MyIterator.next() as Int
                if (tempPort == targetPort) MyIterator.remove()
            }


            try {
                val HL = HackerLinker(this, computerHandler)
                RunFactory.runCode(finalizeScript, HL, computer!!.MAX_OPS)
            } catch (e: Exception) {
            }

            computer!!.addMessage(
                MessageHandler.REDIRECT_FINISHED, arrayOf<Any>(this.port), arrayOf<Any?>(
                    windowHandle,
                    this.iP
                )
            )
            computer!!.addMessage(MessageHandler.REDIRECT_FINISHED_GAME, arrayOf<Any>(this.port))

            computerHandler!!.addData(
                ApplicationData("cancelattack", null, this.getTargetPort(), this.iP),
                this.getTargetIP()
            )
            computer!!.incrementSuccessfulHacks()
            this.attacking = false
            return
        } else if (applicationData.getFunction() == "requestattack") {
            val parameters = applicationData.getParameters() as Array<Any?>
            val windowHandle = parameters[5] as Int
            if (parentPort!!.getAttacking()) {
                computerHandler!!.addData(
                    ApplicationData(
                        "message", MessageHandler.REDIRECT_FAIL_ALREADY_REDIRECTING, 0,
                        this.iP
                    ), this.iP
                )
                return
            }
            if (parentPort!!.getOverHeated()) {
                computerHandler!!.addData(
                    ApplicationData(
                        "message", MessageHandler.REDIRECT_FAIL_OVERHEATED, 0,
                        this.iP
                    ), this.iP
                )
                return
            }

            switching = false
            this.windowHandle = windowHandle
            //Check whether you have enough money in your account to perform an attack.
            if (!computer!!.checkBank()) {
                computer!!.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND)
            } else if (computer!!.getPettyCash() >= 10.0f) {
                targetIP = parameters[0] as String
                targetPort = (parameters[1] as Int?)!!

                if (parameters.size > 2) { //Add any secondary targets.
                    SecondaryTargets = ArrayList()
                    SecondaryTargets.add(targetPort)
                    currentTarget = 0


                    val I = parameters[2] as Array<Int?>
                    for (i in I!!.indices) SecondaryTargets.add(I[i])
                }

                if (parameters.size > 3) { //Add malicious scripts
                    MaliciousCode = parameters[3] as Array<Array<String?>?>
                }

                val AD = ApplicationData(
                    "mine",
                    computer!!.getNetwork(),
                    targetPort,
                    applicationData.getSourceIP()
                )
                AD.setSourcePort(parentPort!!.getNumber())
                computerHandler!!.addData(AD, targetIP)
            } else computer!!.addMessage(MessageHandler.REDIRECT_FAIL_NOT_ENOUGH_MONEY)

            return
        } else if (applicationData.getFunction() == "requestcancelattack" && parentPort!!.getAttacking() && (applicationData.getSourceIP() == parentPort!!.getIP())) {
            computerHandler!!.addData(
                ApplicationData("cancelattack", null, this.getTargetPort(), this.iP),
                this.getTargetIP()
            )
            this.attacking = false
            return
        } else return
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
        this.parentPort = parentPort
    }

    fun getLastFile(): HackerFile? {
        return (LastFile)
    }

    /**
     * Return the program that should be installed based on type and malicious code.
     */
    fun getMaliciousCode(): HashMap<*, *>? {
        if (MaliciousCode!![targetPortType] == null) return (null)

        val HF = computer!!.getFileSystem()
            .getFile(MaliciousCode!![targetPortType]!![0], MaliciousCode!![targetPortType]!![1])

        if (HF == null) return (null)

        LastFile = HF

        HF.setQuantity(HF.getQuantity() - 1)
        if (HF.getQuantity() <= 0) {
            computer!!.getFileSystem()
                .deleteFile(MaliciousCode!![targetPortType]!![0], MaliciousCode!![targetPortType]!![1])
        }

        return (HF.getContent())
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

    companion object {
        //TIMEOUT FOR HOW LONG A SINGLE ATTACK IS ALLOWED TO CONTINUE.
        private const val ATTACK_TIMEOUT: Long = 450000
        private const val PWN_PERCENT = 0.005
    }
}
