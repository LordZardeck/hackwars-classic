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

abstract class Program(computer: Computer?, var computerHandler: NetworkSwitch?) {
    private var error: String? = ""

    // The Computer this program is installed on.
    var computer: Computer? = computer
        protected set

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
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    abstract fun installScript(script: HashMap<*, *>)

    /**
     * Execute the program with the RFC provided.
     */
    abstract fun execute(applicationData: ApplicationData)

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
