package game

import game.computer.session.ComputerSessionService
import game.payload.DeliveredDirectoryToNpcPayload
import game.payload.DeliveredDirectoryToPlayerPayload
import game.payload.FloatCommandPayload
import game.payload.PettyCashDeltaPayload
import game.payload.RequestDirectoryPayload
import game.payload.SaveFileRequestPayload
import game.payload.SellFilePayload
import game.payload.StolenSaveFilePayload
import hackscript.model.TypeBoolean
import hackscript.model.TypeFloat
import hackscript.model.TypeInteger
import hackscript.model.TypeString
import hackscript.model.Variable
import com.hackwars.rpc.CompileFile
import com.hackwars.rpc.DeleteFile
import com.hackwars.rpc.DeleteMulti
import com.hackwars.rpc.DecompileFile
import com.hackwars.rpc.RequestFile
import com.hackwars.rpc.RequestGame
import com.hackwars.rpc.SaveFile
import com.hackwars.rpc.SellFile
import com.hackwars.rpc.SellFileMulti
import com.hackwars.rpc.SetFileDescription
import com.hackwars.rpc.SetFilePrice
import util.LocalWebConfig
import java.lang.reflect.Field
import java.util.HashMap

/**
 * Extracted legacy filesystem and inventory command branches from Computer.
 */
class LegacyFilesystemInventoryCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        when (val payload = applicationData.payload) {
            is RequestDirectoryPayload -> {
                val path = payload.path
                val directory = computer.MyFileSystem.getDirectory(path)
                val response = arrayOfNulls<Any>(directory.size + 1)
                response[0] = payload.requestId
                for (i in directory.indices) {
                    response[i + 1] = directory[i]
                }
                computer.PA.setDirectory(response)
                computer.systemChange = true
                return true
            }
            is DeliveredDirectoryToPlayerPayload -> {
                computer.PA.setSecondaryDirectory(payload.directory)
                computer.systemChange = true
                return true
            }
            is DeliveredDirectoryToNpcPayload -> {
                computer.PA.setSecondaryDirectory(payload.directory)
                computer.PA.setAllowedDir(!payload.npcOnly || computer.isNPC())
                computer.systemChange = true
                return true
            }
            is RequestFile -> {
                val path = payload.path.orEmpty()
                val name = payload.name.orEmpty()
                var file = computer.MyFileSystem.getFile(path, name)
                if (file != null && (file.type == HackerFile.GAME || file.type == HackerFile.GAME_PROJECT)) {
                    file = file.clone()
                    file.setContent(null)
                }
                computer.PA.setFile(file)
                computer.systemChange = true
                return true
            }
            is RequestGame -> {
                val path = payload.path.orEmpty()
                val name = payload.name.orEmpty()
                val file = computer.MyFileSystem.getFile(path, name)

                val loadFile = HashMap<Any?, Any?>()
                val saveFile = computer.MyFileSystem.getFile("", "$name.save")
                if (saveFile != null) {
                    val data = saveFile.getContent()["data"] as String?
                    if (data != null) {
                        val entries = data.split("\n")
                        try {
                            for (entry in entries) {
                                val entryData = entry.split("\t")
                                val key = entryData[0]
                                val type = entryData[1]
                                var variable: Variable? = null
                                if (type == "string") {
                                    variable = TypeString(entryData[2])
                                } else if (type == "bool") {
                                    variable = TypeBoolean(java.lang.Boolean.valueOf(entryData[2]))
                                } else if (type == "int") {
                                    variable = TypeInteger(Integer.valueOf(entryData[2]))
                                } else if (type == "float") {
                                    variable = TypeFloat(java.lang.Float.valueOf(entryData[2]))
                                }

                                if (variable != null) {
                                    loadFile[key] = variable
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
                computer.PA.setLoadFile(loadFile)
                computer.PA.setFile(file)
                computer.systemChange = true
                return true
            }
            is SetFileDescription -> {
                val path = payload.path.orEmpty()
                val name = payload.name.orEmpty()
                val description = payload.description.orEmpty()
                val file = computer.MyFileSystem.getFile(path, name)
                if (file != null && file.type != HackerFile.CLUE) {
                    file.setDescription(description)
                    computer.PA.setFile(file)
                }
                computer.systemChange = true
                return true
            }
            is SetFilePrice -> {
                val path = payload.path.orEmpty()
                val name = payload.name.orEmpty()
                val price = payload.price ?: 0.0f
                val file = computer.MyFileSystem.getFile(path, name)
                if (file != null) {
                    file.setPrice(price)
                    computer.PA.setFile(file)
                }
                computer.systemChange = true
                return true
            }
            is DeleteFile -> {
                computer.MyFileSystem.deleteFile(payload.path.orEmpty(), payload.name.orEmpty())
                computer.PA.setRequestPrimary(true, 1)
                computer.systemChange = true
                return true
            }
            is DeleteMulti -> {
                val allFiles = payload.allFiles ?: emptyArray()
                for (entry in allFiles) {
                    val file = entry as Array<String>
                    val path = file[0]
                    val name = file[1]
                    if (file[2] == "directory") {
                        computer.MyFileSystem.deleteDirectory("$path/$name/")
                    } else {
                        computer.MyFileSystem.deleteFile(path, name)
                    }
                }
                computer.PA.setRequestPrimary(true, 1)
                computer.systemChange = true
                return true
            }
            is DecompileFile -> {
                val path = payload.location.orEmpty()
                val fileName = payload.fileName.orEmpty()
                val existingFile = computer.MyFileSystem.getFile(path, fileName)!!
                val file = existingFile.clone()
                if (existingFile.typeString == "compiled" && existingFile.maker!!.uppercase() == computer.userName!!.uppercase()) {
                    var compilePrice = payload.compileCost ?: 0.0f
                    val levels = HashMap<Any?, Any?>()
                    levels["Attack"] = Integer.valueOf(100)
                    levels["Merchanting"] = Integer.valueOf(100)
                    levels["Watch"] = Integer.valueOf(100)
                    try {
                        val result = executeCompileApplication(computer, existingFile.type, existingFile.getContent(), levels)
                        if (result != null && (result["error"] as String).length == 0) {
                            compilePrice = ((result["price"] as Double).toFloat())
                        }
                    } catch (_: Exception) {
                    }
                    existingFile.setQuantity(existingFile.quantity - 1)
                    if (existingFile.quantity <= 0) {
                        computer.MyFileSystem.deleteFile(path, existingFile.name)
                    }

                    val xp = compilePrice / 100.0f
                    var xpType = ""

                    if (existingFile.type == HackerFile.BANKING_COMPILED) {
                        xpType = "bankxp"
                    } else if (existingFile.type == HackerFile.ATTACKING_COMPILED) {
                        xpType = "attackxp"
                    } else if (existingFile.type == HackerFile.SHIPPING_COMPILED) {
                        xpType = "redirectxp"
                    } else if (existingFile.type == HackerFile.HTTP) {
                        xpType = "httpxp"
                    } else if (existingFile.type == HackerFile.WATCH_COMPILED) {
                        xpType = "watchxp"
                    }

                    computer.MyComputerHandler.addData(
                        ApplicationData(FloatCommandPayload(ApplicationCommand.of(xpType), -1.0f * xp), 0, computer.ip),
                        computer.ip
                    )

                    if (file.type != HackerFile.HTTP) {
                        file.setType(file.type + 1)
                    }
                    if (existingFile.type != HackerFile.HTTP_SCRIPT) {
                        file.setType(existingFile.type + 1)
                    } else {
                        file.setType(HackerFile.HTTP_SCRIPT)
                    }
                    file.setName(existingFile.name.replace("\\.bin".toRegex(), ""))
                    computer.MyComputerHandler.addData(
                        ApplicationData(PettyCashDeltaPayload(compilePrice), 0, computer.ip),
                        computer.ip
                    )
                    computer.saveFile(file, file, path)
                }
                return true
            }
            is SellFileMulti -> {
                val allFiles = payload.allFiles ?: emptyArray()
                val ip = payload.ip
                val compileCost = 0.0f
                var totalPay = 0.0f
                if (computer.checkBank()) {
                    for (entry in allFiles) {
                        val fileData = entry as Array<Any?>
                        val path = fileData[0] as String
                        val name = fileData[1] as String
                        val maker = fileData[2] as String
                        val quantity = fileData[3] as Integer
                        val file = computer.MyFileSystem.getFile(path, name)!!
                        if (file.type != HackerFile.NEW_FIREWALL) {
                            totalPay += (Computer.makers[maker] as Float) * quantity.toInt()
                        } else {
                            val content = file.getContent()
                            val price = content["store_price"]
                            if (price != null) {
                                totalPay += java.lang.Float.parseFloat("" + price)
                            }
                        }
                        if (file != null && file.quantity >= quantity.toInt()) {
                            file.setQuantity(file.quantity - quantity.toInt())
                            if (file.quantity <= 0) {
                                computer.MyFileSystem.deleteFile(path, file.name)
                            }

                            val salePayload = SaveFileRequestPayload(path, file.clone())
                            computer.MyComputerHandler.addData(ApplicationData(salePayload, 0, "store" + computer.serverID), computer.store)
                        }
                    }
                    computer.pettyCash += totalPay
                } else {
                    computer.addMessage(MessageHandler.SELL_FAIL_BANK_PORT)
                }
                computer.PA.setRequestPrimary(true, 1)
                computer.systemChange = true
                return true
            }
            is SaveFile -> {
                val path = payload.path.orEmpty()
                val file = payload.name ?: return true
                val existingFile = computer.MyFileSystem.getFile(path, file.name)
                computer.saveFile(file, existingFile, path)
                if (file.type == HackerFile.PCI || file.type == HackerFile.AGP) {
                    computer.PA.setRequestHardware(true)
                }
                return true
            }
            is SaveFileRequestPayload -> {
                val existingFile = computer.MyFileSystem.getFile(payload.path, payload.file.name)
                computer.saveFile(payload.file, existingFile, payload.path)
                if (payload.file.type == HackerFile.PCI || payload.file.type == HackerFile.AGP) {
                    computer.PA.setRequestHardware(true)
                }
                return true
            }
            is StolenSaveFilePayload -> {
                val existingFile = computer.MyFileSystem.getFile(payload.path, payload.file.name)
                computer.saveFile(payload.file, existingFile, payload.path)
                computer.addMessage(
                    MessageHandler.FILE_SUCCESSFULLY_STOLEN,
                    arrayOf<Any?>(payload.file.name, payload.stolenFromIp),
                    arrayOf<Any?>(payload.stolenFromPort, payload.stolenFromIp)
                )
                computer.addMessage(MessageHandler.FILE_SUCCESSFULLY_STOLEN_GAME, arrayOf<Any?>(payload.file.name, payload.stolenFromIp))
                if (payload.file.type == HackerFile.PCI || payload.file.type == HackerFile.AGP) {
                    computer.PA.setRequestHardware(true)
                }
                return true
            }
            is CompileFile -> {
                var success = true
                val path = payload.path.orEmpty()
                val file = payload.name ?: return true
                val existingFile = computer.MyFileSystem.getFile(path, file.name)

                var price = payload.price ?: 0.0f

                val playerLevels = HashMap<Any?, Any?>()
                playerLevels["Attack"] = Integer.valueOf(computer.getLevel(computer.Stats["Attack"] as Float))
                playerLevels["Merchanting"] = Integer.valueOf(computer.getLevel(computer.Stats["Bank"] as Float))
                playerLevels["Watch"] = Integer.valueOf(computer.getLevel(computer.Stats["Watch"] as Float))
                playerLevels["HTTP"] = Integer.valueOf(computer.getLevel(computer.Stats["Webdesign"] as Float))
                playerLevels["Redirecting"] = Integer.valueOf(computer.getLevel(computer.Stats["Redirecting"] as Float))

                try {
                    val result = executeCompileApplication(computer, file.type, file.getContent(), playerLevels)
                    if (result != null && (result["error"] as String).length == 0) {
                        val cpuCost = (result["cpucost"] as Double).toFloat()
                        price = (result["price"] as Double).toFloat()
                        file.setCPUCost(cpuCost)
                    }
                } catch (_: Exception) {
                    success = false
                }

                if (!computer.checkBank()) {
                    success = false
                    computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND)
                }

                if (existingFile != null && file.checkSumFailed(existingFile)) {
                    success = false
                    computer.addMessage(MessageHandler.FILE_CHANGED_SINCE_LAST_SAVE)
                }

                if (computer.pettyCash < price) {
                    success = false
                    computer.addMessage(MessageHandler.COMPILE_FAIL_NOT_ENOUGH_MONEY)
                } else if (computer.MyFileSystem.getSpaceLeft() < 1 && existingFile == null) {
                    success = false
                    computer.addMessage(MessageHandler.COMPILE_FAIL_HD_FULL)
                } else if (success) {
                    if (file.type != HackerFile.FTP_COMPILED) {
                        val xp = price / 100.0f
                        var xpType = ""

                        if (file.type == HackerFile.BANKING_COMPILED) {
                            xpType = "bankxp"
                        } else if (file.type == HackerFile.ATTACKING_COMPILED) {
                            xpType = "attackxp"
                        } else if (file.type == HackerFile.SHIPPING_COMPILED) {
                            xpType = "redirectxp"
                        } else if (file.type == HackerFile.WATCH_COMPILED) {
                            xpType = "watchxp"
                        } else if (file.type == HackerFile.HTTP) {
                            xpType = "httpxp"
                        }

                        computer.MyComputerHandler.addData(
                            ApplicationData(FloatCommandPayload(ApplicationCommand.of(xpType), xp), 0, computer.ip),
                            computer.ip
                        )
                    }
                    computer.MyComputerHandler.addData(
                        ApplicationData(PettyCashDeltaPayload(price * -1.0f), 0, computer.ip),
                        computer.ip
                    )
                    file.setMaker(computer.userName)
                    computer.saveFile(file, existingFile, path)
                }
                return true
            }
            is SellFile -> {
                val path = payload.location.orEmpty()
                val name = payload.fileName.orEmpty()
                val file = computer.MyFileSystem.getFile(path, name)?.clone() ?: return true
                handleSellFile(computer, SellFilePayload(path, file, payload.compileCost ?: 0.0f, payload.quantity ?: 1))
                return true
            }
            is SellFilePayload -> {
                handleSellFile(computer, payload)
                return true
            }
            else -> return false
        }

    }

    private fun handleSellFile(computer: Computer, payload: SellFilePayload) {
        var success = true
        val path = payload.path
        val file = payload.file
        val existingFile = computer.MyFileSystem.getFile(path, file.name)

        var sellPrice = 0.0f
        var minimumSellPrice = 0.0f
        if (file.type != HackerFile.NEW_FIREWALL) {
            minimumSellPrice = Computer.makers[file.maker] as Float
        }

        file.setQuantity(1)
        var compilePrice = payload.compileCost
        val playerLevels = HashMap<Any?, Any?>()
        playerLevels["Attack"] = Integer.valueOf(100)
        playerLevels["Merchanting"] = Integer.valueOf(100)
        playerLevels["Watch"] = Integer.valueOf(100)
        playerLevels["HTTP"] = Integer.valueOf(100)
        playerLevels["Redirecting"] = Integer.valueOf(100)

        try {
            val result = executeCompileApplication(computer, file.type, file.getContent(), playerLevels)
            if (result != null && (result["error"] as String).length == 0) {
                compilePrice = (result["price"] as Double).toFloat()
            }
        } catch (_: Exception) {
            compilePrice = 0.0f
        }

        val quantity = payload.quantity
        if (file.type == HackerFile.AGP || file.type == HackerFile.PCI) {
            if (file.maker == "Medium") {
                sellPrice = 2000.0f
            } else if (file.maker == "High") {
                sellPrice = 20000.0f
            } else if (file.maker == "Rare") {
                sellPrice = 200000.0f
            }
        } else if (existingFile != null) {
            sellPrice = compilePrice * 2.0f - (compilePrice * 0.01f * (1.0f + existingFile.quantity))
        } else {
            sellPrice = compilePrice * 2.0f - (compilePrice * 0.01f)
        }

        if (sellPrice < minimumSellPrice) {
            sellPrice = minimumSellPrice
        }

        file.setPrice(sellPrice)

        if (file.type == HackerFile.NEW_FIREWALL) {
            success = false
        }

        if (success) {
            file.setLocation("Store/")
            computer.saveFile(file, existingFile, path)

            if (file.type == HackerFile.PCI || file.type == HackerFile.AGP) {
                computer.PA.setRequestHardware(true)
            }
        }
        computer.systemChange = true
    }

    private fun executeCompileApplication(
        computer: Computer,
        type: Int,
        content: HashMap<*, *>,
        levels: HashMap<*, *>
    ): HashMap<*, *>? {
        val params = arrayOf<Any?>(Integer.valueOf(type), content, levels)
        return getSessionService(computer).executeRemote(
            LocalWebConfig.getXmlRpcUrl(),
            "hackerRPC.compileApplication",
            params
        ) as HashMap<*, *>?
    }

    private fun getSessionService(computer: Computer): ComputerSessionService {
        val field: Field = Computer::class.java.getDeclaredField("sessionService")
        field.isAccessible = true
        return field.get(computer) as ComputerSessionService
    }
}
