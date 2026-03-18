package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(FacebookWithdraw.FUNCTION)
object FacebookWithdrawHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FacebookWithdraw.fromRpc(rfc)
        val ip = parsedCall.ip
        val amount = parsedCall.amount
        val defaultPort = parsedCall.defaultPort

        context.computerHandler.addData(
            ApplicationData(
                FacebookWithdraw.FUNCTION,
                amount,
                defaultPort,
                ip
            ), ip, ApplicationData.OUTSIDE
        )
    }
}
