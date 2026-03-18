package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestCancelAttack.FUNCTION)
object RequestcancelattackHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            RequestCancelAttack.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val port =
            parsedCall.port
        context.computerHandler.addData(
            ApplicationData(
                RequestCancelAttack.FUNCTION,
                null,
                port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
