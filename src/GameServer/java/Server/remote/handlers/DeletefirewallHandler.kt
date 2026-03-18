package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(DeleteFirewall.FUNCTION)
object DeletefirewallHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            DeleteFirewall.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val portID =
            parsedCall.portID
        context.computerHandler.addData(
            ApplicationData(
                DeleteFirewall.FUNCTION,
                portID,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
