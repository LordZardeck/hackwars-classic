package game

import com.hackwars.game.program.AttackProgram
import com.hackwars.game.program.HTTPProgram
import com.hackwars.game.program.ShippingProgram
import com.hackwars.rpc.SaveFile
import game.payload.*
import java.text.DecimalFormat
import java.text.NumberFormat

class LegacyEconomyWebSocialCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        val function = applicationData.command.wireName()

        return when (function) {
            com.hackwars.rpc.GameCommandWires.SETPREFERENCES -> {
                val preferences = when (val payload = applicationData.payload) {
                    is SetPreferencesPayload -> payload.preferences
                    else -> return false
                }
                computer.preferences = preferences
                true
            }

            com.hackwars.rpc.GameCommandWires.MESSAGE -> {
                when (val payload = applicationData.payload) {
                    is MessageTextPayload -> computer.addMessage(payload.text)
                    is StructuredMessagePayload -> {
                        if (payload.portInfo != null) {
                            computer.addMessage(payload.message, payload.parameters, payload.portInfo)
                        } else if (payload.parameters != null) {
                            computer.addMessage(payload.message, payload.parameters)
                        } else {
                            computer.addMessage(payload.message)
                        }
                    }

                    else -> return false
                }
                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.SENDEMAIL -> {
                val message = when (val payload = applicationData.payload) {
                    is SendEmailPayload -> payload.message
                    else -> return false
                }
//                if (computer.checkBank() && computer.pettyCash >= 100.0f) {
//                    try {
//                        val params = arrayOf<Any?>(computer.ip, message)
//                        // TODO: Removed legacy remote mail endpoint: http://www.hackwars.net/xmlrpc/mail.php
//                    } catch (_: Exception) {
//                    }
//                    computer.getComputerHandler().addData(
//                        ApplicationData(PettyCashDeltaPayload(-100.0f), 0, computer.ip),
//                        computer.ip
//                    )
//                }
                true
            }

            com.hackwars.rpc.GameCommandWires.SENDFACEBOOK -> {
                // TODO: Removed legacy remote social endpoint: http://www.hackwars.net/xmlrpc/facebook.php
                true
            }

            com.hackwars.rpc.GameCommandWires.FACEBOOKUPDATE -> {
                // TODO: Removed legacy remote social endpoint: http://www.hackwars.net/xmlrpc/facebook.php
                true
            }

            com.hackwars.rpc.GameCommandWires.DAILYPAYSET -> {
                val bountyIp = when (val payload = applicationData.payload) {
                    is DailyPaySetPayload -> payload.bountyIp
                    else -> return false
                }
                computer.MyMakeBounty!!.checkBounty(
                    computer,
                    null,
                    MakeBounty.CHANGE,
                    applicationData.sourceIP,
                    false,
                    bountyIp
                )
                true
            }

            com.hackwars.rpc.GameCommandWires.PETTYCASH -> {
                val payload = when (val typed = applicationData.payload) {
                    is PettyCashTransferPayload -> typed
                    is PettyCashDeltaPayload -> PettyCashTransferPayload(typed.amount)
                    else -> return false
                }

                val nf = DecimalFormat("#.00")
                val noobLevel = computer.getNoobSafety()
                if (computer.getTotalLevel() < noobLevel && applicationData.sourceIP != computer.ip) {
                    computer.addMessage(MessageHandler.TRANSFER_FAIL_NOOB_LEVEL, arrayOf<Any?>(noobLevel))
                    if (applicationData.sourceIP != computer.ip) {
                        computer.getComputerHandler().addData(applicationData, applicationData.sourceIP)
                    }
                } else if (computer.checkBank()) {
                    computer.setPettyCash(computer.pettyCash + payload.amount)
                    CentralLogging.getInstance()
                        .addOutput("${computer.ip}\t${applicationData.sourceIP}\t1\t${payload.amount}\n")
                    if (applicationData.sourceIP != computer.ip && payload.sendMessage) {
                        val message = ApplicationData(
                            StructuredMessagePayload(
                                MessageHandler.TRANSFER_SENT_SUCCESSFUL,
                                arrayOf<Any?>(nf.format(payload.amount))
                            ),
                            0,
                            applicationData.sourceIP
                        )
                        computer.getComputerHandler().addData(message, applicationData.sourceIP)
                        computer.addMessage(
                            MessageHandler.TRANSFER_RECEIVED,
                            arrayOf<Any?>(nf.format(payload.amount), applicationData.sourceIP)
                        )
                    }
                    if (computer.pettyCash < 0) {
                        computer.pettyCash = 0.0f
                    }
                } else {
                    computer.addMessage(
                        MessageHandler.TRANSFER_RECEIVE_FAIL_BANK_PORT,
                        arrayOf<Any?>(nf.format(payload.amount), applicationData.sourceIP)
                    )
                    if (applicationData.sourceIP != computer.ip) {
                        val pettyCash =
                            ApplicationData(PettyCashDeltaPayload(payload.returnAmount), 0, applicationData.sourceIP)
                        val message = ApplicationData(
                            StructuredMessagePayload(MessageHandler.TRANSFER_SEND_FAIL_BANK_PORT),
                            0,
                            applicationData.sourceIP
                        )
                        computer.getComputerHandler().addData(pettyCash, applicationData.sourceIP)
                        computer.getComputerHandler().addData(message, applicationData.sourceIP)
                    }
                }

                if (computer.pettyCash < computer.respawnMoney) {
                    computer.respawn(Port.BANKING)
                }

                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.COMMODITY -> {
                if (computer.checkShipping()) {
                    val payload = when (val typed = applicationData.payload) {
                        is game.payload.CommodityPayload -> typed
                        else -> return false
                    }
                    val port = computer.Ports[payload.redirectPort] as Port
                    val windowHandle = getWindowHandle(port)
                    computer.setCommodityAmount(
                        payload.commodity,
                        computer.getCommodity(payload.commodity) + payload.value
                    )
                    computer.addMessage(
                        MessageHandler.RECEIVED_COMMODITY,
                        arrayOf<Any?>(payload.value.toInt(), Computer.commodityString[payload.commodity]),
                        arrayOf<Any?>(windowHandle, computer.ip)
                    )
                    computer.addMessage(
                        MessageHandler.RECEIVED_COMMODITY_GAME,
                        arrayOf<Any?>(
                            payload.value.toInt(),
                            Computer.commodityString[payload.commodity],
                            payload.targetIp
                        )
                    )
                } else {
                    computer.addMessage(MessageHandler.RECEIVED_COMMODITY_FAIL)
                    if (applicationData.sourceIP != computer.ip) {
                        computer.getComputerHandler().addData(applicationData, applicationData.sourceIP)
                    }
                }

                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.BANK -> {
                val value = when (val payload = applicationData.payload) {
                    is game.payload.FloatCommandPayload -> payload.value
                    else -> return false
                }
                if (computer.checkBank()) {
                    computer.bankMoney += value
                    if (value > 0) {
                        CentralLogging.getInstance()
                            .addOutput("${computer.ip}\t${applicationData.sourceIP}\t0\t$value\n")
                    }
                } else if (value > 0.0f) {
                    computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND)
                }
                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.REQUESTPAGE -> {
                computer.PA.setBody(computer.body)
                computer.PA.setTitle(computer.title)
                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.REQUESTPURCHASE -> {
                val payload = when (val typed = applicationData.payload) {
                    is RequestPurchasePayload -> typed
                    else -> return false
                }

                if (payload.quantity > 0) {
                    val hackerFile = computer.MyFileSystem.getFile("Store/", payload.fileName)
                    if (hackerFile != null) {
                        var quantity = payload.quantity
                        if (hackerFile.getQuantity() < quantity && hackerFile.getQuantity() != -1) {
                            quantity = hackerFile.getQuantity()
                        }

                        val purchasedFile = hackerFile.clone()
                        purchasedFile.setQuantity(quantity)

                        if (hackerFile.getQuantity() != -1) {
                            hackerFile.setQuantity(hackerFile.getQuantity() - quantity)
                            if (hackerFile.getQuantity() <= 0) {
                                computer.MyFileSystem.deleteFile("Store/", payload.fileName)
                            }
                        }

                        val continuePurchase =
                            ContinuePurchasePayload(purchasedFile, computer.storeRevenueTarget, computer.type)
                        computer.getComputerHandler()
                            .addData(ApplicationData(continuePurchase, 0, computer.ip), applicationData.sourceIP)
                    } else {
                        computer.getComputerHandler().addData(
                            ApplicationData(
                                StructuredMessagePayload(MessageHandler.PURCHASE_FAIL_FILE_NOT_FOUND),
                                0,
                                computer.ip
                            ),
                            applicationData.sourceIP
                        )
                    }
                }
                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.CONTINUEPURCHASE -> {
                val payload = when (val typed = applicationData.payload) {
                    is ContinuePurchasePayload -> typed
                    else -> return false
                }
                val hackerFile = payload.file
                val payTarget = payload.revenueTarget
                val sellerType = payload.sellerType
                val quantity = hackerFile.getQuantity()
                val existingFile = computer.MyFileSystem.getFile("", hackerFile.getName())

                var level = 0.0f
                if (hackerFile.getType() == HackerFile.CPU) {
                    level = (hackerFile.getContent()["level"] as String).toFloat()
                } else if (hackerFile.getType() == HackerFile.HD) {
                    level = (hackerFile.getContent()["level"] as String).toFloat()
                } else if (hackerFile.getType() == HackerFile.FIREWALL) {
                    level = (hackerFile.getContent()["level"] as String).toFloat()
                } else if (hackerFile.getType() == HackerFile.MEMORY) {
                    level = (hackerFile.getContent()["level"] as String).toFloat()
                }

                var totalLevel = 0.0f
                if (hackerFile.getType() != HackerFile.FIREWALL) {
                    totalLevel += computer.getLevel(computer.Stats["Attack"] as Float)
                    totalLevel += computer.getLevel(computer.Stats["Bank"] as Float)
                    totalLevel += computer.getLevel(computer.Stats["Watch"] as Float)
                    totalLevel += computer.getLevel(computer.Stats["Scanning"] as Float)
                    totalLevel += computer.getLevel(computer.Stats["Webdesign"] as Float)
                    totalLevel += computer.getLevel(computer.Stats["Redirecting"] as Float)
                    totalLevel += computer.getLevel(computer.Stats["Repair"] as Float)
                }
                totalLevel += computer.getLevel(computer.Stats["FireWall"] as Float)

                val mult = (computer.getLevel(computer.Stats["Bank"] as Float) - 50.0f) / 100.0f
                val price = (quantity * hackerFile.getPrice()) - (quantity * hackerFile.getPrice() * mult)

                if (computer.MyFileSystem.getSpaceLeft() <= 0) {
                    computer.addMessage(MessageHandler.PURCHASE_FAIL_HD_FULL)
                    computer.getComputerHandler().addData(
                        ApplicationData(SaveFile(computer.ip, "Store/", hackerFile), 0, computer.ip),
                        applicationData.sourceIP
                    )
                } else if (!computer.checkBank() && price > 0) {
                    computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND)
                    computer.getComputerHandler().addData(
                        ApplicationData(SaveFile(computer.ip, "Store/", hackerFile), 0, computer.ip),
                        applicationData.sourceIP
                    )
                } else if (computer.pettyCash < price) {
                    computer.addMessage(MessageHandler.PURCHASE_FAIL_NOT_ENOUGH_MONEY)
                    computer.getComputerHandler().addData(
                        ApplicationData(SaveFile(computer.ip, "Store/", hackerFile), 0, computer.ip),
                        applicationData.sourceIP
                    )
                } else if (computer.MyFileSystem.getSpaceLeft() < 1 && !((hackerFile.getType() == HackerFile.CPU || hackerFile.getType() == HackerFile.HD || hackerFile.getType() == HackerFile.MEMORY) || existingFile != null)) {
                    computer.addMessage(MessageHandler.PURCHASE_FAIL_HD_FULL)
                    computer.getComputerHandler().addData(
                        ApplicationData(SaveFile(computer.ip, "Store/", hackerFile), 0, computer.ip),
                        applicationData.sourceIP
                    )
                } else if (totalLevel < level && sellerType == 1) {
                    computer.getComputerHandler().addData(
                        ApplicationData(SaveFile(computer.ip, "Store/", hackerFile), 0, computer.ip),
                        applicationData.sourceIP
                    )
                    computer.addMessage(MessageHandler.PURCHASE_FAIL_NOT_HIGH_ENOUGH_LEVEL)
                } else {
                    var buyFail = false
                    if (hackerFile.getType() == HackerFile.CPU) {
                        val type = (hackerFile.getContent()["data"] as String).toInt()
                        if (Computer.CPU_CHART[type] > Computer.CPU_CHART[computer.cputype]) {
                            computer.cputype = type
                            computer.addMessage(MessageHandler.PURCHASE_NEW_CPU, arrayOf<Any?>(hackerFile.getName()))
                        } else {
                            buyFail = true
                            computer.addMessage(MessageHandler.PURCHASE_FAIL_OLDER_CPU)
                        }
                    } else if (hackerFile.getType() == HackerFile.HD) {
                        val type = (hackerFile.getContent()["data"] as String).toInt()
                        if (computer.MyFileSystem.checkType(type)) {
                            computer.MyFileSystem.setHDType(type)
                            computer.addMessage(MessageHandler.PURCHASE_NEW_HD, arrayOf<Any?>(hackerFile.getName()))
                        } else {
                            buyFail = true
                            computer.addMessage(MessageHandler.PURCHASE_FAIL_OLDER_HD)
                        }
                    } else if (hackerFile.getType() == HackerFile.MEMORY) {
                        val type = (hackerFile.getContent()["data"] as String).toInt()
                        if (Computer.MEMORY_CHART[type] > Computer.MEMORY_CHART[computer.memorytype] || Computer.WATCH_CHART[type] > Computer.WATCH_CHART[computer.memorytype]) {
                            computer.memorytype = type
                            computer.addMessage(MessageHandler.PURCHASE_NEW_MEMORY, arrayOf<Any?>(hackerFile.getName()))
                        } else {
                            buyFail = true
                            computer.addMessage(MessageHandler.PURCHASE_FAIL_OLDER_MEMORY)
                        }
                    } else {
                        hackerFile.setLocation("")
                        if (hackerFile.getType() == HackerFile.FIREWALL) {
                            val setLevel = hackerFile.getContent()
                            setLevel["level"] = "0"
                        }
                        computer.getComputerHandler().addData(
                            ApplicationData(SaveFile(computer.ip, "", hackerFile), 0, computer.ip),
                            computer.ip
                        )
                        val format = NumberFormat.getCurrencyInstance()
                        computer.addMessage(
                            MessageHandler.PURCHASE_SUCCESS,
                            arrayOf<Any?>(quantity, hackerFile.getName(), format.format(price))
                        )
                    }

                    if (price > 0 && !buyFail) {
                        computer.getComputerHandler().addData(
                            ApplicationData(PettyCashDeltaPayload(-price), 0, computer.ip),
                            computer.ip
                        )
                        computer.getComputerHandler().addData(
                            ApplicationData(PettyCashDeltaPayload(price), 0, computer.ip),
                            payTarget
                        )
                    }
                    computer.getComputerHandler().addData(
                        ApplicationData(RequestWebPagePayload(null), 0, computer.ip),
                        applicationData.sourceIP
                    )
                    computer.getComputerHandler().addData(
                        ApplicationData(
                            IntCommandPayload(com.hackwars.rpc.GameCommands.REQUESTEQUIPMENT.command, 13),
                            0,
                            computer.ip
                        ),
                        computer.ip
                    )
                }
                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.REQUESTWEBPAGE -> {
                val payload = when (val typed = applicationData.payload) {
                    is RequestWebPagePayload -> typed
                    else -> return false
                }
                val port = computer.Ports[computer.defaultHTTP] as Port?
                if (port != null && port.getType() == Port.HTTP) {
                    if (port.getProgram() != null && port.getOn()) {
                        var attack: Any? = null
                        val parameters = payload.requestParameters
                        if (parameters != null) {
                            attack = parameters["Attack"]
                        }
                        if (attack != null || computer.type != Computer.NPC) {
                            val program = port.getProgram()
                            if (program != null) {
                                program.execute(applicationData)
                            }
                        } else {
                            computer.getComputerHandler().addData(
                                ApplicationData(
                                    QuestInformationPayload(
                                        payload.requestParameters,
                                        computer.InvolvedQuests
                                    ), 0, computer.ip
                                ),
                                applicationData.sourceIP
                            )
                        }
                    } else {
                        val httpProgram = HTTPProgram(computer, computer.getComputerHandler())
                        httpProgram.serveWebPage(applicationData, payload.requestParameters?.get("packetid") as Int?)
                    }
                } else {
                    val httpProgram = HTTPProgram(computer, computer.getComputerHandler())
                    httpProgram.serveWebPage(applicationData, payload.requestParameters?.get("packetid") as Int?)
                }
                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.WEBPAGE -> {
                val payload = when (val typed = applicationData.payload) {
                    is WebPagePayload -> typed
                    else -> return false
                }
                computer.PA.setBody(payload.body)
                computer.PA.setTitle(payload.title)
                val files = payload.files
                val temp = if (files != null) arrayOfNulls<Any?>(files.size + 1) else arrayOfNulls<Any?>(1)
                temp[0] = payload.packetId ?: 0
                if (files != null) {
                    for (i in files.indices) {
                        temp[i + 1] = files[i]
                    }
                }
                @Suppress("UNCHECKED_CAST")
                computer.PA.setDirectory(temp as Array<Any>)
                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.SAVEPAGE -> {
                val payload = when (val typed = applicationData.payload) {
                    is SavePagePayload -> typed
                    else -> return false
                }
                if (payload.body.length > 30000) {
                    computer.addMessage(MessageHandler.WEBSITE_SAVE_FAIL_TOO_BIG)
                } else {
                    computer.pageTitle = payload.title
                    computer.pageBody = payload.body
                    computer.pageChanged = true
                }
                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.SUBMIT -> {
                val payload = when (val typed = applicationData.payload) {
                    is SubmitPayload -> typed
                    else -> return false
                }
                val port = computer.Ports[computer.defaultHTTP] as Port?
                if (port != null && port.getType() == Port.HTTP) {
                    val program = port.getProgram()
                    if (program != null && port.getOn()) {
                        program.execute(
                            ApplicationData(
                                SubmitPayload(payload.submitParameters),
                                applicationData.port,
                                applicationData.sourceIP
                            )
                        )
                    }
                }
                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.EXIT -> {
                val port = computer.Ports[computer.defaultHTTP] as Port?
                if (port != null && port.getType() == Port.HTTP) {
                    val program = port.getProgram()
                    if (program != null) {
                        program.execute(applicationData)
                    }
                }
                computer.systemChange = true
                true
            }

            com.hackwars.rpc.GameCommandWires.VOTE -> {
                val noobLevel = computer.getNoobSafety()
                if (computer.getTotalLevel() < noobLevel) {
                    computer.addMessage(MessageHandler.VOTE_FAIL_NOOB_LEVEL, arrayOf<Any?>(noobLevel))
                }
                if (applicationData.sourceIP == computer.ip) {
                    computer.addMessage(MessageHandler.VOTE_FAIL_OWN_SITE)
                } else if (computer.myVotes > 0) {
                    computer.MyMakeBounty!!.checkBounty(
                        computer,
                        null,
                        MakeBounty.VOTE,
                        applicationData.sourceIP,
                        false,
                        ""
                    )
                    computer.myVotes -= 1
                    computer.getComputerHandler().addData(
                        ApplicationData(
                            game.payload.FloatCommandPayload(
                                com.hackwars.rpc.GameCommands.HTTPXP.command,
                                500.7337f
                            ), 0, computer.ip
                        ), applicationData.sourceIP
                    )
                    computer.addMessage(MessageHandler.VOTE_SUCCESS, arrayOf<Any?>(computer.myVotes))
                } else {
                    computer.addMessage(MessageHandler.VOTE_FAIL_NO_VOTES)
                }
                computer.systemChange = true
                true
            }

            else -> false
        }
    }

    private fun getWindowHandle(port: Port?): Int {
        return if (port == null) {
            0
        } else {
            val program = port.getProgram()
            when (program) {
                is AttackProgram -> program.getWindowHandle()
                is ShippingProgram -> program.getWindowHandle()
                else -> 0
            }
        }
    }

}
