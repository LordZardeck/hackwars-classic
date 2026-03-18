package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("withdraw")
object WithdrawHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            Withdraw.fromRpc(
                rfc
            )
        val amount =
            parsedCall.amount
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val port =
            parsedCall.port
        context.computerHandler.addData(
            ApplicationData(
                "withdraw",
                amount,
                port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
