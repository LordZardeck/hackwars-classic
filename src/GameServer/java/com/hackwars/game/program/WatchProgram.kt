package com.hackwars.game.program

import game.*
import hackscript.model.RunFactory

/**
 * WatchProgram.java
 * A watch program is executed by the watch handler when a specific event has fired.
 */

class WatchProgram(MyComputer: Computer?, MyComputerHandler: NetworkSwitch?, ParentWatch: Watch?) : Program() {
    private var fireScript: String? = "" //Script that runs when watch fires.
    private var MyComputer: Computer? = null //Computer associated with this program.
    private var ParentWatch: Watch? = null //Watch housing this program.
    private var triggered = false

    //Constructor.
    init {
        super.setComputerHandler(MyComputerHandler)
        super.setComputer(MyComputer)
        this.MyComputer = MyComputer
        this.ParentWatch = ParentWatch
    }

    fun getTriggered(): Boolean {
        return (triggered)
    }

    fun setTriggered(triggered: Boolean) {
        this.triggered = triggered
    }

    //Get the parent watch associated with this program.
    fun getParentWatch(): Watch? {
        return (ParentWatch)
    }

    /**
     * Shut down all observed ports.
     */
    fun shutDownPorts() {
        ParentWatch!!.shutDownPorts()
    }

    /**
     * Shut down a specific port.
     */
    fun shutDownPort(port: Int) {
        ParentWatch!!.shutDownPort(port)
    }

    /**
     * Shut down all observed ports.
     */
    fun turnOnPorts() {
        ParentWatch!!.turnOnPorts()
    }

    /**
     * Shut down a specific port.
     */
    fun turnOnPort(port: Int) {
        ParentWatch!!.turnOnPort(port)
    }

    var pettyCash: Float
        /**
         * Return the amount currently in the player's petty cash.
         */
        get() = (MyComputer!!.getPettyCash())
        /**
         * Set the amount currently in the player's petty cash.
         */
        set(pettyCash) {
            MyComputer!!.setPettyCash(pettyCash)
        }

    /**
     * Switch the fire wall from the port provided to this port.
     */
    fun switchFireWall(port: Int) {
        ParentWatch!!.switchFireWall(port)
    }

    /**
     * Switch any fire wall from a port in the observed ports array.
     */
    fun switchAnyFireWall() {
        ParentWatch!!.switchAnyFireWall()
    }

    /**
     * Check the array of observed fire walls for the fire wall with the given name.
     */
    fun checkForFireWall(FireWallName: String?): Int {
        return (ParentWatch!!.checkForFireWall(FireWallName))
    }

    /**
     * Check whether the given fire wall is present on the port this program is installed on.
     */
    fun checkFireWall(FireWallName: String?): Boolean {
        return (ParentWatch!!.checkFireWall(FireWallName))
    }

    val defaultBank: Int
        /**
         * Get the banking application associated with the computer.
         */
        get() = (MyComputer!!.getDefaultBank())

    val defaultAttack: Int
        /**
         * Get the default attacking application associated with the computer.
         */
        get() = (MyComputer!!.getDefaultAttack())

    val defaultFTP: Int
        /**
         * Get the ftp application associated with the computer.
         */
        get() = (MyComputer!!.getDefaultBank())

    val defaultHTTP: Int
        /**
         * Get the default HTTP application associated with the computer.
         */
        get() = (MyComputer!!.getDefaultHTTP())

    val targetIP: String?
        /**
         * Get the IP that triggered this watch (Used if this happens to be a damage or health watch).
         */
        get() = (ParentWatch!!.getTargetIP())

    val targetPort: Int
        /**
         * Get the port that triggered this watch.
         */
        get() = (ParentWatch!!.getTargetPort())

    val iP: String?
        /**
         * Get the IP of the computer associated with this program.
         */
        get() = (MyComputer!!.getIP())

    val number: Int
        /**
         * Get the port number associated with this program.
         */
        get() = (ParentWatch!!.getNumber())

    val searchFireWall: String?
        /**
         * Get the fire wall that should be searched for based on initial setup.
         */
        get() = (NewFireWall.FireWallNames[ParentWatch!!.getSearchFireWall()])

    /**
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    override fun installScript(Script: HashMap<*, *>) {
        fireScript = Script.get("fire") as String?
    }

    /**
     * Return a hash map representation of the program currently installed on this port.
     */
    override fun getContent(): HashMap<*, *> {
        val returnMe: HashMap<Any?, Any?> = HashMap()
        returnMe.put("fire", fireScript)
        return (returnMe)
    }

    /**
     * Execute the script associated with this watch.
     */
    override fun execute(MyApplicationData: ApplicationData) {
        try {
            val HL = HackerLinker(this, super.getComputerHandler())
            RunFactory.runCode(fireScript, HL, MyComputer!!.MAX_OPS)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        triggered = false
    }

    /**
     * Returns the keys associated with this program type.
     */
    override fun getTypeKeys(): Array<String?> {
        val returnMe: Array<String?>? = arrayOf<String?>("fire")
        return (returnMe!!)
    }

    /**
     * Output the class data in XML format.
     */
    override fun outputXML(): String {
        var returnMe = ""

        if (fireScript != null) returnMe += "<fire><![CDATA[" + fireScript!!.replace(
            "]]>".toRegex(),
            "]]&gt;"
        ) + "]]></fire>\n"
        else returnMe += "<fire><![CDATA[" + fireScript + "]]></fire>\n"

        return (returnMe)
    }
}
