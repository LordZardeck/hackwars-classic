package com.hackwars.game.program

import game.*
import hackscript.model.RunFactory

/**
 * WatchProgram.java
 * A watch program is executed by the watch handler when a specific event has fired.
 */

class WatchProgram(computer: Computer?, computerHandler: NetworkSwitch?, parentWatch: Watch?) :
    Program(computer, computerHandler) {
    private var fireScript: String? = "" //Script that runs when watch fires.

    var triggered = false
    var parentWatch: Watch? = parentWatch
        private set

    /**
     * Shut down all observed ports.
     */
    fun shutDownPorts() {
        parentWatch!!.shutDownPorts()
    }

    /**
     * Shut down a specific port.
     */
    fun shutDownPort(port: Int) {
        parentWatch!!.shutDownPort(port)
    }

    /**
     * Shut down all observed ports.
     */
    fun turnOnPorts() {
        parentWatch!!.turnOnPorts()
    }

    /**
     * Shut down a specific port.
     */
    fun turnOnPort(port: Int) {
        parentWatch!!.turnOnPort(port)
    }

    var pettyCash: Float
        /**
         * Return the amount currently in the player's petty cash.
         */
        get() = (computer!!.pettyCash)
        /**
         * Set the amount currently in the player's petty cash.
         */
        set(pettyCash) {
            computer!!.setPettyCash(pettyCash)
        }

    /**
     * Switch the fire wall from the port provided to this port.
     */
    fun switchFireWall(port: Int) {
        parentWatch!!.switchFireWall(port)
    }

    /**
     * Switch any fire wall from a port in the observed ports array.
     */
    fun switchAnyFireWall() {
        parentWatch!!.switchAnyFireWall()
    }

    /**
     * Check the array of observed fire walls for the fire wall with the given name.
     */
    fun checkForFireWall(fireWallName: String): Int {
        return parentWatch!!.checkForFireWall(fireWallName)
    }

    /**
     * Check whether the given fire wall is present on the port this program is installed on.
     */
    fun checkFireWall(fireWallName: String): Boolean {
        return (parentWatch!!.checkFireWall(fireWallName))
    }

    val defaultBank: Int
        /**
         * Get the banking application associated with the computer.
         */
        get() = (computer!!.defaultBank)

    val defaultAttack: Int
        /**
         * Get the default attacking application associated with the computer.
         */
        get() = (computer!!.defaultAttack)

    val defaultFTP: Int
        /**
         * Get the ftp application associated with the computer.
         */
        get() = (computer!!.defaultBank)

    val defaultHTTP: Int
        /**
         * Get the default HTTP application associated with the computer.
         */
        get() = (computer!!.defaultHTTP)

    val targetIP: String?
        /**
         * Get the IP that triggered this watch (Used if this happens to be a damage or health watch).
         */
        get() = (parentWatch!!.targetIP)

    val targetPort: Int
        /**
         * Get the port that triggered this watch.
         */
        get() = (parentWatch!!.targetPort)

    val iP: String?
        /**
         * Get the IP of the computer associated with this program.
         */
        get() = (computer!!.ip)

    /**
     * Get the port number associated with this program.
     */
    val number get() = (parentWatch!!.number)

    /**
     * Get the fire wall that should be searched for based on initial setup.
     */
    val searchFireWall: String? get() = (NewFireWall.FireWallNames[parentWatch!!.searchFireWall])

    /**
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    override fun installScript(script: HashMap<*, *>) {
        fireScript = script.get("fire") as String?
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
    override fun execute(applicationData: ApplicationData) {
        try {
            val HL = HackerLinker(this, computerHandler)
            RunFactory.runCode(fireScript, HL, computer!!.MAX_OPS)
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
