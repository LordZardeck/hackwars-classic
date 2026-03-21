package game

import assignments.PacketPort
import com.hackwars.game.program.*
import com.hackwars.rpc.*
import java.util.Map

/**
 * Legacy run-loop handler for port/application/firewall commands that still
 * live in [Computer.processQueuedItem].
 */
class LegacyPortApplicationCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        val function = applicationData.command.wireName()

        if (com.hackwars.rpc.GameCommandWires.DELETEFIREWALL == function) {
            handleDeleteFirewall(computer, applicationData)
            return true
        }
        if (com.hackwars.rpc.GameCommandWires.INSTALLFIREWALL == function) {
            handleInstallFirewall(computer, applicationData, resolvedPort)
            return true
        }
        if (com.hackwars.rpc.GameCommandWires.REPLACEAPPLICATION == function) {
            handleReplaceApplication(computer, applicationData, resolvedPort)
            return true
        }
        if (com.hackwars.rpc.GameCommandWires.INSTALLAPPLICATION == function) {
            handleInstallApplication(computer, applicationData, resolvedPort)
            return true
        }
        if (com.hackwars.rpc.GameCommandWires.FETCHPORTS == function) {
            handleFetchPorts(computer)
            return true
        }
        if (com.hackwars.rpc.GameCommandWires.REQUESTSECONDARYDIRECTORY == function) {
            // This still belongs to FTP port execution after pre-dispatch FTP normalization.
            return false
        }

        return false
    }

    private fun handleDeleteFirewall(computer: Computer, applicationData: ApplicationData) {
        val maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus()
        if (computer.cPULoad <= maxCPU) {
            val targetPort = applicationData.payloadAs<DeleteFirewall>().portID ?: return

            val portIterator = computer.Ports.entries.iterator()
            while (portIterator.hasNext()) {
                val tempPort = (portIterator.next() as Map.Entry<*, *>).value as Port
                if (tempPort.getNumber() == targetPort) {
                    if (computer.MyFileSystem.getSpaceLeft() > 0) {
                        var installedAlready = tempPort.getFireWall()!!.getHackerFile()
                        installedAlready = computer.checkRename(installedAlready!!, "")
                        installedAlready!!.setLocation("")
                        if (installedAlready.getQuantity() == 0) {
                            installedAlready.setQuantity(1)
                        }
                        computer.MyFileSystem.addFile(installedAlready, false)
                        tempPort.setFireWall(NewFireWall.createNoneFirewall())
                        computer.addMessage(MessageHandler.REMOVE_FIREWALL_SUCCESS, arrayOf(installedAlready.getName()))
                    } else {
                        computer.addMessage(MessageHandler.REMOVE_FIREWALL_FAIL_HD_FULL)
                    }
                    break
                }
            }

            refreshPorts(computer)
        }
    }

    private fun handleInstallFirewall(computer: Computer, applicationData: ApplicationData, resolvedPort: Int) {
        val payload = applicationData.payloadAs<InstallFirewall>()
        val path = payload.path ?: return
        val name = payload.name ?: return
        val hackerFile = computer.MyFileSystem.getFile(path, name)

        if (hackerFile != null) {
            val equipLevel = Integer.parseInt(hackerFile.getContent()["equip_level"] as String)
            if (computer.getLevel(computer.Stats["FireWall"] as Float) >= equipLevel) {
                val cpuCheck = computer.cPULoad + hackerFile.getCPUCost()
                val maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus()
                if (cpuCheck <= maxCPU) {
                    val port = findPort(computer, resolvedPort)
                    if (port != null) {
                        hackerFile.setQuantity(hackerFile.getQuantity() - 1)
                        if (hackerFile.getQuantity() <= 0) {
                            computer.MyFileSystem.deleteFile(path, name)
                        }

                        var installedAlready = port.getFireWall()!!.getHackerFile()
                        if (installedAlready!!.getName() != "None") {
                            installedAlready = computer.checkRename(installedAlready, "")
                            installedAlready!!.setLocation("")
                            if (installedAlready.getQuantity() == 0) {
                                installedAlready.setQuantity(1)
                            }
                            computer.MyFileSystem.addFile(installedAlready, false)
                            port.setFireWall(hackerFile)
                            computer.addMessage(MessageHandler.FIREWALL_REPLACED, arrayOf(installedAlready.getName()))
                        } else {
                            port.setFireWall(hackerFile)
                        }
                    }
                } else {
                    computer.addMessage(MessageHandler.CPU_TOO_HIGH)
                }
            } else {
                computer.addMessage(MessageHandler.INSTALL_FIREWALL_FAIL_LEVEL, arrayOf(equipLevel))
            }
        }
        refreshPorts(computer)
    }

    private fun handleReplaceApplication(computer: Computer, applicationData: ApplicationData, resolvedPort: Int) {
        val payload = applicationData.payloadAs<ReplaceApplication>()
        val path = payload.path ?: return
        val name = payload.name ?: return
        val hackerFile = computer.MyFileSystem.getFile(path, name)

        if (hackerFile != null) {
            var port: Port? = findPort(computer, resolvedPort) ?: return
            val currentPort = port ?: return

            val cpuCheck = computer.cPULoad + hackerFile.getCPUCost() - currentPort.getBaseCPUCost()
            val maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus()
            if (cpuCheck <= maxCPU) {
                var allow = true
                var message: Array<Any?>? = null

                if (currentPort.getAccessing() != "") {
                    allow = false
                    message = MessageHandler.REPLACE_APPLICATION_UNDER_ATTACK
                }
                if (currentPort.getAttacking()) {
                    allow = false
                    message = MessageHandler.REPLACE_APPLICATION_ATTACKING
                }
                if (currentPort.getOverHeated()) {
                    allow = false
                    message = MessageHandler.REPLACE_APPLICATION_OVERHEATED
                }

                if (!allow) {
                    computer.addMessage(message)
                    port = null
                }

                if (port != null) {
                    hackerFile.setQuantity(hackerFile.getQuantity() - 1)
                    if (hackerFile.getQuantity() <= 0) {
                        computer.MyFileSystem.deleteFile(path, name)
                    }

                    val portType = hackerFile.getPortType()
                    if (portType >= 0) {
                        port.setType(portType)
                        port.setMaliciousTarget(computer.ip)
                        val script = hackerFile.getContent() as HashMap<*, *>
                        var newProgram: Program? = null
                        port.setCPUCost(hackerFile.getCPUCost())

                        if (portType == Port.ATTACK) {
                            newProgram = AttackProgram(
                                computer,
                                computer.MyComputerHandler,
                                port,
                                computer.Choices,
                                computer.MyMakeBounty
                            )
                        } else if (portType == Port.SHIPPING) {
                            newProgram = ShippingProgram(computer, computer.MyComputerHandler, port)
                        } else if (portType == Port.BANKING) {
                            newProgram = Banking(computer, computer.MyComputerHandler, port)
                        } else if (portType == Port.FTP) {
                            newProgram = FTPProgram(computer, computer.MyComputerHandler, computer.MyFileSystem, port)
                        } else if (portType == Port.HTTP) {
                            computer.adRevenueTarget = computer.ip
                            computer.storeRevenueTarget = computer.ip
                            newProgram = HTTPProgram(computer, computer.MyComputerHandler)
                            computer.dailyPayReduction = 1.0f
                        }

                        newProgram!!.installScript(script)
                        port.setProgram(newProgram)
                        computer.addMessage(MessageHandler.REPLACE_APPLICATION_SUCCESS)
                    }
                }
            } else {
                computer.addMessage(MessageHandler.CPU_TOO_HIGH)
            }
        }
        refreshPorts(computer)
    }

    private fun handleInstallApplication(computer: Computer, applicationData: ApplicationData, resolvedPort: Int) {
        if (resolvedPort < Computer.MEMORY_CHART[computer.memorytype].toInt()) {
            val payload = applicationData.payloadAs<InstallApplication>()
            val path = payload.path ?: return
            val name = payload.name ?: return
            val hackerFile = computer.MyFileSystem.getFile(path, name)

            if (hackerFile != null) {
                val cpuCheck = computer.cPULoad + hackerFile.getCPUCost()
                val maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus()

                if (cpuCheck <= maxCPU) {
                    hackerFile.setQuantity(hackerFile.getQuantity() - 1)
                    if (hackerFile.getQuantity() <= 0) {
                        computer.MyFileSystem.deleteFile(path, name)
                    }

                    val portType = hackerFile.getPortType()
                    if (portType >= 0) {
                        val port = Port(computer, computer.MyComputerHandler)
                        port.setNumber(resolvedPort)
                        port.setType(portType)
                        port.setHealth(100.0f)
                        port.setCPUCost(hackerFile.getCPUCost())
                        port.setNote("")
                        val firewall = NewFireWall(computer.MyComputerHandler)
                        firewall.loadHackerFile(NewFireWall.createNoneFirewall())
                        firewall.setParentPort(port)
                        port.setFireWall(firewall)
                        port.setDummy(false)
                        port.setOn(true)
                        port.setMaliciousTarget(computer.ip)
                        val script = hackerFile.getContent() as HashMap<*, *>
                        var newProgram: Program? = null

                        if (portType == Port.ATTACK) {
                            if (computer.defaultAttack == 0) {
                                computer.defaultAttack = resolvedPort
                            }
                            newProgram = AttackProgram(
                                computer,
                                computer.MyComputerHandler,
                                port,
                                computer.Choices,
                                computer.MyMakeBounty
                            )
                        } else if (portType == Port.SHIPPING) {
                            if (computer.defaultShipping == 0) {
                                computer.defaultShipping = resolvedPort
                            }
                            newProgram = ShippingProgram(computer, computer.MyComputerHandler, port)
                        } else if (portType == Port.BANKING) {
                            if (computer.defaultBank == 0) {
                                computer.defaultBank = resolvedPort
                            }
                            newProgram = Banking(computer, computer.MyComputerHandler, port)
                        } else if (portType == Port.FTP) {
                            if (computer.defaultFTP == 0) {
                                computer.defaultFTP = resolvedPort
                            }
                            newProgram = FTPProgram(computer, computer.MyComputerHandler, computer.MyFileSystem, port)
                        } else if (portType == Port.HTTP) {
                            if (computer.defaultHTTP == 0) {
                                computer.defaultHTTP = resolvedPort
                            }
                            computer.adRevenueTarget = computer.ip
                            computer.storeRevenueTarget = computer.ip
                            newProgram = HTTPProgram(computer, computer.MyComputerHandler)
                        }

                        if (newProgram != null) {
                            newProgram.installScript(script)
                            port.setProgram(newProgram)
                        }
                        computer.Ports[resolvedPort] = port
                    }
                } else {
                    computer.addMessage(MessageHandler.CPU_TOO_HIGH)
                }
            } else {
                computer.addMessage(MessageHandler.APPLICATION_NOT_FOUND)
            }

            refreshPorts(computer)
        } else {
            computer.systemChange = true
            computer.addMessage(MessageHandler.MAX_PROGRAMS_REACHED)
        }
    }

    private fun handleFetchPorts(computer: Computer) {
        val packetPorts = arrayOfNulls<PacketPort>(computer.Ports.size)
        val portIterator = computer.Ports.entries.iterator()
        var index = 0
        while (portIterator.hasNext()) {
            val tempPort = (portIterator.next() as Map.Entry<*, *>).value as Port
            packetPorts[index] = tempPort.getPacketPort()
            index++
        }
        computer.PA.setPacketPorts(packetPorts.requireNoNulls())
        computer.systemChange = true
    }

    private fun findPort(computer: Computer, portNumber: Int): Port? {
        val portIterator = computer.Ports.entries.iterator()
        while (portIterator.hasNext()) {
            val tempPort = (portIterator.next() as Map.Entry<*, *>).value as Port
            if (tempPort.getNumber() == portNumber) {
                return tempPort
            }
        }
        return null
    }

    private fun refreshPorts(computer: Computer) {
        computer.MyComputerHandler.addData(ApplicationData(FetchPorts(computer.ip), 0, computer.ip), computer.ip)
    }
}
