package game.payload

import game.ApplicationCommand
import game.ApplicationPayload
import game.HackerFile

val REQUEST_SECONDARY_DIRECTORY_COMMAND: ApplicationCommand =
    com.hackwars.rpc.GameCommands.REQUESTSECONDARYDIRECTORY.command
val REQUEST_FTP_UPDATE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTFTPUPDATE.command
val GET_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.GET.command
val PUT_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.PUT.command
val FINALIZE_PUT_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.FINALIZEPUT.command

data class RequestSecondaryDirectoryPayload(
    val targetIp: String,
    val path: String,
    val requestId: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = REQUEST_SECONDARY_DIRECTORY_COMMAND
    fun legacyParameters(): Any = arrayOf<Any?>(targetIp, path, requestId)

    companion object {
        fun fromLegacy(parameters: Any?): RequestSecondaryDirectoryPayload {
            val values = parameters as Array<Any?>
            return RequestSecondaryDirectoryPayload(
                targetIp = values[0] as String,
                path = values[1] as String,
                requestId = values[2] as Int
            )
        }
    }
}

object RequestFtpUpdatePayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = REQUEST_FTP_UPDATE_COMMAND
    fun legacyParameters(): Any? = null
}

data class GetFilePayload(
    val targetIp: String,
    val name: String?,
    val fetchPath: String?,
    val targetPath: String,
    val password: String,
    val quantity: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = GET_COMMAND
    fun legacyParameters(): Any = arrayOf<Any?>(
        targetIp,
        name,
        fetchPath,
        targetPath,
        password,
        quantity
    )

    companion object {
        fun fromLegacy(parameters: Any?): GetFilePayload {
            val values = parameters as Array<Any?>
            return GetFilePayload(
                targetIp = values[0] as String,
                name = values[1] as String?,
                fetchPath = values[2] as String?,
                targetPath = values[3] as String,
                password = values[4] as String,
                quantity = values[5] as Int
            )
        }
    }
}

data class PutFilePayload(
    val targetIp: String,
    val name: String?,
    val fetchPath: String,
    val targetPath: String?,
    val password: String,
    val quantity: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = PUT_COMMAND
    fun legacyParameters(): Any = arrayOf<Any?>(
        targetIp,
        name,
        fetchPath,
        targetPath,
        password,
        quantity
    )

    companion object {
        fun fromLegacy(parameters: Any?): PutFilePayload {
            val values = parameters as Array<Any?>
            return PutFilePayload(
                targetIp = values[0] as String,
                name = values[1] as String?,
                fetchPath = values[2] as String,
                targetPath = values[3] as String?,
                password = values[4] as String,
                quantity = values[5] as Int
            )
        }
    }
}

data class FinalizePutPayload(
    val senderIp: String,
    val name: String?,
    val fetchPath: String,
    val targetPath: String,
    val password: String,
    val file: HackerFile
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = FINALIZE_PUT_COMMAND
    fun legacyParameters(): Any = arrayOf<Any?>(
        senderIp,
        name,
        fetchPath,
        targetPath,
        password,
        file
    )

    companion object {
        fun fromLegacy(parameters: Any?): FinalizePutPayload {
            val values = parameters as Array<Any?>
            return FinalizePutPayload(
                senderIp = values[0] as String,
                name = values[1] as String?,
                fetchPath = values[2] as String,
                targetPath = values[3] as String,
                password = values[4] as String,
                file = values[5] as HackerFile
            )
        }
    }
}

data class MalGetPayload(
    val targetIp: String,
    val name: String?,
    val fetchPath: String,
    val targetPath: String,
    val password: String?,
    val sourcePort: Int?,
    val attackPort: Int?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = MALGET_COMMAND

    fun legacyParameters(): Any = if (sourcePort == null) {
        arrayOf<Any?>(targetIp, name, fetchPath, targetPath, password, attackPort)
    } else {
        arrayOf<Any?>(targetIp, name, fetchPath, targetPath, password, sourcePort, attackPort)
    }

    companion object {
        fun fromLegacy(parameters: Any?): MalGetPayload {
            val values = parameters as Array<Any?>
            return if (values.size == 6) {
                MalGetPayload(
                    targetIp = values[0] as String,
                    name = values[1] as String?,
                    fetchPath = values[2] as String,
                    targetPath = values[3] as String,
                    password = values[4] as String?,
                    sourcePort = null,
                    attackPort = values[5] as Int
                )
            } else {
                MalGetPayload(
                    targetIp = values[0] as String,
                    name = values[1] as String?,
                    fetchPath = values[2] as String,
                    targetPath = values[3] as String,
                    password = values[4] as String?,
                    sourcePort = values[5] as Int,
                    attackPort = values[6] as Int
                )
            }
        }
    }
}
