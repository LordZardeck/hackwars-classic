package game

import game.payload.DamagePayload
import game.payload.PettyCashDeltaPayload
import game.payload.PettyCashTransferPayload
import game.payload.WatchXpPayload

/**
 * Handles all the watches currently installed.
 */

class WatchHandler(private val computer: Computer?, private val computerHandler: ComputerHandler?) {
    companion object {
        //Types of watches.
        const val HEALTH: Int = 0
        const val PETTY_CASH: Int = 1
        const val SCAN: Int = 2
    }

    val watches = ArrayList<Any?>() //Array of watches currently installed.

    /**
     * Check for a watch at the given port number.
     */
    fun checkForWatch(port: Int): Boolean {
        return watches.find { (it as? Watch)?.port == port }?.let { true } ?: false
    }

    /**
     * Automatically trigger a watch.
     */
    fun triggerWatch(index: Int, sourceIP: String?, parameters: HashMap<*, *>?) {
        (watches.getOrNull(index) as? Watch)?.takeIf { it.on }?.let { watch ->
            watch.targetIP = sourceIP
            watch.targetPort = 0
            watch.external = true
            watch.setPort(computer?.ports[watch.port] as? Port)
            watch.setTriggerParam(parameters)
            watch.setTriggered(true)
            watch.execute(
                ApplicationData(
                    DamagePayload(
                        damage = 0.0f,
                        targetIp = sourceIP,
                        targetPort = 0,
                        damageFromFireWall = false,
                        zombieSource = sourceIP,
                        windowHandle = 0,
                        commodityId = -1
                    ),
                    0,
                    sourceIP
                )
            )
        }
    }


    /**
     * Automatically trigger a watch.
     */
    fun triggerWatch(note: String?, sourceIP: String?, TriggerParam: HashMap<*, *>?) {
        var TempWatch: Watch? = null
        for (i in watches.indices) {
            val Temp = watches.get(i) as Watch?
            if (Temp != null && Temp.note == note) {
                TempWatch = Temp
                break
            }
        }

        if (TempWatch != null && TempWatch.on) {
            TempWatch.targetIP = sourceIP
            TempWatch.targetPort = 0
            val TempPort = computer!!.ports.get(TempWatch.port) as Port?
            TempWatch.setPort(TempPort)
            val AD = ApplicationData(
                DamagePayload(
                    damage = 0.0f,
                    targetIp = sourceIP,
                    targetPort = 0,
                    damageFromFireWall = false,
                    zombieSource = sourceIP,
                    windowHandle = 0,
                    commodityId = -1
                ),
                0,
                sourceIP
            )
            TempWatch.setTriggerParam(TriggerParam)
            TempWatch.setTriggered(true)

            //System.out.println("About to execute watch based on the note found.");
            TempWatch.execute(AD)
        }
    }

    /**
     * Add a new watch to the list of watches.
     */
    fun addWatch(W: Watch?) {
        watches.add(W)
    }

    /**
     * Remove a watch from the Watches list.
     */
    fun removeWatch(index: Int) {
        watches.removeAt(index)
    }

    /**
     * Get a watch.
     */
    fun getWatch(index: Int): Watch? {
        return watches.getOrNull(index) as? Watch
    }

    /**
     * Destroy all the watches installed on the given port number.
     */
    fun destroyWatches(port: Int) {
        val watchIterator = watches.iterator()
        while (watchIterator.hasNext()) {
            (watchIterator.next() as? Watch)
                ?.takeIf { watch -> watch.port == port && watch.type != SCAN && watch.on }
                ?.let {
                    watchIterator.remove()
                }
        }
    }

    /**
     * Update the initial health quanity for a given port.
     */
    fun updateInitialHealthQuantity(port: Int, quantity: Float) {
        val watchIterator = watches.iterator()
        while (watchIterator.hasNext()) {
            (watchIterator.next() as? Watch)
                ?.takeIf { watch -> watch.port == port && watch.type == HEALTH }
                ?.apply {
                    initialQuantity = quantity
                }
        }
    }


    /**
     * Get the number of watches that are currently on.
     */
    val watchCount get() = watches.filter { (it as? Watch)?.on == true }.size

    /**
     * Check the watches, and return the current CPU load.
     */
    fun checkWatches(applicationData: ApplicationData, ports: HashMap<*, *>, pettyCash: Float): Float {
        val commandName = applicationData.command.wireName()
        var gainedXP = false
        var watchCost = 0.0f
        val MyIterator = watches.iterator()
        var TempWatch: Watch? = null
        while (MyIterator.hasNext()) {
            TempWatch = MyIterator.next() as Watch?
            if (TempWatch!!.on) {
                val TempPort = ports.get(TempWatch.port) as Port?
                var overheated = false
                if (TempPort != null) overheated = TempPort.getOverHeated()

                if (!overheated || commandName == com.hackwars.rpc.GameCommandWires.SCANSUCCESS) { //Make sure our port isn't overheated.

                    if (commandName == com.hackwars.rpc.GameCommandWires.DAMAGE) {
                        val damagePayload = applicationData.payloadAs<DamagePayload>()
                        var zombieDamage =
                            false //Is the damge being dealt by a port that has been maliciously taken over?
                        val zombieSource = damagePayload.zombieSource
                        if (zombieSource != null) {
                            zombieDamage = true
                        }

                        var targetIP = damagePayload.targetIp
                        if (zombieDamage) {
                            targetIP = zombieSource
                        }
                        val targetPort = damagePayload.targetPort

                        if (TempWatch.type == HEALTH) { //Fired when health reaches a certain quanity.

                            if (TempPort != null && applicationData.port == TempPort.getNumber()) {
                                val value = TempPort.getHealth()
                                if ((TempWatch.initialQuantity >= TempWatch.quantity) && (value < TempWatch.quantity)) {
                                    //Set the source of the watch to internal or external.
                                    if (applicationData.source == ApplicationData.INSIDE) TempWatch.external =
                                        false
                                    else TempWatch.external = true

                                    TempWatch.targetIP = targetIP
                                    TempWatch.targetPort = targetPort
                                    TempWatch.setPort(TempPort)
                                    TempWatch.execute(applicationData)
                                    if (!gainedXP) {
                                        computerHandler!!.addData(
                                            ApplicationData(
                                                WatchXpPayload(computer!!.watchLevel),
                                                0,
                                                computer!!.getIP()
                                            ), computer!!.getIP()
                                        )
                                        gainedXP = true
                                    }
                                }
                                TempWatch.initialQuantity = value
                            }
                        }
                    }

                    //Make sure the initial quantity value remains valid.
                    if (commandName == com.hackwars.rpc.GameCommandWires.BANK) {
                        if (TempWatch.type == PETTY_CASH) {
                            TempWatch.initialQuantity = pettyCash
                        }
                    }

                    //Fired when petty cash reaches a certain amount.
                    if (commandName == com.hackwars.rpc.GameCommandWires.PETTYCASH) {
                        val value = pettyCash

                        if (TempWatch.type == PETTY_CASH) {
                            if (((TempWatch.initialQuantity < TempWatch.quantity) && (value >= TempWatch.quantity))) {
                                if (TempPort != null && TempPort.getOn() && !TempPort.getDummy()) {
                                    if (TempPort.getType() == Port.BANKING) { //Make sure this is installed on banking.

                                        //Set the source of the watch to internal or external.

                                        if (applicationData.source == ApplicationData.INSIDE) TempWatch.external =
                                            false
                                        else TempWatch.external = true

                                        val amount = value - TempWatch.initialQuantity
                                        val deposit = when (val payload = applicationData.payload) {
                                            is PettyCashDeltaPayload -> payload.amount
                                            is PettyCashTransferPayload -> payload.amount
                                            else -> error("Expected pettycash payload for ${applicationData.command.wireName()}")
                                        }

                                        TempWatch.depositAmount = deposit
                                        TempWatch.targetIP = applicationData.sourceIP
                                        TempWatch.targetPort = TempPort.getNumber()
                                        TempWatch.setPort(TempPort)
                                        TempWatch.execute(applicationData)
                                        if (!gainedXP) {
                                            computerHandler!!.addData(
                                                ApplicationData(
                                                    WatchXpPayload(amount / 50.0f),
                                                    0,
                                                    computer!!.getIP()
                                                ), computer!!.getIP()
                                            )
                                            gainedXP = true
                                        }
                                    }
                                }
                            }
                            TempWatch.initialQuantity = value
                        }
                    }

                    //Fired when petty cash reaches a certain amount.
                    if (commandName == com.hackwars.rpc.GameCommandWires.SCANSUCCESS) {
                        val value = pettyCash
                        if (TempWatch.type == SCAN) {
                            //Set the source of the watch to internal or external.

                            if (applicationData.source == ApplicationData.INSIDE) TempWatch.external = false
                            else TempWatch.external = true

                            TempWatch.targetIP = applicationData.sourceIP
                            TempWatch.targetPort = 0
                            TempWatch.execute(applicationData)
                            if (!gainedXP) {
                                computerHandler!!.addData(
                                    ApplicationData(
                                        WatchXpPayload(computer!!.watchLevel / 4.0f),
                                        0,
                                        computer!!.getIP()
                                    ), computer!!.getIP()
                                )
                                gainedXP = true
                            }
                        }
                    }
                }
                watchCost += TempWatch.currentCpuCost
            }
        }
        return (watchCost)
    }

    /**
     * Output the contents of this class as an XML string.
     */
    fun outputXML(): String {
        var returnMe = "<watches>\n"
        for (i in watches.indices) {
            returnMe += (watches.get(i) as Watch).outputXML()
        }
        returnMe += "</watches>\n"
        return (returnMe)
    }
}
