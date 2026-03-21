package game.payload

import game.ApplicationCommand
import game.ApplicationPayload

data class NoArgumentsPayload(
    private val command: ApplicationCommand
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = command
}

data class FloatCommandPayload(
    private val command: ApplicationCommand,
    val value: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = command
    fun legacyParameters(): Any = value
}

data class IntCommandPayload(
    private val command: ApplicationCommand,
    val value: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = command
    fun legacyParameters(): Any = value
}

data class BooleanCommandPayload(
    private val command: ApplicationCommand,
    val value: Boolean
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = command
    fun legacyParameters(): Any = value
}

data class StringCommandPayload(
    private val command: ApplicationCommand,
    val value: String?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = command
    fun legacyParameters(): Any? = value
}

data class MapCommandPayload(
    private val command: ApplicationCommand,
    val values: HashMap<Any, Any>
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = command
    fun legacyParameters(): Any = values
}

data class RequestEquipmentPayload(
    val windowHandle: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTEQUIPMENT.command
    fun legacyParameters(): Any = windowHandle
}

data class InstallEquipmentPayload(
    val position: Int,
    val name: String,
    val windowHandle: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.INSTALLEQUIPMENT.command
    fun legacyParameters(): Any = arrayOf<Any?>(position, name, windowHandle)
}

data class RepairEquipmentPayload(
    val position: Int,
    val name: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.REPAIREQUIPMENT.command
    fun legacyParameters(): Any = arrayOf<Any?>(position, name)
}

data class BountyHttpPayload(
    val bountyIp: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.BOUNTYHTTP.command
    fun legacyParameters(): Any = bountyIp
}

data class RequestInstallScriptPayload(
    val targetIp: String,
    val targetPort: Int,
    val path: String,
    val file: String,
    val maliciousParameters: Any?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTINSTALLSCRIPT.command
    fun legacyParameters(): Any = arrayOf<Any?>(targetIp, targetPort, path, file, maliciousParameters)
}

@Suppress("ArrayInDataClass")
data class InstallScriptPayload(
    val script: HashMap<*, *>,
    val maliciousParameters: Any?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.INSTALL_SCRIPT.command
    fun legacyParameters(): Any = arrayOf<Any?>(script, maliciousParameters)
}

object PingPayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.PING.command
}

@Suppress("ArrayInDataClass")
data class AddShowChoicesPayload(
    val choices: Array<Any?>
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.ADDSHOWCHOICES.command
    fun legacyParameters(): Any = choices
}

data class DoChallengePayload(
    val challengeFile: String,
    val challengeId: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.DOCHALLENGE.command
    fun legacyParameters(): Any = arrayOf<Any>(challengeFile, challengeId)
}

data class RequestAttackDefaultPayload(
    val target: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTATTACKDEFAULT.command
    fun legacyParameters(): Any = arrayOf<Any?>("ignored", target)
}

data class RequestSavePayload(
    val fileName: String,
    val triggerParameters: HashMap<Any, Any>?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTSAVE.command
    fun legacyParameters(): Any = arrayOf<Any?>(fileName, triggerParameters)
}

data class RequestTaskPayload(
    val fileName: String?,
    val questId: Int,
    val taskName: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTTASK.command
    fun legacyParameters(): Any = arrayOf<Any?>(fileName, questId, taskName)
}

data class TriggerWatchByIndexPayload(
    val watchNumber: Int,
    val triggerParameters: HashMap<Any, Any>?,
    val targetIp: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTTRIGGER.command
    fun legacyParameters(): Any = arrayOf<Any?>(watchNumber, triggerParameters, targetIp)
}

data class TriggerWatchByNotePayload(
    val watchNote: String,
    val triggerParameters: HashMap<Any, Any>?,
    val targetIp: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTTRIGGERNOTE.command
    fun legacyParameters(): Any = arrayOf<Any?>(watchNote, triggerParameters, targetIp)
}

@Suppress("ArrayInDataClass")
data class ZombieAttackPayload(
    val targetIp: String,
    val targetPort: Int,
    val sourceIp: String,
    val sourcePort: Int,
    val secondaryPorts: Array<Int?>?,
    val scripts: Array<Array<String?>?>?,
    val extraInfo: Array<Any?>?,
    val parentIp: String?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.ZOMBIEATTACK.command
    fun legacyParameters(): Any = arrayOf<Any?>(
        targetIp,
        targetPort,
        secondaryPorts,
        scripts,
        extraInfo,
        parentIp
    )
}

data class PettyCashDeltaPayload(
    val amount: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.PETTYCASH.command
    fun legacyParameters(): Any = amount
}

data class LaunchNetworkAttackPayload(
    val npcIp: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.LAUNCH_NETWORK_ATTACK.command
    fun legacyParameters(): Any = arrayOf<Any>(npcIp)
}
