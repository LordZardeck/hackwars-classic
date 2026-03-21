package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.UninstallPort
import game.ApplicationCommand
import game.ApplicationData
import game.payload.IntCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.UNINSTALLPORT)
object UninstallPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = UninstallPort.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                IntCommandPayload(
                    ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.UNINSTALLPORT),
                    parsedCall.port
                ), parsedCall.port, ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
