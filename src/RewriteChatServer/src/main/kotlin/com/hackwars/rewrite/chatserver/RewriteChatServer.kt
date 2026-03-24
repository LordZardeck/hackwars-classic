package com.hackwars.rewrite.chatserver

import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy

data class RewriteChatServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 15025,
    val timeouts: ProtocolTimeoutPolicy = ProtocolTimeoutPolicy(),
)

class RewriteChatServerBootstrap(
    private val config: RewriteChatServerConfig = RewriteChatServerConfig(),
) {
    fun banner(): String {
        return buildString {
            append("RewriteChatServer scaffold ready on ")
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
    println(RewriteChatServerBootstrap().banner())
}
