package com.hackwars.game.program

import game.ApplicationData
import game.Computer
import game.NetworkSwitch

/**
 * Program.java
 * 
 * 
 * An abstract program used to handle common functionality between the various programs.
 * (Banking,Attacking,FTP,etc.).
 */

abstract class Program {
    private var myComputerHandler: NetworkSwitch? = null //Central mechanism for contacting other computers.
    private var myComputer: Computer? = null //The Computer this program is installed on.
    private var error: String? = ""

    /**
     * Set the error string for this program which is used when compiling.
     */
    fun setError(error: String?) {
        this.error = error
    }

    /**
     * Get the error string associated with this program which is used when compiling.
     */
    fun getError(): String? {
        return error
    }

    /**
     * setComputerHandler(ComputerHandler MyComputerHandler)
     * sets the computer handler associated with this program.
     */
    fun setComputerHandler(myComputerHandler: NetworkSwitch?) {
        this.myComputerHandler = myComputerHandler
    }

    /**
     * setComput(Computer MyComputer)
     * sets the computer associated with this program.
     */
    fun setComputer(myComputer: Computer?) {
        this.myComputer = myComputer
    }

    /**
     * Get the computer setup to run with this application.
     */
    open fun getComputer(): Computer? {
        return myComputer
    }

    /**
     * getComputerHandler()
     * returns the computer handler associated with this program.
     */
    fun getComputerHandler(): NetworkSwitch? {
        return myComputerHandler
    }

    /**
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    abstract fun installScript(Script: HashMap<*, *>)

    /**
     * Execute the program with the RFC provided.
     */
    abstract fun execute(MyApplicationData: ApplicationData)

    /**
     * Returns the keys that should be parsed from the save file given this program type.
     */
    abstract fun getTypeKeys(): Array<String?>

    /**
     * Return a hashmap representation of the code.
     */
    abstract fun getContent(): HashMap<*, *>

    /**
     * Output the class data in XML format.
     */
    abstract fun outputXML(): String
}
