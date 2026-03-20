package com.hackwars.game.program

import com.hackwars.rpc.SaveFile
import game.*
import game.payload.*
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
        val function = applicationData.command.wireName()

        if (function == "requestsecondarydirectory") {
            val payload = applicationData.payloadAs<RequestSecondaryDirectoryPayload>()
            val path = payload.path
            val targetIP = payload.targetIp
            var directory: Array<Any?>
            if (this@FTPProgram.computer!!.getType() != Computer.NPC) {
                directory = MyFileSystem!!.getDirectory(path) ?: arrayOfNulls<Any>(0)
                val temp: Array<Any?> = arrayOfNulls<Any>(directory.size + 1)
                temp[0] = payload.requestId
                for (i in directory.indices) {
                    temp[i + 1] = directory[i]
                }
                directory = temp
            } else {
                val id = payload.requestId
                directory = arrayOfNulls<Any>(2)
                val listing: Array<Any?>? = arrayOfNulls<Any>(7)
                if (this@FTPProgram.computer!!.getDrop() == null) {
                    HF = this@FTPProgram.computer!!.getDropTable().generateDrop()
                    this@FTPProgram.computer!!.setDrop(HF)
                }
                directory[0] = id
                listing!![0] = HF!!.getName()
                listing[1] = HF!!.getType()
                listing[2] = HF!!.getQuantity()
                listing[3] = HF!!.getPrice()
                listing[4] = HF!!.getMaker()
                listing[5] = HF!!.getCPUCost()
                listing[6] = HF!!.getDescription()
                directory[1] = listing
            }

            this@FTPProgram.computerHandler!!.addData(
                if (this@FTPProgram.computer!!.isNPC()) {
                    ApplicationData(DeliveredDirectoryToNpcPayload(directory, true), 0, this@FTPProgram.computer!!.getIP())
                } else {
                    ApplicationData(DeliveredDirectoryToPlayerPayload(directory), 0, this@FTPProgram.computer!!.getIP())
                },
                targetIP
            )
            return
        } else if (function == "malget") {
            var transferred: HackerFile? = null
            val payload = applicationData.payloadAs<MalGetPayload>()
            this.targetIP = payload.targetIp
            var name = payload.name
            if (this@FTPProgram.computer!!.getType() != Computer.NPC) {
                if (name == null) {
                    name = ""
                    val files = this@FTPProgram.computer!!.fileSystem.getWebDirectory("Public/")
                    for (i in files!!.indices) {
                        if (files[i] is HackerFile) {
                            name = (files[i] as HackerFile).getName()
                            break
                        }
                    }
                }
                val fetchPath = payload.fetchPath
                this.path = payload.targetPath

                if (fetchPath != "Store/") {
                    val sourceFile = MyFileSystem!!.getFile(fetchPath, name)
                    var quantity = 1
                    if (sourceFile != null) {
                        if (sourceFile.isStacking()) {
                            quantity = sourceFile.getQuantity() - 1
                        }
                    } else return

                    sourceFile.setQuantity(quantity)
                    if (sourceFile.getQuantity() <= 0 || !sourceFile.isStacking()) {
                        MyFileSystem!!.deleteFile(fetchPath, name)
                    }

                    transferred = sourceFile.clone()
                    transferred.setQuantity(1)
                    transferred.setLocation(path)
                }
            } else {
                transferred = this@FTPProgram.computer!!.getDrop()
                if (transferred == null) {
                    transferred = this@FTPProgram.computer!!.getDropTable().generateDrop()
                }
            }

            ParentPort!!.currentPacket!!.setRequestPrimary(true, 8)
            ParentPort!!.currentPacket!!.setRequestSecondary(true, 8)
            this@FTPProgram.computer!!.setDrop(null)
            this@FTPProgram.computerHandler!!.addData(
                ApplicationData(
                    SaveFile(targetIP, "", transferred),
                    0,
                    this@FTPProgram.computer!!.getIP()
                ),
                targetIP
            )
            return
        } else if (function == "get") {
            val payload = applicationData.payloadAs<GetFilePayload>()
            val targetIP = payload.targetIp
            this.targetIP = targetIP
            val name = payload.name
            val fetchPath = payload.fetchPath
            this.path = payload.targetPath
            val password = payload.password
            val getQuantity = payload.quantity

            if (targetIP != this@FTPProgram.computer!!.getIP()) {
                if (password != this@FTPProgram.computer!!.getPassword()) {
                    this@FTPProgram.computerHandler!!.addData(
                        messageData(MessageHandler.FTP_FAIL_PASSWORD_INCORRECT, this.iP),
                        targetIP
                    )
                    return
                }
            }

            val sourceFile = MyFileSystem!!.getFile(path, name)

            var quantity = 1
            if (sourceFile != null) {
                if (sourceFile.isStacking()) {
                    if (sourceFile.getQuantity() < getQuantity) return

                    quantity = sourceFile.getQuantity() - getQuantity
                }
            } else return

            sourceFile.setQuantity(quantity)
            if (sourceFile.getQuantity() <= 0 || !sourceFile.isStacking()) {
                MyFileSystem!!.deleteFile(path, name)
            }

            HF = sourceFile.clone()
            HF!!.setQuantity(getQuantity)
            HF!!.setLocation(fetchPath)
            this.fetchPath = fetchPath

            script = getScript

            if (targetIP != this@FTPProgram.computer!!.getIP()) this@FTPProgram.computer!!.computerHandler
                .addData(ApplicationData(RequestFtpUpdatePayload, 0, this@FTPProgram.computer!!.getIP()), targetIP)
            this@FTPProgram.computer!!.computerHandler
                .addData(ApplicationData(RequestFtpUpdatePayload, 0, this@FTPProgram.computer!!.getIP()), this@FTPProgram.computer!!.getIP())
        } else if (function == "put") {
            val payload = applicationData.payloadAs<PutFilePayload>()
            val targetIP = payload.targetIp
            val name = payload.name
            val fetchPath = payload.fetchPath
            val sourceFile = MyFileSystem!!.getFile(fetchPath, name)
            val tpath = payload.targetPath
            val putQuantity = payload.quantity

            var quantity = 1
            if (sourceFile != null) {
                if (sourceFile.isStacking()) {
                    if (sourceFile.getQuantity() < putQuantity) return

                    quantity = sourceFile.getQuantity() - putQuantity
                }
            } else return

            sourceFile.setQuantity(quantity)
            if (sourceFile.getQuantity() <= 0 || !sourceFile.isStacking()) {
                MyFileSystem!!.deleteFile(fetchPath, name)
            }

            val sendFile = sourceFile.clone()
            sendFile.setQuantity(putQuantity)
            sendFile.setLocation(tpath)

            this@FTPProgram.computerHandler!!.addData(
                ApplicationData(
                    FinalizePutPayload(
                        this@FTPProgram.computer!!.getIP(),
                        name,
                        fetchPath,
                        tpath ?: "",
                        payload.password,
                        sendFile
                    ),
                    ParentPort!!.getNumber(),
                    this@FTPProgram.computer!!.getIP()
                ),
                targetIP
            )

            if (targetIP != this@FTPProgram.computer!!.getIP()) this@FTPProgram.computerHandler!!.addData(
                ApplicationData(RequestFtpUpdatePayload, 0, this@FTPProgram.computer!!.getIP()),
                targetIP
            )
            this@FTPProgram.computerHandler!!.addData(
                ApplicationData(RequestFtpUpdatePayload, 0, this@FTPProgram.computer!!.getIP()),
                this@FTPProgram.computer!!.getIP()
            )
        } else if (function == "finalizeput") {
            val payload = applicationData.payloadAs<FinalizePutPayload>()
            this.targetIP = this@FTPProgram.computer!!.getIP()
            val name = payload.name
            val fetchPath = payload.fetchPath
            this.path = payload.targetPath
            val password = payload.password
            HF = payload.file

            if (this@FTPProgram.computer!!.fileSystem.getSpaceLeft() <= 0) {
                this@FTPProgram.computerHandler!!.addData(
                    messageData(MessageHandler.FTP_PUT_FAIL_HD_FULL, this.iP),
                    applicationData.getSourceIP()
                )
                HF!!.setLocation("")
                this@FTPProgram.computerHandler!!.addData(
                    ApplicationData(SaveFile(applicationData.getSourceIP(), fetchPath, HF), 0, this.iP),
                    applicationData.getSourceIP()
                )
                return
            } else if (applicationData.getSourceIP() != this@FTPProgram.computer!!.getIP()) {
                if (password != this@FTPProgram.computer!!.getPassword()) {
                    this@FTPProgram.computerHandler!!.addData(
                        messageData(MessageHandler.FTP_FAIL_PASSWORD_INCORRECT, this.iP),
                        applicationData.getSourceIP()
                    )
                    HF!!.setLocation("")
                    this@FTPProgram.computerHandler!!.addData(
                        ApplicationData(SaveFile(applicationData.getSourceIP(), fetchPath, HF), 0, this.iP),
                        applicationData.getSourceIP()
                    )
                    return
                }
            }

            script = putScript

            if (targetIP != this@FTPProgram.computer!!.getIP()) this@FTPProgram.computerHandler!!.addData(
                ApplicationData(RequestFtpUpdatePayload, 0, this@FTPProgram.computer!!.getIP()),
                targetIP
            )
            this@FTPProgram.computerHandler!!.addData(
                ApplicationData(RequestFtpUpdatePayload, 0, this@FTPProgram.computer!!.getIP()),
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
