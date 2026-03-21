package game.payload

import game.ApplicationCommand
import game.ApplicationPayload

val ATTACK_CONTINUE_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.ATTACKCONTINUE.command
val REQUEST_CANCEL_ATTACK_COMMAND: ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTCANCELATTACK.command

data object AttackContinuePayload : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ATTACK_CONTINUE_COMMAND
}

data class AttackInstallScriptPayload(
    val script: HashMap<*, *>?,
    val maliciousParameters: Any?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.INSTALL_SCRIPT.command
    fun legacyParameters(): Any = arrayOf<Any?>(script, maliciousParameters)
}
