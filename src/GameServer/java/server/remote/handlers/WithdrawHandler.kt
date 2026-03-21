package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.Withdraw
import game.ApplicationCommand
import game.ApplicationData
import game.payload.FloatCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.WITHDRAW)
object WithdrawHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = Withdraw.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                FloatCommandPayload(
                    ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.WITHDRAW),
                    parsedCall.amount
                ), parsedCall.port, ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
