package com.hackwars.game.program

import game.*
import game.payload.RequestWebPagePayload
import game.payload.SubmitPayload
import game.payload.WebPagePayload
import hackscript.model.RunFactory

/**
 * FTPProgram.java
 * 
 * 
 * A program that can be installed on a port of type HTTP and which performs "enter" and "exit" and "submit" operations.
 */

class HTTPProgram(computer: Computer?, computerHandler: NetworkSwitch?) : Program(computer, computerHandler) {
    //Scripts.
    private var enterScript: String? = ""
    private var exitScript: String? = ""
    private var submitScript: String? = ""
    private var content = computer?.body ?: ""

    private var targetIP: String? = "" //IP of computer that caused this program to run.
    private var Parameters: HashMap<*, *>? = null //The parameters submitted along with a submit.

    /**
     * Trigger a watch.
     */
    fun triggerWatch(watchNumber: Int, TriggerParam: HashMap<*, *>?) {
        computer!!.watchHandler.triggerWatch(watchNumber, targetIP, TriggerParam)
    }

    /**
     * Get the path of the file being transfered.
     */
    fun getTargetIP(): String? {
        return (targetIP)
    }

    /**
     * Should the store be served?
     */
    var hideStore: Boolean = false

    fun hideStore() {
        hideStore = true
    }

    /**
     * HTTP Get variable.
     */
    var GetString: HashMap<*, *>? = null

    fun fetchGetVariable(key: String?): String {
        if (GetString == null) return ("")
        val Data = GetString!!.get(key) as String?
        if (Data == null) return ("")
        else return (Data)
    }

    /**
     * Replace a place-holder string in the HTML page.
     */
    fun replaceContent(key: String?, content: String) {
        var content = content
        content = content.replace("\\\\".toRegex(), "\\\\\\\\")
        content = content.replace("\\$".toRegex(), "\\\\\\$")
        this.content = this.content.replace(("\\<\\?" + key + "\\?\\>").toRegex(), content)
    }

    /**
     * Get a parameter from
     */
    fun getParameter(Key: String?): String? {
        var returnMe: String? = ""
        if (Parameters == null) return ("")
        else returnMe = Parameters!!.get(Key) as String?
        if (Key != null) return (returnMe)
        else return ("")
    }

    /**
     * Execute the commands.
     */
    override fun execute(applicationData: ApplicationData) {
        content = computer!!.body
        var script: String? = ""
        hideStore = false
        var packetID: Int? = 0

        when (applicationData.command.wireName()) {
            "requestwebpage" -> {
                val payload = applicationData.readRequestWebPagePayload()
                GetString = payload.requestParameters
                packetID = GetString?.get("packetid") as Int?
                targetIP = applicationData.sourceIP
                script = enterScript
            }

            "exit" -> {
                targetIP = applicationData.sourceIP
                script = exitScript
            }

            "submit" -> {
                targetIP = applicationData.sourceIP
                val payload = applicationData.readSubmitPayload()
                Parameters = payload.submitParameters
                packetID = Parameters?.get("packetid") as Int?
                script = submitScript

                try {
                    val HL = HackerLinker(this, computerHandler)
                    RunFactory.runCode(script, HL, computer!!.MAX_OPS)
                } catch (e: Exception) {
                }

                script = enterScript
            }
        }

        if (script != null && script != "") {
            try {
                val HL = HackerLinker(this, computerHandler)
                RunFactory.runCode(script, HL, computer!!.MAX_OPS)
            } catch (e: Exception) {
            }
        }

        if (applicationData.command.wireName() != com.hackwars.rpc.GameCommandWires.EXIT)  //Serve the web-page.
            serveWebPage(applicationData, packetID)

        Parameters = null
    }

    /**
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    override fun installScript(script: HashMap<*, *>) {
        enterScript = script.get("enter") as String?
        exitScript = script.get("exit") as String?
        submitScript = script.get("submit") as String?
    }

    /**
     * Return a hash map representation of the program currently installed on this port.
     */
    override fun getContent(): HashMap<*, *> {
        val returnMe: HashMap<Any?, Any?> = HashMap()
        returnMe.put("enter", enterScript)
        returnMe.put("exit", exitScript)
        returnMe.put("submit", submitScript)
        return (returnMe)
    }

    /**
     * Returns the keys associated with this program type.
     */
    override fun getTypeKeys(): Array<String?> {
        val returnMe: Array<String?>? = arrayOf<String?>("enter", "exit", "submit")
        return (returnMe!!)
    }

    /**
     * Server a webpage to a player.
     */
    fun serveWebPage(MyApplicationData: ApplicationData, packetID: Int?) {
        val activeComputer = computer ?: return
        var PageTitle = activeComputer.title
        var PageBody: String? = content

        //Receive Payment.
        var Files = activeComputer.fileSystem.getWebDirectory("Store/")
        for (i in Files!!.indices) { //Make sure we describe hardware.
            if (Files[i] != null && Files[i] is HackerFile && ((Files[i] as HackerFile).type == HackerFile.PCI || (Files[i] as HackerFile).type == HackerFile.AGP)) {
                activeComputer.equipmentController.degrade(Files[i] as HackerFile?)
                activeComputer.equipmentSheet.describeCard(Files[i] as HackerFile?) //Testing outputting a description of the bonus.
            }
        }

        var TempPort: Port? = null
        if (!computer!!.checkHTTP()) {
            //if(((TempPort=(Port)MyComputer.getPorts().get(new Integer(MyComputer.getDefaultHTTP())))==null)||TempPort.getType()!=Port.HTTP||!TempPort.getOn()||TempPort.getDummy()){
            PageTitle = "Server Not Found"
            PageBody =
                "<html><head><title>Hack Wars - Error report</title><style><!--H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;color:white} H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} BODY {background-color:rgb(0,0,0);font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;color:white;} B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;color:white;} P {color:white;font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}A {color : black;}A.name {color : black;}HR {color : #525D76;}--></style> </head><body><h1 style=\"width:100%\">HTTP Status 408</h1><HR size=\"1\" noshade=\"noshade\"><p style=\"background-color:black;\"><b>type</b> HTTP Error</p><p style=\"background-color:black;\"><b>message</b> <u>Resource not found.</u></p><p style=\"background-color:black\"><b>description</b> <u>The HTTP server of the player you attempted to connect to does not seem to be on.</u></p><HR size=\"1\" noshade=\"noshade\"><h3>&copy; Hack Wars</h3></body></html>"
            Files = null
        }

        //If no banking application is found don't return the store listing.
        if (!computer!!.checkBank()) {
            Files = null
        }

        //If the default FTP application is null don't return the store listing.
        if ((((computer!!.ports.get(computer!!.defaultFTP) as Port?).also {
                TempPort = it
            }) == null) || TempPort!!.type != Port.FTP || !TempPort.on) {
            Files = null
        }

        if (hideStore) Files = null

        computerHandler!!.addData(
            ApplicationData(WebPagePayload(PageTitle, PageBody ?: "", Files, packetID), 0, computer!!.ip),
            MyApplicationData.sourceIP
        )
    }


    /**
     * Output the class data in XML format.
     */
    override fun outputXML(): String {
        var returnMe = ""
        if (enterScript != null) returnMe += "<enter><![CDATA[" + enterScript!!.replace(
            "]]>".toRegex(),
            "]]&gt;"
        ) + "]]></enter>\n"
        else returnMe += "<enter><![CDATA[" + enterScript + "]]></enter>\n"

        if (exitScript != null) returnMe += "<exit><![CDATA[" + exitScript!!.replace(
            "]]>".toRegex(),
            "]]&gt;"
        ) + "]]></exit>\n"
        else returnMe += "<exit><![CDATA[" + exitScript + "]]></exit>\n"

        if (submitScript != null) returnMe += "<submit><![CDATA[" + submitScript!!.replace(
            "]]>".toRegex(),
            "]]&gt;"
        ) + "]]></submit>\n"
        else returnMe += "<submit><![CDATA[" + submitScript + "]]></submit>\n"

        return (returnMe)
    }

    private fun ApplicationData.readRequestWebPagePayload(): RequestWebPagePayload {
        return payload as? RequestWebPagePayload
            ?: error("Expected RequestWebPagePayload for ${command.wireName()}")
    }

    private fun ApplicationData.readSubmitPayload(): SubmitPayload {
        return payload as? SubmitPayload
            ?: error("Expected SubmitPayload for ${command.wireName()}")
    }
}
