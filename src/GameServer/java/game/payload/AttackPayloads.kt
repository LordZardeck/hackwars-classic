package game.payload

import game.ApplicationCommand
import game.ApplicationPayload
import java.util.HashMap

val ATTACK_CONTINUE_COMMAND: ApplicationCommand = ApplicationCommand.of("attackcontinue")
val REQUEST_CANCEL_ATTACK_COMMAND: ApplicationCommand = ApplicationCommand.of("requestcancelattack")

data object AttackContinuePayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ATTACK_CONTINUE_COMMAND
}

data class AttackInstallScriptPayload(
    val script: HashMap<*, *>?,
    val maliciousParameters: Any?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("installScript")
    fun legacyParameters(): Any = arrayOf<Any?>(script, maliciousParameters)
}
