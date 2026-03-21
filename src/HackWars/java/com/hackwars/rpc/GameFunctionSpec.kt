package com.hackwars.rpc

import assignments.RemoteFunctionCall
import game.ApplicationCommand

class GameFunctionSpec constructor(
    val commandSpec: GameCommandSpec,
    val payloadType: Class<out RemoteFunctionCallImpl>,
    private val parser: (RemoteFunctionCall) -> RemoteFunctionCallImpl
) {
    val wireName: String
        get() = commandSpec.wireName

    val command: ApplicationCommand
        get() = commandSpec.command

    fun fromRpc(rfc: RemoteFunctionCall): RemoteFunctionCallImpl = parser(rfc)

    override fun toString(): String = wireName
}
