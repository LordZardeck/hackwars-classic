package server.remote

import game.ComputerHandler

class RemoteCallContext(
    val computerHandler: ComputerHandler,
    private val cryptIp: (String) -> String
) {
    fun crypt(ip: String): String = cryptIp(ip)
}
