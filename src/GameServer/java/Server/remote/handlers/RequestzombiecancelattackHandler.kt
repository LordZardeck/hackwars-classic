package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("requestzombiecancelattack")
object RequestzombiecancelattackHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            RequestZombieCancelAttack.fromRpc(
                rfc
            )
        val ip =
            parsedCall.ip
        val port =
            parsedCall.port
        var targetIP =
            parsedCall.targetIP
        targetIP =
            context.crypt(
                targetIP)
        context.computerHandler.addData(
            ApplicationData(
                "requestcancelattack",
                null,
                port,
                targetIP
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
