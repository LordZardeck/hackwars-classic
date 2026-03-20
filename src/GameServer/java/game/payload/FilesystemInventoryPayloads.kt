package game.payload

import game.ApplicationCommand
import game.ApplicationPayload
import game.HackerFile

data class RequestDirectoryPayload(
    val path: String,
    val requestId: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("requestdirectory")
    fun legacyParameters(): Any = arrayOf(path, requestId)
}

sealed interface DeliveredDirectoryPayload : ApplicationPayload

data class DeliveredDirectoryToPlayerPayload(
    val directory: Array<Any?>
) : DeliveredDirectoryPayload, ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("delivereddirectory")
    fun legacyParameters(): Any = directory
}

data class DeliveredDirectoryToNpcPayload(
    val directory: Array<Any?>,
    val npcOnly: Boolean
) : DeliveredDirectoryPayload, ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("delivereddirectory")
    fun legacyParameters(): Any = arrayOf(directory, npcOnly)
}

data class SaveFileRequestPayload(
    val path: String,
    val file: HackerFile
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("savefile")
    fun legacyParameters(): Any = arrayOf(path, file)
}

data class StolenSaveFilePayload(
    val path: String,
    val file: HackerFile,
    val stolenFromIp: String,
    val stolenFromPort: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("savefile")
    fun legacyParameters(): Any = arrayOf(path, file, stolenFromIp, stolenFromPort)
}

data class SellFilePayload(
    val path: String,
    val file: HackerFile,
    val compileCost: Float,
    val quantity: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("sellfile")
    fun legacyParameters(): Any = arrayOf(path, file, compileCost, quantity)
}
