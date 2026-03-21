package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.PeekLogs
import game.ApplicationCommand
import game.ApplicationData
import game.payload.NoArgumentsPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.PEEKLOGS)
object PeekLogsHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = PeekLogs.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                NoArgumentsPayload(ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.PEEKLOGS)),
                parsedCall.port,
                ip
            ),
            parsedCall.targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
