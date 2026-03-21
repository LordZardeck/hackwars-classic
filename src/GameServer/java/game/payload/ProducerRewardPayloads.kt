package game.payload

import game.ApplicationCommand
import game.ApplicationPayload

data class ChallengeResultsPayload(
    val rewardMoney: Float,
    val rewardXp: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.CHALLENGERESULTS.command
    fun legacyParameters(): Any = arrayOf<Any?>(rewardMoney, rewardXp)
}

data class CheckBountyPayload(
    val fileName: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.CHECKBOUNTY.command
    fun legacyParameters(): Any = fileName
}

data class RequestNetworkHopPayload(
    val computerIp: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = com.hackwars.rpc.GameCommands.REQUESTNETWORKHOP.command
    fun legacyParameters(): Any = computerIp
}
