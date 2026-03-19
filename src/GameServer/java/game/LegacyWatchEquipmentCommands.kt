package game

import assignments.PacketWatch
import com.hackwars.game.program.Program
import com.hackwars.game.program.WatchProgram
import java.util.HashMap

/**
 * Legacy watch/equipment command extraction from [Computer.processQueuedItem].
 */
class LegacyWatchEquipmentCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        val function = applicationData.function

        if (function == "changewatchtype") {
            val parameters = applicationData.parameters as Array<Int>
            val targetWatch = parameters[0]
            val newType = parameters[1]

            if (targetWatch < computer.MyWatchHandler.watches.size) {
                val myWatch = computer.MyWatchHandler.getWatch(targetWatch) as Watch
                myWatch.type = newType
                queueFetchWatches(computer)
            }
            return true
        } else if (function == "installwatch") {
            val parameters = applicationData.parameters as Array<Any?>
            val path = parameters[0] as String
            val name = parameters[1] as String
            val type = parameters[2] as Int

            val hackerFile = computer.fileSystem.getFile(path, name)

            if (computer.MyWatchHandler.watches.size < 21) {
                if (hackerFile != null && hackerFile.type == HackerFile.WATCH_COMPILED) {
                    val cpuCheck = computer.cPULoad + hackerFile.cpuCost
                    val maxCpu = computer.maximumCPULoad
                    if (cpuCheck <= maxCpu) {
                        hackerFile.quantity = hackerFile.quantity - 1
                        if (hackerFile.quantity <= 0) {
                            computer.fileSystem.deleteFile(path, name)
                        }

                        val watch = Watch(computer)
                        watch.type = type
                        watch.searchFireWall = 0
                        watch.actualCpuCost = hackerFile.cpuCost
                        watch.note = name
                        watch.on = false
                        watch.quantity = 0.0f

                        if (watch.type == Watch.PETTY_CASH) {
                            watch.initialQuantity = computer.getPettyCash()
                        } else if (watch.type == Watch.HEALTH) {
                            watch.initialQuantity = 100.0f
                        }

                        watch.port = applicationData.port
                        val script = hackerFile.content as HashMap<*, *>
                        val program: Program = WatchProgram(computer, computer.MyComputerHandler, watch)
                        program.computerHandler = computer.MyComputerHandler
                        program.installScript(script)
                        watch.program = program
                        computer.MyWatchHandler.addWatch(watch)
                    } else {
                        computer.addMessage(MessageHandler.CPU_TOO_HIGH)
                    }
                } else {
                    computer.addMessage(MessageHandler.FILE_NOT_FOUND)
                }
            } else {
                computer.addMessage(MessageHandler.MAX_WATCHES_REACHED)
            }

            queueFetchWatches(computer)
            return true
        } else if (function == "requestequipment") {
            var equipment = computer.MyFileSystem.getEquipment("")
            var temp = arrayOfNulls<Any?>(equipment.size + 1)
            temp[0] = applicationData.parameters
            for (i in equipment.indices) {
                if (equipment[i] != null) {
                    computer.MyEquipmentSheet.describeCard(equipment[i] as HackerFile)
                }
                temp[i + 1] = equipment[i]
            }
            equipment = temp
            computer.PA.directory = equipment

            equipment = computer.MyEquipmentSheet.getEquipment()
            temp = arrayOfNulls(equipment.size + 1)
            temp[0] = applicationData.parameters
            for (i in equipment.indices) {
                if (equipment[i] != null) {
                    computer.MyEquipmentSheet.describeCard(equipment[i] as HackerFile)
                }
                temp[i + 1] = equipment[i]
            }
            equipment = temp

            computer.PA.secondaryDirectory = equipment
            computer.systemChange = true
            return true
        } else if (function == "installequipment") {
            val parameters = applicationData.parameters as Array<Any?>
            val position = parameters[0] as Int
            val name = parameters[1] as String
            computer.MyEquipmentSheet.equip(position, name)

            var equipment = computer.MyFileSystem.getEquipment("")
            var temp = arrayOfNulls<Any?>(equipment.size + 1)
            temp[0] = parameters[2] as Int
            for (i in equipment.indices) {
                temp[i + 1] = equipment[i]
            }
            equipment = temp
            computer.PA.directory = equipment

            equipment = computer.MyEquipmentSheet.getEquipment()
            temp = arrayOfNulls(equipment.size + 1)
            temp[0] = parameters[2] as Int
            for (i in equipment.indices) {
                temp[i + 1] = equipment[i]
            }
            equipment = temp

            computer.PA.secondaryDirectory = equipment
            computer.systemChange = true
            return true
        } else if (function == "repairequipment") {
            val parameters = applicationData.parameters as Array<Any?>
            val position = parameters[0] as Int
            val name = parameters[1] as String

            if (position != -1) {
                computer.MyEquipmentSheet.repair(position)
            } else {
                val equipment = computer.MyFileSystem.getFile("", name)
                if (equipment != null) {
                    computer.MyEquipmentSheet.repair(equipment)
                }
            }

            computer.MyComputerHandler.addData(
                ApplicationData("requestequipment", Integer.valueOf(13), 0, computer.ip),
                computer.ip
            )
            return true
        } else if (function == "fetchwatches") {
            val packetWatches = arrayOfNulls<PacketWatch>(computer.MyWatchHandler.watches.size)
            val watchIterator = computer.MyWatchHandler.watches.iterator()
            var index = 0
            while (watchIterator.hasNext()) {
                val tempWatch = watchIterator.next() as Watch
                packetWatches[index] = tempWatch.packetWatch
                index++
            }
            computer.PA.packetWatches = packetWatches.requireNoNulls()
            computer.systemChange = true
            return true
        } else if (function == "setwatchquantity") {
            val parameters = applicationData.parameters as Array<Any?>
            val watchId = parameters[0] as Int
            val quantity = parameters[1] as Float
            val watch = computer.MyWatchHandler.watches[watchId] as Watch?
            watch?.quantity= quantity
            queueFetchWatches(computer)
            return true
        } else if (function == "setwatchonoff") {
            val parameters = applicationData.parameters as Array<Any?>
            val watchId = parameters[0] as Int
            val state = parameters[1] as Boolean
            val watch = computer.MyWatchHandler.watches[watchId] as Watch?
            if (watch != null) {
                val cpuCheck = computer.cPULoad + watch.actualCpuCost
                val maxCpu = computer.maximumCPULoad
                if (state) {
                    if (computer.MyWatchHandler.watchCount < computer.maximumWatches) {
                        if (cpuCheck <= maxCpu) {
                            watch.on = state
                        }
                    } else {
                        computer.addMessage(MessageHandler.WATCH_ON_FAIL)
                    }
                } else if (computer.cPULoad <= maxCpu) {
                    watch.on = state
                }
            }
            queueFetchWatches(computer)
            return true
        } else if (function == "setwatchsearchfirewall") {
            val parameters = applicationData.parameters as Array<Any?>
            val watchId = parameters[0] as Int
            val searchFireWall = parameters[1] as Int
            val watch = computer.MyWatchHandler.watches[watchId] as Watch?
            if (watch != null) {
                watch.searchFireWall = searchFireWall
            }
            queueFetchWatches(computer)
            return true
        } else if (function == "setwatchnote") {
            val parameters = applicationData.parameters as Array<Any?>
            val watchId = parameters[0] as Int
            val note = parameters[1] as String
            val watch = computer.MyWatchHandler.watches[watchId] as Watch?
            if (watch != null) {
                watch.note = note
            }
            queueFetchWatches(computer)
            return true
        } else if (function == "deletewatch") {
            val maxCpu = computer.maximumCPULoad
            if (computer.cPULoad <= maxCpu) {
                val parameters = applicationData.parameters as Array<Any?>
                val watchId = parameters[0] as Int
                computer.MyWatchHandler.removeWatch(watchId)
                queueFetchWatches(computer)
            }
            return true
        } else if (function == "setwatchobservedports") {
            val parameters = applicationData.parameters as Array<Any?>
            val watchId = parameters[0] as Int
            val observedPorts = parameters[1] as Array<Int>
            val watch = computer.MyWatchHandler.watches[watchId] as Watch?
            if (watch != null) {
                watch.setObservedPorts(observedPorts)
            }
            queueFetchWatches(computer)
            return true
        }

        return false
    }

    private fun queueFetchWatches(computer: Computer) {
        computer.MyComputerHandler.addData(ApplicationData("fetchwatches", null, 0, computer.ip), computer.ip)
    }
}
