package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.FacebookWithdraw
import game.ApplicationCommand
import game.ApplicationData
import game.payload.FloatCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.FACEBOOKWITHDRAW)
object FacebookWithdrawHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FacebookWithdraw.fromRpc(rfc)
        val ip = parsedCall.ip.orEmpty()
        context.computerHandler.addData(
            ApplicationData(
                FloatCommandPayload(
                    ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.WITHDRAW),
                    parsedCall.amount ?: 0.0f
                ),
                parsedCall.defaultPort,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
