package com.hackwars.game.program

import game.ApplicationData
import hackerlogin.ToyLinker
import hackscript.model.RunFactory
import kotlin.math.abs

/**
 * Program.java
 * 
 * 
 * An abstract program used to handle common functionality between the various programs.
 * (Banking,Attacking,FTP,etc.).
 */

class ToyProgram(script: String?, MAX_ARRAY: Int, MAX_ITERATIONS: Int) : Program() {
    private var MAX_ARRAY = 64
    private var MAX_ITERATIONS = 128
    private val InFloat: ArrayList<Any?> = ArrayList()
    private var countIF = 0
    private val OutFloat: ArrayList<Any?> = ArrayList()
    private val countOF = 0
    private val InString: ArrayList<Any?> = ArrayList()
    private var countIS = 0
    private val OutString: ArrayList<Any?> = ArrayList()
    private val countOS = 0
    private var script: String? = ""
    private val InInt: ArrayList<Any?> = ArrayList()
    private var countII = 0
    private val OutInt: ArrayList<Any?> = ArrayList()
    private val countOI = 0

    //The target state of the program.
    private var TargetString: ArrayList<Any?>? = null
    private var TargetInt: ArrayList<Any?>? = null
    private var TargetFloat: ArrayList<Any?>? = null

    private var success = false

    /**
     * Constructor.
     */
    init {
        this.script = script
        this.MAX_ARRAY = MAX_ARRAY
        this.MAX_ITERATIONS = MAX_ITERATIONS
    }

    /**
     * Return whether or not this program passed the test.
     */
    fun getSuccess(): Boolean {
        return (success)
    }

    /**
     * Add the target array of strings to be outputted.
     */
    fun addTargetString(s: String?) {
        if (TargetString == null) TargetString = ArrayList()
        TargetString!!.add(s)
    }

    /**
     * Add the target array of floats to be outputted.
     */
    fun addTargetFloat(f: Float) {
        if (TargetFloat == null) TargetFloat = ArrayList()
        TargetFloat!!.add(f)
    }

    /**
     * Add the target array of ints to be outputted.
     */
    fun addTargetInt(i: Int) {
        if (TargetInt == null) TargetInt = ArrayList()
        TargetInt!!.add(i)
    }

    /**
     * Add a float to the inbound float array.
     */
    fun addInFloat(f: Float) {
        InFloat.add(f)
    }

    /**
     * Add a float to the inbound float array.
     */
    fun addOutFloat(f: Float) {
        if (OutFloat.size < MAX_ARRAY) OutFloat.add(f)
    }

    /**
     * Add a string to the inbound string array.
     */
    fun addInString(s: String?) {
        InString.add(s)
    }

    /**
     * Add a string to the inbound string array.
     */
    fun addOutString(s: String?) {
        if (OutString.size < MAX_ARRAY) OutString.add(s)
    }

    /**
     * Add an int to the inbound int array.
     */
    fun addOutInt(i: Int) {
        if (OutInt.size < MAX_ARRAY) OutInt.add(i)
    }

    /**
     * Add an int to the inbound int array.
     */
    fun addInInt(i: Int) {
        InInt.add(i)
    }

    /**
     * Get a string from the inbound string array.
     */
    fun getInString(): String? {
        if (countIS < InString.size) {
            countIS++
            return (InString.get(countIS - 1) as String?)
        }
        return ("")
    }

    /**
     * Get a float from the inbound float array.
     */
    fun getInFloat(): Float {
        if (countIF < InFloat.size) {
            countIF++
            return ((InFloat.get(countIF - 1) as Float?) as Float)
        }
        return (0.0f)
    }

    /**
     * Get an int from the inbound int array.
     */
    fun getInInt(): Int {
        if (countII < InInt.size) {
            countII++
            return ((InInt.get(countII - 1) as Int?) as Int)
        }
        return (0)
    }

    val inputIntSize: Int
        /**
         * Return the size of the array of inbound ints.
         */
        get() = (InInt.size)

    val inputFloatSize: Int
        /**
         * Return the size of the array of inbound floats.
         */
        get() = (InFloat.size)

    val inputStringSize: Int
        /**
         * Return the size of the array of inbound Strings.
         */
        get() = (InString.size)

    /**
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    override fun installScript(Script: HashMap<*, *>) {
    }

    /**
     * Execute the program with the RFC provided.
     */
    override fun execute(MyApplicationData: ApplicationData) {
        try {
            val HL = ToyLinker(this, null)
            RunFactory.runCode(script, HL, MAX_ITERATIONS)
        } catch (e: Exception) {
            this.setError("Syntax error in challenge code compiler returned [" + e.message + "]")
        }

        //Check to see whether this was successful.
        success = true
        if (TargetFloat != null) for (i in TargetFloat!!.indices) {
            if (i < OutFloat.size) {
                val f1 = OutFloat.get(i) as Float
                val f2 = TargetFloat!!.get(i) as Float
                if (abs(f1 - f2) > 0.01) {
                    success = false
                    break
                }
            } else {
                success = false
            }
        }

        if (TargetString != null) for (i in TargetString!!.indices) {
            if (i < OutString.size) {
                val f1 = OutString.get(i) as String
                val f2 = TargetString!!.get(i) as String?
                if (f1 != f2) {
                    success = false
                    break
                }
            } else {
                success = false
            }
        }

        if (TargetInt != null) for (i in TargetInt!!.indices) {
            if (i < OutInt.size) {
                val f1 = OutInt.get(i) as Int
                val f2 = TargetInt!!.get(i) as Int
                if (f1 != f2) {
                    success = false
                    break
                }
            } else {
                success = false
            }
        }
    }

    /**
     * Returns the array of outputted ints.
     */
    fun getOutInt(): Array<Any?> {
        return (OutInt.toTypedArray())
    }

    val outDouble: Array<Any?>
        /**
         * Returns the array of outputted doubles.
         */
        get() {
            val returnMe: Array<Any?> = arrayOfNulls(OutFloat.size)
            val MyIterator = OutFloat.iterator()
            var i = 0
            while (MyIterator.hasNext()) {
                returnMe[i] = (MyIterator.next() as Float?) as Float
                i++
            }
            return (returnMe)
        }

    /**
     * Returns the array of outputted strings.
     */
    fun getOutString(): Array<Any?> {
        return (OutString.toTypedArray())
    }

    /**
     * Returns the keys that should be parsed from the save file given this program type.
     */
    override fun getTypeKeys(): Array<String?> {
        return emptyArray()
    }

    /**
     * Return a hashmap representation of the code.
     */
    override fun getContent(): HashMap<*, *> {
        return HashMap<Any?, Any?>()
    }

    /**
     * Output the class data in XML format.
     */
    override fun outputXML(): String {
        return ("")
    }
}
