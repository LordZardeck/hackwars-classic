package com.hackwars.game.program

import game.*
import hackscript.model.RunFactory

/**
 * FTPProgram.java
 * 
 * 
 * A program that can be installed on a port of type FTP and which performs "get" and "put" operations.
 */

class FTPProgram(
    computer: Computer?,
    computerHandler: NetworkSwitch?,
    MyFileSystem: FileSystem?,
    ParentPort: Port?
) : Program(computer, computerHandler) {
    //Scripts.
    private var putScript: String? = ""
    private var getScript: String? = ""

    //Execute specific data.
    private var path = "" //Path to place the file in.
    private var HF: HackerFile? = null //The cloned hacker file to transfer.
    private var targetIP = computer?.ip ?: ""

    //Data
    private var MyFileSystem: FileSystem? = null //File system.
    private var ParentPort: Port? = null //Port this program is installed on.

    /**
     * Get the path of the file being transfered.
     */
    fun getPath(): String? {
        return (path)
    }

    val file: HackerFile?
        /**
         * Get the hacker file being transfered.
         */
        get() = (HF)

    val iP: String?
        /**
         * Get the IP that the file should be transfered to.
         */
        get() = (targetIP)

    val maliciousIP: String?
        /**
         * Get the malicious IP address that programs should be transfered to.
         */
        get() = (ParentPort!!.maliciousTarget)

    /**
     * Get the fetch path of this file. Where it will be saved to.
     */
    private var fetchPath: String? = ""

    //Constructor.
    init {
        this.MyFileSystem = MyFileSystem
        this.ParentPort = ParentPort
    }

    fun getFetchPath(): String? {
        return (fetchPath)
    }

    /**
     * Execute the "put" and "get" commands.
     */
    override fun execute(applicationData: ApplicationData) {
        var script: String? = ""

        //A DIRECTORY LISTING HAS BEEN REQUESTED FROM THE FILE SYSTEM.
        if (applicationData.getFunction() == "requestsecondarydirectory") { //Request a directory listing.
            val parameters = applicationData.getParameters() as Array<Any?>
            val path = parameters[1] as String
            val targetIP = parameters[0] as String
            var Directory: Array<Any?>? = null
            if (this@FTPProgram.computer!!.getType() != Computer.NPC) {
                Directory = MyFileSystem!!.getDirectory(path)
                val Temp: Array<Any?>? = arrayOfNulls<Any>(Directory.size + 1)
                Temp!![0] = (applicationData.getParameters() as Array<Any?>?)!![2] as Int? //Add an ID
                for (i in Directory.indices) {
                    Temp[i + 1] = Directory[i]
                }
                Directory = Temp
            } else {
                val id = (applicationData.getParameters() as Array<Any?>?)!![2] as Int? as Int //Add an ID
                Directory = arrayOfNulls<Any>(2)
                val listing: Array<Any?>? = arrayOfNulls<Any>(7)
                if (this@FTPProgram.computer!!.getDrop() == null) {
                    HF = this@FTPProgram.computer!!.getDropTable().generateDrop()
                    this@FTPProgram.computer!!.setDrop(HF)
                }
                Directory[0] = id
                listing!![0] = HF!!.getName()
                listing[1] = HF!!.getType()
                listing[2] = HF!!.getQuantity()
                listing[3] = HF!!.getPrice()
                listing[4] = HF!!.getMaker()
                listing[5] = HF!!.getCPUCost()
                listing[6] = HF!!.getDescription()
                Directory[1] = listing
            }

            this@FTPProgram.computerHandler!!.addData(
                ApplicationData(
                    "delivereddirectory",
                    arrayOf<Any>(Directory, this@FTPProgram.computer!!.isNPC()),
                    0,
                    this@FTPProgram.computer!!.getIP()
                ), targetIP
            )
            return
        } else if (applicationData.getFunction() == "malget") {
            var HF: HackerFile? = null
            val parameters = applicationData.getParameters() as Array<Any?>
            this.targetIP = parameters[0] as String
            var name = parameters[1] as String?
            var stolenPort = 0
            if (parameters.size == 6) {
                stolenPort = (parameters[5] as Int?)!!
            } else if (parameters.size == 7) {
                stolenPort = (parameters[6] as Int?)!!
            }
            if (this@FTPProgram.computer!!.getType() != Computer.NPC) {
                if (name == null) { //Steal the first file found.
                    name = ""
                    val O = this@FTPProgram.computer!!.getFileSystem().getWebDirectory("Public/")
                    for (i in O!!.indices) {
                        if (O[i] is HackerFile) {
                            name = (O[i] as HackerFile).getName()
                            break
                        }
                    }
                }
                val fetch_path = parameters[2] as String
                this.path = parameters[3] as String
                val password = parameters[4] as String?


                if (fetch_path !== "Store/") {
                    val THF = MyFileSystem!!.getFile(fetch_path, name)

                    var quantity = 1
                    if (THF != null) {
                        if (THF.isStacking()) {
                            quantity = THF.getQuantity() - 1
                        }
                    } else return

                    THF.setQuantity(quantity)
                    if (THF.getQuantity() <= 0 || !THF.isStacking()) {
                        MyFileSystem!!.deleteFile(fetch_path, name)
                    }

                    HF = THF.clone()
                    HF.setQuantity(1)
                    HF.setLocation(path)
                }
            } else {
                HF = this@FTPProgram.computer!!.getDrop()
                if (HF == null) {
                    HF = this@FTPProgram.computer!!.getDropTable().generateDrop()
                }
            }

            val PA = ParentPort!!.getCurrentPacket()
            PA.setRequestPrimary(true, 8)
            PA.setRequestSecondary(true, 8)
            this@FTPProgram.computer!!.setDrop(null)
            val Parameter: Array<Any?>? =
                arrayOf<Any?>("", HF, this@FTPProgram.computer!!.getIP(), ParentPort!!.getLastDamageWindowHandle())
            this@FTPProgram.computerHandler!!.addData(ApplicationData("savefile", Parameter, 0, this@FTPProgram.computer!!.getIP()), targetIP)


            //MyComputer.respawn(Port.FTP);
            return
        } else if (applicationData.getFunction() == "get") {
            this.targetIP = (applicationData.getParameters() as Array<Any?>?)!![0] as String
            val name = (applicationData.getParameters() as Array<Any?>?)!![1] as String?
            val fetch_path = (applicationData.getParameters() as Array<Any?>?)!![2] as String?
            this.path = (applicationData.getParameters() as Array<Any?>?)!![3] as String
            val password = (applicationData.getParameters() as Array<Any?>?)!![4] as String
            val getQuantity = (applicationData.getParameters() as Array<Any?>?)!![5] as Int

            //Check whether you have permission to peform this action.
            if (targetIP != this@FTPProgram.computer!!.getIP()) {
                if (password != this@FTPProgram.computer!!.getPassword()) {
                    this@FTPProgram.computerHandler!!.addData(
                        ApplicationData(
                            "message", MessageHandler.FTP_FAIL_PASSWORD_INCORRECT, 0,
                            this.iP
                        ), targetIP
                    )
                    return
                }
            }

            val THF = MyFileSystem!!.getFile(path, name)

            var quantity = 1
            if (THF != null) {
                if (THF.isStacking()) {
                    if (THF.getQuantity() < getQuantity) return

                    quantity = THF.getQuantity() - getQuantity
                }
            } else return

            THF.setQuantity(quantity)
            if (THF.getQuantity() <= 0 || !THF.isStacking()) {
                MyFileSystem!!.deleteFile(path, name)
            }

            HF = THF.clone()
            HF!!.setQuantity(getQuantity)
            HF!!.setLocation(fetch_path)
            this.fetchPath = fetch_path

            script = getScript

            if (targetIP != this@FTPProgram.computer!!.getIP()) this@FTPProgram.computer!!.getComputerHandler()
                .addData(ApplicationData("requestftpupdate", null, 0, this@FTPProgram.computer!!.getIP()), targetIP)
            this@FTPProgram.computer!!.getComputerHandler()
                .addData(ApplicationData("requestftpupdate", null, 0, this@FTPProgram.computer!!.getIP()), this@FTPProgram.computer!!.getIP())
        } else  //Prepare the put message.
            if (applicationData.getFunction() == "put") {
                val targetIP = (applicationData.getParameters() as Array<Any?>?)!![0] as String
                val name = (applicationData.getParameters() as Array<Any?>?)!![1] as String?
                val fetch_path = (applicationData.getParameters() as Array<Any?>?)!![2] as String
                val HF = MyFileSystem!!.getFile(fetch_path, name)
                val tpath = (applicationData.getParameters() as Array<Any?>?)!![3] as String?
                val putQuantity = (applicationData.getParameters() as Array<Any?>?)!![5] as Int

                var SF: HackerFile? = null

                var quantity = 1
                if (HF != null) {
                    if (HF.isStacking()) {
                        if (HF.getQuantity() < putQuantity) return

                        quantity = HF.getQuantity() - putQuantity
                    }
                } else return

                HF.setQuantity(quantity)
                if (HF.getQuantity() <= 0 || !HF.isStacking()) {
                    MyFileSystem!!.deleteFile(fetch_path, name)
                }

                SF = HF.clone()
                SF.setQuantity(putQuantity)
                SF.setLocation(tpath)

                val Parameters: Array<Any?>? = arrayOf<Any?>(
                    this@FTPProgram.computer!!.getIP(),
                    (applicationData.getParameters() as Array<Any?>?)!![1],
                    (applicationData.getParameters() as Array<Any?>?)!![2],
                    (applicationData.getParameters() as Array<Any?>?)!![3],
                    (applicationData.getParameters() as Array<Any?>?)!![4],
                    SF
                )
                this@FTPProgram.computerHandler!!.addData(
                    ApplicationData(
                        "finalizeput",
                        Parameters,
                        ParentPort!!.getNumber(),
                        this@FTPProgram.computer!!.getIP()
                    ), targetIP
                )

                if (targetIP != this@FTPProgram.computer!!.getIP()) this@FTPProgram.computerHandler!!.addData(
                    ApplicationData(
                        "requestftpupdate",
                        null,
                        0,
                        this@FTPProgram.computer!!.getIP()
                    ), targetIP
                )
                this@FTPProgram.computerHandler!!.addData(
                    ApplicationData("requestftpupdate", null, 0, this@FTPProgram.computer!!.getIP()),
                    this@FTPProgram.computer!!.getIP()
                )
            } else if (applicationData.getFunction() == "finalizeput") {
                this.targetIP = this@FTPProgram.computer!!.getIP()
                val name = (applicationData.getParameters() as Array<Any?>?)!![1] as String?
                val fetch_path = (applicationData.getParameters() as Array<Any?>?)!![2] as String?
                this.path = (applicationData.getParameters() as Array<Any?>?)!![3] as String
                val password = (applicationData.getParameters() as Array<Any?>?)!![4] as String
                HF = (applicationData.getParameters() as Array<Any?>?)!![5] as HackerFile?

                //Check whether you have permission to peform this action.
                if (this@FTPProgram.computer!!.getFileSystem().getSpaceLeft() <= 0) {
                    this@FTPProgram.computerHandler!!.addData(
                        ApplicationData(
                            "message", MessageHandler.FTP_PUT_FAIL_HD_FULL, 0,
                            this.iP
                        ), applicationData.getSourceIP()
                    )
                    HF!!.setLocation("")
                    val Parameters: Array<Any?>? = arrayOf<Any?>(fetch_path, HF)
                    this@FTPProgram.computerHandler!!.addData(
                        ApplicationData("savefile", Parameters, 0, this.iP),
                        applicationData.getSourceIP()
                    )
                    return
                } else if (applicationData.getSourceIP() != this@FTPProgram.computer!!.getIP()) {
                    if (password != this@FTPProgram.computer!!.getPassword()) {
                        this@FTPProgram.computerHandler!!.addData(
                            ApplicationData(
                                "message", MessageHandler.FTP_FAIL_PASSWORD_INCORRECT, 0,
                                this.iP
                            ), applicationData.getSourceIP()
                        )
                        HF!!.setLocation("")
                        val Parameters: Array<Any?>? = arrayOf<Any?>(fetch_path, HF)
                        this@FTPProgram.computerHandler!!.addData(
                            ApplicationData("savefile", Parameters, 0, this.iP),
                            applicationData.getSourceIP()
                        )
                        return
                    }
                }

                script = putScript

                if (targetIP != this@FTPProgram.computer!!.getIP()) this@FTPProgram.computerHandler!!.addData(
                    ApplicationData(
                        "requestftpupdate",
                        null,
                        0,
                        this@FTPProgram.computer!!.getIP()
                    ), targetIP
                )
                this@FTPProgram.computerHandler!!.addData(
                    ApplicationData("requestftpupdate", null, 0, this@FTPProgram.computer!!.getIP()),
                    this@FTPProgram.computer!!.getIP()
                )
            } else return

        try {
            val HL = HackerLinker(this, this@FTPProgram.computerHandler)
            RunFactory.runCode(script, HL, 4096)
        } catch (e: Exception) {
        }
    }

    /**
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    override fun installScript(script: HashMap<*, *>) {
        putScript = script.get("put") as String?
        getScript = script.get("get") as String?
    }

    /**
     * Return a hash map representation of the program currently installed on this port.
     */
    override fun getContent(): HashMap<*, *> {
        val returnMe: HashMap<Any?, Any?> = HashMap()
        returnMe.put("put", putScript)
        returnMe.put("get", getScript)
        return (returnMe)
    }

    /**
     * Returns the keys associated with this program type.
     */
    override fun getTypeKeys(): Array<String?> {
        val returnMe: Array<String?>? = arrayOf<String?>("get", "put")
        return (returnMe!!)
    }

    /**
     * Output the class data in XML format.
     */
    override fun outputXML(): String {
        var returnMe = ""

        if (putScript != null) returnMe += "<put><![CDATA[" + putScript!!.replace(
            "]]>".toRegex(),
            "]]&gt;"
        ) + "]]></put>\n"
        else returnMe += "<put><![CDATA[" + putScript + "]]></put>\n"

        if (getScript != null) returnMe += "<get><![CDATA[" + getScript!!.replace(
            "]]>".toRegex(),
            "]]&gt;"
        ) + "]]></get>\n"
        else returnMe += "<get><![CDATA[" + getScript + "]]></get>\n"

        return (returnMe)
    }
}
