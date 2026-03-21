package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.FetchPorts
import game.ApplicationCommand
import game.ApplicationData
import game.payload.NoArgumentsPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.FETCHPORTS)
object FetchPortsHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val fetchPortsCall = FetchPorts.fromRpc(rfc)
        val ip = context.crypt(fetchPortsCall.encryptedIp)
        context.computerHandler.addData(
            ApplicationData(
                NoArgumentsPayload(ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.FETCHPORTS)),
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
