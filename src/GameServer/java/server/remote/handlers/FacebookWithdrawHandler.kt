package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationCommand
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.FloatCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(FacebookWithdraw.FUNCTION)
object FacebookWithdrawHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FacebookWithdraw.fromRpc(rfc)
        val ip = parsedCall.ip.orEmpty()
        context.computerHandler.addData(
            ApplicationData(
                FloatCommandPayload(ApplicationCommand.of(Withdraw.FUNCTION), parsedCall.amount ?: 0.0f),
                parsedCall.defaultPort,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
