package game

import assignments.PacketWatch
import com.hackwars.game.program.Program
import com.hackwars.game.program.WatchProgram
import game.payload.BooleanCommandPayload
import hackscript.model.TypeString
import hackscript.model.Variable
import java.util.*

/**
 * Represents a watch which will fire given an appropriate event in the computer.
 */

class Watch(private var computer: Computer?) {
    var observedPorts = ArrayList<Any?>() //The ports that are observed for purposes of switching a firewall.
        private set

    private var MyPort: Port? = null //Port associated with this watch.
    private var TriggerParams: HashMap<*, *>? = null //Parameters can be passed in by trigger watch.

    var quantity = 0f //What quanity of 'X' should this fire on if applicable.
    var port = 0 //What port is this watch on if applicable.
    var on = false //Is this watch on?
    var note: String? = "" //Helpful note associated with port.
    var depositAmount: Float = 0.0f
    var program: Program? = null //Program attached to this watch.
    var type = 0 //Type of watch.
    var searchFireWall = 0 //Type of fire wall to search for.


    /**
     * The CPU Cost of this watch without checking if the port is on
     */
    var actualCpuCost: Float = 0.0f

    /**
     * Return the CPU cost associated with this watch if active
     */
    val currentCpuCost: Float get() = actualCpuCost.takeIf { on } ?: 0.0f

    /**
     * Set the quanity that this watch will fire on.
     * What this quantity actually is varies depending on the type of watch. */
    /**
     * Get the intial quanity that this watch is currently set to.
     */
    var initialQuantity: Float = 0f //The quantity that this watch starts at.

    /**
     * Fetch whether this watch is being triggered by an external process or internal one.
     */
    var external: Boolean = false //Is this watch being run from an external process or an internal one?

    /**
     * Set the IP that triggered this watch.
     */
    //Attack specific.
    var targetIP: String? = "" //IP that triggered watch.

    /**
     * Set the port that triggered this watch.
     */
    var targetPort: Int = -1 //Port that triggered watch.

    //The IP of the port that caused this to fire.
    fun setTriggered(triggered: Boolean) {
        (program as WatchProgram).triggered = triggered
    }

    fun setTriggerParam(TriggerParams: HashMap<*, *>?) {
        this.TriggerParams = TriggerParams
    }

    fun getTriggerParameter(key: String?): Variable {
        if (TriggerParams == null) return (TypeString(""))
        val ReturnMe = TriggerParams!!.get(key) as Variable?
        if (ReturnMe == null) return (TypeString(""))
        return (ReturnMe)
    }

    /**
     * Check the array of observed fire walls for the fire wall with the given name.
     */
    fun checkForFireWall(FireWallName: String): Int {
        var FireWallName = FireWallName
        FireWallName = FireWallName.lowercase(Locale.getDefault())
        FireWallName = FireWallName.replace(" ".toRegex(), "")
        val Ports: HashMap<*, *> = computer!!.ports
        for (i in observedPorts.indices) {
            val tport = Ports.get(observedPorts.get(i) as Int?) as Port?
            if (tport != null) {
                if (tport.getFireWall().getName().lowercase(Locale.getDefault()) == FireWallName) {
                    return (tport.getNumber())
                }
            }
        }
        return (-1)
    }

    /**
     * Check whether the given fire wall exisits on the port this program is installed on.
     */
    fun checkFireWall(FireWallName: String): Boolean {
        var FireWallName = FireWallName
        if (MyPort == null) return (false)

        FireWallName = FireWallName.lowercase(Locale.getDefault())
        FireWallName = FireWallName.replace(" ".toRegex(), "")
        if (MyPort!!.getFireWall().getName() == FireWallName) {
            return (true)
        }
        return (false)
    }

    /**
     * Switch the fire wall from the port provided to this port.
     */
    fun switchFireWall(port: Int) {
        if (MyPort == null) return

        val CurrentFireWall = MyPort!!.getFireWall()
        val Ports: HashMap<*, *> = computer!!.ports
        val SwitchPort = Ports.get(port) as Port?

        if (SwitchPort == null) return

        if (!SwitchPort.getOn()) return

        val SwitchFireWall = SwitchPort.getFireWall()

        if (switchPossible(SwitchPort)) {
            val F1 = MyPort!!.getFireWall().getHackerFile()
            val F2 = SwitchFireWall.getHackerFile()
            MyPort!!.getFireWall().loadHackerFile(F2)
            SwitchPort.getFireWall().loadHackerFile(F1)
        }

        computer!!.sendDamagePacket()
    }

    /**
     * Return whether or not this action will cause things to overheat.
     */
    fun switchPossible(TempPort: Port): Boolean {
        //Check to see if this will cause an overheat.
        var cpuLoad = computer!!.cPULoad
        if (TempPort.getDummy()) cpuLoad -= TempPort.getFireWall().getCPUCost() / 2.0f
        else cpuLoad -= TempPort.getFireWall().getCPUCost()

        if (MyPort!!.getDummy()) cpuLoad -= MyPort!!.getFireWall().getCPUCost() / 2.0f
        else cpuLoad -= MyPort!!.getFireWall().getCPUCost()

        if (TempPort.getDummy()) cpuLoad += MyPort!!.getFireWall().getCPUCost() / 2.0f
        else cpuLoad += MyPort!!.getFireWall().getCPUCost()

        if (MyPort!!.getDummy()) cpuLoad += TempPort.getFireWall().getCPUCost() / 2.0f
        else cpuLoad += TempPort.getFireWall().getCPUCost()

        if (cpuLoad > computer!!.maximumCPULoad) {
            return (false)
        }

        return (true)
    }

    /**
     * Switch any fire wall from the observed ports to this port.
     */
    fun switchAnyFireWall() {
        if (MyPort == null) return

        val CurrentFireWall = MyPort!!.getFireWall()
        val Ports: HashMap<*, *> = computer!!.ports
        var SwitchPort: Port? = null

        val PortIterator: MutableIterator<*> = Ports.entries.iterator()
        var ii = 0

        for (i in observedPorts.indices) {
            val TempPort = Ports.get(observedPorts.get(i) as Int?) as Port
            if (!(TempPort.getFireWall().getName() == "None")) {
                if (switchPossible(TempPort)) {
                    SwitchPort = TempPort
                    break
                }
            }
            ii++
        }

        if (SwitchPort == null) return

        if (!SwitchPort.getOn()) return

        val SwitchFireWall = SwitchPort.getFireWall()

        val F1 = MyPort!!.getFireWall().getHackerFile()
        val F2 = SwitchFireWall.getHackerFile()
        MyPort!!.getFireWall().loadHackerFile(F2)
        SwitchPort.getFireWall().loadHackerFile(F1)

        computer!!.sendDamagePacket()
    }

    /**
     * Shut down all the ports being observed by this watch.
     */
    fun shutDownPorts() {
        val Ports: HashMap<*, *> = computer!!.ports
        val PortIterator: MutableIterator<*> = Ports.entries.iterator()
        var ii = 0
        for (i in observedPorts.indices) {
            val TempPort = Ports.get(observedPorts.get(i) as Int?) as Port?
            if (TempPort != null) {
                val ip = computer!!.getIP()
                val port = TempPort.getNumber()
                val on = false
                computer!!.computerHandler.addData(
                    ApplicationData(
                        BooleanCommandPayload(com.hackwars.rpc.GameCommands.PORTONOFF.command, on),
                        port,
                        ip
                    ),
                    ip
                )
            }
            ii++
        }
    }

    /**
     * Shut down the port number provided.
     */
    fun shutDownPort(port: Int) {
        val Ports: HashMap<*, *> = computer!!.ports
        val TempPort = Ports.get(port) as Port?
        if (TempPort == null) return
        if (TempPort != null) {
            val ip = computer!!.getIP()
            val on = false
            computer!!.computerHandler.addData(
                ApplicationData(BooleanCommandPayload(com.hackwars.rpc.GameCommands.PORTONOFF.command, on), port, ip),
                ip
            )
        }
    }

    /**
     * Shut down all the ports being observed by this watch.
     */
    fun turnOnPorts() {
        val Ports: HashMap<*, *> = computer!!.ports
        val PortIterator: MutableIterator<*> = Ports.entries.iterator()
        var ii = 0
        for (i in observedPorts.indices) {
            val TempPort = Ports.get(observedPorts.get(i) as Int?) as Port?
            if (TempPort != null) {
                val ip = computer!!.getIP()
                val port = TempPort.getNumber()
                val on = true
                computer!!.computerHandler.addData(
                    ApplicationData(
                        BooleanCommandPayload(com.hackwars.rpc.GameCommands.PORTONOFF.command, on),
                        port,
                        ip
                    ),
                    ip
                )
            }
            ii++
        }
    }

    /**
     * Shut down the port number provided.
     */
    fun turnOnPort(port: Int) {
        val Ports: HashMap<*, *> = computer!!.ports
        val TempPort = Ports.get(port) as Port?
        if (TempPort == null) return
        if (TempPort != null) {
            val ip = computer!!.getIP()
            val on = true
            computer!!.computerHandler.addData(
                ApplicationData(BooleanCommandPayload(com.hackwars.rpc.GameCommands.PORTONOFF.command, on), port, ip),
                ip
            )
        }
    }

    /**
     * Get the IP of the computer associated with this watch.
     */
    val iP: String
        get() = computer!!.getIP()

    /**
     * Get the port number associated with this watch.
     */
    val number: Int
        get() = MyPort?.number ?: 0

    /**
     * Set the port that this program is installed on.
     */
    fun setPort(MyPort: Port?) {
        this.MyPort = MyPort
    }

    /**
     * Exectue the program attached to this port.
     */
    fun execute(MyApplicationData: ApplicationData) {
        program!!.execute(MyApplicationData)
    }

    /**
     * Add an observerd port.
     */
    fun addObservedPort(observedPort: Int) {
        observedPorts.add(observedPort)
    }

    /**
     * Set the list of observed ports.
     */
    fun setObservedPorts(portArray: Array<Int>?) {
        observedPorts = ArrayList<Any?>()
        portArray?.forEach { observedPorts.add(it) }
    }

    /**
     * Get the packet version of the watch.
     */
    val packetWatch: PacketWatch
        get() {
            val returnMe = PacketWatch()
            returnMe.setType(type)
            returnMe.setOn(on)
            returnMe.setPort(port)
            returnMe.setNote(note)
            returnMe.setCPUCost(actualCpuCost)
            returnMe.setSearchFireWall(searchFireWall)
            returnMe.setQuantity(quantity)
            returnMe.setObservedPorts(observedPorts)
            return returnMe
        }

    /**
     * Output this watch in XML format for saving.
     */
    fun outputXML(): String {
        var returnMe = "<watch>\n"
        returnMe += "<cpu>" + actualCpuCost + "</cpu>\n"
        if (on) returnMe += "<on>1</on>\n"
        else returnMe += "<on>0</on>\n"
        returnMe += "<type>" + type + "</type>\n"

        if (note != null) returnMe += "<note><![CDATA[" + note!!.replace("]]>".toRegex(), "]]&gt;") + "]]></note>\n"
        else returnMe += "<note><![CDATA[" + note + "]]></note>\n"


        for (i in observedPorts.indices) {
            returnMe += "<observedport>" + observedPorts.get(i) as Int? + "</observedport>\n"
        }
        returnMe += "<installport>" + port + "</installport>\n"

        returnMe += "<searchfirewall>" + searchFireWall + "</searchfirewall>\n"
        returnMe += "<quantity>" + quantity + "</quantity>\n"
        returnMe += program!!.outputXML()
        returnMe += "</watch>\n"
        return (returnMe)
    }

    companion object {
        //Watch constants.
        const val HEALTH: Int = 0
        const val PETTY_CASH: Int = 1
        const val SCAN: Int = 2
    }
}
