package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(PortOnOff.FUNCTION)
object PortonoffHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            PortOnOff.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val port =
            parsedCall.port
        val on =
            parsedCall.on
        context.computerHandler.addData(
            ApplicationData(
                PortOnOff.FUNCTION,
                on,
                port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
