package game.payload

import com.hackwars.rpc.GameCommands
import game.ApplicationCommand
import game.ApplicationPayload

object HealPayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = GameCommands.HEAL.command
    fun legacyParameters(): Any? = null
}

data class EmptyPettyCashPayload(
    val windowHandle: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = GameCommands.EMPTY_PETTY_CASH.command
    fun legacyParameters(): Any = windowHandle

    companion object {
        fun fromLegacy(parameters: Any?): EmptyPettyCashPayload {
            return EmptyPettyCashPayload(parameters as Int)
        }
    }
}

object FinalizeCancelledPayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = GameCommands.FINALIZECANCELLED.command
    fun legacyParameters(): Any? = null
}

data class DeleteLogPayload(
    val ipAddress: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = GameCommands.DELETELOG.command
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
    override fun getCommand(): ApplicationCommand = GameCommands.EDIT_LOGS.command
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
    override fun getCommand(): ApplicationCommand = GameCommands.CHANGEDAILYPAY.command
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
    override fun getCommand(): ApplicationCommand = GameCommands.DESTROY_WATCH.command
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

data class AttackInitializePayload(
    val xp: Float,
    val health: Float,
    val pettyCash: Float,
    val cpuCost: Float,
    val targetWatch: Boolean,
    val npc: Boolean
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = GameCommands.ATTACKINITIALIZE.command
    fun legacyParameters(): Any = arrayOf<Any?>(
        arrayOf(xp, health, pettyCash, cpuCost),
        targetWatch,
        npc
    )
}

data class CancelAttackPayload(
    val heal: Boolean? = null
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = GameCommands.CANCELATTACK.command
    fun legacyParameters(): Any? = heal

    companion object {
        fun fromLegacy(parameters: Any?): CancelAttackPayload {
            return CancelAttackPayload(parameters as? Boolean)
        }
    }
}

object FreezePayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = GameCommands.FREEZE.command
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
    override fun getCommand(): ApplicationCommand = GameCommands.DAMAGE.command
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
    override fun getCommand(): ApplicationCommand = GameCommands.ATTACKFINALIZE.command
    fun legacyParameters(): Any = portType
}
