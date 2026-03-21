package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.PortOnOff
import game.ApplicationCommand
import game.ApplicationData
import game.payload.BooleanCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.PORTONOFF)
object PortOnOffHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = PortOnOff.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                BooleanCommandPayload(
                    ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.PORTONOFF),
                    parsedCall.on ?: false
                ),
                parsedCall.port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
