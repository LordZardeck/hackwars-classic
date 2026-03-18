package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(PeekCode.FUNCTION)
object PeekcodeHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            PeekCode.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val targetIP =
            parsedCall.targetIP
        val port =
            parsedCall.port
        context.computerHandler.addData(
            ApplicationData(
                PeekCode.FUNCTION,
                null,
                port,
                ip
            ),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
