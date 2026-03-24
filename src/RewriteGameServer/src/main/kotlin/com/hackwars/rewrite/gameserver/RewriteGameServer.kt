package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy

data class RewriteGameServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 15020,
    val timeouts: ProtocolTimeoutPolicy = ProtocolTimeoutPolicy(),
)

class RewriteGameServerBootstrap(
    private val config: RewriteGameServerConfig = RewriteGameServerConfig(),
) {
    fun banner(): String {
        return buildString {
            append("RewriteGameServer scaffold ready on ")
            append("${config.host}:${config.port}")
            append(" with session-ticket auth, auth-timeout=")
            append(config.timeouts.authTimeout)
            append(", idle-timeout=")
            append(config.timeouts.idleTimeout)
            append(".")
        }
    }
}

fun main() {
    println(RewriteGameServerBootstrap().banner())
}
