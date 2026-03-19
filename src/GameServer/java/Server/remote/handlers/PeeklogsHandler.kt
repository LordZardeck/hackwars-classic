package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(PeekLogs.FUNCTION)
object PeekLogsHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            PeekLogs.fromRpc(
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
                PeekLogs.FUNCTION,
                null,
                port,
                ip
            ),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
