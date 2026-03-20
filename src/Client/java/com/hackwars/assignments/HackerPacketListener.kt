package com.hackwars.assignments

import assignments.PacketAssignment
import assignments.RemoteFunctionCall
import gui.Hacker
import gui.MacroDialog
import java.awt.image.BufferedImage

class HackerPacketListener(private val onFunctionCall: (RemoteFunctionCall) -> Unit) : PacketAssignmentListener {
    val lock = Any()
    var receiver: Hacker? = null
        set(value) {
            synchronized(lock) {
                field = value
            }
        }

    override fun onPacketAssignment(event: AssignmentEvent<PacketAssignment>) {
        val assignment = event.assignment
        println(
            "Applying PacketAssignment: requestPrimary=${assignment.requestPrimary()} requestHardware=${assignment.requestHardware} " +
                "directory=${assignment.directory != null} secondaryDirectory=${assignment.secondaryDirectory != null} " +
                "packetPorts=${assignment.packetPorts?.size ?: 0} messages=${assignment.messages?.size ?: 0}"
        )

        synchronized(lock) {
            receiver?.let { receiver ->
                if (receiver.loading) return

                receiver.pettyCash = assignment.pettyCash
                receiver.loadFile = assignment.loadFile
                receiver.loadFile = assignment.loadFile
                receiver.bankMoney = assignment.bankMoney
                receiver.defaultBank = assignment.defaultBank
                receiver.defaultAttack = assignment.defaultAttack
                receiver.setDefaultFTP(assignment.defaultFTP)
                receiver.setDefaultHTTP(assignment.defaultHTTP)
                receiver.defaultRedirect = assignment.defaultShipping
                receiver.cpuType = assignment.cpuType
                receiver.hdType = assignment.hdType
                receiver.hdMax = assignment.hdMaximum
                receiver.hdQuantity = assignment.hdQuantity
                receiver.memoryType = assignment.memoryType
                receiver.setHackOMeter(assignment.hackCount)
                receiver.setVoteOMeter(assignment.voteCount)
                receiver.setServerLoad(assignment.serverLoad)
                receiver.setCommodities(assignment.commodities)
                receiver.statsPanel.cpuLoadIcon.load = assignment.cpuCost.toInt().toFloat()
                receiver.statsPanel.cpuLoadIcon.totalLoad = assignment.cpuMax.toInt().toFloat()
                receiver.healDiscount = assignment.healDiscount
                receiver.setVotesLeft(assignment.votesLeft)

                if (assignment.preferences != null) {
                    receiver.preferences = assignment.preferences
                }
                if (assignment.countDown != -1) {
                    receiver.setCountDown(assignment.countDown)
                }
                assignment.messages.forEach(receiver::showMessage)
                if (assignment.packetPorts != null) {
                    receiver.setPorts(assignment.packetPorts)
                }
                if (assignment.directory != null) {
                    receiver.receivedDirectory(assignment.directory)
                }
                if (assignment.secondaryDirectory != null) {
                    receiver.receivedSecondaryDirectory(
                        assignment.secondaryDirectory,
                        assignment.allowedDir
                    )
                }
                if (assignment.file != null) {
                    receiver.receivedFile(assignment.file)
                }
                if (assignment.body != null) {
                    receiver.receivedPage(assignment.title, assignment.body)
                }
                if (assignment.scannedPorts != null) {
                    receiver.receivedScan(assignment.scannedPorts)
                }
                if (assignment.packetWatches != null) {
                    receiver.receivedWatches(assignment.packetWatches)
                }
                if (assignment.requestPrimary()) {
                    val reqDir: Int = receiver.requestedDirectory
                    val objects: Array<Any?> = arrayOf(receiver.encryptedIP, receiver.currentFolder)
                    if (reqDir != Hacker.BROWSER && reqDir != Hacker.EQUIPMENT) {
                        onFunctionCall(
                            RemoteFunctionCall(
                                assignment.requestPrimaryID,
                                "requestdirectory",
                                objects
                            )
                        )
                    } else if (reqDir == Hacker.EQUIPMENT) {
                        onFunctionCall(RemoteFunctionCall(Hacker.EQUIPMENT, "requestequipment", objects))
                    }
                }
                if (assignment.requestHardware) {
                    receiver.requestedDirectory = Hacker.EQUIPMENT
                    onFunctionCall(
                        RemoteFunctionCall(
                            Hacker.EQUIPMENT,
                            "requestequipment",
                            arrayOf<Any?>(receiver.encryptedIP)
                        )
                    )
                }
                if (assignment.requestSecondary()) {
                    onFunctionCall(
                        RemoteFunctionCall(
                            Hacker.FTP, "requestsecondarydirectory", arrayOf<Any?>(
                                receiver.ftpip,
                                receiver.secondaryFolder,
                                receiver.encryptedIP,
                                receiver.ftpPort
                            )
                        )
                    )
                }
                if (assignment.choices.size != 0) {
                    receiver.showChoices(assignment.choices)
                }
                if (assignment.peakCode != null) {
                    receiver.showCode(assignment.peakCode)
                }
                if (assignment.logUpdate != null) {
                    receiver.receivedLogUpdate(assignment.logUpdate)
                }
                if (assignment.captcha != null) {
                    MacroDialog(
                        BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB).apply {
                            setRGB(0, 0, 175, 45, assignment.captcha as? IntArray?, 0, 175)
                        },
                        receiver
                    )
                }
                if (assignment.packetNetwork != null) {
                    receiver.setNetwork(assignment.packetNetwork)
                }
            }
        }
    }
}
