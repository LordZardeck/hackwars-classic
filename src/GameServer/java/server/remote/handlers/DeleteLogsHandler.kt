package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationCommand
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.NoArgumentsPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(DeleteLogs.FUNCTION)
object DeleteLogsHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = DeleteLogs.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(NoArgumentsPayload(ApplicationCommand.of(DeleteLogs.FUNCTION)), 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
