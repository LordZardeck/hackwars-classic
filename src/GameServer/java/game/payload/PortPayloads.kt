package game.payload

import game.ApplicationCommand
import game.ApplicationPayload

val HEAL_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.HEAL.command
val EMPTY_PETTY_CASH_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.EMPTY_PETTY_CASH.command
val FINALIZE_CANCELLED_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.FINALIZECANCELLED.command
val MALGET_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.MALGET.command
val DELETE_LOG_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.DELETELOG.command
val PEEK_CODE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.PEEKCODE.command
val PEEK_LOGS_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.PEEKLOGS.command
val EDIT_LOGS_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.EDIT_LOGS.command
val CHANGE_DAILY_PAY_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.CHANGEDAILYPAY.command
val DESTROY_WATCH_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.DESTROY_WATCH.command
val ATTACK_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.ATTACK.command
val MINE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.MINE.command
val ATTACK_INITIALIZE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.ATTACKINITIALIZE.command
val CANCEL_ATTACK_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.CANCELATTACK.command
val FREEZE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.FREEZE.command
val DAMAGE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.DAMAGE.command
val OPPONENT_UPDATE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.OPPONENTUPDATE.command
val ATTACK_XP_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.ATTACKXP.command
val MINING_DAMAGE_UPDATE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.MININGDAMAGEUPDATE.command
val ATTACK_FINALIZE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.ATTACKFINALIZE.command
val LOG_MESSAGE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.LOGMESSAGE.command
val ZOMBIE_ATTACK_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.ZOMBIEATTACK.command

object HealPayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = HEAL_COMMAND
    fun legacyParameters(): Any? = null
}

data class EmptyPettyCashPayload(
    val windowHandle: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = EMPTY_PETTY_CASH_COMMAND
    fun legacyParameters(): Any = windowHandle

    companion object {
        fun fromLegacy(parameters: Any?): EmptyPettyCashPayload {
            return EmptyPettyCashPayload(parameters as Int)
        }
    }
}

object FinalizeCancelledPayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = FINALIZE_CANCELLED_COMMAND
    fun legacyParameters(): Any? = null
}

data class DeleteLogPayload(
    val ipAddress: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = DELETE_LOG_COMMAND
    fun legacyParameters(): Any = ipAddress

    companion object {
        fun fromLegacy(parameters: Any?): DeleteLogPayload {
            return DeleteLogPayload(parameters as String)
        }
    }
}

data class EditLogsPayload(
    val data: String,
    val replace: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = EDIT_LOGS_COMMAND
    fun legacyParameters(): Any = arrayOf<Any?>(data, replace)

    companion object {
        fun fromLegacy(parameters: Any?): EditLogsPayload {
            val values = parameters as Array<Any?>
            return EditLogsPayload(
                data = values[0] as String,
                replace = values[1] as String
            )
        }
    }
}

data class ChangeDailyPayPayload(
    val targetIp: String,
    val windowHandle: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = CHANGE_DAILY_PAY_COMMAND
    fun legacyParameters(): Any = arrayOf<Any?>(targetIp, windowHandle)

    companion object {
        fun fromLegacy(parameters: Any?): ChangeDailyPayPayload {
            val values = parameters as Array<Any?>
            return ChangeDailyPayPayload(
                targetIp = values[0] as String,
                windowHandle = values[1] as Int
            )
        }
    }
}

object DestroyWatchPayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = DESTROY_WATCH_COMMAND
    fun legacyParameters(): Any? = null
}

sealed interface PortEntryPayload : ApplicationPayload {
    val network: String
    fun responseTargetIp(defaultTargetIp: String): String
}

data class LocalPortEntryPayload(
    private val command: ApplicationCommand,
    override val network: String
) : PortEntryPayload {
    override fun getCommand(): ApplicationCommand = command
    fun legacyParameters(): Any = network
    override fun responseTargetIp(defaultTargetIp: String): String = defaultTargetIp
}

data class RedirectedPortEntryPayload(
    private val command: ApplicationCommand,
    val targetIp: String,
    override val network: String
) : PortEntryPayload {
    override fun getCommand(): ApplicationCommand = command
    fun legacyParameters(): Any = arrayOf(targetIp, network)
    override fun responseTargetIp(defaultTargetIp: String): String = targetIp
}

fun legacyPortEntryPayload(command: ApplicationCommand, parameters: Any?): PortEntryPayload {
    return when (parameters) {
        is String -> LocalPortEntryPayload(command, parameters)
        is Array<*> -> RedirectedPortEntryPayload(
            command = command,
            targetIp = parameters[0] as String,
            network = parameters[1] as String
        )

        else -> throw IllegalStateException("Unsupported port entry payload for ${command.wireName()}: ${parameters?.javaClass?.name}")
    }
}

data class AttackInitializePayload(
    val xp: Float,
    val health: Float,
    val pettyCash: Float,
    val cpuCost: Float,
    val targetWatch: Boolean,
    val npc: Boolean
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ATTACK_INITIALIZE_COMMAND
    fun legacyParameters(): Any = arrayOf<Any?>(
        arrayOf(xp, health, pettyCash, cpuCost),
        targetWatch,
        npc
    )
}

data class CancelAttackPayload(
    val heal: Boolean? = null
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = CANCEL_ATTACK_COMMAND
    fun legacyParameters(): Any? = heal

    companion object {
        fun fromLegacy(parameters: Any?): CancelAttackPayload {
            return CancelAttackPayload(parameters as? Boolean)
        }
    }
}

object FreezePayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = FREEZE_COMMAND
    fun legacyParameters(): Any? = null
}

data class DamagePayload(
    val damage: Float,
    val targetIp: String?,
    val targetPort: Int,
    val damageFromFireWall: Boolean,
    val zombieSource: String?,
    val windowHandle: Int,
    val commodityId: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = DAMAGE_COMMAND
    fun legacyParameters(): Any = arrayOf<Any?>(
        damage,
        targetIp,
        targetPort,
        damageFromFireWall,
        zombieSource,
        windowHandle,
        commodityId
    )

    companion object {
        fun fromLegacy(parameters: Any?): DamagePayload {
            val values = parameters as Array<Any?>
            return DamagePayload(
                damage = values[0] as Float,
                targetIp = values[1] as String?,
                targetPort = values[2] as Int,
                damageFromFireWall = values[3] as Boolean,
                zombieSource = values[4] as String?,
                windowHandle = values[5] as Int,
                commodityId = values[6] as Int
            )
        }
    }
}

data class CombatResolutionPayload(
    private val command: ApplicationCommand,
    val xp: Float,
    val health: Float,
    val pettyCash: Float,
    val cpuCost: Float,
    val appliedDamage: Float,
    val targetWatch: Boolean,
    val damageFromFireWall: Boolean,
    val mining: Boolean,
    val zombieSource: String? = null
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = command

    fun legacyParameters(): Any {
        val values = arrayOf(xp, health, pettyCash, cpuCost, appliedDamage)
        return if (zombieSource == null) {
            arrayOf<Any?>(values, targetWatch, damageFromFireWall, mining)
        } else {
            arrayOf<Any?>(values, targetWatch, zombieSource, damageFromFireWall, mining)
        }
    }
}

data class AttackFinalizePayload(
    val portType: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ATTACK_FINALIZE_COMMAND
    fun legacyParameters(): Any = portType
}
