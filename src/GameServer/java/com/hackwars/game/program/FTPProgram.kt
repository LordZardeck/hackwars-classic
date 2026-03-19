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
    MyComputer: Computer?,
    MyFileSystem: FileSystem?,
    ParentPort: Port?,
    MyComputerHandler: NetworkSwitch?
) : Program() {
    //Scripts.
    private var putScript: String? = ""
    private var getScript: String? = ""

    //Execute specific data.
    private var path = "" //Path to place the file in.
    private var HF: HackerFile? = null //The cloned hacker file to transfer.
    private var targetIP = ""

    //Data
    private var MyFileSystem: FileSystem? = null //File system.
    private var MyComputer: Computer? = null //Computer this program is associated with.
    private var ParentPort: Port? = null //Port this program is installed on.
    private var MyComputerHandler: NetworkSwitch? = null //Central messaging system.

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
        get() = (ParentPort!!.getMaliciousTarget())

    /**
     * Get the fetch path of this file. Where it will be saved to.
     */
    private var fetchPath: String? = ""

    //Constructor.
    init {
        super.setComputerHandler(MyComputerHandler)
        super.setComputer(MyComputer)
        this.MyFileSystem = MyFileSystem
        this.MyComputer = MyComputer
        this.ParentPort = ParentPort
        this.MyComputerHandler = MyComputerHandler
        if (MyComputer != null) this.targetIP = MyComputer.getIP()
    }

    fun getFetchPath(): String? {
        return (fetchPath)
    }

    /**
     * Execute the "put" and "get" commands.
     */
    override fun execute(MyApplicationData: ApplicationData) {
        var script: String? = ""

        //A DIRECTORY LISTING HAS BEEN REQUESTED FROM THE FILE SYSTEM.
        if (MyApplicationData.getFunction() == "requestsecondarydirectory") { //Request a directory listing.
            val parameters = MyApplicationData.getParameters() as Array<Any?>
            val path = parameters[1] as String
            val targetIP = parameters[0] as String
            var Directory: Array<Any?>? = null
            if (MyComputer!!.getType() != Computer.NPC) {
                Directory = MyFileSystem!!.getDirectory(path)
                val Temp: Array<Any?>? = arrayOfNulls<Any>(Directory.size + 1)
                Temp!![0] = (MyApplicationData.getParameters() as Array<Any?>?)!![2] as Int? //Add an ID
                for (i in Directory.indices) {
                    Temp[i + 1] = Directory[i]
                }
                Directory = Temp
            } else {
                val id = (MyApplicationData.getParameters() as Array<Any?>?)!![2] as Int? as Int //Add an ID
                Directory = arrayOfNulls<Any>(2)
                val listing: Array<Any?>? = arrayOfNulls<Any>(7)
                if (MyComputer!!.getDrop() == null) {
                    HF = MyComputer!!.getDropTable().generateDrop()
                    MyComputer!!.setDrop(HF)
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

            MyComputerHandler!!.addData(
                ApplicationData(
                    "delivereddirectory",
                    arrayOf<Any>(Directory, MyComputer!!.isNPC()),
                    0,
                    MyComputer!!.getIP()
                ), targetIP
            )
            return
        } else if (MyApplicationData.getFunction() == "malget") {
            var HF: HackerFile? = null
            val parameters = MyApplicationData.getParameters() as Array<Any?>
            this.targetIP = parameters[0] as String
            var name = parameters[1] as String?
            var stolenPort = 0
            if (parameters.size == 6) {
                stolenPort = (parameters[5] as Int?)!!
            } else if (parameters.size == 7) {
                stolenPort = (parameters[6] as Int?)!!
            }
            if (MyComputer!!.getType() != Computer.NPC) {
                if (name == null) { //Steal the first file found.
                    name = ""
                    val O = MyComputer!!.getFileSystem().getWebDirectory("Public/")
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
                HF = MyComputer!!.getDrop()
                if (HF == null) {
                    HF = MyComputer!!.getDropTable().generateDrop()
                }
            }

            val PA = ParentPort!!.getCurrentPacket()
            PA.setRequestPrimary(true, 8)
            PA.setRequestSecondary(true, 8)
            MyComputer!!.setDrop(null)
            val Parameter: Array<Any?>? =
                arrayOf<Any?>("", HF, MyComputer!!.getIP(), ParentPort!!.getLastDamageWindowHandle())
            MyComputerHandler!!.addData(ApplicationData("savefile", Parameter, 0, MyComputer!!.getIP()), targetIP)


            //MyComputer.respawn(Port.FTP);
            return
        } else if (MyApplicationData.getFunction() == "get") {
            this.targetIP = (MyApplicationData.getParameters() as Array<Any?>?)!![0] as String
            val name = (MyApplicationData.getParameters() as Array<Any?>?)!![1] as String?
            val fetch_path = (MyApplicationData.getParameters() as Array<Any?>?)!![2] as String?
            this.path = (MyApplicationData.getParameters() as Array<Any?>?)!![3] as String
            val password = (MyApplicationData.getParameters() as Array<Any?>?)!![4] as String
            val getQuantity = (MyApplicationData.getParameters() as Array<Any?>?)!![5] as Int

            //Check whether you have permission to peform this action.
            if (targetIP != MyComputer!!.getIP()) {
                if (password != MyComputer!!.getPassword()) {
                    MyComputerHandler!!.addData(
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

            if (targetIP != MyComputer!!.getIP()) MyComputer!!.getComputerHandler()
                .addData(ApplicationData("requestftpupdate", null, 0, MyComputer!!.getIP()), targetIP)
            MyComputer!!.getComputerHandler()
                .addData(ApplicationData("requestftpupdate", null, 0, MyComputer!!.getIP()), MyComputer!!.getIP())
        } else  //Prepare the put message.
            if (MyApplicationData.getFunction() == "put") {
                val targetIP = (MyApplicationData.getParameters() as Array<Any?>?)!![0] as String
                val name = (MyApplicationData.getParameters() as Array<Any?>?)!![1] as String?
                val fetch_path = (MyApplicationData.getParameters() as Array<Any?>?)!![2] as String
                val HF = MyFileSystem!!.getFile(fetch_path, name)
                val tpath = (MyApplicationData.getParameters() as Array<Any?>?)!![3] as String?
                val putQuantity = (MyApplicationData.getParameters() as Array<Any?>?)!![5] as Int

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
                    MyComputer!!.getIP(),
                    (MyApplicationData.getParameters() as Array<Any?>?)!![1],
                    (MyApplicationData.getParameters() as Array<Any?>?)!![2],
                    (MyApplicationData.getParameters() as Array<Any?>?)!![3],
                    (MyApplicationData.getParameters() as Array<Any?>?)!![4],
                    SF
                )
                MyComputerHandler!!.addData(
                    ApplicationData(
                        "finalizeput",
                        Parameters,
                        ParentPort!!.getNumber(),
                        MyComputer!!.getIP()
                    ), targetIP
                )

                if (targetIP != MyComputer!!.getIP()) MyComputerHandler!!.addData(
                    ApplicationData(
                        "requestftpupdate",
                        null,
                        0,
                        MyComputer!!.getIP()
                    ), targetIP
                )
                MyComputerHandler!!.addData(
                    ApplicationData("requestftpupdate", null, 0, MyComputer!!.getIP()),
                    MyComputer!!.getIP()
                )
            } else if (MyApplicationData.getFunction() == "finalizeput") {
                this.targetIP = MyComputer!!.getIP()
                val name = (MyApplicationData.getParameters() as Array<Any?>?)!![1] as String?
                val fetch_path = (MyApplicationData.getParameters() as Array<Any?>?)!![2] as String?
                this.path = (MyApplicationData.getParameters() as Array<Any?>?)!![3] as String
                val password = (MyApplicationData.getParameters() as Array<Any?>?)!![4] as String
                HF = (MyApplicationData.getParameters() as Array<Any?>?)!![5] as HackerFile?

                //Check whether you have permission to peform this action.
                if (MyComputer!!.getFileSystem().getSpaceLeft() <= 0) {
                    MyComputerHandler!!.addData(
                        ApplicationData(
                            "message", MessageHandler.FTP_PUT_FAIL_HD_FULL, 0,
                            this.iP
                        ), MyApplicationData.getSourceIP()
                    )
                    HF!!.setLocation("")
                    val Parameters: Array<Any?>? = arrayOf<Any?>(fetch_path, HF)
                    MyComputerHandler!!.addData(
                        ApplicationData("savefile", Parameters, 0, this.iP),
                        MyApplicationData.getSourceIP()
                    )
                    return
                } else if (MyApplicationData.getSourceIP() != MyComputer!!.getIP()) {
                    if (password != MyComputer!!.getPassword()) {
                        MyComputerHandler!!.addData(
                            ApplicationData(
                                "message", MessageHandler.FTP_FAIL_PASSWORD_INCORRECT, 0,
                                this.iP
                            ), MyApplicationData.getSourceIP()
                        )
                        HF!!.setLocation("")
                        val Parameters: Array<Any?>? = arrayOf<Any?>(fetch_path, HF)
                        MyComputerHandler!!.addData(
                            ApplicationData("savefile", Parameters, 0, this.iP),
                            MyApplicationData.getSourceIP()
                        )
                        return
                    }
                }

                script = putScript

                if (targetIP != MyComputer!!.getIP()) MyComputerHandler!!.addData(
                    ApplicationData(
                        "requestftpupdate",
                        null,
                        0,
                        MyComputer!!.getIP()
                    ), targetIP
                )
                MyComputerHandler!!.addData(
                    ApplicationData("requestftpupdate", null, 0, MyComputer!!.getIP()),
                    MyComputer!!.getIP()
                )
            } else return

        try {
            val HL = HackerLinker(this, MyComputerHandler)
            RunFactory.runCode(script, HL, 4096)
        } catch (e: Exception) {
        }
    }

    /**
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    override fun installScript(Script: HashMap<*, *>) {
        putScript = Script.get("put") as String?
        getScript = Script.get("get") as String?
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
