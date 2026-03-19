package game

import com.hackwars.game.program.AttackProgram
import com.hackwars.game.program.HTTPProgram
import com.hackwars.game.program.Program
import com.hackwars.game.program.ShippingProgram
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.HashMap

class LegacyEconomyWebSocialCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        val function = applicationData.getFunction()

        return when (function) {
            "setpreferences" -> {
                @Suppress("UNCHECKED_CAST")
                val preferences = (applicationData.getParameters() as Array<Any?>)[1] as HashMap<Any?, Any?>
                computer.preferences = preferences
                true
            }

            "message" -> {
                val messageObject = applicationData.getParameters()
                when (messageObject) {
                    is String -> computer.addMessage(messageObject)
                    is Array<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val messageArray = messageObject as Array<Any?>
                        val message = messageArray[0] as Array<Any?>
                        if (messageArray[1] is Array<*>) {
                            @Suppress("UNCHECKED_CAST")
                            val parameters = messageArray[1] as Array<Any?>
                            if (messageArray.size > 2) {
                                @Suppress("UNCHECKED_CAST")
                                val portInfo = messageArray[2] as Array<Any?>
                                computer.addMessage(message, parameters, portInfo)
                            } else {
                                computer.addMessage(message, parameters)
                            }
                        } else {
                            computer.addMessage(messageArray)
                        }
                    }
                }
                computer.systemChange = true
                true
            }

            "sendemail" -> {
                val message = applicationData.getParameters() as String
                if (computer.checkBank()) {
                    if (computer.pettyCash >= 100.0f) {
                        try {
                            val params = arrayOf<Any?>(computer.ip, message)
                            computer.sessionService.executeRemote("http://www.hackwars.net/xmlrpc/mail.php", "sendEmail", params)
                        } catch (_: Exception) {
                        }
                        computer.getComputerHandler().addData(ApplicationData("pettycash", -100.0f, 0, computer.ip), computer.ip)
                    }
                }
                true
            }

            "sendfacebook" -> {
                val params = applicationData.getParameters() as Array<*>
                val message = params[0] as String
                val targetIP = params[1] as String
                try {
                    computer.sessionService.executeRemote(
                        "http://www.hackwars.net/xmlrpc/facebook.php",
                        "sendFacebook",
                        arrayOf<Any?>(computer.ip, targetIP, message)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                true
            }

            "facebookupdate" -> {
                applicationData.getParameters() as String
                try {
                    computer.sessionService.executeRemote(
                        "http://www.hackwars.net/xmlrpc/facebook.php",
                        "updateFacebook",
                        arrayOf<Any?>(
                            computer.ip,
                            computer.pettyCash.toDouble(),
                            computer.bankMoney.toDouble(),
                            computer.defaultBank
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                true
            }

            "dailypayset" -> {
                val bountyip = applicationData.getParameters() as String
                computer.MyMakeBounty!!.checkBounty(computer, null, MakeBounty.CHANGE, applicationData.getSourceIP(), false, bountyip)
                true
            }

            "pettycash" -> {
                val nf = DecimalFormat("#.00")
                var value = 0.0f
                var returnValue = 0.0f
                var sendMessage = true
                val parameters = applicationData.getParameters()
                when (parameters) {
                    is Float -> value = parameters
                    is Array<*> -> {
                        value = parameters[0] as Float
                        val secondEntry = parameters[1]
                        when (secondEntry) {
                            is Float -> returnValue = secondEntry
                            is Boolean -> sendMessage = false
                        }
                    }
                }

                val noobLevel = computer.getNoobSafety()
                if (computer.getTotalLevel() < noobLevel && applicationData.getSourceIP() != computer.ip) {
                    computer.addMessage(MessageHandler.TRANSFER_FAIL_NOOB_LEVEL, arrayOf<Any?>(noobLevel))
                    if (applicationData.getSourceIP() != computer.ip) {
                        computer.getComputerHandler().addData(applicationData, applicationData.getSourceIP())
                    }
                } else if (computer.checkBank()) {
                    computer.setPettyCash(computer.pettyCash + value)
                    CentralLogging.getInstance().addOutput("${computer.ip}\t${applicationData.getSourceIP()}\t1\t$value\n")
                    if (applicationData.getSourceIP() != computer.ip && sendMessage) {
                        val message = ApplicationData(
                            "message",
                            arrayOf<Any?>(MessageHandler.TRANSFER_SENT_SUCCESSFUL, arrayOf<Any?>(nf.format(value))),
                            0,
                            applicationData.getSourceIP()
                        )
                        computer.getComputerHandler().addData(message, applicationData.getSourceIP())
                        computer.addMessage(MessageHandler.TRANSFER_RECEIVED, arrayOf<Any?>(nf.format(value), applicationData.getSourceIP()))
                    }
                    if (computer.pettyCash < 0) {
                        computer.pettyCash = 0.0f
                    }
                } else {
                    computer.addMessage(MessageHandler.TRANSFER_RECEIVE_FAIL_BANK_PORT, arrayOf<Any?>(nf.format(value), applicationData.getSourceIP()))
                    if (applicationData.getSourceIP() != computer.ip) {
                        val pettyCash = ApplicationData("pettycash", returnValue.toFloat(), 0, applicationData.getSourceIP())
                        val message = ApplicationData("message", MessageHandler.TRANSFER_SEND_FAIL_BANK_PORT, 0, applicationData.getSourceIP())
                        computer.getComputerHandler().addData(pettyCash, applicationData.getSourceIP())
                        computer.getComputerHandler().addData(message, applicationData.getSourceIP())
                    }
                }

                if (computer.pettyCash < computer.respawnMoney) {
                    computer.respawn(Port.BANKING)
                }

                computer.systemChange = true
                true
            }

            "commodity" -> {
                if (computer.checkShipping()) {
                    val parameters = applicationData.getParameters() as Array<*>
                    val commodity = parameters[0] as Int
                    val value = parameters[1] as Float
                    val redirectPort = parameters[2] as Int
                    val port = computer.Ports[redirectPort] as Port
                    val windowHandle = getWindowHandle(port)
                    computer.setCommodityAmount(commodity, computer.getCommodity(commodity) + value)
                    val targetIP = parameters[3] as String
                    computer.addMessage(
                        MessageHandler.RECEIVED_COMMODITY,
                        arrayOf<Any?>(value.toInt(), Computer.commodityString[commodity]),
                        arrayOf<Any?>(windowHandle, computer.ip)
                    )
                    computer.addMessage(
                        MessageHandler.RECEIVED_COMMODITY_GAME,
                        arrayOf<Any?>(value.toInt(), Computer.commodityString[commodity], targetIP)
                    )
                } else {
                    computer.addMessage(MessageHandler.RECEIVED_COMMODITY_FAIL)
                    if (applicationData.getSourceIP() != computer.ip) {
                        computer.getComputerHandler().addData(applicationData, applicationData.getSourceIP())
                    }
                }

                computer.systemChange = true
                true
            }

            "bank" -> {
                val value = applicationData.getParameters() as Float
                if (computer.checkBank()) {
                    computer.bankMoney += value
                    if (value > 0) {
                        CentralLogging.getInstance().addOutput("${computer.ip}\t${applicationData.getSourceIP()}\t0\t$value\n")
                    }
                } else if (value > 0.0f) {
                    computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND)
                }
                computer.systemChange = true
                true
            }

            "requestpage" -> {
                computer.PA.setBody(computer.body)
                computer.PA.setTitle(computer.title)
                computer.systemChange = true
                true
            }

            "requestpurchase" -> {
                val params = applicationData.getParameters() as Array<*>
                val file = params[0] as String
                var quantity = params[1] as Int

                if (quantity > 0) {
                    val hackerFile = computer.MyFileSystem.getFile("Store/", file)
                    if (hackerFile != null) {
                        if (hackerFile.getQuantity() < quantity && hackerFile.getQuantity() != -1) {
                            quantity = hackerFile.getQuantity()
                        }

                        val purchasedFile = hackerFile.clone()
                        purchasedFile.setQuantity(quantity)

                        if (hackerFile.getQuantity() != -1) {
                            hackerFile.setQuantity(hackerFile.getQuantity() - quantity)
                            if (hackerFile.getQuantity() <= 0) {
                                computer.MyFileSystem.deleteFile("Store/", file)
                            }
                        }

                        val payload = arrayOf<Any?>(purchasedFile, computer.storeRevenueTarget, computer.type)
                        computer.getComputerHandler().addData(ApplicationData("continuepurchase", payload, 0, computer.ip), applicationData.getSourceIP())
                    } else {
                        computer.getComputerHandler().addData(ApplicationData("message", MessageHandler.PURCHASE_FAIL_FILE_NOT_FOUND, 0, computer.ip), applicationData.getSourceIP())
                    }
                }
                computer.systemChange = true
                true
            }

            "continuepurchase" -> {
                val params = applicationData.getParameters() as Array<*>
                val hackerFile = params[0] as HackerFile
                val payTarget = params[1] as String
                val sellerType = params[2] as Int
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
                    val payload = arrayOf<Any?>("Store/", hackerFile)
                    computer.getComputerHandler().addData(ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP())
                } else if (!computer.checkBank() && price > 0) {
                    computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND)
                    val payload = arrayOf<Any?>("Store/", hackerFile)
                    computer.getComputerHandler().addData(ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP())
                } else if (computer.pettyCash < price) {
                    computer.addMessage(MessageHandler.PURCHASE_FAIL_NOT_ENOUGH_MONEY)
                    val payload = arrayOf<Any?>("Store/", hackerFile)
                    computer.getComputerHandler().addData(ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP())
                } else if (computer.MyFileSystem.getSpaceLeft() < 1 && !((hackerFile.getType() == HackerFile.CPU || hackerFile.getType() == HackerFile.HD || hackerFile.getType() == HackerFile.MEMORY) || existingFile != null)) {
                    computer.addMessage(MessageHandler.PURCHASE_FAIL_HD_FULL)
                    val payload = arrayOf<Any?>("Store/", hackerFile)
                    computer.getComputerHandler().addData(ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP())
                } else if (totalLevel < level && sellerType == 1) {
                    val payload = arrayOf<Any?>("Store/", hackerFile)
                    computer.getComputerHandler().addData(ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP())
                    computer.addMessage(MessageHandler.PURCHASE_FAIL_NOT_HIGH_ENOUGH_LEVEL)
                } else {
                    var buyFail = false
                    val payload = arrayOf<Any?>("", hackerFile)
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
                        computer.getComputerHandler().addData(ApplicationData("savefile", payload, 0, computer.ip), computer.ip)
                        val format = NumberFormat.getCurrencyInstance()
                        computer.addMessage(MessageHandler.PURCHASE_SUCCESS, arrayOf<Any?>(quantity, hackerFile.getName(), format.format(price)))
                    }

                    if (price > 0 && !buyFail) {
                        computer.getComputerHandler().addData(ApplicationData("pettycash", arrayOf<Any?>(-price, false), 0, computer.ip), computer.ip)
                        computer.getComputerHandler().addData(ApplicationData("pettycash", arrayOf<Any?>(price, false), 0, computer.ip), payTarget)
                    }
                    computer.getComputerHandler().addData(ApplicationData("requestwebpage", null, 0, computer.ip), applicationData.getSourceIP())
                    computer.getComputerHandler().addData(ApplicationData("requestequipment", 13, 0, computer.ip), computer.ip)
                }
                computer.systemChange = true
                true
            }

            "requestwebpage" -> {
                val port = computer.Ports[computer.defaultHTTP] as Port?
                if (port != null && port.getType() == Port.HTTP) {
                    if (port.getProgram() != null && port.getOn()) {
                        var attack: Any? = null
                        val parameters = applicationData.getParameters()
                        if (parameters != null) {
                            attack = (parameters as HashMap<*, *>)["Attack"]
                        }
                    if (attack != null || computer.type != Computer.NPC) {
                            val program = port.getProgram()
                            if (program != null) {
                                program.execute(applicationData)
                            }
                        } else {
                            computer.getComputerHandler().addData(
                                ApplicationData("questinformation", arrayOf<Any?>(applicationData.getParameters(), computer.InvolvedQuests), 0, computer.ip),
                                applicationData.getSourceIP()
                            )
                        }
                    } else {
                        val httpProgram = HTTPProgram(computer, computer.getComputerHandler())
                        httpProgram.serveWebPage(applicationData, (applicationData.getParameters() as HashMap<*, *>)["packetid"] as Int)
                    }
                } else {
                    val httpProgram = HTTPProgram(computer, computer.getComputerHandler())
                    httpProgram.serveWebPage(applicationData, (applicationData.getParameters() as HashMap<*, *>)["packetid"] as Int)
                }
                computer.systemChange = true
                true
            }

            "webpage" -> {
                val params = applicationData.getParameters() as Array<*>
                val pageTitle = params[0] as String
                val pageBody = params[1] as String
                val files = params[2] as Array<Any?>?
                val temp = if (files != null) arrayOfNulls<Any?>(files.size + 1) else arrayOfNulls<Any?>(1)
                temp[0] = params[3] as Int
                if (files != null) {
                    for (i in files.indices) {
                        temp[i + 1] = files[i]
                    }
                }
                computer.PA.setBody(pageBody)
                computer.PA.setTitle(pageTitle)
                @Suppress("UNCHECKED_CAST")
                computer.PA.setDirectory(temp as Array<Any>)
                computer.systemChange = true
                true
            }

            "savepage" -> {
                if ((applicationData.getParameters() as Array<*>)[1].toString().length > 30000) {
                    computer.addMessage(MessageHandler.WEBSITE_SAVE_FAIL_TOO_BIG)
                } else {
                    val params = applicationData.getParameters() as Array<*>
                    computer.pageTitle = params[0] as String
                    computer.pageBody = params[1] as String
                    computer.pageChanged = true
                }
                computer.systemChange = true
                true
            }

            "submit" -> {
                val port = computer.Ports[computer.defaultHTTP] as Port?
                if (port != null && port.getType() == Port.HTTP) {
                    val program = port.getProgram()
                    if (program != null && port.getOn()) {
                        program.execute(applicationData)
                    }
                }
                computer.systemChange = true
                true
            }

            "exit" -> {
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

            "vote" -> {
                val noobLevel = computer.getNoobSafety()
                if (computer.getTotalLevel() < noobLevel) {
                    computer.addMessage(MessageHandler.VOTE_FAIL_NOOB_LEVEL, arrayOf<Any?>(noobLevel))
                }
                if (applicationData.getSourceIP() == computer.ip) {
                    computer.addMessage(MessageHandler.VOTE_FAIL_OWN_SITE)
                } else if (computer.myVotes > 0) {
                    computer.MyMakeBounty!!.checkBounty(computer, null, MakeBounty.VOTE, applicationData.getSourceIP(), false, "")
                    computer.myVotes -= 1
                    computer.getComputerHandler().addData(ApplicationData("httpxp", 500.7337f, 0, computer.ip), applicationData.getSourceIP())
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
