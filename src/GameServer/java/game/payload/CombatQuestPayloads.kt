package game.payload

import assignments.PacketPort
import com.hackwars.game.program.AttackProgram
import com.hackwars.game.program.ShippingProgram
import game.ApplicationCommand
import game.ApplicationPayload
import game.Computer
import game.HackerFileInterop
import game.Port

data class CombatDamageValues(
    val xp: Float,
    val health: Float,
    val pettyCash: Float,
    val cpuCost: Float,
    val damage: Float
)

data class CombatFirewallXpPayload(
    val amount: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.FIREWALLXP.command
}

data class CombatOpponentUpdatePayload(
    val values: CombatDamageValues,
    val targetWatch: Boolean,
    val damageFromFirewall: Boolean,
    val mining: Boolean
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.OPPONENTUPDATE.command
}

data class CombatAttackXpAwardPayload(
    val amount: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.ATTACKXP.command
}

data class CombatAttackXpUpdatePayload(
    val values: CombatDamageValues,
    val targetWatch: Boolean,
    val damageFromFirewall: Boolean,
    val mining: Boolean,
    val targetIp: String? = null
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.ATTACKXP.command
}

data class CombatMiningDamageUpdatePayload(
    val values: CombatDamageValues,
    val targetWatch: Boolean,
    val damageFromFirewall: Boolean,
    val mining: Boolean,
    val targetIp: String? = null
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.MININGDAMAGEUPDATE.command
}

data class CombatRequestScanPayload(
    val targetIp: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTSCAN.command
}

data class CombatScanPayload(
    val opponentFirewall: Int,
    val packetPorts: Array<PacketPort?>,
    val defaultBank: Int,
    val defaultAttack: Int,
    val defaultFtp: Int,
    val defaultHttp: Int,
    val npc: Boolean,
    val defaultShipping: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.SCAN.command
}

data class CombatScanXpPayload(
    val amount: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.SCANXP.command
}

object CombatScanSuccessPayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.SCANSUCCESS.command
}

data class CombatCheckBountyPayload(
    val fileName: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.CHECKBOUNTY.command
}

data class CombatMakeBountyPayload(
    val anonymous: Boolean,
    val target: String,
    val type: Int,
    val fileName: String,
    val filePath: String,
    val iterations: Int,
    val reward: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.MAKEBOUNTY.command
}

data class CombatChangeNetworkPayload(
    val network: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.CHANGENETWORK.command
}

data class CombatChangeNetwork2Payload(
    val network: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.CHANGENETWORK2.command
}

data class CombatQuestInformationPayload(
    val questParameters: HashMap<Any?, Any?>,
    val interestedQuests: ArrayList<Any?>
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.QUESTINFORMATION.command
}

data class CombatGiveExperiencePayload(
    val stat: String,
    val amount: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.GIVEEXPERIENCE.command
}

data class CombatGiveTaskPayload(
    val taskName: String,
    val taskLabel: String,
    val questId: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.GIVETASK.command
}

data class CombatSetTaskPayload(
    val taskName: String,
    val questId: Int,
    val setTo: Boolean
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.SETTASK.command
}

data class CombatCompleteTaskPayload(
    val taskName: String,
    val questId: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.COMPLETETASK.command
}

data class CombatGiveCommodityPayload(
    val commodityType: Int,
    val amount: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.GIVECOMMODITY.command
}

data class CombatGiveAccessPayload(
    val accessNetwork: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.GIVEACCESS.command
}

data class CombatGiveFilePayload(
    val fileId: String,
    val quantity: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.GIVEFILE.command
}

data class CombatTakeFilePayload(
    val fileId: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.TAKEFILE.command
}

data class CombatTakeFile2Payload(
    val fileId: String,
    val quantity: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.TAKEFILE2.command
}

data class CombatFinishQuestPayload(
    val questId: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.FINISHQUEST.command
}

data class CombatGiveQuestPayload(
    val questId: Int,
    val description: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.GIVEQUEST.command
}

data class CombatTakeMoneyPayload(
    val amount: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.TAKEMONEY.command
}

data class CombatTakeCommodityPayload(
    val amount: Float,
    val commodityType: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.TAKECOMMODITY.command
}

data class CombatExchangeCommodityPayload(
    val exchangeAmount: Int,
    val commodityType: Int,
    val exchangeCost: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.EXCHANGECOMMODITY.command
}

data class CombatExchangeFilePayload(
    val exchangeAmount: Int,
    val commodityType: Int,
    val exchangeCost: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.EXCHANGEFILE.command
}

data class CombatQuestTaskState(
    val completed: Boolean,
    val label: String
)

data class CombatQuestState(
    val tasks: HashMap<String, CombatQuestTaskState>,
    val label: String
)

data class CombatCompletedQuestState(
    val questId: Int,
    val label: String
)

object CombatQuestSupport {
    @JvmStatic
    fun isAttackXpAwardPayload(payload: ApplicationPayload): Boolean {
        return payload is CombatAttackXpAwardPayload
    }

    @JvmStatic
    fun attackXpAwardAmount(payload: ApplicationPayload): Float {
        return (payload as CombatAttackXpAwardPayload).amount
    }

    @JvmStatic
    fun windowHandle(port: Port?): Int {
        val program = port?.program
        return when (program) {
            is AttackProgram -> program.getWindowHandle()
            is ShippingProgram -> program.getWindowHandle()
            else -> 0
        }
    }

    @JvmStatic
    fun currentQuest(computer: Computer, questId: Int): CombatQuestState? {
        return toQuestState(computer.CurrentQuests[questId])
    }

    @JvmStatic
    fun putCurrentQuest(computer: Computer, questId: Int, state: CombatQuestState) {
        computer.CurrentQuests[questId] = arrayOf<Any?>(state.tasks, state.label)
    }

    @JvmStatic
    fun addTask(computer: Computer, questId: Int, taskName: String, taskLabel: String) {
        val quest = currentQuest(computer, questId) ?: CombatQuestState(HashMap(), "")
        quest.tasks[taskName] = CombatQuestTaskState(false, taskLabel)
        putCurrentQuest(computer, questId, quest)
    }

    @JvmStatic
    fun setTask(computer: Computer, questId: Int, taskName: String, setTo: Boolean) {
        val quest = currentQuest(computer, questId) ?: CombatQuestState(HashMap(), "")
        val existing = quest.tasks[taskName]
        val label = existing?.label ?: taskName
        quest.tasks[taskName] = CombatQuestTaskState(setTo, label)
        putCurrentQuest(computer, questId, quest)
    }

    @JvmStatic
    fun completeTask(computer: Computer, questId: Int, taskName: String) {
        setTask(computer, questId, taskName, true)
    }

    @JvmStatic
    fun finishQuest(computer: Computer, questId: Int): CombatCompletedQuestState? {
        val quest = toQuestState(computer.CurrentQuests.remove(questId)) ?: return null
        computer.CompletedQuests.add(arrayOf<Any?>(questId, quest.label))
        return CombatCompletedQuestState(questId, quest.label)
    }

    @JvmStatic
    fun buildQuestInformation(
        computer: Computer,
        parameters: HashMap<Any?, Any?>,
        interestedQuests: ArrayList<Any?>
    ): HashMap<Any?, Any?> {
        val response = parameters

        val questItems = computer.MyFileSystem.getFilesOfType(game.HackerFile.QUEST_ITEM)
        if (questItems != null) {
            for (index in questItems.indices) {
                val file = questItems[index] as game.HackerFile
                val itemName = HackerFileInterop.visualItemContent(file)?.itemName ?: continue
                response[itemName] = "true"
                response["${itemName}_quantity"] = file.quantity.toString()
            }
        }

        for (i in interestedQuests.indices) {
            val questId = interestedQuests[i] as? Int ?: continue
            val quest = currentQuest(computer, questId) ?: continue
            for ((taskName, taskState) in quest.tasks) {
                response[taskName] = taskState.completed.toString()
            }
        }

        for (i in computer.CompletedQuests.indices) {
            val completed = toCompletedQuestState(computer.CompletedQuests[i])
            if (completed != null) {
                response["quest${completed.questId}"] = "true"
            }
        }

        for ((key, value) in computer.CurrentQuests.entries) {
            val questId = key as? Int ?: continue
            response["quest$questId"] = "false"
        }

        for (i in computer.commodityAmount.indices) {
            response["commodity$i"] = computer.commodityAmount[i].toString()
        }

        response["Attack"] = computer.attackLevel.toString()
        response["Bank"] = computer.bankLevel.toString()
        response["Watch"] = computer.watchLevel.toString()
        response["Scanning"] = computer.scanningLevel.toString()
        response["FireWall"] = computer.fireWallLevel.toString()
        response["HTTP"] = computer.hTTPLevel.toString()
        response["pettycash"] = computer.pettyCash.toString()
        response["Redirecting"] = computer.redirectingLevel.toString()
        response["Repair"] = computer.repairLevel.toString()

        response["defaultattack"] = computer.getDefaultAttack().toString()
        response["defaultbank"] = computer.getDefaultBank().toString()
        response["defaulthttp"] = computer.getDefaultHTTP().toString()
        response["defaultredirecting"] = computer.getDefaultShipping().toString()
        response["repaired"] = computer.getRepaired().toString()
        response[computer.network] = "true"
        response["websitemade"] = (computer.pageBody.length > 1).toString()
        response["firewallinstalled"] = computer.checkFirewall().toString()
        response["watchinstalled"] = (computer.watchHandler.watchCount > 0).toString()
        return response
    }

    @JvmStatic
    fun addDamage(
        computer: Computer,
        windowHandle: Int,
        amount: Float,
        damageFromFirewall: Boolean,
        mining: Boolean
    ) {
        computer.Damage.add(arrayOf<Any?>(windowHandle, amount, damageFromFirewall, mining))
    }

    @JvmStatic
    fun addDamage(
        computer: Computer,
        windowHandle: Int,
        amount: Float,
        targetIp: String,
        damageFromFirewall: Boolean,
        mining: Boolean
    ) {
        computer.Damage.add(arrayOf<Any?>(windowHandle, amount, targetIp, damageFromFirewall, mining))
    }

    private fun toQuestState(raw: Any?): CombatQuestState? {
        return when (raw) {
            null -> null
            is CombatQuestState -> raw
            is Array<*> -> {
                val tasks = HashMap<String, CombatQuestTaskState>()
                val rawTasks = raw.getOrNull(0) as? Map<*, *> ?: HashMap<Any?, Any?>()
                for ((key, value) in rawTasks) {
                    val taskName = key as? String ?: continue
                    val taskState = toTaskState(value) ?: CombatQuestTaskState(false, taskName)
                    tasks[taskName] = taskState
                }
                val label = raw.getOrNull(1) as? String ?: ""
                CombatQuestState(tasks, label)
            }

            else -> null
        }
    }

    private fun toTaskState(raw: Any?): CombatQuestTaskState? {
        return when (raw) {
            null -> null
            is CombatQuestTaskState -> raw
            is Array<*> -> {
                val completed = raw.getOrNull(0) as? Boolean ?: false
                val label = raw.getOrNull(1) as? String ?: ""
                CombatQuestTaskState(completed, label)
            }

            else -> null
        }
    }

    private fun toCompletedQuestState(raw: Any?): CombatCompletedQuestState? {
        return when (raw) {
            null -> null
            is CombatCompletedQuestState -> raw
            is Array<*> -> {
                val questId = raw.getOrNull(0) as? Int ?: return null
                val label = raw.getOrNull(1) as? String ?: ""
                CombatCompletedQuestState(questId, label)
            }

            else -> null
        }
    }
}
