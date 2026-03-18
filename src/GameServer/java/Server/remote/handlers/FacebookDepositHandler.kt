package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("facebookdeposit")
object FacebookDepositHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FacebookDeposit.fromRpc(rfc)
        val ip = parsedCall.ip
        val amount = parsedCall.amount
        val defaultPort = parsedCall.defaultPort

        context.computerHandler.addData(
            ApplicationData(
                "deposit",
                amount,
                defaultPort,
                ip
            ), ip, ApplicationData.OUTSIDE
        )
    }
}
