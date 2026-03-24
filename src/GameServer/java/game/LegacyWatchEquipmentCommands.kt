package game

import assignments.PacketWatch
import com.hackwars.game.program.Program
import com.hackwars.game.program.WatchProgram
import com.hackwars.rpc.*
import game.payload.InstallEquipmentPayload
import game.payload.RepairEquipmentPayload
import game.payload.RequestEquipmentPayload

/**
 * Legacy watch/equipment command extraction from [Computer.processQueuedItem].
 */
class LegacyWatchEquipmentCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        val function = applicationData.command.wireName()

        if (function == com.hackwars.rpc.GameCommandWires.CHANGEWATCHTYPE) {
            val payload = applicationData.payloadAs<ChangeWatchType>()

            val targetWatch = payload.watchID ?: return true
            val newType = payload.portID ?: return true

            if (targetWatch < computer.MyWatchHandler.watches.size) {
                val myWatch = computer.MyWatchHandler.getWatch(targetWatch) as Watch
                myWatch.type = newType
                queueFetchWatches(computer)
            }
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.INSTALLWATCH) {
            val payload = applicationData.payloadAs<InstallWatch>()

            val hackerFile = computer.fileSystem.getFile(payload.path, payload.name)

            if (computer.MyWatchHandler.watches.size < 21) {
                if (hackerFile != null && hackerFile.type == HackerFile.WATCH_COMPILED) {
                    val cpuCheck = computer.cPULoad + hackerFile.cpuCost
                    val maxCpu = computer.maximumCPULoad
                    if (cpuCheck <= maxCpu) {
                        hackerFile.quantity = hackerFile.quantity - 1
                        if (hackerFile.quantity <= 0) {
                            computer.fileSystem.deleteFile(payload.path, payload.name)
                        }

                        val watch = Watch(computer)
                        watch.type = payload.type
                        watch.searchFireWall = 0
                        watch.actualCpuCost = hackerFile.cpuCost
                        watch.note = payload.name
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
        } else if (function == com.hackwars.rpc.GameCommandWires.REQUESTEQUIPMENT) {
            val payload = applicationData.payloadAs<RequestEquipmentPayload>()

            var equipment = computer.MyFileSystem.getEquipment("")
            var temp = arrayOfNulls<Any?>(equipment.size + 1)
            temp[0] = payload.windowHandle
            for (i in equipment.indices) {
                if (equipment[i] != null) {
                    computer.equipmentSheet.describeCard(equipment[i] as HackerFile)
                }
                temp[i + 1] = equipment[i]
            }
            equipment = temp
            computer.PA.directory = equipment

            equipment = computer.equipmentSheet.getEquipment() as Array<Any?>
            temp = arrayOfNulls(equipment.size + 1)
            temp[0] = payload.windowHandle
            for (i in equipment.indices) {
                if (equipment[i] != null) {
                    computer.equipmentSheet.describeCard(equipment[i] as HackerFile)
                }
                temp[i + 1] = equipment[i]
            }
            equipment = temp

            computer.PA.secondaryDirectory = equipment
            computer.systemChange = true
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.INSTALLEQUIPMENT) {
            val payload = applicationData.payloadAs<InstallEquipmentPayload>()

            computer.equipmentSheet.equip(payload.position, payload.name)

            var equipment = computer.MyFileSystem.getEquipment("")
            var temp = arrayOfNulls<Any?>(equipment.size + 1)
            temp[0] = payload.windowHandle
            for (i in equipment.indices) {
                temp[i + 1] = equipment[i]
            }
            equipment = temp
            computer.PA.directory = equipment

            equipment = computer.equipmentSheet.getEquipment() as Array<Any?>
            temp = arrayOfNulls(equipment.size + 1)
            temp[0] = payload.windowHandle
            for (i in equipment.indices) {
                temp[i + 1] = equipment[i]
            }
            equipment = temp

            computer.PA.secondaryDirectory = equipment
            computer.systemChange = true
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.REPAIREQUIPMENT) {
            val payload = applicationData.payloadAs<RepairEquipmentPayload>()

            if (payload.position != -1) {
                computer.equipmentSheet.repair(payload.position)
            } else {
                val equipment = computer.MyFileSystem.getFile("", payload.name)
                if (equipment != null) {
                    computer.equipmentSheet.repair(equipment)
                }
            }

            computer.MyComputerHandler.addData(
                ApplicationData(RequestEquipmentPayload(13), 0, computer.ip),
                computer.ip
            )
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.FETCHWATCHES) {
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
        } else if (function == com.hackwars.rpc.GameCommandWires.SETWATCHQUANTITY) {
            val payload = applicationData.payloadAs<SetWatchQuantity>()

            val watchId = payload.watchID ?: return true
            val quantity = payload.quantity ?: return true
            val watch = computer.MyWatchHandler.watches[watchId] as Watch?
            watch?.quantity = quantity
            queueFetchWatches(computer)
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.SETWATCHONOFF) {
            val payload = applicationData.payloadAs<SetWatchOnOff>()

            val watchId = payload.watchID ?: return true
            val state = payload.state ?: return true
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
        } else if (function == com.hackwars.rpc.GameCommandWires.SETWATCHSEARCHFIREWALL) {
            val payload = applicationData.payloadAs<SetWatchSearchFirewall>()

            val watchId = payload.watchID ?: return true
            val searchFireWall = payload.searchFireWall ?: return true
            val watch = computer.MyWatchHandler.watches[watchId] as Watch?
            if (watch != null) {
                watch.searchFireWall = searchFireWall
            }
            queueFetchWatches(computer)
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.SETWATCHNOTE) {
            val payload = applicationData.payloadAs<SetWatchNote>()

            val watchId = payload.watchID ?: return true
            val note = payload.note ?: return true
            val watch = computer.MyWatchHandler.watches[watchId] as Watch?
            if (watch != null) {
                watch.note = note
            }
            queueFetchWatches(computer)
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.DELETEWATCH) {
            val maxCpu = computer.maximumCPULoad
            if (computer.cPULoad <= maxCpu) {
                val payload = applicationData.payloadAs<DeleteWatch>()

                val watchId = payload.watchID ?: return true
                computer.MyWatchHandler.removeWatch(watchId)
                queueFetchWatches(computer)
            }
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.SETWATCHOBSERVEDPORTS) {
            val payload = applicationData.payloadAs<SetWatchObservedPorts>()

            val watchId = payload.watchID ?: return true
            val observedPorts = payload.observedPorts ?: return true
            val watch = computer.MyWatchHandler.watches[watchId] as Watch?
            if (watch != null) {
                watch.setObservedPorts(observedPorts.requireNoNulls())
            }
            queueFetchWatches(computer)
            return true
        }

        return false
    }

    private fun queueFetchWatches(computer: Computer) {
        computer.MyComputerHandler.addData(ApplicationData(FetchWatches(computer.ip), 0, computer.ip), computer.ip)
    }
}
