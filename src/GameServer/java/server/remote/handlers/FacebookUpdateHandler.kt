package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.FacebookUpdate
import game.ApplicationCommand
import game.ApplicationData
import game.payload.NoArgumentsPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.FACEBOOKUPDATE)
object FacebookUpdateHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FacebookUpdate.fromRpc(rfc)
        val ip = parsedCall.ip.orEmpty()
        context.computerHandler.addData(
            ApplicationData(
                NoArgumentsPayload(ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.FACEBOOKUPDATE)),
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
